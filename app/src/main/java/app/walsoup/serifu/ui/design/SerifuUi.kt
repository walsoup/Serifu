package app.walsoup.serifu.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.walsoup.serifu.R
import app.walsoup.serifu.ui.theme.DelaGothicOne
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.MutableInteractionSource

/**
 * Serifu design system — the Hanko palette.
 * Paper #F3EEE3, Ink #16140F, Hanko vermilion #C8322B, and their dark inversions.
 */
data class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val textSecondary: Color,
    val muted: Color,
    val hairline: Color,
    val primary: Color,
    val onPrimary: Color,
    val inactive: Color,
    /** Soft filled-block colour used in illustrations and unfilled seal badges. */
    val container: Color = surfaceAlt,
    /** Vermilion tuned for small text on `bg`; `primary` stays the fill colour. */
    val primaryText: Color = primary,
)

val LightPalette = Palette(
    bg = Color(0xFFF3EEE3),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFDF7F0),
    text = Color(0xFF16140F),
    textSecondary = Color(0xFF6B6558),
    muted = Color(0xFF8A8171),
    hairline = Color(0xFFDDD5C5),
    primary = Color(0xFFC8322B),
    onPrimary = Color(0xFFF3EEE3),
    inactive = Color(0xFFE4DDD0),
    primaryText = Color(0xFFC8322B),
)

val DarkPalette = Palette(
    bg = Color(0xFF16140F),
    surface = Color(0xFF1E1B15),
    surfaceAlt = Color(0xFF221A17),
    text = Color(0xFFF3EEE3),
    textSecondary = Color(0xFF998F80),
    muted = Color(0xFF888070),
    hairline = Color(0xFF2A261E),
    primary = Color(0xFFC8322B),
    onPrimary = Color(0xFFF3EEE3),
    inactive = Color(0xFF2E2A22),
    primaryText = Color(0xFFE0564C),
)

/** Types: DM-Sans-equivalent system sans for UI, monospace for technical values. */
data class Type(
    val body: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.SansSerif),
    val bodySecondary: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.SansSerif),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.SansSerif),
    val title: TextStyle = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    val headline: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    val overline: TextStyle = TextStyle(fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    val monoSmall: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
    val monoTiny: TextStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
    val brand: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, fontFamily = FontFamily.SansSerif),
    val hero: TextStyle = TextStyle(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif),
)

object SerifuTypes { val type = Type() }

val LocalSerifuPalette = staticCompositionLocalOf { LightPalette }

/** Design-system facade: `val c = Serifu.colors`. */
object Serifu {
    val colors: Palette @Composable get() = LocalSerifuPalette.current
    val type: Type = SerifuTypes.type
    val margin: Dp = 24.dp

    /** Corner radii: one per kind of surface. */
    val radiusControl: Dp = 14.dp
    val radiusSurface: Dp = 16.dp
}

/** Horizontal inset applied to rows; 0 on flat screens, set by containers that pad their rows. */
val LocalRowInset = staticCompositionLocalOf { 0.dp }

/** Soft tick used by every tappable control. */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK,
            )
        }
    }
}

/** Brand wordmark in brand type with the ink color. */
@Composable
fun Wordmark(text: String) {
    Text(text, style = Serifu.type.brand, color = Serifu.colors.text)
}

/** The name in Dela Gothic One, beside the launcher mark. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier) {
    val c = Serifu.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Serifu",
            fontFamily = DelaGothicOne,
            fontSize = 28.sp,
            color = c.text,
        )
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 10.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
fun SerifuScreen(
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Serifu.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Serifu.margin),
        content = content,
    )
}

/** Back header with title. */
@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    val c = Serifu.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.back),
                tint = c.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = Serifu.type.headline, color = c.text)
    }
}

/** Page header: overline, big title, optional lede. */
@Composable
fun PageHeader(overline: String, title: String, lede: String? = null) {
    val c = Serifu.colors
    Spacer(Modifier.height(16.dp))
    Text(overline.uppercase(), style = Serifu.type.overline, color = c.textSecondary)
    Spacer(Modifier.height(4.dp))
    Text(title, style = Serifu.type.brand, color = c.text)
    if (lede != null) {
        Spacer(Modifier.height(8.dp))
        Text(lede, style = Serifu.type.body, color = c.textSecondary)
    }
    Spacer(Modifier.height(20.dp))
    Hairline()
}

