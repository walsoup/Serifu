package app.serifu.core.translate

import app.serifu.core.Tuning
import app.serifu.core.model.ModelVariant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parser for Mozilla Remote Settings `translations-models-v2`.
 * Lite = `tiny`, Full prefers `base-memory` then `base`.
 */
object BergamotCatalog {
    const val RECORDS_URL =
        "https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/translations-models-v2/records"
    const val CDN_ROOT = "https://firefox-settings-attachments.cdn.mozilla.net"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val litePreference = listOf("tiny", "base", "base-memory")
    private val fullPreference = listOf("base-memory", "base", "tiny")

    fun parse(recordsJson: String): Catalog {
        val envelope = json.decodeFromString(RecordsEnvelope.serializer(), recordsJson)
        return fromRecords(envelope.data)
    }

    fun fromRecords(records: List<CatalogRecord>): Catalog {
        val grouped = LinkedHashMap<PairKey, MutableMap<String, MutableMap<String, MutableList<CatalogRecord>>>>()
        for (record in records) {
            val src = record.sourceLanguage.trim()
            val trg = record.targetLanguage.trim()
            val arch = record.architecture.trim()
            val version = record.version.trim()
            if (src.isEmpty() || trg.isEmpty() || arch.isEmpty() || version.isEmpty()) continue
            if (record.fileType !in REQUIRED_OR_OPTIONAL) continue
            val attachment = record.attachment ?: continue
            if (attachment.location.isBlank()) continue
            val pair = PairKey(src, trg)
            val byArch = grouped.getOrPut(pair) { LinkedHashMap() }
            val byVersion = byArch.getOrPut(arch) { LinkedHashMap() }
            byVersion.getOrPut(version) { ArrayList() }.add(record)
        }

        val pairs = LinkedHashMap<PairKey, LanguagePairModels>()
        for ((pair, byArch) in grouped) {
            val variants = LinkedHashMap<String, ModelFiles>()
            for ((arch, byVersion) in byArch) {
                val latest = byVersion.keys.maxWithOrNull(VERSION_ORDER) ?: continue
                val files = byVersion[latest] ?: continue
                val model = toModelFiles(pair, arch, latest, files) ?: continue
                variants[arch] = model
            }
            if (variants.isNotEmpty()) {
                pairs[pair] = LanguagePairModels(pair.from, pair.to, variants)
            }
        }
        return Catalog(pairs)
    }

    fun resolveArchitecture(variant: ModelVariant, available: Set<String>, allowFallback: Boolean = true): String? {
        val order = when (variant) {
            ModelVariant.LITE -> if (allowFallback) litePreference else listOf("tiny")
            ModelVariant.FULL -> if (allowFallback) fullPreference else listOf("base-memory", "base")
        }
        return order.firstOrNull { it in available }
    }

    fun preferredArchitecture(variant: ModelVariant): String = when (variant) {
        ModelVariant.LITE -> "tiny"
        ModelVariant.FULL -> "base-memory"
    }

    fun hasPreferredArchitecture(variant: ModelVariant, available: Set<String>): Boolean {
        return when (variant) {
            ModelVariant.LITE -> "tiny" in available
            ModelVariant.FULL -> "base" in available || "base-memory" in available
        }
    }

    /**
     * Maps Serifu language tags onto Mozilla's catalog tags.
     * Chinese defaults to Simplified (`zh-Hans`).
     */
    fun catalogLanguage(tag: String): String = when (tag.lowercase()) {
        "zh" -> "zh-Hans"
        else -> tag.lowercase()
    }

    fun pairKey(from: String, to: String): PairKey =
        PairKey(catalogLanguage(from), catalogLanguage(to))

    /**
     * Direct pair, or English pivot when neither side is English and both
     * halves exist for the requested variant.
     */
    fun resolveRoute(
        catalog: Catalog,
        fromTag: String,
        toTag: String,
        variant: ModelVariant,
    ): ModelRoute? {
        val from = catalogLanguage(fromTag)
        val to = catalogLanguage(toTag)
        if (from == to) return null

        catalog.pair(from, to)?.let { pair ->
            val arch = resolveArchitecture(variant, pair.architectures) ?: return@let
            val files = pair.variants[arch] ?: return@let
            return ModelRoute.Direct(files)
        }

        val pivot = Tuning.BERGAMOT_PIVOT_LANGUAGE
        if (from == pivot || to == pivot) return null
        val first = catalog.pair(from, pivot) ?: return null
        val second = catalog.pair(pivot, to) ?: return null
        val firstArch = resolveArchitecture(variant, first.architectures) ?: return null
        val secondArch = resolveArchitecture(variant, second.architectures) ?: return null
        val firstFiles = first.variants[firstArch] ?: return null
        val secondFiles = second.variants[secondArch] ?: return null
        return ModelRoute.Pivot(firstFiles, secondFiles)
    }

