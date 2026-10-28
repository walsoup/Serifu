package app.serifu.ml

import android.content.Context
import java.io.File

/**
 * Pinned Gemma 4 E2B model artifact (plan.md 11.4).
 *
 * Source: litert-community/gemma-4-E2B-it-litert-lm on Hugging Face
 * (Google AI Edge community upload of google/gemma-4-E2B-it).
 * GPU variant selected because the runtime uses Backend.GPU() first.
 *
 * Observed 2026-10-28 via the HF resolve CDN (us.aws.cdn.hf.co):
 * - x-linked-size: 2008432640 bytes (~1.9 GiB; the plan's 2.58 GB figure
 *   matches the base .litertlm / S26-Ultra benchmark row, not this file)
 * - x-linked-etag: "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5"
 * - x-xet-hash: 10e0c748186ee08a9bdde2a6ff615f00b021969b1737ceef164e6bf2be5ec108
 *
 * Hugging Face does not publish a SHA-256 for this blob, so the download
 * is trust-on-first-use over HTTPS, verified against size + ETag, and the
 * SHA-256 is recorded to a sidecar on completion. Every later load
 * re-verifies size and the recorded hash and fails closed on mismatch.
 */
object ModelCatalog {
    const val REPO = "litert-community/gemma-4-E2B-it-litert-lm"
    const val FILE = "gemma-4-E2B-it-gpu.litertlm"
    const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm"

    const val EXPECTED_BYTES = 2_008_432_640L
    const val EXPECTED_ETAG = "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5"
    const val EXPECTED_XET_HASH = "10e0c748186ee08a9bdde2a6ff615f00b021969b1737ceef164e6bf2be5ec108"

    const val MODEL_NAME = "gemma4-e2b.litertlm"
    const val PART_SUFFIX = ".part"
    const val SHA_SUFFIX = ".sha256"

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(context: Context): File = File(modelsDir(context), MODEL_NAME)

    fun partFile(context: Context): File = File(modelsDir(context), MODEL_NAME + PART_SUFFIX)

    fun shaFile(context: Context): File = File(modelsDir(context), MODEL_NAME + SHA_SUFFIX)

    /** True only when a complete, size-verified model is on disk. */
    fun isComplete(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() == EXPECTED_BYTES && !partFile(context).exists()
    }
}
