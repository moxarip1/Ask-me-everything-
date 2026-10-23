package com.example.model

enum class TopicDomain(val labelAr: String, val iconEmoji: String, val badgeColorHex: String) {
    REPAIR_ELECTRONICS("إصلاح وتفكيك أجهزة", "🔧", "#F59E0B"),
    MEDICINE_ANATOMY("طب وتشريح جسم الإنسان", "🫀", "#F43F5E"),
    MECHANICS_ENGINES("ميكانيكا ومحركات", "⚙️", "#38BDF8"),
    PHYSICS_SPACE("فيزياء وفضاء", "🌌", "#A855F7"),
    CHEMISTRY_ATOMS("كيمياء وجزيئات", "🧪", "#10B981"),
    TECH_TELECOM("تكنولوجيا واتصالات", "📡", "#06B6D4")
}

enum class ViewMode(val labelAr: String, val iconEmoji: String) {
    REAL_MEDIA("📷 صور وفيديو حقيقي", "📷"),
    SIMULATOR_3D("🧊 محاكي 3D", "🧊"),
    SPLIT_PIP("⚡ عرض مدمج", "⚡")
}

enum class ApiKeyStatus(val labelAr: String, val isHealthy: Boolean) {
    ACTIVE("نشط ✓", true),
    TESTING("قيد التجربة...", true),
    READY("جاهز للسباق", true),
    INVALID("غير صالح / انتهت الحصة", false)
}

data class ApiKeyItem(
    val key: String,
    val isBuiltIn: Boolean = false,
    val status: ApiKeyStatus = ApiKeyStatus.READY,
    val latencyMs: Long? = null
) {
    val maskedKey: String
        get() = if (isBuiltIn) {
            "Firebase AI / Default Key (مدمج)"
        } else if (key.length > 12) {
            "${key.take(7)}••••••••${key.takeLast(4)}"
        } else {
            "${key.take(4)}••••"
        }
}

enum class Shape3DType {
    SPHERE,
    CYLINDER,
    TORUS,
    BOX,
    CONE,
    HELIX_RING,
    CAPSULE,
    GEAR,
    CHAMBER,
    PISTON,
    DOME,
    TUBE_ARC;

    companion object {
        fun fromString(raw: String): Shape3DType {
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: CYLINDER
        }
    }
}

enum class Animation3DType {
    PULSE,
    ROTATE_X,
    ROTATE_Y,
    ROTATE_Z,
    OSCILLATE_Y,
    FLOW_ORBIT,
    NONE;

    companion object {
        fun fromString(raw: String): Animation3DType {
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: PULSE
        }
    }
}

data class InteractiveHotspot(
    val id: String,
    val xPercent: Float,
    val yPercent: Float,
    val labelAr: String,
    val functionAr: String,
    val zoomScale: Float = 2.4f
)

data class Part3DSpec(
    val id: String,
    val nameAr: String,
    val roleAr: String,
    val shapeType: Shape3DType,
    val isShell: Boolean,
    val isInternal: Boolean,
    val colorHex: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val scaleX: Float,
    val scaleY: Float,
    val scaleZ: Float,
    val explodeX: Float,
    val explodeY: Float,
    val explodeZ: Float,
    val animationType: Animation3DType,
    val activeInStages: List<Int>
)

data class ExplainStage(
    val stageNumber: Int,
    val totalStages: Int,
    val titleAr: String,
    val explanationAr: String,
    val toolsOrFactsTitleAr: String,
    val toolsOrFacts: List<String>,
    val mediaUrl: String,
    val isVideoOrGif: Boolean = false,
    val mediaCaptionAr: String,
    val hotspots: List<InteractiveHotspot>,
    val activePartIds: List<String>
)

data class VerifiedSource(
    val sourceBadgeTextAr: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val isIfixit: Boolean,
    val fetchedMediaCount: Int,
    val fetchDurationMs: Long
)

