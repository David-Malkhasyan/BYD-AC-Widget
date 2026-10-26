// One composable per piece of the bubble and strip.
@file:Suppress("TooManyFunctions")

package com.bydmate.app.acbubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R

// Colours come from the chosen AcTheme: every piece shares its background.

/** Base size of the oval bubble, before the user's size setting: the stock car button's. */
const val AC_BUBBLE_W_DP = 84
const val AC_BUBBLE_H_DP = 48
/** Width of the edge tab; its height is the strip's, so the two sit flush when it opens. */
const val AC_TAB_W_DP = 56
/** Corner radius of the edge tab's free side. */
private const val TAB_CORNER_DP = 18
private const val BUTTON_DP = 58
private const val ROW_GAP_DP = 6
private const val STRIP_PAD_V_DP = 6
/** Strip height: two rows of buttons plus padding. Shared with the controller to centre it on the bubble. */
const val AC_STRIP_HEIGHT_DP = 2 * BUTTON_DP + ROW_GAP_DP + 2 * STRIP_PAD_V_DP
/** The row of custom buttons under the two rows of controls. */
private const val PRESET_ROW_DP = 46
/**
 * Width of a control row: five buttons, a separator and a stepper. The custom buttons share it
 * evenly, so their row lines up with the two above whether there is one button or four.
 */
private const val CONTROL_ROW_W_DP = 476f

/** Strip height with or without the custom buttons' row; the tab and the grip match it. */
fun acStripHeightDp(presetRow: Boolean): Int = AC_STRIP_HEIGHT_DP + if (presetRow) PRESET_ROW_DP + ROW_GAP_DP else 0
/** Gap on each side of a button; twice this separates two glows. */
private const val BUTTON_SPACING_DP = 3
/** Strip open/close duration; the controller waits this long before shrinking the window. */
const val AC_STRIP_ANIM_MS = 220
private const val STRIP_START_SCALE = 0.85f
private const val BUBBLE_PRESSED_SCALE = 0.92f
private const val BUBBLE_PRESS_MS = 90
const val GRIP_W_DP = 22
private const val GRIP_DOTS = 3
/** Space between the bubble (or grip) and the strip. */
const val AC_STRIP_GAP_DP = 8

/** The always-visible oval: "A/C", plus the set temperature while the climate runs. */
@Composable
fun AcBubble(state: AcState, t: AcTheme, alpha: Float, scale: Float, pressed: Boolean) {
    val on = state.power == true
    val shape = RoundedCornerShape(percent = 50)
    val press by animateFloatAsState(if (pressed) BUBBLE_PRESSED_SCALE else 1f, tween(BUBBLE_PRESS_MS), label = "press")
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = press; scaleY = press }
            .size((AC_BUBBLE_W_DP * scale).dp, (AC_BUBBLE_H_DP * scale).dp)
            .background(t.glass(alpha), shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("A/C", color = t.fg, fontSize = (16 * scale).sp, fontWeight = FontWeight.Bold)
        if (on && state.tempC != null) {
            Spacer(Modifier.width((6 * scale).dp))
            Text(tempText(state.tempC, scale), color = t.fg)
        }
    }
}

/**
 * The edge tab: docked flush to a side of the screen (square on that side, rounded on the
 * other), showing the set temperature, the fan speed and where the air goes. Tapping it opens
 * the strip, which is exactly as tall, next to it.
 */
