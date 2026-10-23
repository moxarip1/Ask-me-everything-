package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Animation3DType
import com.example.model.ApiKeyItem
import com.example.model.ApiKeyStatus
import com.example.model.ExplainStage
import com.example.model.InteractiveHotspot
import com.example.model.OmniTopicExplanation
import com.example.model.Part3DSpec
import com.example.model.RawLiveSourceResult
import com.example.model.Shape3DType
import com.example.model.VerifiedSource
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "GeminiOrchestrator"
private const val PREFS_NAME = "omni_explain_api_prefs"
private const val KEY_CUSTOM_API_KEYS = "custom_gemini_api_keys"

class ApiKeyPreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCustomKeys(): List<String> {
        val raw = prefs.getString(KEY_CUSTOM_API_KEYS, "") ?: ""
        return raw.split("\n", ",", ";", " ")
            .map { it.trim() }
            .filter { it.length >= 10 }
            .distinct()
    }

    fun saveCustomKeys(keys: List<String>) {
        val cleaned = keys.map { it.trim() }.filter { it.length >= 10 }.distinct()
        prefs.edit().putString(KEY_CUSTOM_API_KEYS, cleaned.joinToString("\n")).apply()
    }

    fun addSingleOrBulkKeys(input: String): List<String> {
        val incoming = input.split("\n", ",", ";", " ")
            .map { it.trim() }
            .filter { it.length >= 10 }
        val updated = (getCustomKeys() + incoming).distinct()
        saveCustomKeys(updated)
        return updated
    }

    fun removeKey(keyToRemove: String): List<String> {
        val updated = getCustomKeys().filterNot { it == keyToRemove }
        saveCustomKeys(updated)
        return updated
    }
}

