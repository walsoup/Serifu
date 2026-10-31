package app.serifu.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.serifu.R
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.SfxMode
import app.serifu.core.model.TargetLanguage
import app.serifu.core.model.TriggerMode

/** Full font name, for rows and previews. */
val LetteringFont.fullName: String
    get() = when (this) {
        LetteringFont.COMIC_NEUE -> "Comic Neue Bold"
        LetteringFont.ATKINSON -> "Atkinson Hyperlegible"
        LetteringFont.PATRICK_HAND -> "Patrick Hand"
    }

/** Short font name, for pills. */
val LetteringFont.shortName: String
    get() = when (this) {
        LetteringFont.COMIC_NEUE -> "Comic Neue"
        LetteringFont.ATKINSON -> "Atkinson"
        LetteringFont.PATRICK_HAND -> "Patrick Hand"
    }

val SfxMode.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun TriggerMode.label(): String = stringResource(
    when (this) {
        TriggerMode.AUTO -> R.string.trigger_auto
        TriggerMode.MANUAL -> R.string.trigger_manual
        TriggerMode.OFF -> R.string.trigger_off
    },
)

@Composable
fun TriggerMode.hint(): String = stringResource(
    when (this) {
        TriggerMode.AUTO -> R.string.trigger_auto_hint
        TriggerMode.MANUAL -> R.string.trigger_manual_hint
        TriggerMode.OFF -> R.string.trigger_off_hint
    },
)

fun targetName(tag: String): String = TargetLanguage.fromTag(tag).englishName
