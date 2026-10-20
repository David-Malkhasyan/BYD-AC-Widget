package com.bydmate.app.acbubble

import com.bydmate.app.data.vehicle.CommandTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcBubbleStateTest {

    // The shipped asset: fan-only (ac_flow_only_*) only exists in the competitor list.
    private val allowlist = com.bydmate.app.data.vehicle.WriteAllowlist.loadProduction {
        java.io.File("src/main/assets/competitor-actions.json").readText()
    }

    private val all = listOf(
        AcAction.TogglePower, AcAction.ToggleCompressor, AcAction.ToggleFanOnly,
        AcAction.ToggleAuto, AcAction.ToggleRecirc,
        AcAction.ToggleDefrostFront, AcAction.ToggleDefrostRear,
        AcAction.ToggleDirection(AirDirection.FACE), AcAction.ToggleDirection(AirDirection.FEET),
        AcAction.ToggleDirection(AirDirection.WINDSHIELD),
        AcAction.FanStep(1), AcAction.FanStep(-1), AcAction.TempStep(1), AcAction.TempStep(-1),
    )

    @Test
    fun `every write the bubble sends reaches the car's allowlist, both directions`() {
        val states = listOf(
            AcState(),
            AcState(power = true, auto = true, recircInner = true, defrostFront = true,
                defrostRear = true, fanLevel = 4, tempC = 22, windMode = 3),
        )
        for (s in states) for (a in all) {
            when (val w = AcCommands.write(a, AcCommands.target(a, s))) {
                is AcWrite.Command -> assertTrue("'${w.text}' for $a", CommandTranslator.resolve(w.text).isNotEmpty())
                is AcWrite.Direct -> {
                    val e = allowlist.find(w.action)
                    assertTrue("${w.action} for $a", e != null && w.value in e.valueMin..e.valueMax)
                }
            }
        }
    }

    @Test
    fun `toggles map to the allowlist actions`() {
        fun action(a: AcAction, s: AcState) =
            CommandTranslator.resolve(AcCommands.command(a, AcCommands.target(a, s))).single()
        assertEquals("ac_on", action(AcAction.TogglePower, AcState(power = false)).actionName)
        assertEquals("ac_off", action(AcAction.TogglePower, AcState(power = true)).actionName)
        assertEquals("ac_auto_on", action(AcAction.ToggleAuto, AcState(auto = false)).actionName)
        assertEquals("ac_auto_off", action(AcAction.ToggleAuto, AcState(auto = true)).actionName)
        assertEquals("ac_cycle_inner", action(AcAction.ToggleRecirc, AcState()).actionName)
        assertEquals("ac_cycle_outer", action(AcAction.ToggleRecirc, AcState(recircInner = true)).actionName)
        assertEquals("defrost_rear_on", action(AcAction.ToggleDefrostRear, AcState()).actionName)
    }

    @Test
    fun `compressor writes its allowlist entry directly`() {
        val on = AcCommands.write(AcAction.ToggleCompressor, AcCommands.target(AcAction.ToggleCompressor, AcState()))
        assertEquals(AcWrite.Direct("ac_compressor_on", 1), on)
        val off = AcCommands.write(
            AcAction.ToggleCompressor,
            AcCommands.target(AcAction.ToggleCompressor, AcState(compressor = true)),
        )
        assertEquals(AcWrite.Direct("ac_compressor_off", 0), off)
        val list = com.bydmate.app.data.vehicle.WriteAllowlist.loadProduction { "{}" }
        for (w in listOf(on, off).filterIsInstance<AcWrite.Direct>()) {
            val e = list.find(w.action)!!
            assertEquals(1000, e.dev)
            assertEquals(501219344, e.writeFid)
            assertTrue(w.value in e.valueMin..e.valueMax)
        }
    }

    @Test
    fun `fan only writes the ventilation entry both ways`() {
        val on = AcCommands.write(AcAction.ToggleFanOnly, AcCommands.target(AcAction.ToggleFanOnly, AcState()))
        assertEquals(AcWrite.Direct("ac_flow_only_on", 1), on)
        val off = AcCommands.write(
            AcAction.ToggleFanOnly,
            AcCommands.target(AcAction.ToggleFanOnly, AcState(fanOnly = true)),
        )
        assertEquals(AcWrite.Direct("ac_flow_only_off", 0), off)
    }

    @Test
    fun `non-compressor taps go through the translator`() {
        assertEquals(AcWrite.Command("风量4"), AcCommands.write(AcAction.FanStep(1), AcState(fanLevel = 4)))
    }

    @Test
    fun `fan and temperature steps clamp to the car's range`() {
        val fan = AcCommands.target(AcAction.FanStep(1), AcState(fanLevel = 7))
        assertEquals(7, fan.fanLevel)
        val low = AcCommands.target(AcAction.FanStep(-1), AcState(fanLevel = 1))
        assertEquals(1, low.fanLevel)
        val r = CommandTranslator.resolve(AcCommands.command(AcAction.FanStep(1), AcState(fanLevel = 5))).single()
        assertEquals("ac_wind_level", r.actionName)
        assertEquals(5, r.value)
        val t = CommandTranslator.resolve(AcCommands.command(AcAction.TempStep(1), AcState(tempC = 23))).single()
        assertEquals("ac_temp_main", t.actionName)
        assertEquals(23, t.value)
    }

    @Test
    fun `fan up from off or unknown asks for speed 1, never 0`() {
        assertEquals(1, AcCommands.target(AcAction.FanStep(1), AcState(fanLevel = 0)).fanLevel)
        assertEquals(1, AcCommands.target(AcAction.FanStep(1), AcState()).fanLevel)
        assertEquals(1, AcCommands.target(AcAction.FanStep(-1), AcState(fanLevel = 0)).fanLevel)
        assertEquals(AcWrite.Command("风量1"), AcCommands.write(AcAction.FanStep(1), AcState(fanLevel = 1)))
        assertEquals(21, AcCommands.target(AcAction.TempStep(-1), AcState()).tempC)
    }

    @Test
    fun `fan level 0 is kept as the off reading`() {
        assertEquals(0, AcState.fromFields(mapOf("fanLevel" to 0)).fanLevel)
    }

    @Test
    fun `airflow directions toggle into the car's modes`() {
        val face = AirDirection.FACE
        val feet = AirDirection.FEET
        val glass = AirDirection.WINDSHIELD
        assertEquals(2, AcCommands.toggleDirection(1, feet))   // face + feet
        assertEquals(3, AcCommands.toggleDirection(2, face))   // feet
        assertEquals(4, AcCommands.toggleDirection(3, glass))  // feet + windshield
        assertEquals(5, AcCommands.toggleDirection(4, feet))   // windshield
        assertEquals(1, AcCommands.toggleDirection(2, feet))   // face
    }

    @Test
    fun `face with windshield and all three are real modes`() {
        assertEquals(7, AcCommands.toggleDirection(1, AirDirection.WINDSHIELD))
        assertEquals(6, AcCommands.toggleDirection(2, AirDirection.WINDSHIELD))
        assertEquals(6, AcCommands.toggleDirection(4, AirDirection.FACE))
        assertEquals(4, AcCommands.toggleDirection(6, AirDirection.FACE))
        assertEquals(5, AcCommands.toggleDirection(7, AirDirection.FACE))
    }

    @Test
    fun `the last direction cannot be switched off`() {
        assertEquals(1, AcCommands.toggleDirection(1, AirDirection.FACE))
        assertEquals(5, AcCommands.toggleDirection(5, AirDirection.WINDSHIELD))
        assertEquals(3, AcCommands.toggleDirection(null, AirDirection.FEET))
    }

    @Test
    fun `airflow taps write the wind mode entry directly`() {
        val w = AcCommands.write(AcAction.ToggleDirection(AirDirection.FEET), AcState(windMode = 6))
        assertEquals(AcWrite.Direct("ac_wind_mode", 6), w)
        val e = allowlist.find("ac_wind_mode")!!
        assertEquals(1, e.valueMin)
        assertEquals(7, e.valueMax)
    }

    @Test
    fun `overlay holds a tap until the car reports it`() {
        val o = AcStateOverlay(holdMs = 1000)
        val car = AcState(fanLevel = 2)
        o.request(AcAction.FanStep(1), AcState(fanLevel = 3), nowMs = 0)
        assertEquals(3, o.merge(car, nowMs = 100).fanLevel)
        // Car caught up: the tap is settled and dropped.
        assertEquals(3, o.merge(AcState(fanLevel = 3), nowMs = 200).fanLevel)
        assertEquals(2, o.merge(car, nowMs = 300).fanLevel)
    }

    @Test
    fun `overlay gives the car's value back after the hold expires`() {
        val o = AcStateOverlay(holdMs = 1000)
        o.request(AcAction.TogglePower, AcState(power = true), nowMs = 0)
        assertEquals(true, o.merge(AcState(power = false), nowMs = 999).power)
        assertEquals(false, o.merge(AcState(power = false), nowMs = 1000).power)
    }

    @Test
    fun `a field the firmware lacks keeps the tap as the shown state`() {
        val o = AcStateOverlay(holdMs = 1000)
        val lacking = setOf(AcStateOverlay.Field.DEFROST_REAR)
        assertNull(o.merge(AcState(), nowMs = 0, lacking).defrostRear)
        o.request(AcAction.ToggleDefrostRear, AcState(defrostRear = true), nowMs = 0)
        assertEquals(true, o.merge(AcState(), nowMs = 60_000, lacking).defrostRear)
    }

    @Test
    fun `a field that is merely unanswered expires like any other`() {
        val o = AcStateOverlay(holdMs = 1000)
        o.request(AcAction.ToggleDefrostRear, AcState(defrostRear = true), nowMs = 0)
        assertEquals(true, o.merge(AcState(), nowMs = 999).defrostRear)
        assertNull(o.merge(AcState(), nowMs = 1000).defrostRear)
    }

    @Test
    fun `fromFields decodes the bubble's own read like the poll`() {
        val s = AcState.fromFields(
            mapOf("acStatus" to 1, "acCtrlMode" to 0, "acCirc" to 1, "acDefrostFront" to 0, "fanLevel" to 4,
                "acTemp" to 22, "acWindMode" to 6, "acCompressor" to 1, "acDefrostRear" to 0, "acVentilation" to null),
        )
        assertEquals(AcState(power = true, compressor = true, fanOnly = null, auto = true, recircInner = true,
            defrostFront = false, defrostRear = false, fanLevel = 4, tempC = 22, windMode = 6), s)
        assertNull(AcState.fromFields(mapOf("fanLevel" to 9, "acTemp" to 99)).fanLevel)
        assertNull(AcState.fromFields(mapOf("fanLevel" to 9, "acTemp" to 99)).tempC)
    }

    @Test
    fun `rear defrost the car does report follows the car like any other field`() {
        val o = AcStateOverlay(holdMs = 1000)
        o.request(AcAction.ToggleDefrostRear, AcState(defrostRear = true), nowMs = 0)
        assertEquals(true, o.merge(AcState(defrostRear = false), nowMs = 500).defrostRear)
        assertEquals(false, o.merge(AcState(defrostRear = false), nowMs = 1000).defrostRear)
    }

    @Test
    fun `a change made elsewhere shows once no tap is pending`() {
        val o = AcStateOverlay(holdMs = 1000)
        assertEquals(5, o.merge(AcState(fanLevel = 5), nowMs = 0).fanLevel)
        assertEquals(2, o.merge(AcState(fanLevel = 2), nowMs = 10).fanLevel)
    }
}
