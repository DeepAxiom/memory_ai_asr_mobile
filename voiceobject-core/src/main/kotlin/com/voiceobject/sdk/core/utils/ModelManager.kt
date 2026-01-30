package com.voiceobject.sdk.core.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ModelSpec(
    val fileName: String,
    val url: String,
    val type: ModelType
)

enum class ModelType {
    WHISPER_ENCODER,
    WHISPER_DECODER,
    WHISPER_TOKENS,
    LLM_GEMMA
}

data class ModelPaths(
    val whisperEncoder: String,
    val whisperDecoder: String,
    val whisperTokens: String,
    val gemmaModel: String
)

interface DownloadListener {
    fun onProgress(fileName: String, progress: Int)
    fun onError(error: String)
    fun onAllFinished(paths: ModelPaths)
}

class ModelManager(private val context: Context, private val hfToken: String) {

    // 1. AUMENTAMOS LOS TIMEOUTS PARA ARCHIVOS GRANDES
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Tiempo para conectar con el servidor
        .readTimeout(120, TimeUnit.SECONDS)    // Tiempo para leer datos (descarga)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val specs = listOf(
        ModelSpec(
            "tiny-encoder.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-encoder.int8.onnx",
            ModelType.WHISPER_ENCODER
        ),
        ModelSpec(
            "tiny-decoder.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-decoder.int8.onnx",
            ModelType.WHISPER_DECODER
        ),
        ModelSpec(
            "tiny-tokens.txt",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-tokens.txt",
            ModelType.WHISPER_TOKENS
        ),
        // Enlace corregido y verificado para Gemma 3
        ModelSpec(
            "gemma3-1b-it-int4.task",
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task?download=true",
            ModelType.LLM_GEMMA
        )
    )

    suspend fun checkAndDownloadModels(listener: DownloadListener) {
        withContext(Dispatchers.IO) {
            val finalPaths = mutableMapOf<ModelType, String>()
            var hasError = false

            for (spec in specs) {
                if (hasError) break

                val file = File(context.filesDir, spec.fileName)

                if (file.exists() && file.length() > 0) {
                    Log.d("VoiceObjectSDK", "Modelo ya existe: ${spec.fileName} (${file.length()} bytes)")
                    finalPaths[spec.type] = file.absolutePath
                    listener.onProgress(spec.fileName, 100)
                } else {
                    Log.d("VoiceObjectSDK", "Iniciando descarga: ${spec.fileName} desde ${spec.url}")
                    
                    // Borramos archivos parciales corruptos
                    if (file.exists()) file.delete()

                    val success = downloadFile(spec.url, file) { progress ->
                        listener.onProgress(spec.fileName, progress)
                    }

                    if (success) {
                        finalPaths[spec.type] = file.absolutePath
                    } else {
                        hasError = true
                        // El error detallado ya se logueó en downloadFile, aquí notificamos genérico
                        listener.onError("Fallo al descargar ${spec.fileName}. Revisa logs.")
                    }
                }
            }

            if (!hasError) {
                val result = ModelPaths(
                    whisperEncoder = finalPaths[ModelType.WHISPER_ENCODER] ?: "",
                    whisperDecoder = finalPaths[ModelType.WHISPER_DECODER] ?: "",
                    whisperTokens = finalPaths[ModelType.WHISPER_TOKENS] ?: "",
                    gemmaModel = finalPaths[ModelType.LLM_GEMMA] ?: ""
                )
                withContext(Dispatchers.Main) {
                    listener.onAllFinished(result)
                }
            }
        }
    }

    private fun downloadFile(url: String, file: File, onProgress: (Int) -> Unit): Boolean {
        val requestBuilder = Request.Builder().url(url)
    
        if (url.contains("huggingface.co")) {
            if (hfToken.isNotEmpty()){
                requestBuilder.addHeader("authorization", "Bearer $hfToken")
            } else {
                Log.w("VoiceObjectSDK", "Advertencia: Intentando Descargar de HF sin Token")
            }
        }

        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("VoiceObjectSDK", "Error HTTP: ${response.code} para $url")
                    return false
                }

                val body = response.body
                if (body == null) {
                    Log.e("VoiceObjectSDK", "Cuerpo de respuesta vacío")
                    return false
                }

                val totalSize = body.contentLength()
                Log.d("VoiceObjectSDK", "Tamaño del archivo: $totalSize bytes")

                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied: Long = 0
                        var read: Int
                        var lastProgress = 0

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read

                            if (totalSize > 0) {
                                val progress = ((bytesCopied * 100) / totalSize).toInt()
                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    // Usamos try-catch aquí por si el UI thread se cierra
                                    try { onProgress(progress) } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                }
                Log.d("VoiceObjectSDK", "Descarga completada: ${file.name}")
                return true
            }
        } catch (e: IOException) {
            Log.e("VoiceObjectSDK", "Excepción de red al descargar ${file.name}", e)
            return false
        }
    }
}