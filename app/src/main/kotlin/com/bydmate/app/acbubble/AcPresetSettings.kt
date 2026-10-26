package com.bydmate.app.acbubble

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.components.bydSwitchColors
import com.bydmate.app.ui.settings.SettingChipRow
import com.bydmate.app.ui.settings.SettingHint
import com.bydmate.app.ui.settings.SettingSliderRow
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary

/** The custom buttons block of the AC bubble settings: one row per slot, tap to edit. */
@Composable
fun AcPresetSettingsBlock(prefs: AcBubblePreferences, enabled: Boolean) {
    val presets by remember { prefs.presetsFlow() }.collectAsStateWithLifecycle(initialValue = prefs.getPresets())
    var editing by remember { mutableStateOf<Int?>(null) }

    Text(
        stringResource(R.string.ac_preset_header),
        color = if (enabled) TextPrimary else TextMuted,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
    SettingHint(text = stringResource(R.string.ac_preset_hint))
    presets.forEachIndexed { slot, p ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled) { editing = slot }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    p.name.ifBlank { stringResource(R.string.ac_preset_slot, slot + 1) },
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (p.isEmpty) stringResource(R.string.ac_preset_empty) else presetSummary(p),
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            TextButton(onClick = { editing = slot }, enabled = enabled) {
                Text(stringResource(R.string.ac_preset_edit), color = if (enabled) AccentGreen else TextMuted)
            }
            Switch(
                checked = p.enabled && !p.isEmpty,
                onCheckedChange = { on -> if (on && p.isEmpty) editing = slot else prefs.setPreset(slot, p.copy(enabled = on)) },
                enabled = enabled,
                colors = bydSwitchColors(),
            )
        }
    }

    editing?.let { slot ->
        PresetEditorDialog(
            slot = slot,
            initial = presets.getOrElse(slot) { AcPreset() },
            onDismiss = { editing = null },
            onSave = {
                prefs.setPreset(slot, it)
                editing = null
            },
        )
    }
}

/** "Climate on · A/C off · Face · Fan 2 · 25°", from what the button sets. */
@Composable
private fun presetSummary(p: AcPreset): String {
    val on = stringResource(R.string.ac_preset_on)
    val off = stringResource(R.string.ac_preset_off)
    fun flag(label: String, v: Boolean?) = v?.let { "$label ${if (it) on else off}" }
    val parts = listOfNotNull(
        flag(stringResource(R.string.ac_preset_power), p.power),
        flag("A/C", p.compressor),
        flag("AUTO", p.auto),
        flag("ONLY", p.fanOnly),
        p.recircInner?.let { stringResource(if (it) R.string.ac_preset_recirc_inner else R.string.ac_preset_recirc_outer) },
        p.windMode?.let { stringResource(windModeLabel(it)) },
        p.fanLevel?.let { stringResource(R.string.ac_preset_fan_value, it) },
        p.tempC?.let { "$it°" },
        flag(stringResource(R.string.ac_bubble_defrost_front), p.defrostFront),
        flag(stringResource(R.string.ac_bubble_defrost_rear), p.defrostRear),
    )
    return parts.joinToString(" · ")
}

private fun windModeLabel(mode: Int): Int = when (mode) {
    1 -> R.string.ac_bubble_wind_face
    2 -> R.string.ac_bubble_wind_face_feet
    3 -> R.string.ac_bubble_wind_feet
    4 -> R.string.ac_bubble_wind_feet_glass
    5 -> R.string.ac_bubble_wind_glass
    6 -> R.string.ac_preset_wind_all
    else -> R.string.ac_preset_wind_face_glass
}

/** The airflow choices in the order the strip shows them, after "do not change". */
private val WIND_ORDER = listOf(1, 2, 3, 4, 5, 7, 6)

