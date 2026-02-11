package com.voiceobject.sdk.core.config

enum class LlmModelVersion(
    val url: String,
    val fileName: String,
    val isAudioNative: Boolean // Indica si el modelo procesa audio directamente (Gemma 3n)
) {
    GEMMA_3_1B(
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task?download=true",
        fileName = "gemma3-1b-it-int4.task",
        isAudioNative = false
    ),
    GEMMA_3_270M(
        url = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.litertlm?download=true",
        fileName = "gemma3-270m-it-q8.litertlm",
        isAudioNative = false
    ),
    GEMMA_3N_2B(
        url = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4-Web.litertlm?download=true",
        fileName = "gemma-3n-E2B-it-int4-Web.litertlm",
        isAudioNative = false
    )
}

enum class SttProvider {
    WHISPER_ONNX,
    ANDROID_NATIVE,
    NONE
}