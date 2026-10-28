package app.serifu.ui.design

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
)

val LightPalette = Palette(
    bg = Color(0xFFF3EEE3),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFDF7F0),
    text = Color(0xFF16140F),
    textSecondary = Color(0xFF8A8478),
    muted = Color(0xFFB0A694),
    hairline = Color(0xFFDDD5C5),
    primary = Color(0xFFC8322B),
    onPrimary = Color(0xFFF3EEE3),
    inactive = Color(0xFFE4DDD0),
)

val DarkPalette = Palette(
    bg = Color(0xFF16140F),
    surface = Color(0xFF1E1B15),
    surfaceAlt = Color(0xFF221A17),
    text = Color(0xFFF3EEE3),
    textSecondary = Color(0xFF998F80),
    muted = Color(0xFF6B6458),
    hairline = Color(0xFF2A261E),
    primary = Color(0xFFC8322B),
    onPrimary = Color(0xFFF3EEE3),
    inactive = Color(0xFF2E2A22),
)

/** Types: DM-Sans-equivalent system sans for UI, monospace for technical values. */
data class Type(
    val body: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.SansSerif),
    val bodySecondary: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.SansSerif),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.SansSerif),
    val title: TextStyle = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    val headline: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    val overline: TextStyle = TextStyle(fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    val monoSmall: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
    val monoTiny: TextStyle = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
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
}

/** Brand wordmark in brand type with the ink color. */
@Composable
fun Wordmark(text: String) {
    Text(text, style = Serifu.type.brand, color = Serifu.colors.text)
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
        Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back button: chevron + touch area
        Text(
            text = "←",
            style = Serifu.type.body.copy(fontSize = 20.sp),
            color = c.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = Serifu.type.headline, color = c.text)
    }
}

/** Page header: overline, big title, optional lede. */
@Composable
fun PageHeader(overline: String, title: String, lede: String? = null) {
    val c = Serifu.colors
    Spacer(Modifier.height(16.dp))
    Text(overline, style = Serifu.type.overline, color = c.muted)
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
    Spacer(Modifier.height(26.dp))
    Text(text, style = Serifu.type.overline, color = c.muted)
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

/** Filled vermilion pill (selected) or outlined ink pill. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Serifu.colors
    Box(
        modifier
            .clip(RoundedCornerShape(100))
            .background(if (selected) c.text else Color.Transparent)
            .border(1.5.dp, if (selected) c.text else c.inactive, RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = if (selected) c.bg else c.textSecondary,
        )
    }
}

/** A setting row inside a card surface. */
@Composable
fun SettingLine(
    label: String,
    value: String? = null,
    subtitle: String? = null,
    emphasis: Boolean = false,
    warning: Boolean = false,
    showChevron: Boolean = true,
    showHairline: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Serifu.colors
    val content: @Composable RowScope.() -> Unit = {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = Serifu.type.body,
                color = when {
                    warning -> c.primary
                    emphasis -> c.primary
                    else -> c.text
                },
            )
            if (subtitle != null) {
                Text(subtitle, style = Serifu.type.caption, color = c.textSecondary)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(value, style = Serifu.type.monoSmall, color = c.textSecondary)
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Text("›", style = Serifu.type.body.copy(fontSize = 18.sp), color = c.muted)
        }
    }

    val row = Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )

    Column(modifier.fillMaxWidth()) {
        row
        if (showHairline) Hairline()
    }
}

/** Card wrapper for a list of rows. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Serifu.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(16.dp)),
        content = content,
    )
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
    val c = Serifu.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = Serifu.type.body, color = c.text)
                if (subtitle != null) {
                    Text(subtitle, style = Serifu.type.caption, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(12.dp))
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
        if (showHairline) Hairline()
    }
}

/** Bare switch styled to the palette (no row chrome). */
@Composable
fun SerifuSwitch(checked: Boolean, enabled: Boolean = true) {
    val c = Serifu.colors
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            uncheckedThumbColor = c.onPrimary,
            uncheckedTrackColor = c.inactive,
            uncheckedBorderColor = c.inactive,
        ),
    )
}

/** Primary text field with a soft card border. */
@Composable
fun SerifuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = Serifu.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Serifu.type.body.copy(color = c.text),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.5.dp, c.hairline, RoundedCornerShape(12.dp)),
        cursorBrush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.primary, c.primary)),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                if (value.isEmpty()) {
                    Text(placeholder, style = Serifu.type.body, color = c.muted)
                }
                inner()
            }
        },
    )
}

/** Big vermilion action button, or tonal variant. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ActionStyle = ActionStyle.Primary,
) {
    val c = Serifu.colors
    val interact = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.4f
    val bg = when (style) {
        ActionStyle.Primary -> c.primary
        ActionStyle.Tonal -> c.text
        ActionStyle.Ghost -> Color.Transparent
    }
    val fg = when (style) {
        ActionStyle.Primary -> c.onPrimary
        ActionStyle.Tonal -> c.bg
        ActionStyle.Ghost -> c.textSecondary
    }
    Box(
        modifier
            .alpha(alpha)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(if (style == ActionStyle.Ghost) Modifier.border(1.5.dp, c.hairline, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(enabled = enabled, interactionSource = interact, indication = null, onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = fg)
    }
}

enum class ActionStyle { Primary, Tonal, Ghost }

/** Labelled slider with mono value readout. */
@Composable
fun LinenSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    subtitle: String? = null,
) {
    val c = Serifu.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Serifu.type.body, color = c.text)
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
            ),
        )
        Hairline()
    }
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
    val c = Serifu.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.surfaceAlt else c.surface)
            .border(1.5.dp, if (selected) c.primary else c.inactive, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(top = 16.dp, bottom = 13.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, style = TextStyle(fontSize = 26.sp), color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(name, style = Serifu.type.overline.copy(letterSpacing = 1.sp, fontSize = 10.sp), color = if (selected) c.primary else c.textSecondary)
    }
}
