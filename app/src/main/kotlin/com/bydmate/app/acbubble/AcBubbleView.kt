// One composable per piece of the bubble and strip.
@file:Suppress("TooManyFunctions")

package com.bydmate.app.acbubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R

// Palette of the newer BYD dock: near-black glass, white line icons, blue for "on".
private val StripBg = Color(0xEB0B0F14)
private val Divider = Color(0x26FFFFFF)
private val IconIdle = Color(0xFFE6E9EE)
private val IconOn = Color(0xFF3DA5FF)
private val TextDim = Color(0xFF8A93A0)
private val Pressed = Color(0x1AFFFFFF)
/** Soft blue behind an active button. */
private val Glow = Color(0x263DA5FF)

/** The dark glass behind everything; the opacity setting fades only this, never the icons. */
private fun glass(opacity: Float): Color = StripBg.copy(alpha = StripBg.alpha * opacity)

/** Base size of the oval bubble, before the user's size setting. */
const val AC_BUBBLE_W_DP = 92
const val AC_BUBBLE_H_DP = 52
/** Strip height: button + its padding. Shared with the controller to centre it on the bubble. */
const val AC_STRIP_HEIGHT_DP = 76
private const val BUTTON_DP = 58
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
fun AcBubble(state: AcState, alpha: Float, scale: Float, pressed: Boolean) {
    val on = state.power == true
    val shape = RoundedCornerShape(percent = 50)
    val press by animateFloatAsState(if (pressed) BUBBLE_PRESSED_SCALE else 1f, tween(BUBBLE_PRESS_MS), label = "press")
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = press; scaleY = press }
            .size((AC_BUBBLE_W_DP * scale).dp, (AC_BUBBLE_H_DP * scale).dp)
            .background(glass(alpha), shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("A/C", color = if (on) IconOn else IconIdle, fontSize = (16 * scale).sp, fontWeight = FontWeight.Bold)
        if (on && state.tempC != null) {
            Spacer(Modifier.width((6 * scale).dp))
            Text(tempText(state.tempC, scale), color = IconIdle)
        }
    }
}

