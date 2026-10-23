package com.example.data

import android.util.Log
import com.example.model.Animation3DType
import com.example.model.ExplainStage
import com.example.model.InteractiveHotspot
import com.example.model.OmniTopicExplanation
import com.example.model.Part3DSpec
import com.example.model.RawFetchedStep
import com.example.model.RawLiveSourceResult
import com.example.model.Shape3DType
import com.example.model.TopicDomain
import com.example.model.VerifiedSource
import com.example.model.WikiArticleExtract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "LiveApiDataSource"
const val OMNI_USER_AGENT =
    "OmniExplainPro/3.0 (Android; Interactive Science & Repair Encyclopedia; +https://aistudio.google.com)"

class LiveApiDataSource {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", OMNI_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()

    // Instant local dictionary for sub-10ms translation of common Arabic scientific & repair terms
    private val fastDictionary = listOf(
        "بطارية آيفون" to "iPhone 15 Pro Battery Replacement",
        "iphone 15" to "iPhone 15 Pro Battery Replacement",
        "آيفون" to "iPhone 15 Pro Teardown",
        "بلايستيشن" to "PlayStation 5 DualSense Controller",
        "ps5" to "PlayStation 5 DualSense Controller",
        "dualsense" to "DualSense Controller",
        "سامسونج" to "Samsung Galaxy S23 Ultra",
        "s23" to "Samsung Galaxy S23 Ultra",
        "ماك بوك" to "MacBook Pro Teardown",
        "macbook" to "MacBook Pro",
        "نينتندو" to "Nintendo Switch Joy-Con",
        "joy-con" to "Nintendo Switch Joy-Con",
        "هاتف" to "Smartphone",
        "جوال" to "Smartphone",
        "موبايل" to "Smartphone",
        "لابتوب" to "Laptop",
        "حاسوب" to "Computer hardware",
        "القلب" to "Human heart",
        "قلب" to "Human heart",
        "العين" to "Human eye",
        "عين" to "Human eye",
        "الدماغ" to "Human brain",
        "دماغ" to "Human brain",
        "الكلية" to "Kidney",
        "كلية" to "Kidney",
        "الأذن" to "Human ear",
        "أذن" to "Human ear",
        "الرئة" to "Lung",
        "رئة" to "Lung",
        "خلية" to "Cell (biology)",
        "الحمض النووي" to "DNA",
        "النفاث" to "Turbofan",
        "توربيني" to "Turbofan",
        "الاحتراق الداخلي" to "Internal combustion engine",
        "محرك" to "Internal combustion engine",
        "المفاعل النووي" to "Nuclear reactor",
        "مفاعل نووي" to "Nuclear reactor",
        "التوربين البخاري" to "Steam turbine",
        "توربين" to "Steam turbine",
        "فرامل" to "Anti-lock braking system",
        "السيارة" to "Car engine",
        "سيارة" to "Car engine",
        "الألياف البصرية" to "Optical fiber",
        "ألياف بصرية" to "Optical fiber",
        "المعالج" to "Central processing unit",
        "ترانزستور" to "Transistor",
        "الأقمار الصناعية" to "Satellite navigation",
        "gps" to "Global Positioning System",
        "الثقب الأسود" to "Black hole",
        "ثقب أسود" to "Black hole",
        "الخلايا الشمسية" to "Solar cell",
        "خلية شمسية" to "Solar cell",
        "الليزر" to "Laser",
        "البطارية" to "Lithium-ion battery"
    )