@Composable
@Suppress("LongMethod") // one flat row per climate value
private fun PresetEditorDialog(slot: Int, initial: AcPreset, onDismiss: () -> Unit, onSave: (AcPreset) -> Unit) {
    var p by remember { mutableStateOf(initial) }
    val keep = stringResource(R.string.ac_preset_keep)
    val on = stringResource(R.string.ac_preset_on)
    val off = stringResource(R.string.ac_preset_off)
    val triOptions = listOf(keep, on, off)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ac_preset_editor_title, slot + 1)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OutlinedTextField(
                    value = p.name,
                    onValueChange = { p = p.copy(name = it.take(AcPresets.NAME_MAX)) },
                    label = { Text(stringResource(R.string.ac_preset_name)) },
                    placeholder = { Text(stringResource(R.string.ac_preset_slot, slot + 1), color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentGreen,
                        cursorColor = AccentGreen,
                    ),
                )
                Tri(triOptions, stringResource(R.string.ac_preset_power), value = p.power) { p = p.copy(power = it) }
                Tri(triOptions, "A/C", stringResource(R.string.ac_preset_compressor_desc), p.compressor) { p = p.copy(compressor = it) }
                Tri(triOptions, "AUTO", value = p.auto) { p = p.copy(auto = it) }
                Tri(triOptions, "ONLY", stringResource(R.string.ac_preset_fan_only_desc), p.fanOnly) { p = p.copy(fanOnly = it) }
                SettingChipRow(
                    title = stringResource(R.string.ac_bubble_recirc),
                    options = listOf(keep, stringResource(R.string.ac_preset_recirc_inner), stringResource(R.string.ac_preset_recirc_outer)),
                    selectedIndex = triIndex(p.recircInner),
                    onSelect = { p = p.copy(recircInner = triValue(it)) },
                )
                SettingChipRow(
                    title = stringResource(R.string.ac_bubble_airflow),
                    options = listOf(keep) + WIND_ORDER.map { stringResource(windModeLabel(it)) },
                    selectedIndex = p.windMode?.let { WIND_ORDER.indexOf(it) + 1 } ?: 0,
                    onSelect = { p = p.copy(windMode = if (it == 0) null else WIND_ORDER[it - 1]) },
                )
                SettingChipRow(
                    title = stringResource(R.string.ac_bubble_fan),
                    options = listOf(keep) + (AcCommands.FAN_MIN..AcCommands.FAN_MAX).map { it.toString() },
                    selectedIndex = p.fanLevel ?: 0,
                    onSelect = { p = p.copy(fanLevel = if (it == 0) null else it) },
                )
                SettingChipRow(
                    title = stringResource(R.string.ac_bubble_temp),
                    options = listOf(keep, stringResource(R.string.ac_preset_set)),
                    selectedIndex = if (p.tempC == null) 0 else 1,
                    onSelect = { p = p.copy(tempC = if (it == 0) null else p.tempC ?: DEFAULT_TEMP) },
                )
                p.tempC?.let { t ->
                    SettingSliderRow(
                        title = "",
                        value = t.toFloat(),
                        onValueChange = { p = p.copy(tempC = it.toInt()) },
                        valueRange = AcCommands.TEMP_MIN.toFloat()..AcCommands.TEMP_MAX.toFloat(),
                        steps = AcCommands.TEMP_MAX - AcCommands.TEMP_MIN - 1,
                        valueLabel = "$t°C",
                    )
                }
                Tri(triOptions, stringResource(R.string.ac_preset_defrost_front), value = p.defrostFront) { p = p.copy(defrostFront = it) }
                Tri(triOptions, stringResource(R.string.ac_preset_defrost_rear), value = p.defrostRear) { p = p.copy(defrostRear = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // A button with nothing to set cannot be shown; one just set up is shown at once.
                onSave(p.copy(name = p.name.trim(), enabled = !p.isEmpty && (p.enabled || initial.isEmpty)))
            }) { Text(stringResource(R.string.ac_preset_save), color = AccentGreen) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(AcPreset()) }) { Text(stringResource(R.string.ac_preset_clear), color = TextMuted) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ac_preset_cancel), color = TextMuted) }
            }
        },
    )
}

private const val DEFAULT_TEMP = 22

private fun triIndex(v: Boolean?) = when (v) { null -> 0; true -> 1; false -> 2 }
private fun triValue(i: Int): Boolean? = when (i) { 1 -> true; 2 -> false; else -> null }

/** "Do not change / On / Off" for one switch of the climate. */
@Composable
private fun Tri(options: List<String>, title: String, description: String? = null, value: Boolean?, set: (Boolean?) -> Unit) =
    SettingChipRow(title = title, description = description, options = options, selectedIndex = triIndex(value),
        onSelect = { set(triValue(it)) })
