@file:Suppress("MatchingDeclarationName", "MaxLineLength") // icon kinds and their path data live together

package com.bydmate.app.acbubble

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Climate icons of the strip. */
enum class AcIconKind { SEAT_FACE, SEAT_FEET, SEAT_WINDSHIELD, RECIRC, FRESH_AIR, DEFROST_FRONT, DEFROST_REAR, FAN, POWER }

@Composable
fun AcIcon(kind: AcIconKind, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Icon(AcIconVectors.of(kind), contentDescription = null, tint = color, modifier = modifier.size(size))
}

/**
 * One line set on the 24×24 grid, drawn for this bubble in the style of current EV cabins: a
 * single rounded stroke weight, no fills, so every icon reads the same at a glance.
 */
object AcIconVectors {
    private val cache = mutableMapOf<AcIconKind, ImageVector>()

    fun of(kind: AcIconKind): ImageVector = cache.getOrPut(kind) {
        ImageVector.Builder(
            name = kind.name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            when (kind) {
                // A seated passenger (ISO 2575 style, no seat) with the arrow where the air goes.
                AcIconKind.SEAT_FACE -> { stroke(Paths.PERSON_FACE); fill(Paths.HEAD) }
                AcIconKind.SEAT_FEET -> { stroke(Paths.PERSON_FEET); fill(Paths.HEAD) }
                AcIconKind.SEAT_WINDSHIELD -> { stroke(Paths.PERSON_WINDSHIELD); fill(Paths.HEAD) }
                // Cabin air: the arrow turns back inside the car.
                AcIconKind.RECIRC -> { stroke(Paths.RECIRC_CAR); stroke(Paths.RECIRC_ARROW, INNER_STROKE) }
                // Outside air: the arrow sweeps in through the open roof line.
                AcIconKind.FRESH_AIR -> { stroke(Paths.FRESH_CAR); stroke(Paths.FRESH_ARROW, INNER_STROKE) }
                AcIconKind.DEFROST_FRONT -> stroke(Paths.DEFROST_FRONT)
                AcIconKind.DEFROST_REAR -> stroke(Paths.DEFROST_REAR)
                AcIconKind.FAN -> stroke(Paths.FAN)
                AcIconKind.POWER -> stroke(Paths.POWER)
            }
        }.build()
    }