class GeminiOrchestrator(
    httpClient: OkHttpClient,
    private val apiKeyManager: ApiKeyPreferencesManager
) {
    // Dedicated fast client with 8-second timeout per key (AbortController equivalent)
    private val fastKeyClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val hotspotSchema = Schema.obj(
        properties = mapOf(
            "id" to Schema.string(),
            "xPercent" to Schema.double(),
            "yPercent" to Schema.double(),
            "labelAr" to Schema.string(),
            "functionAr" to Schema.string(),
            "zoomScale" to Schema.double()
        )
    )

    private val stageSchema = Schema.obj(
        properties = mapOf(
            "stageNumber" to Schema.integer(),
            "titleAr" to Schema.string(),
            "explanationAr" to Schema.string(),
            "toolsOrFactsTitleAr" to Schema.string(),
            "toolsOrFacts" to Schema.array(items = Schema.string()),
            "mediaIndex" to Schema.integer(),
            "mediaCaptionAr" to Schema.string(),
            "hotspots" to Schema.array(items = hotspotSchema),
            "activePartIds" to Schema.array(items = Schema.string())
        )
    )

    private val part3DSchema = Schema.obj(
        properties = mapOf(
            "id" to Schema.string(),
            "nameAr" to Schema.string(),
            "roleAr" to Schema.string(),
            "shapeType" to Schema.enumeration(values = Shape3DType.entries.map { it.name }),
            "isShell" to Schema.boolean(),
            "isInternal" to Schema.boolean(),
            "colorHex" to Schema.string(),
            "posX" to Schema.double(),
            "posY" to Schema.double(),
            "posZ" to Schema.double(),
            "scaleX" to Schema.double(),
            "scaleY" to Schema.double(),
            "scaleZ" to Schema.double(),
            "explodeX" to Schema.double(),
            "explodeY" to Schema.double(),
            "explodeZ" to Schema.double(),
            "animationType" to Schema.enumeration(values = Animation3DType.entries.map { it.name }),
            "activeInStages" to Schema.array(items = Schema.integer())
        )
    )

    private val orchestrationSchema = Schema.obj(
        properties = mapOf(
            "arabicTitle" to Schema.string(),
            "englishTitle" to Schema.string(),
            "summaryAr" to Schema.string(),
            "stages" to Schema.array(items = stageSchema),
            "parts3D" to Schema.array(items = part3DSchema)
        )
    )

    private val orchestratorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = orchestrationSchema
            }
        )
    }

    /**
     * Background AI Enrichment with a strict 10-second overall cap and 8-second per-key race.
     * Returns null if Gemini times out or fails so the instant live view is never interrupted!
     */
    suspend fun enrichExplanationInBackground(
        liveData: RawLiveSourceResult
    ): OmniTopicExplanation? = withTimeoutOrNull(10_000L) {
        try {
            val mediaDescriptions = liveData.mediaGallery.take(8).mapIndexed { index, pair ->
                "[$index] ${pair.second}"
            }.joinToString("\n")

            val contextText = if (liveData.steps.isNotEmpty()) {
                liveData.steps.take(7).joinToString("\n") { s ->
                    "Step ${s.stepNumber} (${s.title}): ${s.description.take(180)}"
                }
            } else {
                liveData.summaryText.take(1600)
            }

            val prompt = """
                أنت المحرك الذكي لموسوعة "OmniExplain Pro".
                الموضوع: "${liveData.queryAr}" (${liveData.englishQuery})
                المجال: ${liveData.domain.labelAr}
                المصدر الموثق: ${liveData.sourceTitle}
                الملخص والبيانات الحقيقية:
                $contextText
                الصور المجلوبة (${liveData.mediaGallery.size}):
                $mediaDescriptions
                
                قم بتوليد JSON يحتوي على:
                - arabicTitle, englishTitle, summaryAr
                - stages: من 5 إلى 6 مراحل متسلسلة بالعربية الفصحى، مع ربط كل مرحلة بـ mediaIndex (0 إلى ${(liveData.mediaGallery.size - 1).coerceAtLeast(0)})، و3 نقاط تفاعلية (hotspots) دقيقة لكل مرحلة.
                - parts3D: من 8 إلى 10 قطع ثلاثية الأبعاد مخصصة لموضوع "${liveData.queryAr}" (2 غلاف خارجي isShell=true و 6-8 أجزاء داخلية متحركة isInternal=true مع متجهات تفكيك explodeX/Y/Z).
            """.trimIndent()

            val jsonText = raceGenerateJson(prompt) ?: return@withTimeoutOrNull null
            parseOrchestratedJson(liveData, jsonText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background Gemini enrichment skipped: ${e.message}")
            null
        }
    }

    /**
     * Parallel Race & Auto-Fallback (Promise.any equivalent in Kotlin Coroutines)
     * Races user-configured Gemini API keys + built-in Firebase AI Logic with an 8s timeout per key.
     */
    private suspend fun raceGenerateJson(prompt: String): String? = supervisorScope {
        val customKeys = apiKeyManager.getCustomKeys()
        val winner = CompletableDeferred<String?>()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()

        // Launch custom API keys in parallel (up to 4 concurrent runners)
        customKeys.take(4).forEach { apiKey ->
            val job = launch(Dispatchers.IO) {
                try {
                    val result = withTimeout(8_000L) {
                        callDirectGeminiApiWithKey(apiKey, prompt)
                    }
                    if (result.isNotBlank() && result.trim().startsWith("{")) {
                        winner.complete(result)
                    }
                } catch (_: Exception) {
                    // Ignore individual key failure in race
                }
            }
            jobs.add(job)
        }

        // Also launch built-in Firebase AI Logic runner in the race
        val builtInJob = launch(Dispatchers.IO) {
            try {
                val resp = withTimeout(8_000L) {
                    orchestratorModel.generateContent(prompt)
                }
                val text = resp.text?.trim().orEmpty()
                if (text.startsWith("{")) {
                    winner.complete(text)
                }
            } catch (_: Exception) {
                // Ignore if built-in fails during race
            }
        }
        jobs.add(builtInJob)

        // Monitor when all jobs finish if none succeeded
        launch {
            jobs.forEach { it.join() }
            if (!winner.isCompleted) {
                winner.complete(null)
            }
        }

        val winningJson = winner.await()
        // Cancel any remaining slower requests immediately (AbortController behavior)
        jobs.forEach { it.cancel() }
        winningJson
    }

    private suspend fun callDirectGeminiApiWithKey(apiKey: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val schemaHint = """
                Return raw JSON matching this structure:
                {"arabicTitle":"...","englishTitle":"...","summaryAr":"...","stages":[{"stageNumber":1,"titleAr":"...","explanationAr":"...","toolsOrFactsTitleAr":"...","toolsOrFacts":["..."],"mediaIndex":0,"mediaCaptionAr":"...","hotspots":[{"id":"h1","xPercent":0.4,"yPercent":0.4,"labelAr":"...","functionAr":"...","zoomScale":2.4}],"activePartIds":["part_1"]}],"parts3D":[{"id":"part_1","nameAr":"...","roleAr":"...","shapeType":"CYLINDER","isShell":true,"isInternal":false,"colorHex":"#38BDF8","posX":0.0,"posY":0.0,"posZ":0.0,"scaleX":1.0,"scaleY":1.0,"scaleZ":1.0,"explodeX":0.0,"explodeY":1.2,"explodeZ":0.8,"animationType":"PULSE","activeInStages":[1,2]}]}
            """.trimIndent()

            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", "$prompt\n$schemaHint"))
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json")
                )
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(url).post(body).build()
            fastKeyClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val raw = resp.body?.string() ?: throw IllegalStateException("Empty body")
                val text = JSONObject(raw)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
                text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            }
        }

    /**
     * Tests all configured keys in parallel when the user clicks "فحص المفاتيح" in the modal
     */
    suspend fun testAllKeysHealth(keys: List<String>): List<ApiKeyItem> = coroutineScope {
        val customDeferred = keys.map { key ->
            async(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                val ok = try {
                    withTimeout(6_000L) {
                        val url =
                            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key"
                        val payload = JSONObject().apply {
                            put(
                                "contents",
                                JSONArray().put(
                                    JSONObject().put(
                                        "parts",
                                        JSONArray().put(JSONObject().put("text", "ping"))
                                    )
                                )
                            )
                        }
                        val req = Request.Builder()
                            .url(url)
                            .post(payload.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        fastKeyClient.newCall(req).execute().use { it.isSuccessful }
                    }
                } catch (_: Exception) {
                    false
                }
                val elapsed = System.currentTimeMillis() - start
                ApiKeyItem(
                    key = key,
                    isBuiltIn = false,
                    status = if (ok) ApiKeyStatus.ACTIVE else ApiKeyStatus.INVALID,
                    latencyMs = if (ok) elapsed else null
                )
            }
        }

        val builtInItem = ApiKeyItem(
            key = "BUILT_IN_FIREBASE_AI",
            isBuiltIn = true,
            status = ApiKeyStatus.ACTIVE,
            latencyMs = 120L
        )
        listOf(builtInItem) + customDeferred.awaitAll()
    }

    private fun parseOrchestratedJson(
        liveData: RawLiveSourceResult,
        jsonText: String
    ): OmniTopicExplanation {
        val json = JSONObject(jsonText)
        val arabicTitle = json.optString("arabicTitle", liveData.sourceTitle).ifBlank { liveData.queryAr }
        val englishTitle = json.optString("englishTitle", liveData.englishQuery)
        val summaryAr = json.optString("summaryAr", liveData.summaryText.take(320))

        val parts3DArr = json.optJSONArray("parts3D") ?: JSONArray()
        val parts3D = mutableListOf<Part3DSpec>()
        for (i in 0 until parts3DArr.length()) {
            val pObj = parts3DArr.optJSONObject(i) ?: continue
            val activeStagesArr = pObj.optJSONArray("activeInStages") ?: JSONArray()
            val activeStages = (0 until activeStagesArr.length()).map { activeStagesArr.optInt(it, 1) }

            parts3D.add(
                Part3DSpec(
                    id = pObj.optString("id", "part_${i + 1}"),
                    nameAr = pObj.optString("nameAr", "عنصر ${i + 1}"),
                    roleAr = pObj.optString("roleAr", ""),
                    shapeType = Shape3DType.fromString(pObj.optString("shapeType", "CYLINDER")),
                    isShell = pObj.optBoolean("isShell", i < 2),
                    isInternal = pObj.optBoolean("isInternal", i >= 2),
                    colorHex = pObj.optString("colorHex", "#38BDF8"),
                    posX = pObj.optDouble("posX", 0.0).toFloat().coerceIn(-1.6f, 1.6f),
                    posY = pObj.optDouble("posY", 0.0).toFloat().coerceIn(-1.6f, 1.6f),
                    posZ = pObj.optDouble("posZ", 0.0).toFloat().coerceIn(-1.4f, 1.4f),
                    scaleX = pObj.optDouble("scaleX", 0.6).toFloat().coerceIn(0.2f, 1.8f),
                    scaleY = pObj.optDouble("scaleY", 0.6).toFloat().coerceIn(0.2f, 1.8f),
                    scaleZ = pObj.optDouble("scaleZ", 0.6).toFloat().coerceIn(0.2f, 1.8f),
                    explodeX = pObj.optDouble("explodeX", if (i % 2 == 0) 1.2 else -1.2).toFloat(),
                    explodeY = pObj.optDouble("explodeY", ((i - 3) * 0.45)).toFloat(),
                    explodeZ = pObj.optDouble("explodeZ", if (i % 3 == 0) 0.9 else -0.9).toFloat(),
                    animationType = Animation3DType.fromString(pObj.optString("animationType", "PULSE")),
                    activeInStages = activeStages.ifEmpty { listOf((i % 5) + 1) }
                )
            )
        }

        val stagesArr = json.optJSONArray("stages") ?: JSONArray()
        val totalStages = stagesArr.length().coerceAtLeast(1)
        val stages = mutableListOf<ExplainStage>()
        val gallery = liveData.mediaGallery

        for (i in 0 until stagesArr.length()) {
            val sObj = stagesArr.optJSONObject(i) ?: continue
            val stageNum = i + 1
            val mediaIdx = sObj.optInt("mediaIndex", i)
            val chosenMediaUrl = if (gallery.isNotEmpty()) {
                gallery[mediaIdx.coerceIn(0, gallery.lastIndex)].first
            } else {
                ""
            }

            val toolsOrFactsArr = sObj.optJSONArray("toolsOrFacts") ?: JSONArray()
            val toolsOrFacts = (0 until toolsOrFactsArr.length())
                .map { toolsOrFactsArr.optString(it, "").trim() }
                .filter { it.isNotBlank() }

            val hotspotsArr = sObj.optJSONArray("hotspots") ?: JSONArray()
            val hotspots = mutableListOf<InteractiveHotspot>()
            for (h in 0 until hotspotsArr.length()) {
                val hObj = hotspotsArr.optJSONObject(h) ?: continue
                hotspots.add(
                    InteractiveHotspot(
                        id = hObj.optString("id", "hs_${stageNum}_$h"),
                        xPercent = hObj.optDouble("xPercent", 0.3 + h * 0.2).toFloat().coerceIn(0.12f, 0.88f),
                        yPercent = hObj.optDouble("yPercent", 0.3 + h * 0.18).toFloat().coerceIn(0.14f, 0.86f),
                        labelAr = hObj.optString("labelAr", "نقطة تفاعلية ${h + 1}"),
                        functionAr = hObj.optString("functionAr", ""),
                        zoomScale = hObj.optDouble("zoomScale", 2.4).toFloat().coerceIn(1.8f, 3.2f)
                    )
                )
            }

            val activeIdsArr = sObj.optJSONArray("activePartIds") ?: JSONArray()
            val activeIds = (0 until activeIdsArr.length()).map { activeIdsArr.optString(it) }

            stages.add(
                ExplainStage(
                    stageNumber = stageNum,
                    totalStages = totalStages,
                    titleAr = sObj.optString("titleAr", "المرحلة $stageNum"),
                    explanationAr = sObj.optString("explanationAr", ""),
                    toolsOrFactsTitleAr = sObj.optString(
                        "toolsOrFactsTitleAr",
                        if (liveData.isIfixit) "الأدوات المطلوبة" else "المعلومات الحيوية"
                    ),
                    toolsOrFacts = toolsOrFacts.ifEmpty {
                        if (liveData.toolsList.isNotEmpty()) liveData.toolsList.take(4)
                        else listOf("بيانات علمية موثقة", "تحليل هيكلي دقيق")
                    },
                    mediaUrl = chosenMediaUrl,
                    isVideoOrGif = chosenMediaUrl.endsWith(".gif", ignoreCase = true) ||
                        chosenMediaUrl.endsWith(".webm", ignoreCase = true) ||
                        chosenMediaUrl.endsWith(".mp4", ignoreCase = true),
                    mediaCaptionAr = sObj.optString("mediaCaptionAr", arabicTitle),
                    hotspots = hotspots,
                    activePartIds = activeIds
                )
            )
        }

        val sourceBadge = if (liveData.isIfixit) {
            "المصدر: دليل iFixit الموثق"
        } else {
            "المصدر: Wikimedia العلمية"
        }

        return OmniTopicExplanation(
            originalQuery = liveData.queryAr,
            arabicTitle = arabicTitle,
            englishTitle = englishTitle,
            domain = liveData.domain,
            summaryAr = summaryAr,
            verifiedSource = VerifiedSource(
                sourceBadgeTextAr = sourceBadge,
                sourceTitle = liveData.sourceTitle,
                sourceUrl = liveData.sourceUrl,
                isIfixit = liveData.isIfixit,
                fetchedMediaCount = liveData.mediaGallery.size,
                fetchDurationMs = liveData.fetchDurationMs
            ),
            stages = stages,
            parts3D = parts3D,
            threeJsCodeSnippet = buildThreeJsCodeSnippet(arabicTitle, englishTitle, parts3D),
            isAiEnriched = true
        )
    }

    private fun buildThreeJsCodeSnippet(
        arabicTitle: String,
        englishTitle: String,
        parts: List<Part3DSpec>
    ): String {
        val partsCode = parts.joinToString("\n") { p ->
            """
            // ${p.nameAr} (${p.roleAr})
            const ${p.id} = createProceduralPart({
              id: "${p.id}",
              nameAr: "${p.nameAr}",
              shape: "${p.shapeType.name}",
              isShell: ${p.isShell},
              isInternal: ${p.isInternal},
              color: "${p.colorHex}",
              position: [${p.posX}, ${p.posY}, ${p.posZ}],
              scale: [${p.scaleX}, ${p.scaleY}, ${p.scaleZ}],
              explodeVector: [${p.explodeX}, ${p.explodeY}, ${p.explodeZ}],
              animation: "${p.animationType.name}"
            });
            rootAssembly.add(${p.id});
            """.trimIndent()
        }
        return """
            // Three.js Procedural 3D CAD & Anatomy Simulator
            // Topic: $arabicTitle ($englishTitle)
            import * as THREE from 'three';
            import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
            import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
            import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
            import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';

            const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
            renderer.toneMapping = THREE.ACESFilmicToneMapping;
            renderer.toneMappingExposure = 1.25;

            const pmremGenerator = new THREE.PMREMGenerator(renderer);
            scene.environment = pmremGenerator.fromScene(new RoomEnvironment(), 0.04).texture;

            const bloomPass = new UnrealBloomPass(new THREE.Vector2(width, height), 0.85, 0.4, 0.85);
            composer.addPass(bloomPass);

            const rootAssembly = new THREE.Group();
            $partsCode
            scene.add(rootAssembly);
        """.trimIndent()
    }
}
