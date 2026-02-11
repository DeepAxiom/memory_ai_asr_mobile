package com.voiceobject.sdk.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.voiceobject.sdk.core.config.LlmModelVersion
import com.voiceobject.sdk.core.config.SttProvider
import com.voiceobject.sdk.core.engines.LlmEngine
import com.voiceobject.sdk.core.engines.VoiceEngine
import com.voiceobject.sdk.core.prompt.SchemaInference
import com.voiceobject.sdk.core.utils.ModelPaths
import kotlinx.coroutines.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class VoiceObjectClient private constructor(
    private val context: Context,
    private val modelPaths: ModelPaths,
    private val schema: Map<String, String>,
    private val maxTokens: Int,
    private val topK: Int,
    private val additionalSystemInstruction: String?,
    private val sttProvider: SttProvider
) {

    private val voiceEngine = VoiceEngine(context)
    private val llmEngine = LlmEngine(context)
    private val promptBuilder = SchemaInference()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var isReady = false
    private var listener: VoiceObjectListener? = null

    fun setListener(listener: VoiceObjectListener) {
        this.listener = listener
    }

    fun initialize() {
        scope.launch {
            try {
                llmEngine.setup(
                    modelPath = modelPaths.gemmaModel,
                    maxTokens = maxTokens,
                    topK = topK
                )

                if (sttProvider == SttProvider.WHISPER_ONNX) {
                    voiceEngine.initWhisper(
                        modelPaths.whisperEncoder,
                        modelPaths.whisperDecoder,
                        modelPaths.whisperTokens
                    )
                }

                isReady = true
                withContext(Dispatchers.Main) { listener?.onReady(true) }

            } catch (e: Exception) {
                Log.e("VoiceObjectSDK", "Init Error", e)
                withContext(Dispatchers.Main) {
                    listener?.onReady(false)
                    listener?.onError(e.message ?: "Unknown Error")
                }
            }
        }
    }

    fun startAction() {
        if (!isReady) {
            listener?.onError("SDK not ready")
            return
        }

        if (sttProvider == SttProvider.ANDROID_NATIVE) {
            scope.launch { processNativeStt() }
        } else {
            if (checkPermissions()) {
                voiceEngine.startAudioCapture()
            } else {
                listener?.onError("Permission RECORD_AUDIO denied")
            }
        }
    }

    fun stopAction() {
        if (sttProvider == SttProvider.ANDROID_NATIVE) return

        scope.launch {
            try {
                val audioFloats = voiceEngine.stopAudioCapture()
                val text = voiceEngine.transcribeWhisper(audioFloats)
                processTextWithLlm(text)
            } catch (e: Exception) {
                notifyError("Error procesamiento: ${e.message}")
            }
        }
    }

    private suspend fun processNativeStt() {
        val text = voiceEngine.recognizeNative()

        if (text.isNotBlank()) {
            processTextWithLlm(text)
        } else {
            withContext(Dispatchers.Main) {
                listener?.onError("No se detectó voz (Nativo)")
            }
        }
    }

    private suspend fun processTextWithLlm(text: String) {
        Log.d("VoiceObjectSDK", "Texto detectado: $text")
        if (text.isBlank()) return

        val prompt = promptBuilder.generatePrompt(
            userTranscript = text,
            fields = schema,
            additionalSystemInstruction = additionalSystemInstruction
        )

        var response = llmEngine.generate(prompt) ?: "{}"
        response = cleanMarkdown(response)

        val clean = sanitizeJsonOutput(response.trim())
        sendResult(clean)
    }

    private fun cleanMarkdown(response: String): String {
        var clean = response
        if (clean.contains("```json")) {
            clean = clean.substringAfter("```json").substringBefore("```")
        } else if (clean.contains("```")) {
            clean = clean.substringAfter("```").substringBefore("```")
        }
        return clean.trim()
    }

    private fun sanitizeJsonOutput(raw: String): String {
        return try {
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(raw, type)
            gson.toJson(map)
        } catch (e: Exception) {
            "{}"
        }
    }

    private suspend fun sendResult(json: String?) {
        withContext(Dispatchers.Main) {
            listener?.onResult(json ?: "{}")
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun notifyError(msg: String) {
        withContext(Dispatchers.Main) {
            listener?.onError(msg)
        }
    }

    fun release() {
        voiceEngine.release()
        llmEngine.close()
        scope.cancel()
    }

    // =========================
    // BUILDER
    // =========================

    class Builder(private val context: Context) {

        private var modelPaths: ModelPaths? = null
        private var schema = emptyMap<String, String>()
        private var maxTokens = 512
        private var topK = 1
        private var additionalSystemInstruction: String? = null
        private var sttProvider = SttProvider.ANDROID_NATIVE

        fun setModelPaths(paths: ModelPaths) = apply { this.modelPaths = paths }
        fun setSchema(schema: Map<String, String>) = apply { this.schema = schema }
        fun setMaxTokens(tokens: Int) = apply { this.maxTokens = tokens }
        fun setTopK(value: Int) = apply { this.topK = value }
        fun setSystemInstruction(instruction: String) = apply { this.additionalSystemInstruction = instruction }
        fun setSttProvider(provider: SttProvider) = apply { this.sttProvider = provider }

        fun build(): VoiceObjectClient {
            requireNotNull(modelPaths) { "ModelPaths required" }

            return VoiceObjectClient(
                context,
                modelPaths!!,
                schema,
                maxTokens,
                topK,
                additionalSystemInstruction,
                sttProvider
            )
        }
    }
}