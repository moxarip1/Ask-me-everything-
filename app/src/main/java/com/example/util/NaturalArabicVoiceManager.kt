package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Base64
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "NaturalArabicVoiceMgr"

/**
 * Dual-Layer Human-Like Natural Arabic Voice Engine:
 * - Central `stopAllAudio()` lifecycle method that immediately halts all neural & system TTS streams.
 * - Layer 1: Gemini Neural TTS (`gemini-2.5-flash-preview-tts` with `responseModalities: ["AUDIO"]`,
 *   voice `Aoede`) with in-memory `AudioCache` so re-listening to any stage is instantaneous.
 * - Layer 2: Optimized Natural System Arabic Voice (`rate = 0.95f`, `pitch = 1.02f`, best natural `ar-SA`/`ar-EG`
 *   voice selection, and natural breathing text sanitization).
 */
class NaturalArabicVoiceManager(
    context: Context,
    baseHttpClient: OkHttpClient,
    private val getApiKeys: () -> List<String>,
    private val onSpeakingStateChanged: (Boolean) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeSpeechJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null
    private var systemTts: TextToSpeech? = null
    private var isSystemTtsReady = false

    // Fast HTTP client for Gemini Neural TTS requests
    private val ttsHttpClient: OkHttpClient = baseHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    // In-memory Audio Cache storing raw 24kHz 16-bit PCM byte arrays per stage
    private val audioCache = object : LruCache<String, ByteArray>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    init {
        systemTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureBestNaturalArabicVoice(systemTts)
                isSystemTtsReady = true
            }
        }
    }

    private fun configureBestNaturalArabicVoice(tts: TextToSpeech?) {
        if (tts == null) return
        try {
            val arSaLocale = Locale("ar", "SA")
            val arEgLocale = Locale("ar", "EG")
            val arGeneric = Locale("ar")

            val langResult = tts.setLanguage(arSaLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                val egResult = tts.setLanguage(arEgLocale)
                if (egResult == TextToSpeech.LANG_MISSING_DATA || egResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(arGeneric)
                }
            }

            // Select the highest-quality natural Arabic voice available on the device
            val availableVoices = tts.voices?.filter { v ->
                v.locale.language.equals("ar", ignoreCase = true)
            }.orEmpty()

            val bestVoice: Voice? = availableVoices.maxByOrNull { v ->
                var score = 0
                val nameLower = v.name.lowercase()
                if (v.quality >= Voice.QUALITY_HIGH) score += 40
                if (v.quality >= Voice.QUALITY_VERY_HIGH) score += 60
                if (nameLower.contains("natural") || nameLower.contains("wavenet") || nameLower.contains("neural")) score += 50
                if (nameLower.contains("ar-xa") || nameLower.contains("ar-sa") || nameLower.contains("ar-eg")) score += 25
                if (nameLower.contains("hamed") || nameLower.contains("salma") || nameLower.contains("zariyah")) score += 30
                if (!v.isNetworkConnectionRequired) score += 10
                score
            }

            if (bestVoice != null) {
                tts.voice = bestVoice
                Log.d(TAG, "Selected natural Arabic voice: ${bestVoice.name} (quality=${bestVoice.quality})")
            }

            // Human-like natural cadence & warm pitch
            tts.setSpeechRate(0.95f)
            tts.setPitch(1.02f)

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeakingStateChanged(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeakingStateChanged(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeakingStateChanged(false)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed configuring Arabic TTS voice details", e)
        }
    }

    /**
     * Central Auto-Stop Audio Method:
     * Immediately stops all active speech synthesis, cancels neural TTS jobs, and flushes AudioTrack.
     */
    fun stopAllAudio() {
        activeSpeechJob?.cancel()
        activeSpeechJob = null

        try {
            systemTts?.stop()
        } catch (_: Exception) {
        }

        try {
            activeAudioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
                track.release()
            }
        } catch (_: Exception) {
        } finally {
            activeAudioTrack = null
        }

        onSpeakingStateChanged(false)
    }

    /**
     * Cleans symbols, brackets, dashes, and English technical noise, and adds natural breathing pauses.
     */
    fun sanitizeArabicTextForNaturalSpeech(rawText: String): String {
        return rawText
            // Remove parenthetical English terms e.g. (Logic Board)
            .replace(Regex("\\([A-Za-z0-9\\s\\-_/]+\\)"), " ")
            // Remove brackets, quotes, and bullet symbols
            .replace(Regex("[\\[\\]{}()«»\"'•*#_~`<>|\\\\]"), " ")
            // Replace dashes and slashes with natural comma pause
            .replace(Regex("\\s*[-–—/]+\\s*"), "، ")
            // Add breathing pause after colons and semicolons
            .replace(Regex("[:؛]"), "، ")
            // Normalize multiple spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Speaks the given stage text using Layer 1 (Gemini Neural TTS + AudioCache) with
     * instant fallback to Layer 2 (Optimized Natural System Arabic Voice).
     */
    fun speakStageNaturally(
        cacheKey: String,
        stageNumber: Int,
        totalStages: Int,
        titleAr: String,
        explanationAr: String,
        bulletPoints: List<String>
    ) {
        // Always stop any previous audio first!
        stopAllAudio()

        val fullRawText = buildString {
            append("المرحلة $stageNumber من $totalStages، $titleAr. ")
            append(explanationAr)
            if (bulletPoints.isNotEmpty()) {
                append(". ومن أهم النقاط في هذه المرحلة، ")
                append(bulletPoints.take(3).joinToString("، و "))
            }
        }
        val cleanText = sanitizeArabicTextForNaturalSpeech(fullRawText)

        onSpeakingStateChanged(true)

        activeSpeechJob = scope.launch {
            try {
                // 1. Check in-memory Audio Cache first
                val cachedPcm = audioCache.get(cacheKey)
                if (cachedPcm != null && cachedPcm.isNotEmpty()) {
                    playPcm24kHzAudio(cachedPcm)
                    return@launch
                }

                // 2. Try Layer 1: Gemini Neural TTS (`gemini-2.5-flash-preview-tts`, voice `Aoede`) if custom API key is configured
                val customKeys = getApiKeys()
                var neuralPcm: ByteArray? = null
                if (customKeys.isNotEmpty()) {
                    neuralPcm = fetchGeminiNeuralTtsAudio(cleanText, customKeys.first())
                    if (neuralPcm != null && neuralPcm.isNotEmpty()) {
                        audioCache.put(cacheKey, neuralPcm)
                    }
                }

                if (!isActive) return@launch

                if (neuralPcm != null && neuralPcm.isNotEmpty()) {
                    playPcm24kHzAudio(neuralPcm)
                } else {
                    // 3. Layer 2: Instant Optimized Natural System Arabic Voice Fallback
                    speakWithOptimizedSystemVoice(cleanText, cacheKey)
                }
            } catch (e: CancellationException) {
                onSpeakingStateChanged(false)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fallback to natural system TTS: ${e.message}")
                speakWithOptimizedSystemVoice(cleanText, cacheKey)
            }
        }
    }

    private suspend fun fetchGeminiNeuralTtsAudio(
        cleanArabicText: String,
        apiKey: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            withTimeout(4_200L) {
                val url =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$apiKey"
                val payload = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "text",
                                        "اقرأ هذا النص العلمي بصوت بشري عربي فصيح ودافئ وواضح:\n$cleanArabicText"
                                    )
                                )
                            )
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("responseModalities", JSONArray().put("AUDIO"))
                            put(
                                "speechConfig",
                                JSONObject().put(
                                    "voiceConfig",
                                    JSONObject().put(
                                        "prebuiltVoiceConfig",
                                        JSONObject().put("voiceName", "Aoede")
                                    )
                                )
                            )
                        }
                    )
                }

                val req = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                ttsHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withTimeout null
                    val bodyStr = resp.body?.string() ?: return@withTimeout null
                    val inlineData = JSONObject(bodyStr)
                        .optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optJSONObject("inlineData")
                    val base64Audio = inlineData?.optString("data", "").orEmpty()
                    if (base64Audio.isNotBlank()) {
                        Base64.decode(base64Audio, Base64.DEFAULT)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun playPcm24kHzAudio(pcmBytes: ByteArray) = withContext(Dispatchers.IO) {
        val sampleRate = 24000
        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcmBytes.size)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeAudioTrack = track
        track.write(pcmBytes, 0, pcmBytes.size)
        track.play()

        // Calculate playback duration from 24kHz 16-bit mono PCM (48,000 bytes/sec)
        val durationMs = ((pcmBytes.size.toLong() * 1000L) / (sampleRate * 2)).coerceAtLeast(500L)
        kotlinx.coroutines.delay(durationMs + 150L)

        withContext(Dispatchers.Main.immediate) {
            if (activeAudioTrack === track) {
                stopAllAudio()
            }
        }
    }

    private fun speakWithOptimizedSystemVoice(cleanText: String, utteranceId: String) {
        val tts = systemTts
        if (tts != null && isSystemTtsReady) {
            tts.setSpeechRate(0.95f)
            tts.setPitch(1.02f)
            tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            onSpeakingStateChanged(false)
        }
    }

    fun release() {
        stopAllAudio()
        try {
            systemTts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
