package com.bydmate.app.acbubble

import com.bydmate.app.acbubble.AcStateOverlay.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcPresetTest {

    /** The user's example: fan 2, 25 °C, face, A/C off (heater only). */
    private val heaterFace = AcPreset(enabled = true, name = "Heat", compressor = false, fanLevel = 2, tempC = 25, windMode = 1)

    private val running = AcState(power = true, compressor = true, auto = true, fanLevel = 5, tempC = 21, windMode = 3)

    @Test
    fun `apply sends only the values the button sets, levels after modes`() {
        val plan = AcPresets.apply(heaterFace, running)
        assertEquals(listOf(Field.COMPRESSOR, Field.WIND, Field.FAN, Field.TEMP), plan.fields)
        assertEquals(
            listOf(
                AcWrite.Direct("ac_compressor_off", 0),
                AcWrite.Direct("ac_wind_mode", 1),
                AcWrite.Command("风量2"),
                AcWrite.Command("设置温度25"),
            ),
            plan.steps.map { it.second },
        )
        assertEquals(running.copy(compressor = false, fanLevel = 2, tempC = 25, windMode = 1), plan.target)
    }

    @Test
    fun `power on goes first and power off goes last`() {
        val on = AcPresets.apply(AcPreset(power = true, tempC = 24, auto = true), AcState(power = false))
        assertEquals(listOf(Field.POWER, Field.AUTO, Field.TEMP), on.fields)
        assertEquals(AcWrite.Command("自动空调"), on.steps.first().second)

        val off = AcPresets.apply(AcPreset(power = false, defrostRear = false), running)
        assertEquals(listOf(Field.DEFROST_REAR, Field.POWER), off.fields)
        assertEquals(AcWrite.Command("关闭空调"), off.steps.last().second)
    }

    @Test
    fun `lit only when the car shows every value the button sets`() {
        val applied = AcPresets.apply(heaterFace, running).target
        assertTrue(AcPresets.matches(heaterFace, applied))
        assertFalse(AcPresets.matches(heaterFace, applied.copy(fanLevel = 3)))
        assertFalse(AcPresets.matches(heaterFace, applied.copy(compressor = null)))
        // Off climate: only a "power off" button can be in effect.
        assertFalse(AcPresets.matches(heaterFace, applied.copy(power = false)))
        assertTrue(AcPresets.matches(AcPreset(power = false), AcState(power = false)))
        assertFalse(AcPresets.matches(AcPreset(), running))
    }

    @Test
    fun `second tap puts back the earlier values of the touched fields`() {
        val applied = AcPresets.apply(heaterFace, running).target
        val back = AcPresets.revert(heaterFace, running, applied)!!
        assertEquals(listOf(Field.COMPRESSOR, Field.WIND, Field.FAN, Field.TEMP), back.fields)
        assertEquals(running, back.target)
    }

    @Test
    fun `second tap on a button that switched the climate on switches it off`() {
        val p = AcPreset(power = true, fanLevel = 2)
        val before = AcState(power = false, fanLevel = 4)
        val after = AcPresets.apply(p, before).target
        val back = AcPresets.revert(p, before, after)!!
        assertEquals(listOf(Field.POWER), back.fields)
        assertEquals(AcWrite.Command("关闭空调"), back.steps.single().second)
        // Without a remembered "before" (app restarted) the same holds.
        assertEquals(listOf(Field.POWER), AcPresets.revert(p, null, after)!!.fields)
    }

    @Test
    fun `nothing to put back gives null`() {
        assertNull(AcPresets.revert(heaterFace, null, running))
        assertNull(AcPresets.revert(heaterFace, AcState(), running))
    }

    @Test
    fun `every write a button can send reaches the allowlist`() {
        val allowlist = com.bydmate.app.data.vehicle.WriteAllowlist.loadProduction {
            java.io.File("src/main/assets/competitor-actions.json").readText()
        }
        val full = listOf(true, false).map { b ->
            AcPreset(power = b, compressor = b, auto = b, fanOnly = b, recircInner = b, defrostFront = b, defrostRear = b,
                windMode = if (b) 7 else 6, fanLevel = if (b) 1 else 7, tempC = if (b) 16 else 33)
        }
        full.forEach { p ->
            AcPresets.apply(p, AcState()).steps.forEach { (_, w) ->
                when (w) {
                    is AcWrite.Direct -> {
                        val e = allowlist.find(w.action)
                        assertTrue("${w.action} missing", e != null)
                        assertTrue("${w.action}=${w.value} out of range", w.value in e!!.valueMin..e.valueMax)
                    }
                    is AcWrite.Command -> assertTrue("${w.text} unknown",
                        com.bydmate.app.data.vehicle.CommandTranslator.resolve(w.text).isNotEmpty())
                }
            }
        }
    }

    @Test
    fun `storage round trip keeps four slots and drops bad values`() {
        val list = listOf(heaterFace, AcPreset(), AcPreset(enabled = true, name = "Defrost", defrostFront = true, recircInner = false))
        val decoded = AcPresets.decode(AcPresets.encode(list))
        assertEquals(AcPresets.MAX, decoded.size)
        assertEquals(heaterFace, decoded[0])
        assertEquals(list[2], decoded[2])
        assertEquals(AcPreset(), decoded[3])

        val bad = """[{"enabled":true,"name":"0123456789ABCDEF","fanLevel":9,"tempC":40,"windMode":0,"power":true}]"""
        val p = AcPresets.decode(bad).first()
        assertEquals("0123456789AB", p.name)
        assertNull(p.fanLevel)
        assertNull(p.tempC)
        assertNull(p.windMode)
        assertEquals(true, p.power)
        assertEquals(List(AcPresets.MAX) { AcPreset() }, AcPresets.decode("not json"))
        assertEquals(List(AcPresets.MAX) { AcPreset() }, AcPresets.decode(null))
    }
}
