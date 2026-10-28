package app.serifu.core.vision

import app.serifu.core.model.SourceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizerTest {
    @Test
    fun nfkcWhitespaceEllipsisAndLongVowel() {
        assertEquals("A", TextNormalizer.normalize("\uFF21", SourceLanguage.JA))
        assertEquals("「", TextNormalizer.normalize("\uFE41", SourceLanguage.JA))
        assertEquals("おはよう", TextNormalizer.normalize("おは よう", SourceLanguage.JA))
        assertEquals("你好", TextNormalizer.normalize("你 好", SourceLanguage.ZH))
        assertEquals("안녕 하세요", TextNormalizer.normalize("안녕  하세요", SourceLanguage.KO))
        assertEquals("…", TextNormalizer.normalize("......", SourceLanguage.JA))
        assertEquals("…", TextNormalizer.normalize("・・・", SourceLanguage.JA))
        assertEquals("コーヒー", TextNormalizer.normalize("コーヒー", SourceLanguage.JA))
    }
}