data class OmniTopicExplanation(
    val originalQuery: String,
    val arabicTitle: String,
    val englishTitle: String,
    val domain: TopicDomain,
    val summaryAr: String,
    val verifiedSource: VerifiedSource,
    val stages: List<ExplainStage>,
    val parts3D: List<Part3DSpec>,
    val threeJsCodeSnippet: String,
    val isAiEnriched: Boolean = false
)

data class WikiArticleExtract(
    val title: String,
    val extract: String,
    val imageUrl: String?,
    val pageUrl: String
)

data class CommonsMediaItem(
    val title: String,
    val description: String,
    val mediaUrl: String,
    val mimeType: String,
    val isVideoOrGif: Boolean
)

data class RawFetchedStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val imageUrls: List<String>
)

data class RawLiveSourceResult(
    val isIfixit: Boolean,
    val domain: TopicDomain,
    val queryAr: String,
    val englishQuery: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val summaryText: String,
    val arExtracts: List<WikiArticleExtract>,
    val enExtracts: List<WikiArticleExtract>,
    val toolsList: List<String>,
    val steps: List<RawFetchedStep>,
    val mediaGallery: List<Pair<String, String>>, // Pair<ImageUrl, Caption>
    val fetchDurationMs: Long
)

data class QuickCategorySuggestion(
    val id: String,
    val titleAr: String,
    val domain: TopicDomain,
    val suggestedQueries: List<String>
)

val QUICK_CATEGORIES = listOf(
    QuickCategorySuggestion(
        id = "repair",
        titleAr = "🔧 إصلاح وتفكيك أجهزة",
        domain = TopicDomain.REPAIR_ELECTRONICS,
        suggestedQueries = listOf(
            "استبدال بطارية iPhone 15 Pro",
            "تفكيك وإصلاح ذراع تحكم PlayStation 5 DualSense",
            "استبدال شاشة Samsung Galaxy S23 Ultra",
            "تنظيف مروحة وتفكيك MacBook Pro",
            "إصلاح عصا التحكم في Nintendo Switch Joy-Con"
        )
    ),
    QuickCategorySuggestion(
        id = "medicine",
        titleAr = "🫀 طب وجسم الإنسان",
        domain = TopicDomain.MEDICINE_ANATOMY,
        suggestedQueries = listOf(
            "كيف يعمل القلب البشري والدورة الدموية",
            "كيف تعمل العين البشرية والشبكية في الرؤية",
            "كيف يعمل الدماغ البشري والسيالات العصبية",
            "كيف تعمل الكلية البشرية في تنقية الدم",
            "تشريح الأذن الداخلية وكيف نسمع الأصوات"
        )
    ),
    QuickCategorySuggestion(
        id = "mechanics",
        titleAr = "⚙️ ميكانيكا ومحركات",
        domain = TopicDomain.MECHANICS_ENGINES,
        suggestedQueries = listOf(
            "كيف يعمل المحرك النفاث التوربيني Turbofan",
            "كيف يعمل محرك الاحتراق الداخلي رباعي الأشواط",
            "كيف يعمل المفاعل النووي لتوليد الطاقة",
            "كيف يعمل التوربين البخاري ومولد الكهرباء",
            "كيف تعمل فرامل السيارات الهيدروليكية ABS"
        )
    ),
    QuickCategorySuggestion(
        id = "tech",
        titleAr = "📡 تكنولوجيا واتصالات",
        domain = TopicDomain.TECH_TELECOM,
        suggestedQueries = listOf(
            "كيف تعمل الألياف البصرية في نقل الإنترنت",
            "كيف يعمل المعالج الدقيق CPU والترانزستورات",
            "كيف تعمل الأقمار الصناعية ونظام تحديد المواقع GPS",
            "كيف يعمل الثقب الأسود وأفق الحدث في الفضاء",
            "كيف تعمل الخلايا الشمسية الكهروضوئية"
        )
    )
)
