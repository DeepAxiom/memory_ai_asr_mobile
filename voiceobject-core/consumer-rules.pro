# Mantener las clases públicas de tu SDK
-keep class com.voiceobject.sdk.core.** { *; }

# Reglas para Sherpa ONNX (importante para que no borre los bindings nativos)
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Reglas para MediaPipe GenAI
-keep class com.google.mediapipe.tasks.genai.** { *; }

# Evitar ofuscación en interfaces que usa el usuario
-keep interface com.voiceobject.sdk.core.VoiceObjectListener
-keep interface com.voiceobject.sdk.core.utils.DownloadListener