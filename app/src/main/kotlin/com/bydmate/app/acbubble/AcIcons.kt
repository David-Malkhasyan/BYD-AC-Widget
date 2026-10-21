@file:Suppress("MatchingDeclarationName", "MaxLineLength") // icon kinds and their path data live together

package com.bydmate.app.acbubble

import androidx.compose.foundation.layout.Box
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
enum class AcIconKind {
    SEAT_FACE, SEAT_FEET, SEAT_WINDSHIELD, RECIRC, FRESH_AIR, DEFROST_FRONT, DEFROST_REAR, FAN, POWER,
    /** The seated passenger alone, and the three arrows drawn over it by [AirflowIcon]. */
    SEAT, ARROW_WINDSHIELD, ARROW_FACE, ARROW_FEET,
}

@Composable
fun AcIcon(kind: AcIconKind, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Icon(AcIconVectors.of(kind), contentDescription = null, tint = color, modifier = modifier.size(size))
}

/**
 * One icon for the whole airflow: the passenger with all three arrows, each lit in [on] when the
 * air goes there and dimmed in [off] otherwise.
 */
@Composable
fun AirflowIcon(directions: Set<AirDirection>, on: Color, off: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        AcIcon(AcIconKind.SEAT, on, size)
        AcIcon(AcIconKind.ARROW_WINDSHIELD, if (AirDirection.WINDSHIELD in directions) on else off, size)
        AcIcon(AcIconKind.ARROW_FACE, if (AirDirection.FACE in directions) on else off, size)
        AcIcon(AcIconKind.ARROW_FEET, if (AirDirection.FEET in directions) on else off, size)
    }
}

/**
 * One line set on the 24×24 grid, drawn for this bubble in the style of current EV cabins: a
 * single rounded stroke weight, one filled arrowhead, so every icon reads the same at a glance.
 */
object AcIconVectors {
    private val cache = mutableMapOf<AcIconKind, ImageVector>()

    fun of(kind: AcIconKind): ImageVector = cache.getOrPut(kind) {
        ImageVector.Builder(
            name = kind.name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            when (kind) {
                // A seated passenger (ISO 2575 style, no seat) with a bold arrow where the air goes.
                AcIconKind.SEAT_FACE -> { person(); arrow(Paths.FACE_SHAFT, Paths.FACE_HEAD) }
                AcIconKind.SEAT_FEET -> { person(); arrow(Paths.FEET_SHAFT, Paths.FEET_HEAD) }
                AcIconKind.SEAT_WINDSHIELD -> { person(); arrow(Paths.WINDSHIELD_SHAFT, Paths.WINDSHIELD_HEAD) }
                // Cabin air: a loop inside the car.
                AcIconKind.RECIRC -> { stroke(Paths.RECIRC_CAR); arrow(Paths.RECIRC_LOOP, Paths.RECIRC_HEAD) }
                // Outside air: the arrow sweeps in through the open roof line.
                AcIconKind.FRESH_AIR -> { stroke(Paths.FRESH_CAR); arrow(Paths.FRESH_ARROW, Paths.FRESH_HEAD) }
                AcIconKind.DEFROST_FRONT -> { stroke(Paths.WINDSHIELD); arrow(Paths.DEFROST_FRONT_SHAFTS, Paths.DEFROST_FRONT_HEADS) }
                AcIconKind.DEFROST_REAR -> { stroke(Paths.REAR_GLASS); arrow(Paths.DEFROST_REAR_SHAFTS, Paths.DEFROST_REAR_HEADS) }
                AcIconKind.FAN -> stroke(Paths.FAN)
                AcIconKind.POWER -> stroke(Paths.POWER)
                AcIconKind.SEAT, AcIconKind.ARROW_WINDSHIELD, AcIconKind.ARROW_FACE, AcIconKind.ARROW_FEET ->
                    airflowLayer(kind)
            }
        }.build()
    }

    private fun ImageVector.Builder.person() { stroke(Paths.PERSON_BODY); fill(Paths.HEAD) }

    /** The layers of [AirflowIcon]: the passenger, then one arrow each. */
    private fun ImageVector.Builder.airflowLayer(kind: AcIconKind) = when (kind) {
        AcIconKind.ARROW_WINDSHIELD -> arrow(Paths.WINDSHIELD_SHAFT, Paths.WINDSHIELD_HEAD)
        AcIconKind.ARROW_FACE -> arrow(Paths.FACE_SHAFT, Paths.FACE_HEAD)
        AcIconKind.ARROW_FEET -> arrow(Paths.FEET_SHAFT, Paths.FEET_HEAD)
        else -> person()
    }

    /** Every arrow in the set: a stroked shaft with the same filled triangular head. */
    private fun ImageVector.Builder.arrow(shaft: String, head: String) {
        stroke(shaft)
        fill(head)
    }

