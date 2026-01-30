package com.voiceobject.sdk.core.engines

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class LlmEngine(private val context: Context) {
    private var llmInference: LlmInference? = null

    fun setup(modelPath: String, maxTokens: Int) {
        val file = File(modelPath)
        
        // Verificamos si la ruta es un archivo existente en el sistema de archivos
        val finalPath = if (file.exists()) {
            modelPath
        } else {
            // Lógica legacy: Copiar de assets si no es una ruta absoluta válida
            val assetFile = File(context.filesDir, "voiceobject_model.task")
            if (!assetFile.exists()) {
                context.assets.open(modelPath).use { input ->
                    java.io.FileOutputStream(assetFile).use { output -> input.copyTo(output) }
                }
            }
            assetFile.absolutePath
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(finalPath)
            .setMaxTokens(maxTokens)
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    fun generate(prompt: String): String? {
        return llmInference?.generateResponse(prompt)
    }

    fun close() {
        llmInference?.close()
    }
}