package com.bydmate.app.acbubble

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.settings.SettingActionRow
import com.bydmate.app.ui.settings.SettingButtonStyle
import com.bydmate.app.ui.settings.SettingHint
import com.bydmate.app.ui.settings.SettingSliderRow
import com.bydmate.app.ui.settings.SettingToggleRow
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary

/** One swatch per [AcTheme]: the chosen one wears a ring and its name is shown as the value. */
@Composable
private fun ColorRow(selected: AcTheme, enabled: Boolean, onSelect: (AcTheme) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ac_bubble_color_label), color = if (enabled) TextPrimary else TextMuted, fontSize = 15.sp)
            Text(stringResource(selected.labelRes), color = TextMuted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcTheme.entries.forEach { t ->
                val ring = if (t == selected) TextPrimary else TextMuted.copy(alpha = 0.35f)
                Box(
                    modifier = Modifier
                        .size(SWATCH_DP.dp)
                        .border(if (t == selected) 2.5.dp else 1.dp, ring, CircleShape)
                        .padding(3.dp)
                        .background(t.bg.copy(alpha = 1f), CircleShape)
                        .clickable(enabled = enabled) { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    // A hint of the icon colour, so a light swatch reads as "dark icons on light".
                    Box(Modifier.size(6.dp).background(t.fg, CircleShape))
                }
            }
        }
    }
}

private const val SWATCH_DP = 30

/** Settings block of the floating AC bubble, shown under the stats widget's own settings. */
@Composable
fun AcBubbleSettingsSection() {
    val context = LocalContext.current
    val prefs = remember { AcBubblePreferences(context) }
    val enabled by remember { prefs.enabledFlow() }.collectAsStateWithLifecycle(initialValue = prefs.isEnabled())
    val alpha by remember { prefs.alphaFlow() }.collectAsStateWithLifecycle(initialValue = prefs.getAlpha())
    val scale by remember { prefs.scaleFlow() }.collectAsStateWithLifecycle(initialValue = prefs.getScale())
    val homeOnly by remember { prefs.homeOnlyFlow() }.collectAsStateWithLifecycle(initialValue = prefs.isHomeOnly())
    val hideYt by remember { prefs.hideOnYoutubeFlow() }.collectAsStateWithLifecycle(initialValue = prefs.isHideOnYoutube())
    val autoClose by remember { prefs.autoCloseSecFlow() }.collectAsStateWithLifecycle(initialValue = prefs.getAutoCloseSec())
    val pinned by remember { prefs.pinnedFlow() }.collectAsStateWithLifecycle(initialValue = prefs.isPinned())
    val docked by remember { prefs.dockedFlow() }.collectAsStateWithLifecycle(initialValue = prefs.isDocked())
    val themeKey by remember { prefs.themeKeyFlow() }.collectAsStateWithLifecycle(initialValue = prefs.getThemeKey())

    // Sliders pop the bubble over Settings for a live look; leaving the screen ends that.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) AcBubbleController.setPreviewMode(context, false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AcBubbleController.setPreviewMode(context, false)
        }
    }

    Text(
        text = stringResource(R.string.ac_bubble_section_header),
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingToggleRow(
                title = stringResource(R.string.ac_bubble_enable_label),
                traceId = "ac_bubble_enabled",
                description = stringResource(R.string.ac_bubble_enable_desc),
                checked = enabled,
                onCheckedChange = { requested ->
                    if (!requested) {
                        prefs.setEnabled(false)
                        AcBubbleController.detach("settings_toggle_off")
                    } else if (AndroidSettings.canDrawOverlays(context)) {
                        prefs.setEnabled(true)
                        AcBubbleController.attach(context, "settings_toggle_on")
                    } else {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            SettingHint(text = stringResource(R.string.ac_bubble_untested_hint))
            // The tab and the pinned strip both replace the bubble: one at a time.
            SettingToggleRow(
                title = stringResource(R.string.ac_bubble_docked_label),
                traceId = "ac_bubble_docked",
                description = stringResource(R.string.ac_bubble_docked_desc),
                checked = docked,
                onCheckedChange = { on ->
                    if (on) prefs.setPinned(false)
                    prefs.setDocked(on)
                },
                enabled = enabled,
            )
            SettingToggleRow(
                title = stringResource(R.string.ac_bubble_pinned_label),
                traceId = "ac_bubble_pinned",
                description = stringResource(R.string.ac_bubble_pinned_desc),
                checked = pinned,
                onCheckedChange = { on ->
                    if (on) prefs.setDocked(false)
                    prefs.setPinned(on)
                },
                enabled = enabled,
            )
            SettingToggleRow(
                title = stringResource(R.string.ac_bubble_home_only_label),
                traceId = "ac_bubble_home_only",
                description = stringResource(R.string.ac_bubble_home_only_desc),
                checked = homeOnly,
                onCheckedChange = { prefs.setHomeOnly(it) },
                enabled = enabled,
            )
            SettingToggleRow(
                title = stringResource(R.string.ac_bubble_hide_youtube_label),
                traceId = "ac_bubble_hide_youtube",
                checked = hideYt,
                onCheckedChange = { prefs.setHideOnYoutube(it) },
                enabled = enabled,
            )
            SettingSliderRow(
                title = stringResource(R.string.ac_bubble_auto_close_label),
                value = autoClose.toFloat(),
                onValueChange = { prefs.setAutoCloseSec(it.toInt()) },
                valueRange = AcBubblePreferences.AUTO_CLOSE_MIN_SEC.toFloat()..AcBubblePreferences.AUTO_CLOSE_MAX_SEC.toFloat(),
                steps = AcBubblePreferences.AUTO_CLOSE_MAX_SEC - AcBubblePreferences.AUTO_CLOSE_MIN_SEC - 1,
                valueLabel = stringResource(R.string.ac_bubble_seconds, autoClose),
                enabled = enabled && !pinned,
            )
            ColorRow(
                selected = AcTheme.of(themeKey),
                enabled = enabled,
                onSelect = {
                    if (enabled) AcBubbleController.setPreviewMode(context, true)
                    prefs.setThemeKey(it.key)
                },
            )
            SettingSliderRow(
                title = stringResource(R.string.ac_bubble_opacity_label),
                value = alpha,
                onValueChange = {
                    if (enabled) AcBubbleController.setPreviewMode(context, true)
                    prefs.setAlpha(it)
                },
                valueRange = AcBubblePreferences.ALPHA_MIN..1.0f,
                valueLabel = "${(alpha * 100).toInt()}%",
                enabled = enabled,
            )
            SettingSliderRow(
                title = stringResource(R.string.ac_bubble_scale_label),
                value = scale,
                onValueChange = {
                    if (enabled) AcBubbleController.setPreviewMode(context, true)
                    prefs.setScale(it)
                },
                valueRange = AcBubblePreferences.SCALE_MIN..AcBubblePreferences.SCALE_MAX,
                valueLabel = "${(scale * 100).toInt()}%",
                enabled = enabled,
            )
            SettingActionRow(
                title = stringResource(R.string.ac_bubble_reset_position),
                buttonLabel = stringResource(R.string.ac_bubble_reset_position),
                onClick = {
                    prefs.resetPosition()
                    AcBubbleController.detach("reset_position")
                    AcBubbleController.attach(context, "reset_position")
                },
                style = SettingButtonStyle.Secondary,
                enabled = enabled,
            )
        }
    }
}