    private fun ImageVector.Builder.stroke(d: String) {
        addPath(
            addPathNodes(d), stroke = SolidColor(Color.White), strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.fill(d: String) {
        addPath(addPathNodes(d), fill = SolidColor(Color.White))
    }

    /** One line weight for the whole set, rounded ends and joins. */
    private const val STROKE = 1.9f

    private object Paths {
        const val POWER = "M7.6,7.4 A6.4,6.4 0 1 0 16.4,7.4 M12,4 V11.6"
        const val FAN = "M12.90,10.30 C12.60,7.60 9.20,5.40 10.20,3.40 C11.60,1.60 16.20,3.00 15.60,7.00 Q14.90,9.60 13.90,10.80 M13.70,12.90 C16.40,12.60 18.60,9.20 20.60,10.20 C22.40,11.60 21.00,16.20 17.00,15.60 Q14.40,14.90 13.20,13.90 M11.10,13.70 C11.40,16.40 14.80,18.60 13.80,20.60 C12.40,22.40 7.80,21.00 8.40,17.00 Q9.10,14.40 10.10,13.20 M10.30,11.10 C7.60,11.40 5.40,14.80 3.40,13.80 C1.60,12.40 3.00,7.80 7.00,8.40 Q9.60,9.10 10.80,10.10 M13.6,12 A1.6,1.6 0 1 1 10.4,12 A1.6,1.6 0 1 1 13.6,12"
        /** The seated passenger: a filled head over a line torso, thigh and shin. */
        const val PERSON_BODY = "M15.4,9.2 C16.5,10.8 16.8,12.4 16.4,14.2 Q16.2,15.2 15.1,15.2 H11 Q10,15.2 10,16.2 V20.4"
        const val HEAD = "M17.3,5 A2.1,2.1 0 1 1 13.1,5 A2.1,2.1 0 1 1 17.3,5"
        // Three identical straight arrows fanning out from one line on the left, kept apart so the combined icon stays legible:
        // up for the windshield, rising to the face, falling to the feet.
        const val FACE_SHAFT = "M2.8,12 L6.09,10.35"
        const val FACE_HEAD = "M8.69,9.05 L6.99,11.91 L5.19,8.79 Z"
        const val FEET_SHAFT = "M2.8,15.4 L5.71,17.84"
        const val FEET_HEAD = "M8.01,19.77 L4.55,19.22 L6.87,16.46 Z"
        const val WINDSHIELD_SHAFT = "M2.8,8.6 L2.8,4.8"
        const val WINDSHIELD_HEAD = "M2.8,1.8 L4.6,4.8 L1,4.8 Z"
        const val RECIRC_CAR = "M1.2,16.4 V14.4 C1.2,13 2.1,12.2 3.4,12 L7.4,11.4 C9.2,8.2 11.4,6.6 14.2,6.5 C17.4,6.4 19.6,8 21,11.2 L22.2,11.8 C22.7,12.2 22.8,12.9 22.8,13.8 V16.4 M1.2,16.4 H3.2 A2.3,2.3 0 0 0 7.8,16.4 H16.2 A2.3,2.3 0 0 0 20.8,16.4 H22.8"
        const val RECIRC_LOOP = "M16.12,10.29 A2.5,2.5 0 1 1 12.77,9.85"
        const val RECIRC_HEAD = "M15.22,8.13 L11.74,8.46 L13.8,11.24 Z"
        const val FRESH_CAR = "M1.2,16.4 V14.4 C1.2,13 2.1,12.2 3.4,12 L7.4,11.4 C8.5,9.5 9.8,8.1 11.4,7.3 M16.2,6.7 C18.5,7.3 20,8.8 21,11.2 L22.2,11.8 C22.7,12.2 22.8,12.9 22.8,13.8 V16.4 M1.2,16.4 H3.2 A2.3,2.3 0 0 0 7.8,16.4 H16.2 A2.3,2.3 0 0 0 20.8,16.4 H22.8"
        const val FRESH_HEAD = "M14.06,13.8 L15.4,10.56 L11.84,11.1 Z"
        const val FRESH_ARROW = "M6.6,1.8 Q11.6,2.4 13.4,8.6 Q13.7,10 13.61,10.83"
        const val WINDSHIELD = "M2.8,6.8 Q12,3.2 21.2,6.8 M2.8,6.8 L4.8,17 M21.2,6.8 L19.2,17"
        const val DEFROST_FRONT_SHAFTS = "M8.4,20.2 C7.1,19 9.7,17.6 8.4,16.4 C7.1,15.2 9.7,13.8 8.4,12.6 M12,20.2 C10.7,19 13.3,17.6 12,16.4 C10.7,15.2 13.3,13.8 12,12.6 M15.6,20.2 C14.3,19 16.9,17.6 15.6,16.4 C14.3,15.2 16.9,13.8 15.6,12.6"
        const val DEFROST_FRONT_HEADS = "M8.4,10.2 L9.9,12.8 L6.9,12.8 Z M12,10.2 L13.5,12.8 L10.5,12.8 Z M15.6,10.2 L17.1,12.8 L14.1,12.8 Z"
        const val REAR_GLASS = "M5,4.2 H19 Q20.6,4.2 20.6,5.8 V14.4 Q20.6,16 19,16 M5,16 Q3.4,16 3.4,14.4 V5.8 Q3.4,4.2 5,4.2"
        const val DEFROST_REAR_SHAFTS = "M8.4,20.4 C7.1,19.2 9.7,16.8 8.4,15.6 C7.1,14.4 9.7,12 8.4,10.8 M12,20.4 C10.7,19.2 13.3,16.8 12,15.6 C10.7,14.4 13.3,12 12,10.8 M15.6,20.4 C14.3,19.2 16.9,16.8 15.6,15.6 C14.3,14.4 16.9,12 15.6,10.8"
        const val DEFROST_REAR_HEADS = "M8.4,8.4 L9.9,11 L6.9,11 Z M12,8.4 L13.5,11 L10.5,11 Z M15.6,8.4 L17.1,11 L14.1,11 Z"
    }
}
