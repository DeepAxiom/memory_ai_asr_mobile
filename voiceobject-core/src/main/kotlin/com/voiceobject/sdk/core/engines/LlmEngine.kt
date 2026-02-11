package com.voiceobject.sdk.core.engines

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

class LlmEngine(private val context: Context) {

    private var llmInference: LlmInference? = null

    fun setup(
        modelPath: String,
        maxTokens: Int,
        topK: Int
    ) {
        llmInference?.close()
        llmInference = null

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .setMaxTopK(topK)
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    fun generate(prompt: String): String? {
        val inference = llmInference ?: return null

        val sessionOptions =
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .build()

        return LlmInferenceSession
            .createFromOptions(inference, sessionOptions)
            .use { session ->
                session.addQueryChunk(prompt)
                session.generateResponse()
            }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
