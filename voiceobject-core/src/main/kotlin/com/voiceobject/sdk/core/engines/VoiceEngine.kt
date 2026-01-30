package com.voiceobject.sdk.core.engines

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
import kotlin.math.min

class VoiceEngine(private val context: Context) {

    private var recognizer: OfflineRecognizer? = null
    private val sampleRate = 16000
    private val isRecording = AtomicBoolean(false)
    private val audioData = mutableListOf<Short>()

    // Ahora pasamos la ruta completa de los archivos
    fun initModel(
        encoderPath: String,
        decoderPath: String,
        tokensPath: String
    ) {
        val config = OfflineRecognizerConfig()

        config.modelConfig.whisper.encoder = encoderPath
        config.modelConfig.whisper.decoder = decoderPath
        config.modelConfig.tokens = tokensPath

        config.modelConfig.whisper.language = "es"
        config.modelConfig.whisper.task = "transcribe"
        config.modelConfig.modelType = "whisper"
        config.modelConfig.numThreads = 4
        config.modelConfig.debug = false
        
        recognizer = OfflineRecognizer(config = config) 
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording.get()) return
        audioData.clear()
        isRecording.set(true)
        
        // Es buena práctica manejar excepciones aquí por si el micro está ocupado
        try {
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            
            Thread {
                val buffer = ShortArray(bufferSize)
                try {
                    recorder.startRecording()
                    while (isRecording.get()) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) synchronized(audioData) { for (i in 0 until read) audioData.add(buffer[i]) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    recorder.stop()
                    recorder.release()
                }
            }.start()
        } catch (e: Exception) {
            isRecording.set(false)
            e.printStackTrace()
        }
    }

    fun stopRecording(): FloatArray {
        isRecording.set(false)
        // Pequeña pausa para asegurar que el thread de grabación termine
        Thread.sleep(100) 
        val captured = synchronized(audioData) { audioData.toShortArray() }
        if (captured.isEmpty()) return floatArrayOf()
        return FloatArray(captured.size) { i -> captured[i] / 32768.0f }
    }

    fun transcribe(samples: FloatArray): String {
        if (recognizer == null || samples.isEmpty()) return ""

        val sampleRate = 16000
        val maxSamplesPerChunk = 30 * sampleRate // 480,000 muestras (30 segundos)
        val fullTranscription = StringBuilder()

        try {
            val totalSamples = samples.size
            var startSample = 0

            Log.d("VoiceObjectSDK", "Procesando audio largo: ${totalSamples / sampleRate} segundos")

            while (startSample < totalSamples) {
                val endSample = minOf(startSample + maxSamplesPerChunk, totalSamples)
                
                // Extraemos el bloque (slice)
                val chunk = samples.sliceArray(startSample until endSample)

                // Transcribimos el bloque
                val stream = recognizer!!.createStream()
                stream.acceptWaveform(chunk, sampleRate)
                recognizer!!.decode(stream)
                val result = recognizer!!.getResult(stream)
                
                // Agregamos el texto al resultado total
                if (result.text.isNotBlank()) {
                    fullTranscription.append(result.text).append(" ")
                }

                stream.release()

                startSample += maxSamplesPerChunk
                
                Log.d("VoiceObjectSDK", "Progreso transcripción: ${((startSample.toFloat() / totalSamples) * 100).toInt()}%")
            }

        } catch (e: Exception) {
            Log.e("VoiceObjectSDK", "Error en transcripción segmentada", e)
        }

        return fullTranscription.toString().trim()
    }

    fun release() {
        isRecording.set(false)
        recognizer?.release()
        recognizer = null
    }
}