    private fun executeGet(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w(TAG, "HTTP ${response.code} for URL: $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network error fetching $url: ${e.message}")
            null
        }
    }

    private fun encodeUrlParam(param: String): String {
        return URLEncoder.encode(param.trim(), "UTF-8").replace("+", "%20")
    }

    fun inferDomainInstantly(query: String): TopicDomain {
        val q = query.lowercase()
        return when {
            listOf(
                "إصلاح", "تفكيك", "بطارية", "شاشة", "آيفون", "iphone", "samsung",
                "ps5", "playstation", "macbook", "switch", "fix", "repair", "استبدال",
                "تنظيف", "ذراع تحكم", "dualsense", "joy-con", "هاتف", "سماعة"
            ).any { q.contains(it) } -> TopicDomain.REPAIR_ELECTRONICS

            listOf(
                "قلب", "عين", "دماغ", "كلية", "دم", "تشريح", "طب", "خلية", "عصب",
                "أذن", "رئة", "معدة", "كبد", "جسم", "شريان", "heart", "brain", "eye", "kidney"
            ).any { q.contains(it) } -> TopicDomain.MEDICINE_ANATOMY

            listOf(
                "محرك", "توربين", "نفاث", "مفاعل", "فرامل", "ميكانيك", "سيارة",
                "طائرة", "شوط", "مكبس", "engine", "turbine", "turbofan", "reactor"
            ).any { q.contains(it) } -> TopicDomain.MECHANICS_ENGINES

            listOf(
                "ثقب", "فضاء", "نجم", "مجرة", "جاذبية", "فيزياء", "كم", "نسبي",
                "ضوء", "كون", "كوكب", "black hole", "gravity"
            ).any { q.contains(it) } -> TopicDomain.PHYSICS_SPACE

            listOf(
                "ذرة", "جزيء", "كيمياء", "تفاعل", "إلكترون", "بروتون", "حمض",
                "عنصر", "atom", "molecule"
            ).any { q.contains(it) } -> TopicDomain.CHEMISTRY_ATOMS

            else -> TopicDomain.TECH_TELECOM
        }
    }

    private fun cleanArabicSearchCore(query: String): String {
        return query
            .replace(Regex("^(كيف يعمل|كيف تعمل|كيف نصلح|كيفية إصلاح|تشريح|شرح|ما هو|ما هي)\\s+"), "")
            .trim()
            .ifBlank { query.trim() }
    }

    /**
     * 1. Fast 200ms Term Translation (without waiting for Gemini):
     * Uses instant local dictionary + MyMemory API (`https://api.mymemory.translated.net/get?q=...&langpair=ar|en`)
     */
    suspend fun translateQueryFast(query: String): String = withContext(Dispatchers.IO) {
        val lower = query.lowercase().trim()
        // Check local instant dictionary first
        for ((arKey, enVal) in fastDictionary) {
            if (lower.contains(arKey)) {
                return@withContext enVal
            }
        }
        // If already mostly English ASCII characters, return cleaned English
        val hasArabic = query.any { it in '\u0600'..'\u06FF' }
        if (!hasArabic) {
            return@withContext query.trim()
        }

        val coreAr = cleanArabicSearchCore(query)
        val translated = withTimeoutOrNull(2200L) {
            val url =
                "https://api.mymemory.translated.net/get?q=${encodeUrlParam(coreAr)}&langpair=ar|en"
            val raw = executeGet(url) ?: return@withTimeoutOrNull null
            try {
                val json = JSONObject(raw)
                val text = json.optJSONObject("responseData")
                    ?.optString("translatedText", "")
                    ?.trim()
                if (!text.isNullOrBlank() && !text.contains("MYMEMORY WARNING", ignoreCase = true)) {
                    text.replace(Regex("^(How does|How do|How to|The)\\s+", RegexOption.IGNORE_CASE), "")
                        .trim()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
        translated?.takeIf { it.isNotBlank() } ?: coreAr
    }

    /**
     * 2. Parallel Sub-Second Live Fetch across iFixit, Arabic Wikipedia, English Wikipedia, and Wikimedia Commons
     */
    suspend fun fetchLiveMultiSourceInstant(query: String): RawLiveSourceResult = coroutineScope {
        val startMs = System.currentTimeMillis()
        val domain = inferDomainInstantly(query)
        val isRepairDomain = (domain == TopicDomain.REPAIR_ELECTRONICS)
        val coreArQuery = cleanArabicSearchCore(query)

        // Translate in ~0-200ms without Gemini
        val englishQuery = translateQueryFast(query)

        // Launch all 4 global APIs in parallel!
        val arWikiDeferred = async(Dispatchers.IO) {
            fetchWikipediaGeneratorSearch(coreArQuery, "ar")
        }
        val enWikiDeferred = async(Dispatchers.IO) {
            fetchWikipediaGeneratorSearch(englishQuery, "en")
        }
        val commonsDeferred = async(Dispatchers.IO) {
            fetchWikimediaCommonsGeneratorSearch(englishQuery)
        }
        val ifixitDeferred = async(Dispatchers.IO) {
            if (isRepairDomain || query.contains("إصلاح") || query.contains("تفكيك") || query.contains("استبدال")) {
                fetchIfixitGuideDirect(englishQuery, startMs)
            } else {
                null
            }
        }

        val ifixitResult = ifixitDeferred.await()
        val arExtracts = arWikiDeferred.await()
        val enExtracts = enWikiDeferred.await()
        val commonsMedia = commonsDeferred.await()

        // If iFixit returned a rich repair guide with steps & images, return it enriched with any Arabic Wiki summary
        if (ifixitResult != null && (ifixitResult.steps.isNotEmpty() || ifixitResult.mediaGallery.isNotEmpty())) {
            val combinedMedia = (ifixitResult.mediaGallery + commonsMedia).distinctBy { it.first }
            return@coroutineScope ifixitResult.copy(
                domain = domain,
                queryAr = query,
                englishQuery = englishQuery,
                arExtracts = arExtracts,
                enExtracts = enExtracts,
                mediaGallery = combinedMedia,
                fetchDurationMs = System.currentTimeMillis() - startMs
            )
        }

        // Otherwise assemble from Arabic Wikipedia + English Wikipedia + Wikimedia Commons
        val gallery = mutableListOf<Pair<String, String>>()
        arExtracts.forEach { item ->
            if (!item.imageUrl.isNullOrBlank()) {
                gallery.add(item.imageUrl to item.title)
            }
        }
        enExtracts.forEach { item ->
            if (!item.imageUrl.isNullOrBlank()) {
                gallery.add(item.imageUrl to item.title)
            }
        }
        gallery.addAll(commonsMedia)

        val uniqueMedia = gallery
            .filter { it.first.startsWith("https://") }
            .distinctBy { it.first }
            .take(16)

        val primaryArTitle = arExtracts.firstOrNull()?.title ?: coreArQuery
        val primaryPageUrl = arExtracts.firstOrNull()?.pageUrl
            ?: enExtracts.firstOrNull()?.pageUrl
            ?: "https://ar.wikipedia.org/wiki/${encodeUrlParam(coreArQuery)}"

        val combinedSummary = buildString {
            arExtracts.forEach {
                if (it.extract.isNotBlank()) {
                    append(it.extract.trim()).append("\n\n")
                }
            }
            if (isEmpty()) {
                enExtracts.forEach {
                    if (it.extract.isNotBlank()) {
                        append(it.extract.trim()).append("\n\n")
                    }
                }
            }
        }.trim()

        val elapsed = System.currentTimeMillis() - startMs
        RawLiveSourceResult(
            isIfixit = false,
            domain = domain,
            queryAr = query,
            englishQuery = englishQuery,
            sourceTitle = primaryArTitle,
            sourceUrl = primaryPageUrl,
            summaryText = combinedSummary,
            arExtracts = arExtracts,
            enExtracts = enExtracts,
            toolsList = emptyList(),
            steps = emptyList(),
            mediaGallery = uniqueMedia,
            fetchDurationMs = elapsed
        )
    }

    /**
     * Official Wikipedia Generator Search API (`origin=*`, `pageimages|extracts`, `pithumbsize=1000`)
     */
    private fun fetchWikipediaGeneratorSearch(
        searchTerm: String,
        lang: String
    ): List<WikiArticleExtract> {
        val url =
            "https://$lang.wikipedia.org/w/api.php?action=query&format=json&origin=*&generator=search&gsrsearch=${encodeUrlParam(searchTerm)}&gsrlimit=4&prop=pageimages|extracts&exintro=1&explaintext=1&piprop=original|thumbnail&pithumbsize=1000"
        val raw = executeGet(url) ?: return emptyList()
        val list = mutableListOf<WikiArticleExtract>()
        try {
            val pages = JSONObject(raw).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val pageList = mutableListOf<Pair<Int, JSONObject>>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val pObj = pages.optJSONObject(keys.next()) ?: continue
                val index = pObj.optInt("index", 99)
                pageList.add(index to pObj)
            }
            pageList.sortBy { it.first }

            for ((_, pageObj) in pageList) {
                val title = pageObj.optString("title", "")
                val extract = pageObj.optString("extract", "").trim()
                val thumbUrl = pageObj.optJSONObject("thumbnail")?.optString("source")
                val origUrl = pageObj.optJSONObject("original")?.optString("source")
                // Prefer 1000px thumbnail for SVGs/large files so they render cleanly everywhere
                val bestImg = (thumbUrl?.takeIf { it.isNotBlank() } ?: origUrl)
                    ?.let { normalizeUrl(it) }
                    ?.takeIf { isEducationalMediaUrl(it) }

                val pageUrl = "https://$lang.wikipedia.org/wiki/${encodeUrlParam(title.replace(" ", "_"))}"
                if (title.isNotBlank()) {
                    list.add(
                        WikiArticleExtract(
                            title = title,
                            extract = extract,
                            imageUrl = bestImg,
                            pageUrl = pageUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing $lang wikipedia generator search", e)
        }
        return list
    }

    /**
     * Official Wikimedia Commons Generator Search API (`gsrnamespace=6`, `iiprop=url|extmetadata|mime`, `iiurlwidth=1000`)
     */
    private fun fetchWikimediaCommonsGeneratorSearch(englishQuery: String): List<Pair<String, String>> {
        val url =
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&origin=*&generator=search&gsrsearch=${encodeUrlParam(englishQuery)}&gsrnamespace=6&gsrlimit=12&prop=imageinfo&iiprop=url|extmetadata|mime&iiurlwidth=1000"
        val raw = executeGet(url) ?: return emptyList()
        val list = mutableListOf<Pair<String, String>>()
        try {
            val pages = JSONObject(raw).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val pageObj = pages.optJSONObject(keys.next()) ?: continue
                val rawTitle = pageObj.optString("title", "")
                if (!isEducationalMediaUrl(rawTitle)) continue
                val imageInfoArr = pageObj.optJSONArray("imageinfo") ?: continue
                if (imageInfoArr.length() == 0) continue
                val info = imageInfoArr.getJSONObject(0)
                val mime = info.optString("mime", "").lowercase()
                if (mime.contains("audio") || mime.contains("ogg")) continue

                val thumbUrl = info.optString("thumburl", "")
                val fullUrl = info.optString("url", "")
                val chosenUrl = if (thumbUrl.isNotBlank()) thumbUrl else fullUrl
                if (chosenUrl.isBlank() || !isEducationalMediaUrl(chosenUrl)) continue

                val extMeta = info.optJSONObject("extmetadata")
                val descHtml = extMeta?.optJSONObject("ImageDescription")?.optString("value", "")
                    ?: extMeta?.optJSONObject("ObjectName")?.optString("value", "")
                    ?: ""
                val cleanDesc = descHtml.replace(Regex("<[^>]*>"), "").trim()
                    .takeIf { it.isNotBlank() && it.length > 4 }
                    ?: rawTitle.removePrefix("File:").substringBeforeLast(".").replace("_", " ")

                list.add(normalizeUrl(chosenUrl) to cleanDesc.take(120))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Wikimedia Commons search", e)
        }
        return list
    }

    /**
     * Direct iFixit API Integration (`suggest` + `guides/{guideid}`)
     */
    private fun fetchIfixitGuideDirect(
        englishQuery: String,
        startMs: Long
    ): RawLiveSourceResult? {
        val suggestUrl =
            "https://www.ifixit.com/api/2.0/suggest/${encodeUrlParam(englishQuery)}?doctypes=guide"
        var suggestJson = executeGet(suggestUrl)
        var guideId = extractGuideIdFromSuggest(suggestJson)

        if (guideId == null) {
            val simplified = englishQuery
                .replace(Regex("(?i)\\b(how to|fix|repair|replace|teardown|disassemble)\\b"), "")
                .trim()
            if (simplified.isNotBlank() && simplified != englishQuery) {
                suggestJson = executeGet(
                    "https://www.ifixit.com/api/2.0/suggest/${encodeUrlParam(simplified)}?doctypes=guide"
                )
                guideId = extractGuideIdFromSuggest(suggestJson)
            }
        }
        if (guideId == null) return null

        val guideBody = executeGet("https://www.ifixit.com/api/2.0/guides/$guideId") ?: return null
        return try {
            val root = JSONObject(guideBody)
            val title = root.optString("title", englishQuery)
            val publicUrl = root.optString("url", "https://www.ifixit.com/Guide/-/$guideId")
            val introRaw = root.optString("introduction_raw", "")
                .ifBlank { root.optString("summary", "") }

            val toolsList = mutableListOf<String>()
            val toolsArr = root.optJSONArray("tools") ?: JSONArray()
            for (i in 0 until toolsArr.length()) {
                val toolText = toolsArr.optJSONObject(i)?.optString("text", "")?.trim().orEmpty()
                if (toolText.isNotBlank()) toolsList.add(toolText)
            }

            val fetchedSteps = mutableListOf<RawFetchedStep>()
            val allMedia = mutableListOf<Pair<String, String>>()

            extractBestIfixitImageUrl(root.optJSONObject("image"))?.let { heroUrl ->
                allMedia.add(heroUrl to title)
            }

            val stepsArr = root.optJSONArray("steps") ?: JSONArray()
            for (i in 0 until stepsArr.length()) {
                val stepObj = stepsArr.optJSONObject(i) ?: continue
                val stepNum = stepObj.optInt("orderby", i + 1)
                val stepTitle = stepObj.optString("title", "").ifBlank { "الخطوة $stepNum" }

                val linesArr = stepObj.optJSONArray("lines") ?: JSONArray()
                val linesText = buildString {
                    for (j in 0 until linesArr.length()) {
                        val rawLine = linesArr.optJSONObject(j)?.optString("text_raw", "")?.trim().orEmpty()
                        if (rawLine.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(rawLine)
                        }
                    }
                }

                val stepImages = mutableListOf<String>()
                val mediaDataArr = stepObj.optJSONObject("media")?.optJSONArray("data") ?: JSONArray()
                for (k in 0 until mediaDataArr.length()) {
                    val imgUrl = extractBestIfixitImageUrl(mediaDataArr.optJSONObject(k))
                    if (!imgUrl.isNullOrBlank()) {
                        stepImages.add(imgUrl)
                        allMedia.add(imgUrl to "$stepTitle: ${linesText.take(90)}")
                    }
                }

                if (linesText.isNotBlank() || stepImages.isNotEmpty()) {
                    fetchedSteps.add(
                        RawFetchedStep(
                            stepNumber = stepNum,
                            title = stepTitle,
                            description = linesText,
                            imageUrls = stepImages
                        )
                    )
                }
            }

            RawLiveSourceResult(
                isIfixit = true,
                domain = TopicDomain.REPAIR_ELECTRONICS,
                queryAr = englishQuery,
                englishQuery = englishQuery,
                sourceTitle = title,
                sourceUrl = publicUrl,
                summaryText = introRaw,
                arExtracts = emptyList(),
                enExtracts = emptyList(),
                toolsList = toolsList,
                steps = fetchedSteps,
                mediaGallery = allMedia.distinctBy { it.first },
                fetchDurationMs = System.currentTimeMillis() - startMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing iFixit guide $guideId", e)
            null
        }
    }

    private fun extractGuideIdFromSuggest(jsonStr: String?): Int? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val results = JSONObject(jsonStr).optJSONArray("results") ?: return null
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                if (item.optString("dataType") == "guide" || item.has("guideid")) {
                    val id = item.optInt("guideid", -1)
                    if (id > 0) return id
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractBestIfixitImageUrl(imgObj: JSONObject?): String? {
        if (imgObj == null) return null
        for (key in listOf("large", "huge", "original", "standard", "medium")) {
            val url = imgObj.optString(key, "").trim()
            if (url.startsWith("http")) return url
        }
        return null
    }

    /**
     * 3. Instant Progressive Explanation Builder (< 1 second, zero Gemini wait):
     * Immediately constructs 4-6 interactive Arabic stages with real fetched Wikipedia/Wikimedia/iFixit
     * images, interactive hotspots, and a custom 3D procedural assembly!
     */
    fun buildInstantExplanation(raw: RawLiveSourceResult): OmniTopicExplanation {
        val parts3D = generateInstantProcedural3D(raw.domain, raw.queryAr, raw.sourceTitle)
        val gallery = raw.mediaGallery

        val stages = mutableListOf<ExplainStage>()

        if (raw.isIfixit && raw.steps.isNotEmpty()) {
            val selectedSteps = raw.steps.take(6)
            val total = selectedSteps.size
            selectedSteps.forEachIndexed { idx, step ->
                val stageNum = idx + 1
                val imgUrl = step.imageUrls.firstOrNull()
                    ?: gallery.getOrNull(idx % gallery.size.coerceAtLeast(1))?.first
                    ?: ""
                stages.add(
                    ExplainStage(
                        stageNumber = stageNum,
                        totalStages = total,
                        titleAr = if (step.title.isNotBlank()) "خطوة الفك والإصلاح $stageNum: ${step.title}" else "المرحلة $stageNum من الدليل الموثق",
                        explanationAr = step.description.ifBlank { raw.summaryText.ifBlank { "فحص وتفكيك المكونات الداخلية بدقة وفق دليل iFixit." } },
                        toolsOrFactsTitleAr = "الأدوات المطلوبة",
                        toolsOrFacts = raw.toolsList.take(4).ifEmpty {
                            listOf("مفك دقيق iFixit", "أداة فتح بلاستيكية Spudger", "ملقط عازل")
                        },
                        mediaUrl = imgUrl,
                        isVideoOrGif = isAnimatedUrl(imgUrl),
                        mediaCaptionAr = step.title.ifBlank { raw.sourceTitle },
                        hotspots = createInstantHotspotsForStage(stageNum, raw.domain, raw.sourceTitle),
                        activePartIds = parts3D.filter { it.activeInStages.contains(stageNum) }.map { it.id }
                    )
                )
            }
        } else {
            // Split Wikipedia Arabic/English summary sentences + Wikimedia Commons gallery into 5 rich stages
            val sentences = raw.summaryText
                .split(Regex("(?<=[.۔!؟\\n])\\s+"))
                .map { it.trim() }
                .filter { it.length > 25 }

            val targetStageCount = maxOf(4, minOf(6, maxOf(gallery.size, sentences.size))).coerceIn(4, 6)
            val stageTitles = getInstantStageTitles(raw.domain, raw.sourceTitle, targetStageCount)

            for (i in 0 until targetStageCount) {
                val stageNum = i + 1
                val mediaPair = if (gallery.isNotEmpty()) {
                    gallery[i % gallery.size]
                } else {
                    "" to raw.sourceTitle
                }
                val explanationChunk = when {
                    sentences.size >= targetStageCount -> {
                        val chunkSize = (sentences.size.toFloat() / targetStageCount).toInt().coerceAtLeast(1)
                        val start = (i * chunkSize).coerceAtMost(sentences.lastIndex)
                        val end = ((i + 1) * chunkSize).coerceAtMost(sentences.size)
                        sentences.subList(start, end).joinToString(" ")
                    }
                    sentences.isNotEmpty() -> sentences[i % sentences.size]
                    else -> "تحليل علمي وتفصيلي للمرحلة $stageNum من عمل «${raw.sourceTitle}» بالاعتماد على المخططات الموثقة من موسوعة ويكيبيديا وWikimedia Commons."
                }

                stages.add(
                    ExplainStage(
                        stageNumber = stageNum,
                        totalStages = targetStageCount,
                        titleAr = stageTitles.getOrElse(i) { "المرحلة $stageNum: ${raw.sourceTitle}" },
                        explanationAr = explanationChunk,
                        toolsOrFactsTitleAr = "المعلومات الحيوية",
                        toolsOrFacts = listOf(
                            "المصدر: ${raw.sourceTitle}",
                            "المجال: ${raw.domain.labelAr}",
                            "الوسائط الموثقة: ${gallery.size} مخطط وصورة"
                        ),
                        mediaUrl = mediaPair.first,
                        isVideoOrGif = isAnimatedUrl(mediaPair.first),
                        mediaCaptionAr = mediaPair.second,
                        hotspots = createInstantHotspotsForStage(stageNum, raw.domain, mediaPair.second),
                        activePartIds = parts3D.filter { it.activeInStages.contains(stageNum) }.map { it.id }
                    )
                )
            }
        }

        val sourceBadge = if (raw.isIfixit) {
            "المصدر: دليل iFixit الموثق"
        } else {
            "المصدر: Wikimedia العلمية"
        }

        return OmniTopicExplanation(
            originalQuery = raw.queryAr,
            arabicTitle = raw.sourceTitle.ifBlank { raw.queryAr },
            englishTitle = raw.englishQuery,
            domain = raw.domain,
            summaryAr = raw.summaryText.take(360).ifBlank { raw.queryAr },
            verifiedSource = VerifiedSource(
                sourceBadgeTextAr = sourceBadge,
                sourceTitle = raw.sourceTitle,
                sourceUrl = raw.sourceUrl,
                isIfixit = raw.isIfixit,
                fetchedMediaCount = raw.mediaGallery.size,
                fetchDurationMs = raw.fetchDurationMs
            ),
            stages = stages,
            parts3D = parts3D,
            threeJsCodeSnippet = buildInstantThreeJsCode(raw.sourceTitle, raw.englishQuery, parts3D),
            isAiEnriched = false
        )
    }

    private fun getInstantStageTitles(
        domain: TopicDomain,
        topicName: String,
        count: Int
    ): List<String> {
        val base = when (domain) {
            TopicDomain.MEDICINE_ANATOMY -> listOf(
                "البنية التشريحية العامة لـ $topicName",
                "الأنسجة والحجرات الداخلية الدقيقة",
                "آلية العمل الحيوي والتدفق الفسيولوجي",
                "التنظيم العصبي والحيوي المتزامن",
                "التكامل الوظيفي داخل الجسم البشري",
                "المؤشرات الحيوية والكفاءة الوظيفية"
            )
            TopicDomain.MECHANICS_ENGINES -> listOf(
                "الهيكل الهندسي الخارجي لـ $topicName",
                "سحب وتوجيه الطاقة والموائع الداخلة",
                "الضغط والتحويل الميكانيكي الحراري",
                "نقل الحركة عبر التروس والمحاور الدوارة",
                "توليد الدفع أو العزم النهائي",
                "أنظمة التبريد والتحكم الديناميكي"
            )
            TopicDomain.REPAIR_ELECTRONICS -> listOf(
                "فحص الجهاز وفصل مصادر الطاقة",
                "فتح الغطاء الخارجي وفك المسامير",
                "تحرير الموصلات الدقيقة واللوحة الأم",
                "استخراج القطعة المستهدفة بأمان",
                "تركيب المكون الجديد وإعادة التوصيل",
                "إغلاق الجهاز واختبار الأداء الشامل"
            )
            TopicDomain.PHYSICS_SPACE -> listOf(
                "البنية الفيزيائية والزمكانية لـ $topicName",
                "توزيع المجالات والقوى الأساسية",
                "تفاعل الطاقة والمادة عند المركز",
                "الإشعاع والموجات المنبعثة في الفضاء",
                "النتائج الكونية والرصد الفلكي الحديث",
                "النماذج الرياضية والفيزيائية المتقدمة"
            )
            else -> listOf(
                "المعمارية الأساسية لنظام $topicName",
                "استقبال ومعالجة الإشارات والبيانات",
                "الدوائر والطبقات الداخلية الدقيقة",
                "نقل الطاقة والمعلومات فائقة السرعة",
                "التكامل والأداء العملي للنظام",
                "معايير الكفاءة والتشغيل المتقدم"
            )
        }
        return base.take(count)
    }

    private fun createInstantHotspotsForStage(
        stageNum: Int,
        domain: TopicDomain,
        caption: String
    ): List<InteractiveHotspot> {
        return listOf(
            InteractiveHotspot(
                id = "hs_${stageNum}_1",
                xPercent = 0.32f,
                yPercent = 0.36f,
                labelAr = "المركز الوظيفي الرئيسي",
                functionAr = "المنطقة المحورية المسؤولة عن التفاعل الأساسي في المرحلة $stageNum ($caption).",
                zoomScale = 2.4f
            ),
            InteractiveHotspot(
                id = "hs_${stageNum}_2",
                xPercent = 0.64f,
                yPercent = 0.45f,
                labelAr = if (domain == TopicDomain.REPAIR_ELECTRONICS) "نقطة التوصيل والتثبيت" else "المسار الداخلي الدقيق",
                functionAr = "يوضح ارتباط الأجزاء الداخلية ومسار انتقال الطاقة أو الإشارة في هذه المرحلة.",
                zoomScale = 2.5f
            ),
            InteractiveHotspot(
                id = "hs_${stageNum}_3",
                xPercent = 0.48f,
                yPercent = 0.68f,
                labelAr = "الغلاف المحيط والحماية",
                functionAr = "يحافظ على ثبات واستقرار المكونات الداخلية أثناء التشغيل أو الفك.",
                zoomScale = 2.2f
            )
        )
    }

    private fun generateInstantProcedural3D(
        domain: TopicDomain,
        queryAr: String,
        title: String
    ): List<Part3DSpec> {
        return when (domain) {
            TopicDomain.MEDICINE_ANATOMY -> listOf(
                Part3DSpec("shell_1", "الغشاء الخارجي المحيط ($title)", "غلاف نسيجي خارجي يحمي الأجزاء الحيوية ويتحول لزجاج شفاف في وضع X-Ray", Shape3DType.SPHERE, true, false, "#F43F5E", 0f, 0f, 0f, 1.35f, 1.45f, 1.25f, 0f, 1.5f, 1.2f, Animation3DType.PULSE, listOf(1, 6)),
                Part3DSpec("shell_2", "الجدار العضلي الواقي", "الطبقة الهيكلية الداعمة للأنسجة الداخلية", Shape3DType.DOME, true, false, "#FB7185", 0f, -0.2f, 0f, 1.3f, 1.2f, 1.2f, 0f, -1.5f, -1.1f, Animation3DType.PULSE, listOf(1, 5)),
                Part3DSpec("int_1", "الحجرة الحيوية اليمنى", "استقبال وتوجيه التدفق الحيوي الأولي", Shape3DType.CAPSULE, false, true, "#38BDF8", -0.45f, 0.15f, 0.1f, 0.55f, 0.72f, 0.55f, -1.4f, 0.4f, 0.5f, Animation3DType.PULSE, listOf(2, 3)),
                Part3DSpec("int_2", "الحجرة الحيوية اليسرى", "الضخ والمعالجة الفسيولوجية عالية الضغط", Shape3DType.CAPSULE, false, true, "#EF4444", 0.45f, 0.1f, 0.1f, 0.6f, 0.8f, 0.6f, 1.4f, 0.3f, 0.5f, Animation3DType.PULSE, listOf(2, 3, 4)),
                Part3DSpec("int_3", "القناة الشريانية / الناقلة الكبرى", "نقل التدفق الرئيسي من المركز إلى بقية أنحاء الجسم", Shape3DType.TUBE_ARC, false, true, "#F59E0B", 0f, 0.75f, 0f, 0.65f, 0.65f, 0.65f, 0f, 1.7f, 0f, Animation3DType.OSCILLATE_Y, listOf(3, 4)),
                Part3DSpec("int_4", "الصمام / المنظم الحيوي المركزي", "ضمان التدفق باتجاه واحد ومنع الارتجاع", Shape3DType.TORUS, false, true, "#10B981", 0f, 0.05f, 0.2f, 0.48f, 0.25f, 0.48f, 0f, 0f, 1.6f, Animation3DType.PULSE, listOf(3, 4, 5)),
                Part3DSpec("int_5", "الشبكة العصبية والوعائية المغذية", "توزيع الإشارات والتغذية الدقيقة للخلايا", Shape3DType.HELIX_RING, false, true, "#A855F7", 0f, -0.35f, -0.15f, 0.75f, 0.4f, 0.75f, 0f, -1.4f, 0.9f, Animation3DType.FLOW_ORBIT, listOf(4, 5, 6)),
                Part3DSpec("int_6", "النواة الوظيفية المركزية", "مركز التوازن الحيوي والتكامل الفسيولوجي", Shape3DType.SPHERE, false, true, "#06B6D4", 0f, 0f, 0f, 0.42f, 0.42f, 0.42f, 1.1f, -0.9f, -1.1f, Animation3DType.PULSE, listOf(1, 2, 5))
            )

            TopicDomain.REPAIR_ELECTRONICS -> listOf(
                Part3DSpec("shell_1", "الشاشة / الواجهة الزجاجية الأمامية", "الطبقة الخارجية الأمامية التي يتم رفعها عند بداية التفكيك", Shape3DType.BOX, true, false, "#38BDF8", 0f, 0f, 0.48f, 1.15f, 1.55f, 0.14f, 0f, 0f, 1.85f, Animation3DType.NONE, listOf(1, 2)),
                Part3DSpec("shell_2", "الهيكل المعدني الخلفي (Chassis)", "الإطار الحامل لجميع المكونات الإلكترونية الداخلية", Shape3DType.BOX, true, false, "#64748B", 0f, 0f, -0.48f, 1.18f, 1.58f, 0.18f, 0f, 0f, -1.85f, Animation3DType.NONE, listOf(1, 6)),
                Part3DSpec("int_1", "وحدة البطارية الليثيوم عالية السعة", "مصدر الطاقة الرئيسي للجهاز ويجب فصل موصلها أولاً للأمان", Shape3DType.BOX, false, true, "#10B981", -0.22f, -0.15f, 0f, 0.58f, 0.95f, 0.22f, -1.45f, -0.4f, 0.4f, Animation3DType.PULSE, listOf(2, 3, 4)),
                Part3DSpec("int_2", "اللوحة الأم (Logic Board)", "تضم المعالج الرئيسي وشرائح الذاكرة ودوائر الطاقة", Shape3DType.BOX, false, true, "#0284C7", 0.35f, 0.15f, 0.05f, 0.42f, 1.05f, 0.18f, 1.45f, 0.3f, 0.3f, Animation3DType.PULSE, listOf(3, 4, 5)),
                Part3DSpec("int_3", "المعالج ووحدة التبريد الحراري", "قلب المعالجة الإلكترونية وتبديد الحرارة", Shape3DType.CYLINDER, false, true, "#F59E0B", 0.35f, 0.32f, 0.16f, 0.35f, 0.22f, 0.35f, 1.1f, 1.2f, 0.9f, Animation3DType.ROTATE_Y, listOf(3, 5)),
                Part3DSpec("int_4", "وحدة الكاميرا والحساسات البصرية", "عدسات وحساسات التصوير الدقيقة أعلى الجهاز", Shape3DType.CYLINDER, false, true, "#A855F7", -0.25f, 0.55f, 0.12f, 0.38f, 0.28f, 0.38f, -1.2f, 1.4f, 0.7f, Animation3DType.PULSE, listOf(4, 5)),
                Part3DSpec("int_5", "موصلات الكابلات المرنة (Flex Cables)", "تربط الشاشة والبطارية باللوحة الأم", Shape3DType.TUBE_ARC, false, true, "#F43F5E", 0.05f, -0.05f, 0.22f, 0.45f, 0.35f, 0.25f, 0f, 1.3f, 1.2f, Animation3DType.OSCILLATE_Y, listOf(2, 3, 5)),
                Part3DSpec("int_6", "محرك الاهتزاز ومنفذ الشحن السفلي", "وحدة التغذية السفلية والاهتزاز اللمسي Taptic Engine", Shape3DType.CAPSULE, false, true, "#06B6D4", 0f, -0.62f, 0.05f, 0.72f, 0.25f, 0.25f, 0f, -1.65f, 0.5f, Animation3DType.PULSE, listOf(4, 6))
            )

            else -> listOf(
                Part3DSpec("shell_1", "الغلاف الهندسي الخارجي العلوي", "الدرع الواقي الخارجي للنظام (يصبح شفافاً في وضع X-Ray)", Shape3DType.CYLINDER, true, false, "#38BDF8", 0f, 0.25f, 0f, 1.35f, 0.9f, 1.35f, 0f, 1.6f, 0.8f, Animation3DType.NONE, listOf(1, 6)),
                Part3DSpec("shell_2", "القاعدة الهيكلية العازلة", "الغلاف السفلي الحامل للمكونات الديناميكية", Shape3DType.CYLINDER, true, false, "#64748B", 0f, -0.35f, 0f, 1.35f, 0.8f, 1.35f, 0f, -1.6f, -0.8f, Animation3DType.NONE, listOf(1, 6)),
                Part3DSpec("int_1", "التوربين / العضو الدوار الرئيسي", "يدور بسرعة عالية لتحويل وتوجيه الطاقة", Shape3DType.GEAR, false, true, "#10B981", 0f, 0.35f, 0f, 0.88f, 0.35f, 0.88f, -1.5f, 0.8f, 0.6f, Animation3DType.ROTATE_Y, listOf(2, 3, 4)),
                Part3DSpec("int_2", "حجرة الضغط والتفاعل المركزية", "توليد الطاقة الحرارية أو الكهرومغناطيسية المكثفة", Shape3DType.SPHERE, false, true, "#F43F5E", 0f, 0f, 0f, 0.65f, 0.65f, 0.65f, 0f, 0f, 1.7f, Animation3DType.PULSE, listOf(2, 3, 5)),
                Part3DSpec("int_3", "حلقات التوجيه والتدفق المغناطيسي", "تنظيم مسار الطاقة ومنع الفقد الحراري", Shape3DType.TORUS, false, true, "#F59E0B", 0f, -0.05f, 0f, 0.98f, 0.32f, 0.98f, 1.5f, 0.4f, 0.6f, Animation3DType.ROTATE_Z, listOf(3, 4)),
                Part3DSpec("int_4", "عمود نقل القدرة المحوري (Core Shaft)", "ينقل العزم والحركة بين مراحل النظام", Shape3DType.CYLINDER, false, true, "#A855F7", 0f, 0f, 0f, 0.28f, 1.45f, 0.28f, -1.3f, -0.7f, -0.9f, Animation3DType.ROTATE_Y, listOf(3, 4, 5)),
                Part3DSpec("int_5", "فوهة الدفع والمخرجات النهائية", "تحويل الضغط الداخلي إلى طاقة حركية أو إشعاعية موجهة", Shape3DType.CONE, false, true, "#06B6D4", 0f, -0.65f, 0f, 0.8f, 0.65f, 0.8f, 1.3f, -1.2f, -0.8f, Animation3DType.OSCILLATE_Y, listOf(4, 5, 6)),
                Part3DSpec("int_6", "المستشعرات ووحدة التحكم الذكية", "مراقبة الأداء وضبط التوازن اللحظي", Shape3DType.HELIX_RING, false, true, "#34D399", 0f, 0.6f, 0f, 0.72f, 0.3f, 0.72f, 0f, 1.5f, -1.2f, Animation3DType.FLOW_ORBIT, listOf(1, 5, 6))
            )
        }
    }

    private fun buildInstantThreeJsCode(
        arabicTitle: String,
        englishTitle: String,
        parts: List<Part3DSpec>
    ): String {
        val partsCode = parts.joinToString("\n") { p ->
            """
            const ${p.id} = createPbrPart({
              nameAr: "${p.nameAr}",
              shape: "${p.shapeType.name}",
              isShell: ${p.isShell},
              isInternal: ${p.isInternal},
              color: "${p.colorHex}",
              pos: [${p.posX}, ${p.posY}, ${p.posZ}],
              explode: [${p.explodeX}, ${p.explodeY}, ${p.explodeZ}]
            });
            scene.add(${p.id});
            """.trimIndent()
        }
        return """
            // Three.js Procedural 3D CAD & Anatomy Simulator
            // Topic: $arabicTitle ($englishTitle)
            import * as THREE from 'three';
            import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
            import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';

            renderer.toneMapping = THREE.ACESFilmicToneMapping;
            scene.environment = pmremGenerator.fromScene(new RoomEnvironment(), 0.04).texture;
            composer.addPass(new UnrealBloomPass(new THREE.Vector2(w, h), 0.85, 0.4, 0.85));

            $partsCode
        """.trimIndent()
    }

    private fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
            else -> trimmed
        }
    }

    private fun isAnimatedUrl(url: String): Boolean {
        val l = url.lowercase()
        return l.endsWith(".gif") || l.endsWith(".webm") || l.endsWith(".mp4")
    }

    private fun isEducationalMediaUrl(urlOrTitle: String): Boolean {
        val lower = urlOrTitle.lowercase()
        val bannedTokens = listOf(
            "commons-logo", "wikimedia-button", "wiki_letter", "question_book",
            "ambox", "edit-clear", "disambig", "padlock", "nuvola",
            "crystal_clear", "oojs_ui", "symbol_", "flag_of_", "speaker_icon",
            "listen_icon", "p_vip", "portal-puzzle", ".ogg", ".oga", ".wav", ".mid"
        )
        return bannedTokens.none { lower.contains(it) }
    }
}
