package app.serifu.core.model

enum class SourceLanguage(
    val tag: String,
    val seal: String,
    val nativeName: String,
    val englishName: String,
) {
    JA("ja", "日", "日本語", "Japanese"),
    ZH("zh", "中", "中文", "Chinese"),
    KO("ko", "한", "한국어", "Korean"),
}

enum class TriggerMode {
    AUTO,
    MANUAL,
    OFF,
}

enum class QualityMode {
    FAST,
    BEST_PROGRESSIVE,
    BEST_ONLY,
}

enum class BubbleKind {
    DIALOGUE,
    CAPTION,
    SFX,
    FREE_TEXT,
}

enum class SfxMode {
    COMPACT,
    COVER,
    OFF,
}

enum class LetteringFont {
    COMIC_NEUE,
    ATKINSON,
    PATRICK_HAND,
}

enum class PillSide {
    START,
    END,
}

enum class ModelDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

data class TargetLanguage(
    val tag: String,
    val englishName: String,
) {
    companion object {
        val English = TargetLanguage("en", "English")

        val all: List<TargetLanguage> = listOf(
            TargetLanguage("af", "Afrikaans"),
            TargetLanguage("sq", "Albanian"),
            TargetLanguage("ar", "Arabic"),
            TargetLanguage("be", "Belarusian"),
            TargetLanguage("bn", "Bengali"),
            TargetLanguage("bg", "Bulgarian"),
            TargetLanguage("ca", "Catalan"),
            TargetLanguage("zh", "Chinese"),
            TargetLanguage("hr", "Croatian"),
            TargetLanguage("cs", "Czech"),
            TargetLanguage("da", "Danish"),
            TargetLanguage("nl", "Dutch"),
            English,
            TargetLanguage("eo", "Esperanto"),
            TargetLanguage("et", "Estonian"),
            TargetLanguage("fi", "Finnish"),
            TargetLanguage("fr", "French"),
            TargetLanguage("gl", "Galician"),
            TargetLanguage("ka", "Georgian"),
            TargetLanguage("de", "German"),
            TargetLanguage("el", "Greek"),
            TargetLanguage("gu", "Gujarati"),
            TargetLanguage("ht", "Haitian Creole"),
            TargetLanguage("he", "Hebrew"),
            TargetLanguage("hi", "Hindi"),
            TargetLanguage("hu", "Hungarian"),
            TargetLanguage("is", "Icelandic"),
            TargetLanguage("id", "Indonesian"),
            TargetLanguage("ga", "Irish"),
            TargetLanguage("it", "Italian"),
            TargetLanguage("ja", "Japanese"),
            TargetLanguage("kn", "Kannada"),
            TargetLanguage("ko", "Korean"),
            TargetLanguage("lv", "Latvian"),
            TargetLanguage("lt", "Lithuanian"),
            TargetLanguage("mk", "Macedonian"),
            TargetLanguage("ms", "Malay"),
            TargetLanguage("mt", "Maltese"),
            TargetLanguage("mr", "Marathi"),
            TargetLanguage("no", "Norwegian"),
            TargetLanguage("fa", "Persian"),
            TargetLanguage("pl", "Polish"),
            TargetLanguage("pt", "Portuguese"),
            TargetLanguage("ro", "Romanian"),
            TargetLanguage("ru", "Russian"),
            TargetLanguage("sk", "Slovak"),
            TargetLanguage("sl", "Slovenian"),
            TargetLanguage("es", "Spanish"),
            TargetLanguage("sw", "Swahili"),
            TargetLanguage("sv", "Swedish"),
            TargetLanguage("tl", "Tagalog"),
            TargetLanguage("ta", "Tamil"),
            TargetLanguage("te", "Telugu"),
            TargetLanguage("th", "Thai"),
            TargetLanguage("tr", "Turkish"),
            TargetLanguage("uk", "Ukrainian"),
            TargetLanguage("ur", "Urdu"),
            TargetLanguage("vi", "Vietnamese"),
            TargetLanguage("cy", "Welsh"),
        ).sortedBy { it.englishName }

        fun fromTag(tag: String): TargetLanguage =
            all.firstOrNull { it.tag == tag } ?: TargetLanguage(tag, tag)
    }
}