    private fun toModelFiles(
        pair: PairKey,
        architecture: String,
        version: String,
        records: List<CatalogRecord>,
    ): ModelFiles? {
        val byType = records.associateBy { it.fileType }
        val model = byType["model"] ?: return null
        val lex = byType["lex"] ?: return null
        val sharedVocab = byType["vocab"]
        val srcVocab = byType["srcvocab"]
        val trgVocab = byType["trgvocab"]
        val vocabFiles = when {
            sharedVocab != null -> listOf(toFile(sharedVocab))
            srcVocab != null && trgVocab != null -> listOf(toFile(srcVocab), toFile(trgVocab))
            else -> return null
        }
        val files = buildList {
            add(toFile(model))
            add(toFile(lex))
            addAll(vocabFiles)
        }
        val bytes = files.sumOf { it.transferBytes }
        return ModelFiles(
            from = pair.from,
            to = pair.to,
            architecture = architecture,
            version = version,
            files = files,
            totalBytes = bytes,
        )
    }

    private fun toFile(record: CatalogRecord): ModelFile {
        val attachment = record.attachment!!
        return ModelFile(
            name = record.name,
            fileType = record.fileType,
            location = attachment.location,
            compressedSize = attachment.size,
            compressedHash = attachment.hash,
            decompressedSize = record.decompressedSize,
            decompressedHash = record.decompressedHash,
        )
    }

    private val REQUIRED_OR_OPTIONAL = setOf("model", "lex", "vocab", "srcvocab", "trgvocab")

    private val VERSION_ORDER = Comparator<String> { a, b ->
        compareVersion(a, b)
    }

    internal fun compareVersion(a: String, b: String): Int {
        val left = versionParts(a)
        val right = versionParts(b)
        val count = maxOf(left.numbers.size, right.numbers.size)
        for (index in 0 until count) {
            val l = left.numbers.getOrElse(index) { 0 }
            val r = right.numbers.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        // 1.0.0 is newer than 1.0.0-beta1. Numeric dots are compared above;
        // a non-numeric suffix sorts before the release with the same numbers.
        if (left.preRelease.isEmpty() != right.preRelease.isEmpty()) {
            return if (left.preRelease.isEmpty()) 1 else -1
        }
        val pre = left.preRelease.compareTo(right.preRelease)
        if (pre != 0) return pre
        return a.compareTo(b)
    }

    private data class VersionParts(val numbers: List<Int>, val preRelease: String)

    private fun versionParts(value: String): VersionParts {
        val trimmed = value.trim()
        val dash = trimmed.indexOf('-')
        val numeric = if (dash >= 0) trimmed.substring(0, dash) else trimmed
        val preRelease = if (dash >= 0) trimmed.substring(dash + 1) else ""
        val numbers = numeric.split('.').map { token ->
            token.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return VersionParts(numbers, preRelease)
    }
}

@Serializable
data class RecordsEnvelope(
    val data: List<CatalogRecord> = emptyList(),
)

@Serializable
data class CatalogRecord(
    val name: String = "",
    val version: String = "",
    val fileType: String = "",
    val architecture: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val decompressedHash: String = "",
    val decompressedSize: Long = 0L,
    val attachment: CatalogAttachment? = null,
)

@Serializable
data class CatalogAttachment(
    val hash: String = "",
    val size: Long = 0L,
    val filename: String = "",
    val location: String = "",
    val mimetype: String = "",
)

data class PairKey(val from: String, val to: String) {
    val code: String get() = "$from-$to"
    override fun toString(): String = code
}

data class Catalog(
    val pairs: Map<PairKey, LanguagePairModels>,
) {
    fun pair(from: String, to: String): LanguagePairModels? =
        pairs[PairKey(from, to)]
}

data class LanguagePairModels(
    val from: String,
    val to: String,
    val variants: Map<String, ModelFiles>,
) {
    val architectures: Set<String> get() = variants.keys
}

data class ModelFiles(
    val from: String,
    val to: String,
    val architecture: String,
    val version: String,
    val files: List<ModelFile>,
    val totalBytes: Long,
) {
    val pairCode: String get() = "$from-$to"
    val sizeMb: Int get() = ((totalBytes + 512L * 1024L) / (1024L * 1024L)).toInt().coerceAtLeast(1)
    val transferBytes: Long
        get() = files.sumOf { it.transferBytes }
}

data class ModelFile(
    val name: String,
    val fileType: String,
    val location: String,
    val compressedSize: Long,
    val compressedHash: String,
    val decompressedSize: Long,
    val decompressedHash: String,
) {
    val url: String get() = "${BergamotCatalog.CDN_ROOT}/$location"

    /** Bytes that actually cross the network. Installed files fall back to their size on disk. */
    val transferBytes: Long
        get() = compressedSize.takeIf { it > 0L } ?: decompressedSize
}

sealed interface ModelRoute {
    val totalBytes: Long
    val sizeMb: Int get() = ((totalBytes + 512L * 1024L) / (1024L * 1024L)).toInt().coerceAtLeast(1)

    data class Direct(val model: ModelFiles) : ModelRoute {
        override val totalBytes: Long get() = model.totalBytes
    }

    data class Pivot(val first: ModelFiles, val second: ModelFiles) : ModelRoute {
        override val totalBytes: Long get() = first.totalBytes + second.totalBytes
    }
}
