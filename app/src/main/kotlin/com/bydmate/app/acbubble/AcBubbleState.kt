package com.bydmate.app.acbubble

import com.bydmate.app.data.remote.DiParsData

/**
 * Climate state as the AC bubble shows it. Every field is nullable: null = the car has not
 * reported it (or, for the rear defrost, there is no read fid at all), and the button then
 * shows a neutral look.
 */
data class AcState(
    val power: Boolean? = null,
    val compressor: Boolean? = null,
    /** Ventilation ("ONLY"): the fan blows with neither cooling nor heating. */
    val fanOnly: Boolean? = null,
    val auto: Boolean? = null,
    val recircInner: Boolean? = null,
    val defrostFront: Boolean? = null,
    val defrostRear: Boolean? = null,
    val fanLevel: Int? = null,
    val tempC: Int? = null,
    val windMode: Int? = null,
) {
    companion object {
        /** Reads the polled snapshot. Compressor and rear defrost are not in it (read separately). */
        fun from(data: DiParsData?): AcState = if (data == null) AcState() else AcState(
            power = data.acStatus?.let { it == 1 },
            auto = data.acCtrlMode?.let { it == 0 },
            recircInner = data.acCirc?.let { it == 1 },
            defrostFront = data.acDefrostFront?.let { it == 1 },
            fanLevel = data.fanLevel?.takeIf { it in AcCommands.FAN_OFF..AcCommands.FAN_MAX },
            tempC = data.acTemp?.takeIf { it in AcCommands.TEMP_MIN..AcCommands.TEMP_MAX },
            windMode = data.acWindMode?.takeIf { it in AcCommands.WIND_MODES },
        )

        /** Builds the state from raw fid values keyed by FidMap field name (the bubble's own read). */
        fun fromFields(v: Map<String, Int?>): AcState = AcState(
            power = v["acStatus"]?.let { it == 1 },
            compressor = v["acCompressor"]?.let { it == 1 },
            fanOnly = v["acVentilation"]?.let { it == 1 },
            auto = v["acCtrlMode"]?.let { it == 0 },
            recircInner = v["acCirc"]?.let { it == 1 },
            defrostFront = v["acDefrostFront"]?.let { it == 1 },
            defrostRear = v["acDefrostRear"]?.let { it == 1 },
            fanLevel = v["fanLevel"]?.takeIf { it in AcCommands.FAN_OFF..AcCommands.FAN_MAX },
            tempC = v["acTemp"]?.takeIf { it in AcCommands.TEMP_MIN..AcCommands.TEMP_MAX },
            windMode = v["acWindMode"]?.takeIf { it in AcCommands.WIND_MODES },
        )
    }
}

/** Where the air goes; the car only takes the combinations in [AcCommands.WIND_MODE_DIRECTIONS]. */
enum class AirDirection { WINDSHIELD, FACE, FEET }

/** One tap on the strip. */
sealed interface AcAction {
    data object TogglePower : AcAction
    data object ToggleCompressor : AcAction
    data object ToggleFanOnly : AcAction
    data object ToggleAuto : AcAction
    data object ToggleRecirc : AcAction
    data object ToggleDefrostFront : AcAction
    data object ToggleDefrostRear : AcAction
    data class ToggleDirection(val direction: AirDirection) : AcAction
    data class FanStep(val delta: Int) : AcAction
    data class TempStep(val delta: Int) : AcAction
}

/**
 * Pure mapping from a tap to the state it asks for and to the command strings
 * [com.bydmate.app.data.vehicle.VehicleApi.dispatch] understands (CommandTranslator table), so
 * the bubble reuses the same write path as voice and automations.
 */
object AcCommands {
    /** The car reports 0 while the fan is off; the first "up" from there asks for speed 1. */
    const val FAN_OFF = 0
    const val FAN_MIN = 1
    const val FAN_MAX = 7
    const val TEMP_MIN = 16
    const val TEMP_MAX = 33

    /** ac_wind_mode values (BYDAutoAcDevice AC_WINDMODE_*), see [WIND_MODE_DIRECTIONS]. */
    val WIND_MODES = 1..7

    /** ac_wind_mode value → the directions it blows to. Every non-empty combination has one. */
    val WIND_MODE_DIRECTIONS: Map<Int, Set<AirDirection>> = mapOf(
        1 to setOf(AirDirection.FACE),
        2 to setOf(AirDirection.FACE, AirDirection.FEET),
        3 to setOf(AirDirection.FEET),
        4 to setOf(AirDirection.FEET, AirDirection.WINDSHIELD),
        5 to setOf(AirDirection.WINDSHIELD),
        6 to setOf(AirDirection.FACE, AirDirection.FEET, AirDirection.WINDSHIELD),
        7 to setOf(AirDirection.FACE, AirDirection.WINDSHIELD),
    )
    private const val DEFAULT_WIND_MODE = 1