@Composable
@Suppress("LongParameterList") // one flat input per visual element
fun AcTab(state: AcState, t: AcTheme, alpha: Float, scale: Float, heightDp: Int, dockRight: Boolean, pressed: Boolean) {
    val r = (TAB_CORNER_DP * scale).dp
    val shape = if (dockRight) RoundedCornerShape(topStart = r, bottomStart = r) else RoundedCornerShape(topEnd = r, bottomEnd = r)
    val press by animateFloatAsState(if (pressed) BUBBLE_PRESSED_SCALE else 1f, tween(BUBBLE_PRESS_MS), label = "press")
    // Values stay readable while the climate is off, just quieter.
    val fg = if (state.power == true) t.fg else t.fg.copy(alpha = 0.7f)
    Column(
        modifier = Modifier
            // Press feedback shrinks toward the edge the tab hangs from, so it never lifts off it.
            .graphicsLayer {
                scaleX = press; scaleY = press
                transformOrigin = TransformOrigin(if (dockRight) 1f else 0f, 0.5f)
            }
            .size((AC_TAB_W_DP * scale).dp, (heightDp * scale).dp)
            .background(t.glass(alpha), shape)
            .padding(vertical = (6 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(tempText(state.tempC, scale, digits = 22), color = fg)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AcIcon(AcIconKind.FAN, fg, (22 * scale).dp)
            Spacer(Modifier.width((3 * scale).dp))
            Text(state.fanLevel?.toString() ?: "–", color = fg, fontSize = (20 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        AirflowIcon(AcCommands.directionsOf(state.windMode), on = fg, off = t.dim.copy(alpha = 0.5f), size = (36 * scale).dp)
    }
}

/** Drag handle that stands in for the bubble while the strip is always shown. */
@Composable
fun AcGrip(t: AcTheme, alpha: Float, scale: Float, heightDp: Int) {
    Column(
        modifier = Modifier
            .size((GRIP_W_DP * scale).dp, (heightDp * scale).dp)
            .background(t.glass(alpha), RoundedCornerShape((12 * scale).dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((4 * scale).dp, Alignment.CenterVertically),
    ) {
        repeat(GRIP_DOTS) {
            Box(Modifier.size((4 * scale).dp).background(t.dim, RoundedCornerShape(50)))
        }
    }
}

/**
 * The control strip that slides out of the bubble: two rows, so it stays compact enough for a
 * rotated screen. Modes, recirculation and the fan above; airflow, defrost and temperature below.
 * The user's custom buttons, when any are shown, get a third row of their own.
 */
@Composable
@Suppress("LongParameterList") // one flat input per visual element
fun AcStrip(
    state: AcState,
    t: AcTheme,
    alpha: Float,
    scale: Float,
    visible: Boolean,
    opensLeft: Boolean,
    presets: List<IndexedValue<AcPreset>>,
    onAction: (AcAction) -> Unit,
    onPreset: (Int) -> Unit,
) {
    // Opening and closing are drawn, not laid out: the window keeps its size during the
    // animation, so the overlay is never re-measured frame by frame.
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AC_STRIP_ANIM_MS, easing = FastOutSlowInEasing),
        label = "strip",
    )
    val slidePx = with(LocalDensity.current) { (24 * scale).dp.toPx() }
    val b = (BUTTON_DP * scale).dp
    val shape = RoundedCornerShape((22 * scale).dp)
    val directions = AcCommands.directionsOf(state.windMode)
    Column(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = progress
                val s = STRIP_START_SCALE + (1f - STRIP_START_SCALE) * progress
                scaleX = s
                scaleY = s
                translationX = (1f - progress) * if (opensLeft) slidePx else -slidePx
                transformOrigin = TransformOrigin(if (opensLeft) 1f else 0f, 0.5f)
            }
            .height((acStripHeightDp(presets.isNotEmpty()) * scale).dp)
            .background(t.glass(alpha), shape)
            .padding(horizontal = (8 * scale).dp, vertical = (STRIP_PAD_V_DP * scale).dp),
        verticalArrangement = Arrangement.spacedBy((ROW_GAP_DP * scale).dp),
    ) {
        StripRow(b) {
            IconButton(b, scale, t, state.power, stringResource(R.string.ac_bubble_power),
                onClick = { onAction(AcAction.TogglePower) }) { c, s ->
                AcIcon(AcIconKind.POWER, c, s)
            }
            TextButton(b, scale, t, state.compressor, "A/C") { onAction(AcAction.ToggleCompressor) }
            TextButton(b, scale, t, state.auto, "AUTO") { onAction(AcAction.ToggleAuto) }
            // The stock "ONLY" button: fan icon over the word, like the BYD climate screen.
            IconButton(b, scale, t, state.fanOnly, "ONLY", onClick = { onAction(AcAction.ToggleFanOnly) }) { c, s ->
                AcIcon(AcIconKind.FAN, c, s)
            }
            // One button, two faces: the icon shows where the air comes from now.
            val inner = state.recircInner == true
            IconButton(b, scale, t, inner, stringResource(R.string.ac_bubble_recirc),
                onClick = { onAction(AcAction.ToggleRecirc) }) { c, s ->
                AcIcon(if (inner) AcIconKind.RECIRC else AcIconKind.FRESH_AIR, c, s)
            }
            Separator(b, t)
            Stepper(b, scale, t, onMinus = { onAction(AcAction.FanStep(-1)) }, onPlus = { onAction(AcAction.FanStep(1)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AcIcon(AcIconKind.FAN, t.fg, (22 * scale).dp)
                    Spacer(Modifier.width((4 * scale).dp))
                    Text(state.fanLevel?.toString() ?: "–", color = t.fg, fontSize = (22 * scale).sp,
                        fontWeight = FontWeight.SemiBold)
                }
                FanBars(state.fanLevel, scale, t)
            }
        }
        StripRow(b) {
            IconButton(b, scale, t, AirDirection.WINDSHIELD in directions, stringResource(R.string.ac_bubble_wind_glass),
                onClick = { onAction(AcAction.ToggleDirection(AirDirection.WINDSHIELD)) }) { c, s ->
                AcIcon(AcIconKind.SEAT_WINDSHIELD, c, s)
            }
            IconButton(b, scale, t, AirDirection.FACE in directions, stringResource(R.string.ac_bubble_wind_face),
                onClick = { onAction(AcAction.ToggleDirection(AirDirection.FACE)) }) { c, s ->
                AcIcon(AcIconKind.SEAT_FACE, c, s)
            }
            IconButton(b, scale, t, AirDirection.FEET in directions, stringResource(R.string.ac_bubble_wind_feet),
                onClick = { onAction(AcAction.ToggleDirection(AirDirection.FEET)) }) { c, s ->
                AcIcon(AcIconKind.SEAT_FEET, c, s)
            }
            IconButton(b, scale, t, state.defrostFront, stringResource(R.string.ac_bubble_defrost_front),
                onClick = { onAction(AcAction.ToggleDefrostFront) }) { c, s ->
                AcIcon(AcIconKind.DEFROST_FRONT, c, s)
            }
            IconButton(b, scale, t, state.defrostRear, stringResource(R.string.ac_bubble_defrost_rear),
                onClick = { onAction(AcAction.ToggleDefrostRear) }) { c, s ->
                AcIcon(AcIconKind.DEFROST_REAR, c, s)
            }
            Separator(b, t)
            Stepper(b, scale, t, onMinus = { onAction(AcAction.TempStep(-1)) }, onPlus = { onAction(AcAction.TempStep(1)) }) {
                Text(tempText(state.tempC, scale, digits = 26), color = t.fg)
            }
        }
        if (presets.isNotEmpty()) {
            StripRow((PRESET_ROW_DP * scale).dp) {
                val w = (CONTROL_ROW_W_DP / presets.size - 2 * BUTTON_SPACING_DP) * scale
                presets.forEach { (slot, p) ->
                    PresetButton(w.dp, scale, t, p.name.ifBlank { "${slot + 1}" }, AcPresets.matches(p, state)) { onPreset(slot) }
                }
            }
        }
    }
}

/**
 * A custom button: its name on a faint pill, and the same LED and glow as the controls above
 * while the car shows every value it sets.
 */
@Composable
private fun PresetButton(width: Dp, scale: Float, t: AcTheme, label: String, on: Boolean, onClick: () -> Unit) {
    val fg by animateColorAsState(if (on) t.on else t.fg, tween(AC_STRIP_ANIM_MS), label = "fg")
    val glow by animateColorAsState(if (on) t.glow else t.track.copy(alpha = 0.35f), tween(AC_STRIP_ANIM_MS), label = "glow")
    Column(
        modifier = Modifier
            .padding(horizontal = (BUTTON_SPACING_DP * scale).dp)
            .size(width, (PRESET_ROW_DP * scale).dp)
            .background(glow, RoundedCornerShape((14 * scale).dp))
            .clickable(onClick = onClick)
            .padding(horizontal = (8 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = fg, fontSize = (15 * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Indicator(on, scale, t)
    }
}

/** One row of the strip; both rows are the button height so the separators line up. */
@Composable
private fun StripRow(height: Dp, content: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.height(height), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** "28°c" the way the BYD dock prints it: big digits, small unit. */
@Composable
private fun tempText(tempC: Int?, scale: Float, digits: Int = 16) = buildAnnotatedString {
    withStyle(SpanStyle(fontSize = (digits * scale).sp, fontWeight = FontWeight.SemiBold)) {
        append(tempC?.toString() ?: "–")
    }
    withStyle(SpanStyle(fontSize = (digits * 0.55f * scale).sp)) { append("°c") }
}

@Composable
private fun Separator(button: Dp, t: AcTheme) {
    // Sized from the button, not the row's constraints, so it shows whatever measured the strip.
    Box(
        Modifier
            .padding(horizontal = button * 0.1f)
            .size(width = 1.5.dp, height = button * 0.55f)
            .background(t.divider, RoundedCornerShape(50)),
    )
}

/** Indicator under an active control, like the LED under a hardware button. */
@Composable
private fun Indicator(on: Boolean, scale: Float, t: AcTheme) {
    val color by animateColorAsState(if (on) t.on else Color.Transparent, tween(AC_STRIP_ANIM_MS), label = "led")
    Box(
        Modifier
            .padding(top = (4 * scale).dp)
            .size((18 * scale).dp, (3 * scale).dp)
            .background(color, RoundedCornerShape(50)),
    )
}

@Suppress("LongParameterList") // one flat input per visual element
@Composable
private fun IconButton(
    size: Dp,
    scale: Float,
    t: AcTheme,
    on: Boolean?,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color, Dp) -> Unit,
) {
    val active = on == true
    val fg by animateColorAsState(if (active) t.on else t.fg, tween(AC_STRIP_ANIM_MS), label = "fg")
    val labelColor by animateColorAsState(if (active) t.on else t.dim, tween(AC_STRIP_ANIM_MS), label = "label")
    val glow by animateColorAsState(if (active) t.glow else Color.Transparent, tween(AC_STRIP_ANIM_MS), label = "glow")
    Column(
        modifier = Modifier
            // Breathing room so two neighbouring glows never merge into one block.
            .padding(horizontal = (BUTTON_SPACING_DP * scale).dp)
            .size(size)
            .background(glow, RoundedCornerShape((14 * scale).dp))
            .clickable(onClick = onClick)
            .padding((4 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(fg, (28 * scale).dp)
        Text(label, color = labelColor, fontSize = (9 * scale).sp, maxLines = 1)
        Indicator(active, scale, t)
    }
}

@Composable
private fun TextButton(size: Dp, scale: Float, t: AcTheme, on: Boolean?, text: String, onClick: () -> Unit) {
    val active = on == true
    val fg by animateColorAsState(if (active) t.on else t.fg, tween(AC_STRIP_ANIM_MS), label = "fg")
    val glow by animateColorAsState(if (active) t.glow else Color.Transparent, tween(AC_STRIP_ANIM_MS), label = "glow")
    Column(
        modifier = Modifier
            .padding(horizontal = (BUTTON_SPACING_DP * scale).dp)
            .size(size)
            .background(glow, RoundedCornerShape((14 * scale).dp))
            .clickable(onClick = onClick)
            .padding((4 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = fg, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold)
        Indicator(active, scale, t)
    }
}

/** ‹ value › block, the BYD dock's temperature control, also used for the fan. */
@Composable
private fun Stepper(
    size: Dp,
    scale: Float,
    t: AcTheme,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Chevron("‹", size, scale, t, onMinus)
        Column(
            modifier = Modifier.width((62 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
        Chevron("›", size, scale, t, onPlus)
    }
}

@Composable
private fun Chevron(glyph: String, size: Dp, scale: Float, t: AcTheme, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = size * 0.7f, height = size)
            .background(Color.Transparent, RoundedCornerShape((12 * scale).dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = t.fg, fontSize = (34 * scale).sp, fontWeight = FontWeight.Light)
    }
}

/** Seven short bars, filled up to the fan level. */
@Composable
private fun FanBars(level: Int?, scale: Float, t: AcTheme) {
    Row(
        modifier = Modifier.padding(top = (4 * scale).dp),
        horizontalArrangement = Arrangement.spacedBy((2 * scale).dp),
    ) {
        for (i in AcCommands.FAN_MIN..AcCommands.FAN_MAX) {
            Box(
                Modifier
                    .size((6 * scale).dp, (3 * scale).dp)
                    .background(if (level != null && i <= level) t.on else t.track, RoundedCornerShape(50)),
            )
        }
    }
}
