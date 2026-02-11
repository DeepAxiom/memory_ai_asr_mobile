package com.voiceobject.sdk.core.engines

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceEngine(private val context: Context) {

    // --- Whisper Variables ---
    private var recognizer: OfflineRecognizer? = null
    private val sampleRate = 16000
    private val isRecording = AtomicBoolean(false)
    private val rawAudioData = ByteArrayOutputStream()

    // --- Init for Whisper ---
    fun initWhisper(encoderPath: String, decoderPath: String, tokensPath: String) {
        if (encoderPath.isEmpty()) return // Skip si no se usa Whisper

        val config = OfflineRecognizerConfig()
        config.modelConfig.whisper.encoder = encoderPath
        config.modelConfig.whisper.decoder = decoderPath
        config.modelConfig.tokens = tokensPath
        config.modelConfig.whisper.language = "es"
        config.modelConfig.modelType = "whisper"
        config.modelConfig.numThreads = 4
        config.modelConfig.debug = false
        recognizer = OfflineRecognizer(config = config)
    }

    // --- Whisper Recording ---
    @SuppressLint("MissingPermission")
    fun startAudioCapture() {
        if (isRecording.get()) return
        rawAudioData.reset()
        isRecording.set(true)

        try {
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)

            Thread {
                val buffer = ByteArray(bufferSize)
                try {
                    recorder.startRecording()
                    while (isRecording.get()) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            synchronized(rawAudioData) {
                                rawAudioData.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
                finally {
                    try { recorder.stop(); recorder.release() } catch(e: Exception){}
                }
            }.start()
        } catch (e: Exception) { isRecording.set(false) }
    }

    fun stopAudioCapture(): FloatArray {
        isRecording.set(false)
        Thread.sleep(100)

        val bytes = synchronized(rawAudioData) { rawAudioData.toByteArray() } // Obtener array directo
        rawAudioData.reset()

        if (bytes.isEmpty()) return floatArrayOf()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return FloatArray(shorts.size) { i -> shorts[i] / 32768.0f }
    }

    fun stopAudioCaptureAndGetWav(): ByteArray {
        isRecording.set(false)
        Thread.sleep(100)
        val pcmData = synchronized(rawAudioData) { rawAudioData.toByteArray() }
        rawAudioData.reset()
        if (pcmData.isEmpty()) return ByteArray(0)
        return addWavHeader(pcmData)
    }

    private fun addWavHeader(pcmData: ByteArray): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size.toLong()
        val byteRate = (16000 * 1 * 16 / 8).toLong() // SampleRate * Channels * Bits / 8
        val totalChunkSize = totalDataLen + 36

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalChunkSize and 0xff).toByte()
        header[5] = ((totalChunkSize shr 8) and 0xff).toByte()
        header[6] = ((totalChunkSize shr 16) and 0xff).toByte()
        header[7] = ((totalChunkSize shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Length of format data
        header[20] = 1; header[21] = 0 // Type of format (1 is PCM)
        header[22] = 1; header[23] = 0 // Channels (1)
        header[24] = (16000 and 0xff).toByte(); header[25] = ((16000 shr 8) and 0xff).toByte() // Sample Rate
        header[26] = ((16000 shr 16) and 0xff).toByte(); header[27] = ((16000 shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0 // Block align (Channels * Bits / 8)
        header[34] = 16; header[35] = 0 // Bits per sample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalDataLen and 0xff).toByte()
        header[41] = ((totalDataLen shr 8) and 0xff).toByte()
        header[42] = ((totalDataLen shr 16) and 0xff).toByte()
        header[43] = ((totalDataLen shr 24) and 0xff).toByte()

        return header + pcmData
    }
    fun transcribeWhisper(samples: FloatArray): String {
        if (recognizer == null) return "Error: Whisper no inicializado"
        if (samples.isEmpty()) return ""
        val stream = recognizer!!.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer!!.decode(stream)
        val result = recognizer!!.getResult(stream)
        stream.release()
        return result.text
    }

    // --- Native Android STT ---
    suspend fun recognizeNative(): String = withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                cont.resume("")
                return@suspendCoroutine
            }

            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.e("VoiceEngine", "Native STT Error code: $error")
                    speechRecognizer.destroy()
                    cont.resume("") // Retornar vacío en error
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    speechRecognizer.destroy()
                    cont.resume(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer.startListening(intent)
        }
    }

    fun release() {
        isRecording.set(false)
        recognizer?.release()
        recognizer = null
    }
}