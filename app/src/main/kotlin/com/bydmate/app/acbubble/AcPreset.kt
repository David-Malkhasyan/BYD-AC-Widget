package com.bydmate.app.acbubble

import org.json.JSONArray
import org.json.JSONObject

/**
 * A custom button on the strip: a set of climate values sent in one tap. Every value is
 * optional; null means "leave as it is". [enabled] shows the button on the strip.
 */
data class AcPreset(
    val enabled: Boolean = false,
    val name: String = "",
    val power: Boolean? = null,
    val compressor: Boolean? = null,
    val auto: Boolean? = null,
    val fanOnly: Boolean? = null,
    val recircInner: Boolean? = null,
    val defrostFront: Boolean? = null,
    val defrostRear: Boolean? = null,
    val windMode: Int? = null,
    val fanLevel: Int? = null,
    val tempC: Int? = null,
) {
    /** The values it sets, as the state it asks for (unset fields stay null). */
    fun asState(): AcState = AcState(
        power = power, compressor = compressor, fanOnly = fanOnly, auto = auto, recircInner = recircInner,
        defrostFront = defrostFront, defrostRear = defrostRear, fanLevel = fanLevel, tempC = tempC, windMode = windMode,
    )

    val isEmpty: Boolean get() = AcPresets.fieldsOf(this).isEmpty()
}

/** What one tap on a custom button sends: the state it asks for and the writes, in order. */
data class AcPlan(val target: AcState, val steps: List<Pair<AcStateOverlay.Field, AcWrite>>) {
    val fields: List<AcStateOverlay.Field> get() = steps.map { it.first }
}

/**
 * Pure logic of the custom buttons: which writes a button sends, when it shows as on, how a
 * second tap puts back what was there before, and how the buttons are stored.
 */
object AcPresets {
    const val MAX = 4
    const val NAME_MAX = 12

    private val F = AcStateOverlay.Field.entries

    /** Send order: power on first (the car wakes the climate), mode before levels, power off last. */
    private val ORDER = listOf(
        AcStateOverlay.Field.AUTO,
        AcStateOverlay.Field.COMPRESSOR,
        AcStateOverlay.Field.FAN_ONLY,
        AcStateOverlay.Field.RECIRC,
        AcStateOverlay.Field.WIND,
        AcStateOverlay.Field.FAN,
        AcStateOverlay.Field.TEMP,
        AcStateOverlay.Field.DEFROST_FRONT,
        AcStateOverlay.Field.DEFROST_REAR,
    )

    fun fieldsOf(p: AcPreset): List<AcStateOverlay.Field> = F.filter { valueOf(it, p.asState()) != null }

    /** On when the car shows every value the button sets. An empty button is never on. */
    fun matches(p: AcPreset, car: AcState): Boolean {
        val fields = fieldsOf(p)
        if (fields.isEmpty()) return false
        val want = p.asState()
        // With the climate off, nothing but "power off" can be in effect.
        if (car.power == false && p.power != false) return false
        return fields.all { valueOf(it, car) == valueOf(it, want) }
    }

    /** The first tap: every value of the button, over what is shown now. */
    fun apply(p: AcPreset, current: AcState): AcPlan = plan(p.asState(), fieldsOf(p), current)

    /**
     * The second tap: puts back the values [before] the button was pressed, for the fields the
     * button touched. The climate that was off is simply switched off again. Null when there is
     * nothing to put back (the app restarted since, or the values were unknown).
     */
    fun revert(p: AcPreset, before: AcState?, current: AcState): AcPlan? {
        if (before == null) return if (p.power == true) plan(AcState(power = false), listOf(AcStateOverlay.Field.POWER), current) else null
        if (before.power == false && p.power == true) {
            return plan(AcState(power = false), listOf(AcStateOverlay.Field.POWER), current)
        }
        val back = fieldsOf(p).filter { valueOf(it, before) != null && valueOf(it, before) != valueOf(it, p.asState()) }
        if (back.isEmpty()) return null
        return plan(before, back, current)
    }

    /**
     * The writes of [plan] the car has not taken: fields it reads back with another value.
     * Unknown values are not sent again, nor anything once the climate is off as asked.
     */
    fun missed(plan: AcPlan, car: AcState): List<AcWrite> {
        if (plan.target.power == false && car.power == false) return emptyList()
        return plan.steps.filter { (f, _) -> valueOf(f, car).let { it != null && it != valueOf(f, plan.target) } }.map { it.second }
    }

    private fun plan(want: AcState, fields: List<AcStateOverlay.Field>, current: AcState): AcPlan {
        var target = current
        fields.forEach { target = withValue(it, target, want) }
        val ordered = buildList {
            if (want.power == true && AcStateOverlay.Field.POWER in fields) add(AcStateOverlay.Field.POWER)
            ORDER.filter { it in fields }.forEach { add(it) }
            if (want.power == false && AcStateOverlay.Field.POWER in fields) add(AcStateOverlay.Field.POWER)
        }
        return AcPlan(target, ordered.map { it to AcCommands.write(actionOf(it), target) })
    }