    private fun ImageVector.Builder.stroke(d: String, width: Float = STROKE) {
        addPath(
            addPathNodes(d), stroke = SolidColor(Color.White), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.fill(d: String) {
        addPath(addPathNodes(d), fill = SolidColor(Color.White))
    }

    private const val STROKE = 1.7f
    /** Arrows drawn inside a car outline, a touch lighter so they stay distinct from the body. */
    private const val INNER_STROKE = 1.4f

    private object Paths {
        const val POWER = "M7.6,7.4 A6.4,6.4 0 1 0 16.4,7.4 M12,4 V11.6"
        const val FAN = "M12.90,10.30 C12.60,7.60 9.20,5.40 10.20,3.40 C11.60,1.60 16.20,3.00 15.60,7.00 Q14.90,9.60 13.90,10.80 M13.70,12.90 C16.40,12.60 18.60,9.20 20.60,10.20 C22.40,11.60 21.00,16.20 17.00,15.60 Q14.40,14.90 13.20,13.90 M11.10,13.70 C11.40,16.40 14.80,18.60 13.80,20.60 C12.40,22.40 7.80,21.00 8.40,17.00 Q9.10,14.40 10.10,13.20 M10.30,11.10 C7.60,11.40 5.40,14.80 3.40,13.80 C1.60,12.40 3.00,7.80 7.00,8.40 Q9.60,9.10 10.80,10.10 M13.6,12 A1.6,1.6 0 1 1 10.4,12 A1.6,1.6 0 1 1 13.6,12"
        const val DEFROST_FRONT = "M2.8,6.8 Q12,3.2 21.2,6.8 M2.8,6.8 L4.8,17 M21.2,6.8 L19.2,17 M8.4,20.2 C7.1000000000000005,19.0 9.700000000000001,16.8 8.4,15.6 C7.1000000000000005,14.4 9.700000000000001,12.3 8.4,11 M7.0,12.5 L8.4,11 L9.8,12.5 M12,20.2 C10.7,19.0 13.3,16.8 12,15.6 C10.7,14.4 13.3,12.3 12,11 M10.6,12.5 L12,11 L13.4,12.5 M15.6,20.2 C14.299999999999999,19.0 16.9,16.8 15.6,15.6 C14.299999999999999,14.4 16.9,12.3 15.6,11 M14.2,12.5 L15.6,11 L17.0,12.5"
        const val DEFROST_REAR = "M5,4.2 H19 Q20.6,4.2 20.6,5.8 V14.4 Q20.6,16 19,16 M5,16 Q3.4,16 3.4,14.4 V5.8 Q3.4,4.2 5,4.2 M8.4,20.4 C7.1000000000000005,19.2 9.700000000000001,15.999999999999998 8.4,14.799999999999999 C7.1000000000000005,13.6 9.700000000000001,10.5 8.4,9.2 M7.0,10.7 L8.4,9.2 L9.8,10.7 M12,20.4 C10.7,19.2 13.3,15.999999999999998 12,14.799999999999999 C10.7,13.6 13.3,10.5 12,9.2 M10.6,10.7 L12,9.2 L13.4,10.7 M15.6,20.4 C14.299999999999999,19.2 16.9,15.999999999999998 15.6,14.799999999999999 C14.299999999999999,13.6 16.9,10.5 15.6,9.2 M14.2,10.7 L15.6,9.2 L17.0,10.7"
        const val HEAD = "M17.3,5 A2.1,2.1 0 1 1 13.1,5 A2.1,2.1 0 1 1 17.3,5"
        const val PERSON_FACE = "M15.2,8.4 C16.4,10.2 16.8,12.2 16.4,14.2 Q16.2,15.2 15.1,15.2 H10.8 Q9.8,15.2 9.8,16.2 V20.6 M1.8,5.2 L10.6,5.2 M8.30,7.20 L10.6,5.2 L8.30,3.20"
        const val PERSON_FEET = "M15.2,8.4 C16.4,10.2 16.8,12.2 16.4,14.2 Q16.2,15.2 15.1,15.2 H10.8 Q9.8,15.2 9.8,16.2 V20.6 M1.8,20 L7.4,20 M5.10,22.00 L7.4,20 L5.10,18.00"
        const val PERSON_WINDSHIELD = "M15.2,8.4 C16.4,10.2 16.8,12.2 16.4,14.2 Q16.2,15.2 15.1,15.2 H10.8 Q9.8,15.2 9.8,16.2 V20.6 M2.2,11.6 L6.4,2.8 M10.2,10.6 L5.8,5.4 M8.81,5.86 L5.8,5.4 L5.76,8.45"
        const val RECIRC_CAR = "M1.6,17 V13.8 Q1.6,12.3 3.1,11.9 L5.8,11.2 L8.8,6.4 Q9.5,5.3 10.8,5.3 H15.6 Q16.9,5.3 17.6,6.4 L20.4,11.2 L21.4,11.6 Q22.4,12 22.4,13.1 V17 M8.2,17.2 A1.9,1.9 0 1 1 4.4,17.2 A1.9,1.9 0 1 1 8.2,17.2 M19.6,17.2 A1.9,1.9 0 1 1 15.8,17.2 A1.9,1.9 0 1 1 19.6,17.2 M8.2,17.2 H15.8"
        const val RECIRC_ARROW = "M7,14.4 H13.6 Q16.2,14.4 16.2,11.9 Q16.2,9.4 13.6,9.4 H11.8 M13.4,7.8 L11.6,9.4 L13.4,11"
        const val FRESH_CAR = "M1.6,17 V13.8 Q1.6,12.3 3.1,11.9 L5.8,11.2 L8.2,7.4 M15.2,5.3 H15.6 Q16.9,5.3 17.6,6.4 L20.4,11.2 L21.4,11.6 Q22.4,12 22.4,13.1 V17 M8.2,17.2 A1.9,1.9 0 1 1 4.4,17.2 A1.9,1.9 0 1 1 8.2,17.2 M19.6,17.2 A1.9,1.9 0 1 1 15.8,17.2 A1.9,1.9 0 1 1 19.6,17.2 M8.2,17.2 H15.8"
        const val FRESH_ARROW = "M4.4,2.8 Q9.4,3 12,7.4 Q13,9.2 13.2,11.4 M11.3,9.8 L13.2,11.8 L15.1,9.8"
    }
}