@Composable
fun SectionTitle(text: String) {
    val c = Serifu.colors
    Spacer(Modifier.height(28.dp))
    Text(text.uppercase(), style = Serifu.type.overline, color = c.textSecondary)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun GroupLabel(text: String) {
    val c = Serifu.colors
    Text(
        text,
        style = Serifu.type.caption,
        color = c.textSecondary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, padding: Dp = 0.dp) {
    val c = Serifu.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = padding)
            .height(1.dp)
            .background(c.hairline),
    )
}

/** A hanko-shaped vermilion seal mark with the セ glyph (the Serifu status mark). */
@Composable
fun SealMark(modifier: Modifier = Modifier, size: Dp = 32.dp, glyph: String = "セ", filled: Boolean = true) {
    val c = Serifu.colors
    Box(
        modifier
            .size(size)
            .rotate(if (filled) 0f else -3f)
            .clip(RoundedCornerShape(5.dp))
            .then(
                if (filled) {
                    Modifier.background(c.primary)
                } else {
                    Modifier.border(2.dp, c.primary, RoundedCornerShape(5.dp))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(fontSize = (size.value * 0.6f).sp, fontWeight = FontWeight.ExtraBold),
            color = if (filled) c.onPrimary else c.primary,
        )
    }
}

/** Ink-filled pill when selected, outlined when not. A 44dp-high radio-style choice. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = rememberTick()
    val c = Serifu.colors
    val shape = RoundedCornerShape(100)

    val bgColor by animateColorAsState(if (selected) c.text else Color.Transparent, tween(180), label = "pill_bg")
    val borderColor by animateColorAsState(if (selected) c.text else c.inactive, tween(180), label = "pill_border")
    val textColor by animateColorAsState(if (selected) c.bg else c.textSecondary, tween(180), label = "pill_text")

    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.5.dp, borderColor, shape)
            .selectable(selected = selected, role = Role.RadioButton) {
                tick()
                onClick()
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A settings row. Rows are flat by default and align with the screen margin;
 * a container can indent them through [LocalRowInset].
 * Set [mono] for values that are numbers or identifiers (ms, sp, versions).
 */
@Composable
fun SettingLine(
    label: String,
    value: String? = null,
    subtitle: String? = null,
    emphasis: Boolean = false,
    warning: Boolean = false,
    showChevron: Boolean = true,
    showHairline: Boolean = true,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val tick = rememberTick()
    val c = Serifu.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button) {
                            tick()
                            onClick()
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = LocalRowInset.current, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = Serifu.type.body,
                    color = if (warning || emphasis) c.primaryText else c.text,
                )
                if (subtitle != null) {
                    Text(subtitle, style = Serifu.type.caption, color = c.textSecondary)
                }
            }
            if (value != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    value,
                    style = if (mono) Serifu.type.monoSmall else Serifu.type.caption.copy(fontSize = 13.sp),
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
            if (showChevron && onClick != null) {
                Spacer(Modifier.width(6.dp))
                Text("\u203A", style = Serifu.type.body.copy(fontSize = 18.sp), color = c.muted)
            }
        }
        if (showHairline) Hairline()
    }
}

/** A switch row. */
@Composable
fun SwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    showHairline: Boolean = true,
) {
    val tick = rememberTick()
    val c = Serifu.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = { next ->
                        tick()
                        onCheckedChange(next)
                    },
                )
                .padding(horizontal = LocalRowInset.current, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = Serifu.type.body, color = c.text)
                if (subtitle != null) {
                    Text(subtitle, style = Serifu.type.caption, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(12.dp))
            SerifuSwitch(checked)
        }
        if (showHairline) Hairline()
    }
}