    /** The strip action whose write sets [field]; the write reads the value from the target. */
    private fun actionOf(field: AcStateOverlay.Field): AcAction = when (field) {
        AcStateOverlay.Field.POWER -> AcAction.TogglePower
        AcStateOverlay.Field.COMPRESSOR -> AcAction.ToggleCompressor
        AcStateOverlay.Field.FAN_ONLY -> AcAction.ToggleFanOnly
        AcStateOverlay.Field.AUTO -> AcAction.ToggleAuto
        AcStateOverlay.Field.RECIRC -> AcAction.ToggleRecirc
        AcStateOverlay.Field.DEFROST_FRONT -> AcAction.ToggleDefrostFront
        AcStateOverlay.Field.DEFROST_REAR -> AcAction.ToggleDefrostRear
        AcStateOverlay.Field.WIND -> AcAction.ToggleDirection(AirDirection.FACE)
        AcStateOverlay.Field.FAN -> AcAction.FanStep(0)
        AcStateOverlay.Field.TEMP -> AcAction.TempStep(0)
    }

    private fun valueOf(field: AcStateOverlay.Field, s: AcState): Any? = when (field) {
        AcStateOverlay.Field.POWER -> s.power
        AcStateOverlay.Field.COMPRESSOR -> s.compressor
        AcStateOverlay.Field.FAN_ONLY -> s.fanOnly
        AcStateOverlay.Field.AUTO -> s.auto
        AcStateOverlay.Field.RECIRC -> s.recircInner
        AcStateOverlay.Field.DEFROST_FRONT -> s.defrostFront
        AcStateOverlay.Field.DEFROST_REAR -> s.defrostRear
        AcStateOverlay.Field.FAN -> s.fanLevel
        AcStateOverlay.Field.TEMP -> s.tempC
        AcStateOverlay.Field.WIND -> s.windMode
    }

    private fun withValue(field: AcStateOverlay.Field, s: AcState, from: AcState): AcState = when (field) {
        AcStateOverlay.Field.POWER -> s.copy(power = from.power)
        AcStateOverlay.Field.COMPRESSOR -> s.copy(compressor = from.compressor)
        AcStateOverlay.Field.FAN_ONLY -> s.copy(fanOnly = from.fanOnly)
        AcStateOverlay.Field.AUTO -> s.copy(auto = from.auto)
        AcStateOverlay.Field.RECIRC -> s.copy(recircInner = from.recircInner)
        AcStateOverlay.Field.DEFROST_FRONT -> s.copy(defrostFront = from.defrostFront)
        AcStateOverlay.Field.DEFROST_REAR -> s.copy(defrostRear = from.defrostRear)
        AcStateOverlay.Field.FAN -> s.copy(fanLevel = from.fanLevel)
        AcStateOverlay.Field.TEMP -> s.copy(tempC = from.tempC)
        AcStateOverlay.Field.WIND -> s.copy(windMode = from.windMode)
    }

    // --- Storage: a JSON array of [MAX] objects, unset values left out ---

    fun encode(presets: List<AcPreset>): String {
        val arr = JSONArray()
        normalize(presets).forEach { p ->
            val o = JSONObject().put("enabled", p.enabled).put("name", p.name)
            p.power?.let { o.put("power", it) }
            p.compressor?.let { o.put("compressor", it) }
            p.auto?.let { o.put("auto", it) }
            p.fanOnly?.let { o.put("fanOnly", it) }
            p.recircInner?.let { o.put("recircInner", it) }
            p.defrostFront?.let { o.put("defrostFront", it) }
            p.defrostRear?.let { o.put("defrostRear", it) }
            p.windMode?.let { o.put("windMode", it) }
            p.fanLevel?.let { o.put("fanLevel", it) }
            p.tempC?.let { o.put("tempC", it) }
            arr.put(o)
        }
        return arr.toString()
    }

    /** Never throws: anything unreadable becomes an empty slot, out-of-range values are dropped. */
    fun decode(json: String?): List<AcPreset> {
        val arr = try { JSONArray(json ?: return normalize(emptyList())) } catch (_: Exception) { return normalize(emptyList()) }
        val out = (0 until minOf(arr.length(), MAX)).map { i ->
            val o = arr.optJSONObject(i) ?: return@map AcPreset()
            AcPreset(
                enabled = o.optBoolean("enabled", false),
                name = o.optString("name", "").take(NAME_MAX),
                power = o.bool("power"),
                compressor = o.bool("compressor"),
                auto = o.bool("auto"),
                fanOnly = o.bool("fanOnly"),
                recircInner = o.bool("recircInner"),
                defrostFront = o.bool("defrostFront"),
                defrostRear = o.bool("defrostRear"),
                windMode = o.int("windMode")?.takeIf { it in AcCommands.WIND_MODES },
                fanLevel = o.int("fanLevel")?.takeIf { it in AcCommands.FAN_MIN..AcCommands.FAN_MAX },
                tempC = o.int("tempC")?.takeIf { it in AcCommands.TEMP_MIN..AcCommands.TEMP_MAX },
            )
        }
        return normalize(out)
    }

    /** Always exactly [MAX] slots. */
    fun normalize(presets: List<AcPreset>): List<AcPreset> =
        presets.take(MAX) + List((MAX - presets.size).coerceAtLeast(0)) { AcPreset() }

    private fun JSONObject.bool(key: String): Boolean? = if (has(key) && !isNull(key)) optBoolean(key) else null
    private fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
}