    private const val DEFAULT_TEMP = 22

    /** The state [action] asks for, given what is on screen now. */
    fun target(action: AcAction, current: AcState): AcState = when (action) {
        AcAction.TogglePower -> current.copy(power = current.power != true)
        AcAction.ToggleCompressor -> current.copy(compressor = current.compressor != true)
        AcAction.ToggleFanOnly -> current.copy(fanOnly = current.fanOnly != true)
        AcAction.ToggleAuto -> current.copy(auto = current.auto != true)
        AcAction.ToggleRecirc -> current.copy(recircInner = current.recircInner != true)
        AcAction.ToggleDefrostFront -> current.copy(defrostFront = current.defrostFront != true)
        AcAction.ToggleDefrostRear -> current.copy(defrostRear = current.defrostRear != true)
        is AcAction.ToggleDirection -> current.copy(windMode = toggleDirection(current.windMode, action.direction))
        is AcAction.FanStep -> current.copy(
            fanLevel = ((current.fanLevel ?: FAN_OFF) + action.delta).coerceIn(FAN_MIN, FAN_MAX),
        )
        is AcAction.TempStep -> current.copy(
            tempC = ((current.tempC ?: DEFAULT_TEMP) + action.delta).coerceIn(TEMP_MIN, TEMP_MAX),
        )
    }

    fun directionsOf(mode: Int?): Set<AirDirection> = WIND_MODE_DIRECTIONS[mode].orEmpty()

    /**
     * Wind mode after tapping [direction]: it is switched on or off in the current set. Air must go
     * somewhere, so switching off the last direction keeps the mode. Should a set ever have no
     * mode, it becomes the mode that keeps the tapped direction and shares the most of the rest.
     */
    fun toggleDirection(mode: Int?, direction: AirDirection): Int {
        val current = directionsOf(mode)
        val wanted = if (direction in current) current - direction else current + direction
        if (wanted.isEmpty()) return mode ?: DEFAULT_WIND_MODE
        WIND_MODE_DIRECTIONS.entries.firstOrNull { it.value == wanted }?.let { return it.key }
        val adding = direction !in current
        return WIND_MODE_DIRECTIONS.entries
            .filter { (direction in it.value) == adding }
            .maxByOrNull { (it.value intersect wanted).size * 10 - it.value.size }
            ?.key ?: DEFAULT_WIND_MODE
    }

    /** The write that makes the car match [target] for the control [action] touches. */
    fun write(action: AcAction, target: AcState): AcWrite = when (action) {
        // No translator phrase exists for the compressor: it goes straight to its allowlist entry.
        AcAction.ToggleCompressor ->
            if (target.compressor == true) AcWrite.Direct("ac_compressor_on", 1)
            else AcWrite.Direct("ac_compressor_off", 0)
        // AC_VENTILATION_STATE_SET; the translator only knows the "on" phrase.
        AcAction.ToggleFanOnly ->
            if (target.fanOnly == true) AcWrite.Direct("ac_flow_only_on", 1)
            else AcWrite.Direct("ac_flow_only_off", 0)
        // Modes 6 and 7 have no translator phrase, so every airflow write goes to the entry itself.
        is AcAction.ToggleDirection -> AcWrite.Direct("ac_wind_mode", target.windMode ?: DEFAULT_WIND_MODE)
        else -> AcWrite.Command(command(action, target))
    }

    /** Command string that makes the car match [target] for the control [action] touches. */
    @Suppress("CyclomaticComplexMethod") // one flat branch per control
    fun command(action: AcAction, target: AcState): String = when (action) {
        AcAction.TogglePower -> if (target.power == true) "自动空调" else "关闭空调"
        AcAction.ToggleCompressor -> error("compressor has no translator command, see write()")
        AcAction.ToggleFanOnly -> error("fan-only has no translator command for off, see write()")
        AcAction.ToggleAuto -> if (target.auto == true) "空调自动" else "空调手动"
        AcAction.ToggleRecirc -> if (target.recircInner == true) "内循环" else "外循环"
        AcAction.ToggleDefrostFront -> if (target.defrostFront == true) "吹前挡" else "关闭吹前挡"
        AcAction.ToggleDefrostRear -> if (target.defrostRear == true) "后视镜加热" else "关闭后视镜加热"
        is AcAction.ToggleDirection -> error("airflow has no translator command for every mode, see write()")
        is AcAction.FanStep -> "风量${target.fanLevel}"
        is AcAction.TempStep -> "设置温度${target.tempC}"
    }
}

