package com.voiceobject.sdk.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.voiceobject.sdk.core.engines.LlmEngine
import com.voiceobject.sdk.core.engines.VoiceEngine
import com.voiceobject.sdk.core.prompt.SchemaInference
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

class VoiceObjectClient private constructor(
    private val context: Context,
    private val gemmaPath: String,
    private val whisperEncoderPath: String,
    private val whisperDecoderPath: String,
    private val whisperTokensPath: String,
    private val schema: Map<String, String>,
    private val maxTokens: Int
) {
    private val voiceEngine = VoiceEngine(context)
    private val llmEngine = LlmEngine(context)
    private val promptBuilder = SchemaInference()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var isVoiceReady = false
    @Volatile private var isLlmReady = false
    
    private var listener: VoiceObjectListener? = null

    fun setListener(listener: VoiceObjectListener) {
        this.listener = listener
    }

    fun initialize() {
        scope.launch {
            try {
                Log.d("VoiceObjectSDK", "Iniciando carga de modelos...")

                val voiceInit = async {
                    val time = measureTimeMillis { 
                        voiceEngine.initModel(whisperEncoderPath, whisperDecoderPath, whisperTokensPath) 
                    }
                    isVoiceReady = true
                    Log.d("VoiceObjectSDK", "✅ Whisper cargado (${time}ms)")
                }

                val llmInit = async {
                    val time = measureTimeMillis { 
                        llmEngine.setup(gemmaPath, maxTokens) 
                    }
                    isLlmReady = true
                    Log.d("VoiceObjectSDK", "✅ Gemma cargado (${time}ms)")
                }

                awaitAll(voiceInit, llmInit)
                
                withContext(Dispatchers.Main) { listener?.onReady(true) }
            } catch (e: Exception) {
                Log.e("VoiceObjectSDK", "❌ Error en inicialización", e)
                withContext(Dispatchers.Main) { 
                    listener?.onReady(false)
                    listener?.onError("Error al cargar modelos: ${e.message}")
                }
            }
        }
    }

    fun startAction() {
        if (!isVoiceReady) {
            listener?.onError("VoiceEngine no está listo. Llama a initialize() primero.")
            return
        }

        // VERIFICACIÓN DE SEGURIDAD
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            listener?.onError("Falta el permiso de RECORD_AUDIO. La app debe solicitarlo al usuario.")
            return
        }

        voiceEngine.startRecording()
    }

    fun stopAction() {
        scope.launch {
            try {
                val audioSamples = voiceEngine.stopRecording()
                if (audioSamples.isEmpty()) {
                    notifyError("No se detectó audio")
                    return@launch
                }

                val transcription = withContext(Dispatchers.Default) {
                    voiceEngine.transcribe(audioSamples)
                }

                if (transcription.isBlank()) {
                    notifyError("No se pudo transcribir el audio")
                    return@launch
                }
                
                Log.d("VoiceObjectSDK", "Transcripción: $transcription")

                val jsonResponse = withContext(Dispatchers.Default) {
                    val finalPrompt = promptBuilder.generatePrompt(transcription, schema)
                    var response = llmEngine.generate(finalPrompt) ?: "{}"
                    
                    if (response.contains("```json")) {
                        response = response.substringAfter("```json").substringBefore("```")
                    } else if (response.contains("```")) {
                        response = response.substringAfter("```").substringBefore("```")
                    }
                    response.trim()
                }

                withContext(Dispatchers.Main) {
                    listener?.onResult(jsonResponse)
                }
            } catch (e: Exception) {
                notifyError("Error durante el procesamiento: ${e.message}")
            }
        }
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

    class Builder(private val context: Context) {
        private var gemmaPath = ""
        private var whisperEncoder = ""
        private var whisperDecoder = ""
        private var whisperTokens = ""
        private var schema = emptyMap<String, String>()
        private var maxTokens = 1024

        fun setModels(
            gemmaPath: String, 
            whisperEncoderPath: String, 
            whisperDecoderPath: String, 
            whisperTokensPath: String
        ) = apply {
            this.gemmaPath = gemmaPath
            this.whisperEncoder = whisperEncoderPath
            this.whisperDecoder = whisperDecoderPath
            this.whisperTokens = whisperTokensPath
        }

        fun setSchema(schema: Map<String, String>) = apply { this.schema = schema }
        fun setMaxTokens(tokens: Int) = apply { this.maxTokens = tokens }

        fun build(): VoiceObjectClient {
            if (gemmaPath.isEmpty() || whisperEncoder.isEmpty()) {
                throw IllegalStateException("Debes configurar las rutas de los modelos antes de construir.")
            }
            return VoiceObjectClient(
                context, 
                gemmaPath, 
                whisperEncoder, 
                whisperDecoder, 
                whisperTokens, 
                schema, 
                maxTokens
            )
        }
    }
}