/** Drag handle that stands in for the bubble while the strip is always shown. */
@Composable
fun AcGrip(alpha: Float, scale: Float) {
    Column(
        modifier = Modifier
            .size((GRIP_W_DP * scale).dp, (AC_STRIP_HEIGHT_DP * scale).dp)
            .background(glass(alpha), RoundedCornerShape((12 * scale).dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((4 * scale).dp, Alignment.CenterVertically),
    ) {
        repeat(GRIP_DOTS) {
            Box(Modifier.size((4 * scale).dp).background(TextDim, RoundedCornerShape(50)))
        }
    }
}

/** The horizontal control strip that slides out of the bubble. */
@Composable
@Suppress("LongParameterList") // one flat input per visual element
fun AcStrip(
    state: AcState,
    alpha: Float,
    scale: Float,
    visible: Boolean,
    opensLeft: Boolean,
    onAction: (AcAction) -> Unit,
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
    Row(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = progress
                val s = STRIP_START_SCALE + (1f - STRIP_START_SCALE) * progress
                scaleX = s
                scaleY = s
                translationX = (1f - progress) * if (opensLeft) slidePx else -slidePx
                transformOrigin = TransformOrigin(if (opensLeft) 1f else 0f, 0.5f)
            }
            .height((AC_STRIP_HEIGHT_DP * scale).dp)
            .background(glass(alpha), shape)
            .padding(horizontal = (8 * scale).dp, vertical = (6 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(b, scale, state.power, stringResource(R.string.ac_bubble_power),
            onClick = { onAction(AcAction.TogglePower) }) { c, s ->
            AcIcon(AcIconKind.POWER, c, s)
        }
        TextButton(b, scale, state.compressor, "A/C") { onAction(AcAction.ToggleCompressor) }
        TextButton(b, scale, state.auto, "AUTO") { onAction(AcAction.ToggleAuto) }
        // The stock "ONLY" button: fan icon over the word, like the BYD climate screen.
        IconButton(b, scale, state.fanOnly, "ONLY", onClick = { onAction(AcAction.ToggleFanOnly) }) { c, s ->
            AcIcon(AcIconKind.FAN, c, s)
        }
        Separator(scale)
        IconButton(b, scale, AirDirection.WINDSHIELD in directions, stringResource(R.string.ac_bubble_wind_glass),
            onClick = { onAction(AcAction.ToggleDirection(AirDirection.WINDSHIELD)) }) { c, s ->
            AcIcon(AcIconKind.SEAT_WINDSHIELD, c, s)
        }
        IconButton(b, scale, AirDirection.FACE in directions, stringResource(R.string.ac_bubble_wind_face),
            onClick = { onAction(AcAction.ToggleDirection(AirDirection.FACE)) }) { c, s ->
            AcIcon(AcIconKind.SEAT_FACE, c, s)
        }
        IconButton(b, scale, AirDirection.FEET in directions, stringResource(R.string.ac_bubble_wind_feet),
            onClick = { onAction(AcAction.ToggleDirection(AirDirection.FEET)) }) { c, s ->
            AcIcon(AcIconKind.SEAT_FEET, c, s)
        }
        Separator(scale)
        // One button, two faces: the icon shows where the air comes from now.
        val inner = state.recircInner == true
        IconButton(b, scale, inner, stringResource(R.string.ac_bubble_recirc),
            onClick = { onAction(AcAction.ToggleRecirc) }) { c, s ->
            AcIcon(if (inner) AcIconKind.RECIRC else AcIconKind.FRESH_AIR, c, s)
        }
        IconButton(b, scale, state.defrostFront, stringResource(R.string.ac_bubble_defrost_front),
            onClick = { onAction(AcAction.ToggleDefrostFront) }) { c, s ->
            AcIcon(AcIconKind.DEFROST_FRONT, c, s)
        }
        IconButton(b, scale, state.defrostRear, stringResource(R.string.ac_bubble_defrost_rear),
            onClick = { onAction(AcAction.ToggleDefrostRear) }) { c, s ->
            AcIcon(AcIconKind.DEFROST_REAR, c, s)
        }
        Separator(scale)
        Stepper(b, scale, onMinus = { onAction(AcAction.FanStep(-1)) }, onPlus = { onAction(AcAction.FanStep(1)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AcIcon(AcIconKind.FAN, IconIdle, (22 * scale).dp)
                Spacer(Modifier.width((4 * scale).dp))
                Text(state.fanLevel?.toString() ?: "–", color = IconIdle, fontSize = (22 * scale).sp,
                    fontWeight = FontWeight.SemiBold)
            }
            FanBars(state.fanLevel, scale)
        }
        Separator(scale)
        Stepper(b, scale, onMinus = { onAction(AcAction.TempStep(-1)) }, onPlus = { onAction(AcAction.TempStep(1)) }) {
            Text(tempText(state.tempC, scale, big = true), color = IconIdle)
        }
    }
}

/** "28°c" the way the BYD dock prints it: big digits, small unit. */
@Composable
private fun tempText(tempC: Int?, scale: Float, big: Boolean = false) = buildAnnotatedString {
    val digits = if (big) 26 else 16
    withStyle(SpanStyle(fontSize = (digits * scale).sp, fontWeight = FontWeight.SemiBold)) {
        append(tempC?.toString() ?: "–")
    }
    withStyle(SpanStyle(fontSize = (digits * 0.55f * scale).sp)) { append("°c") }
}

@Composable
private fun Separator(scale: Float) {
    Box(
        Modifier
            .padding(horizontal = (6 * scale).dp)
            .width(1.dp)
            .fillMaxHeight(0.55f)
            .background(Divider),
    )
}

/** Indicator under an active control, like the LED under a hardware button. */
@Composable
private fun Indicator(on: Boolean, scale: Float) {
    val color by animateColorAsState(if (on) IconOn else Color.Transparent, tween(AC_STRIP_ANIM_MS), label = "led")
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
    on: Boolean?,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color, Dp) -> Unit,
) {
    val active = on == true
    val fg by animateColorAsState(if (active) IconOn else IconIdle, tween(AC_STRIP_ANIM_MS), label = "fg")
    val labelColor by animateColorAsState(if (active) IconOn else TextDim, tween(AC_STRIP_ANIM_MS), label = "label")
    val glow by animateColorAsState(if (active) Glow else Color.Transparent, tween(AC_STRIP_ANIM_MS), label = "glow")
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
        Indicator(active, scale)
    }
}

@Composable
private fun TextButton(size: Dp, scale: Float, on: Boolean?, text: String, onClick: () -> Unit) {
    val active = on == true
    val fg by animateColorAsState(if (active) IconOn else IconIdle, tween(AC_STRIP_ANIM_MS), label = "fg")
    val glow by animateColorAsState(if (active) Glow else Color.Transparent, tween(AC_STRIP_ANIM_MS), label = "glow")
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
        Indicator(active, scale)
    }
}

/** ‹ value › block, the BYD dock's temperature control, also used for the fan. */
@Composable
private fun Stepper(
    size: Dp,
    scale: Float,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Chevron("‹", size, scale, onMinus)
        Column(
            modifier = Modifier.width((62 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
        Chevron("›", size, scale, onPlus)
    }
}

@Composable
private fun Chevron(glyph: String, size: Dp, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = size * 0.7f, height = size)
            .background(Color.Transparent, RoundedCornerShape((12 * scale).dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = IconIdle, fontSize = (34 * scale).sp, fontWeight = FontWeight.Light)
    }
}

/** Seven short bars, filled up to the fan level. */
@Composable
private fun FanBars(level: Int?, scale: Float) {
    Row(
        modifier = Modifier.padding(top = (4 * scale).dp),
        horizontalArrangement = Arrangement.spacedBy((2 * scale).dp),
    ) {
        for (i in AcCommands.FAN_MIN..AcCommands.FAN_MAX) {
            Box(
                Modifier
                    .size((6 * scale).dp, (3 * scale).dp)
                    .background(if (level != null && i <= level) IconOn else Pressed, RoundedCornerShape(50)),
            )
        }
    }
}