/** How one tap reaches the car. */
sealed interface AcWrite {
    /** A CommandTranslator phrase, sent through VehicleApi.dispatch. */
    data class Command(val text: String) : AcWrite
    /** An allowlist action written as is (no translator phrase for it). */
    data class Direct(val action: String, val value: Int) : AcWrite
}

/**
 * What the bubble shows: the car's last report with the user's recent taps laid over it. The
 * car is polled every few seconds, so without the overlay a tap would look ignored until the
 * next poll. A tapped field keeps its requested value until the car reports that same value or
 * [holdMs] passes — after that the car's own report wins again, so a refused command shows up
 * as the button flipping back.
 */
class AcStateOverlay(private val holdMs: Long = DEFAULT_HOLD_MS) {

    private data class Pending(val state: AcState, val until: Long)

    private val pending = mutableMapOf<Field, Pending>()

    enum class Field { POWER, COMPRESSOR, FAN_ONLY, AUTO, RECIRC, DEFROST_FRONT, DEFROST_REAR, FAN, TEMP, WIND }

    fun request(action: AcAction, target: AcState, nowMs: Long) {
        pending[fieldOf(action)] = Pending(target, nowMs + holdMs)
    }

    /**
     * [car] with every still-pending tap applied. Settled or expired taps are dropped. A tap on a
     * field in [unsupported] (the car answered "no such feature" for its read) is the only truth
     * there is and never expires; a field that is merely unanswered expires like any other.
     */
    fun merge(car: AcState, nowMs: Long, unsupported: Set<Field> = emptySet()): AcState {
        var out = car
        val it = pending.entries.iterator()
        while (it.hasNext()) {
            val (field, p) = it.next()
            val wanted = valueOf(field, p.state)
            val reported = valueOf(field, car)
            if (field !in unsupported && (nowMs >= p.until || reported == wanted)) {
                it.remove()
                continue
            }
            out = withValue(field, out, p.state)
        }
        return out
    }

    private fun fieldOf(action: AcAction): Field = when (action) {
        AcAction.TogglePower -> Field.POWER
        AcAction.ToggleCompressor -> Field.COMPRESSOR
        AcAction.ToggleFanOnly -> Field.FAN_ONLY
        AcAction.ToggleAuto -> Field.AUTO
        AcAction.ToggleRecirc -> Field.RECIRC
        AcAction.ToggleDefrostFront -> Field.DEFROST_FRONT
        AcAction.ToggleDefrostRear -> Field.DEFROST_REAR
        is AcAction.ToggleDirection -> Field.WIND
        is AcAction.FanStep -> Field.FAN
        is AcAction.TempStep -> Field.TEMP
    }

    private fun valueOf(field: Field, s: AcState): Any? = when (field) {
        Field.POWER -> s.power
        Field.COMPRESSOR -> s.compressor
        Field.FAN_ONLY -> s.fanOnly
        Field.AUTO -> s.auto
        Field.RECIRC -> s.recircInner
        Field.DEFROST_FRONT -> s.defrostFront
        Field.DEFROST_REAR -> s.defrostRear
        Field.FAN -> s.fanLevel
        Field.TEMP -> s.tempC
        Field.WIND -> s.windMode
    }

    private fun withValue(field: Field, s: AcState, from: AcState): AcState = when (field) {
        Field.POWER -> s.copy(power = from.power)
        Field.COMPRESSOR -> s.copy(compressor = from.compressor)
        Field.FAN_ONLY -> s.copy(fanOnly = from.fanOnly)
        Field.AUTO -> s.copy(auto = from.auto)
        Field.RECIRC -> s.copy(recircInner = from.recircInner)
        Field.DEFROST_FRONT -> s.copy(defrostFront = from.defrostFront)
        Field.DEFROST_REAR -> s.copy(defrostRear = from.defrostRear)
        Field.FAN -> s.copy(fanLevel = from.fanLevel)
        Field.TEMP -> s.copy(tempC = from.tempC)
        Field.WIND -> s.copy(windMode = from.windMode)
    }

    companion object {
        /** Two to three poll cycles (the base interval is ~3 s). */
        const val DEFAULT_HOLD_MS = 8_000L
    }
}
