# VoiceObject SDK - Documentación Técnica
## Introducción

VoiceObject SDK es una librería de Android diseñada para realizar transcripciones de audio y extracción de datos estructurados (JSON) de forma totalmente offline. Utiliza Whisper (Sherpa-ONNX) para el reconocimiento de voz y Gemma 3 (MediaPipe) para el procesamiento de lenguaje natural.

# Configuración
## 1. Agregar la librería
Copia el archivo voiceobject-core-release.aar en la carpeta app/libs de tu proyecto.
## 2. Dependencias
En el build.gradle de tu aplicación, asegúrate de incluir las dependencias necesarias:
dependencies {
    // Librería Local
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Librerias para cargar modelos de IA
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.40")
    implementation("com.google.mediapipe:tasks-genai:0.10.14")
    
    // Utilidades para descargar modelos automaticamente
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.12.0")
}

## 3. Permisos
Permisos para la grabación de voz y descarga de modelos
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />

# Flujo de Trabajo 

Paso 1: Descarga de Modelos

Debido a que los modelos de IA son pesados, el SDK incluye un ModelManager para descargarlos bajo demanda y no aumentar excesivamente el peso del APK.

Configuración de Seguridad (Hugging Face)
Para descargar los modelos protegidos (Gemma 3), cada desarrollador debe proporcionar su propio Access Token:
    Crea un token tipo Read en huggingface.co/settings/tokens.
    Asegúrate de haber aceptado la licencia en el repositorio del modelo Gemma3-1B-IT. [aceptar requerimientos](https://huggingface.co/google/gemma-3-1b-it)
    Pasa el token al invocar la descarga:

Modelos probados (whisper-tiny-8bits y gemma3-1b) 

val modelManager = ModelManager(context, token)

modelManager.checkAndDownloadModels(object : DownloadListener {
    override fun onProgress(fileName: String, progress: Int) {
        // Actualiza tu UI (barra de progreso o mensaje)
        println("Descargando $fileName: $progress%")
    }

    override fun onError(error: String) {
        println("Error de red: $error")
    }

    override fun onAllFinished(paths: ModelPaths) {
        // Fase 1 completada, procede a inicializar el cliente
        initVoiceClient(paths)
    }
})

Paso 2: Inicialización del Cliente

Una vez que tienes las rutas de los archivos (ModelPaths), configura el comportamiento de la IA.

// Define qué datos quieres extraer de los audios
val miEsquema = mapOf(
    "paciente" to "Nombre completo del paciente",
    "diagnostico" to "Resumen del diagnóstico médico",
    "medicinas" to "Lista de medicamentos recetados",
    // se pueden agregar más elementos 
)

val voiceClient = VoiceObjectClient.Builder(context)
    .setModels(
        gemmaPath = paths.gemmaModel,
        whisperEncoderPath = paths.whisperEncoder,
        whisperDecoderPath = paths.whisperDecoder,
        whisperTokensPath = paths.whisperTokens
    )
    .setSchema(miEsquema)
    .setMaxTokens(2048) // Se pueden modificar los tokens (256, 512, 1024, 2048), entre más tokens, más tarda el modelo en generar la respuesta 
    .build()

// Escuchar los eventos del motor
voiceClient.setListener(object : VoiceObjectListener {
    override fun onReady(isReady: Boolean) {
        println("IA lista para escuchar")
    }

    override fun onResult(jsonResult: String) {
        // Recibes un JSON con las llaves definidas en tu esquema
        println("Resultado final: $jsonResult")
    }

    override fun onError(error: String) {
        println("Error: $error")
    }
})

voiceClient.initialize()

Paso 3: Control de Grabación

Usa estos métodos vinculados a tus botones de la interfaz:

    voiceClient.startAction(): Inicia la captura del micrófono. (Maneja audios de larga duración automáticamente).

    voiceClient.stopAction(): Detiene la grabación y dispara el proceso de Transcripción + IA. El resultado llegará al onResult.

# Especificaciones del JSON

El poder de esta librería reside en el Map<String, String> que pasas al setSchema.

    Key: Será el nombre del campo en el JSON resultante.

    Value: Es la instrucción (Prompt) que recibirá la IA para saber qué buscar en la transcripción.

Ejemplo:

    "fecha" to "Extrae la fecha mencionada en formato DD/MM/AAAA"

    "urgencia" to "Clasifica la urgencia en: Baja, Media o Alta"

#