/** Bare switch styled to the palette. Display only: the enclosing row owns the tap. */
@Composable
fun SerifuSwitch(checked: Boolean) {
    val c = Serifu.colors
    Switch(
        checked = checked,
        onCheckedChange = null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            uncheckedThumbColor = c.onPrimary,
            uncheckedTrackColor = c.inactive,
            uncheckedBorderColor = c.inactive,
        ),
    )
}

/** Primary text field built with Linen OutlinedTextField styling. */
@Composable
fun SerifuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val c = Serifu.colors
    val shape = RoundedCornerShape(12.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = if (label != null) { { Text(label, style = Serifu.type.caption) } } else null,
        placeholder = { Text(placeholder, style = Serifu.type.body, color = c.muted) },
        singleLine = singleLine,
        textStyle = Serifu.type.body.copy(color = c.text),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedBorderColor = c.primary,
            unfocusedBorderColor = c.hairline,
            focusedLabelColor = c.primary,
            unfocusedLabelColor = c.textSecondary,
            cursorColor = c.primary,
        ),
    )
}

/** Full-width action: Primary (vermilion), Tonal (ink) or Ghost (outlined). One line, always. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ActionStyle = ActionStyle.Primary,
) {
    val c = Serifu.colors
    val shape = RoundedCornerShape(Serifu.radiusControl)
    val bg = when (style) {
        ActionStyle.Primary -> c.primary
        ActionStyle.Tonal -> c.text
        ActionStyle.Ghost -> Color.Transparent
    }
    val fg = when (style) {
        ActionStyle.Primary -> c.onPrimary
        ActionStyle.Tonal -> c.bg
        ActionStyle.Ghost -> c.text
    }
    val view = LocalView.current
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bg)
            .then(if (style == ActionStyle.Ghost) Modifier.border(1.5.dp, c.hairline, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class ActionStyle { Primary, Tonal, Ghost }

/** Labelled slider with mono value readout. */
@Composable
fun SerifuSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    subtitle: String? = null,
) {
    val c = Serifu.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Serifu.type.body, color = c.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(valueText, style = Serifu.type.monoSmall, color = c.textSecondary)
        }
        if (subtitle != null) {
            Text(subtitle, style = Serifu.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.inactive,
                activeTickColor = c.onPrimary.copy(alpha = 0.7f),
                inactiveTickColor = c.muted,
            ),
        )
        Hairline()
    }
}

/** Confirm a destructive action; [text] says what will be lost. */
@Composable
fun SerifuConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Serifu.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Serifu.type.title, color = c.text) },
        text = { Text(text, style = Serifu.type.body, color = c.textSecondary) },
        confirmButton = {
            ActionButton(confirmLabel, onClick = onConfirm, style = ActionStyle.Primary, modifier = Modifier.widthIn(min = 120.dp).width(IntrinsicSize.Max))
        },
        dismissButton = {
            ActionButton(stringResource(R.string.cancel), onClick = onDismiss, style = ActionStyle.Ghost, modifier = Modifier.widthIn(min = 96.dp).width(IntrinsicSize.Max))
        },
        shape = RoundedCornerShape(Serifu.radiusSurface),
        containerColor = c.surface,
        tonalElevation = 0.dp,
    )
}

/** Segmented language selector cell used in home hero grid. */
@Composable
fun ScriptCell(
    glyph: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = rememberTick()
    val c = Serifu.colors
    val shape = RoundedCornerShape(Serifu.radiusControl)

    val borderColor by animateColorAsState(if (selected) c.primary else c.inactive, tween(180), label = "script_border")
    val bgColor by animateColorAsState(if (selected) c.surfaceAlt else c.surface, tween(180), label = "script_bg")
    val labelColor by animateColorAsState(if (selected) c.primaryText else c.textSecondary, tween(180), label = "script_label")

    Column(
        modifier
            .clip(shape)
            .background(bgColor)
            .border(1.5.dp, borderColor, shape)
            .selectable(selected = selected, role = Role.RadioButton) {
                tick()
                onClick()
            }
            .padding(top = 16.dp, bottom = 13.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, style = TextStyle(fontSize = 26.sp), color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(name, style = Serifu.type.overline.copy(letterSpacing = 1.sp), color = labelColor, maxLines = 1)
    }
}
