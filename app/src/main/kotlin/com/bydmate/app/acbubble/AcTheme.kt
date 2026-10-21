package com.bydmate.app.acbubble

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.bydmate.app.R

/**
 * One colour scheme for every piece of the bubble: the bubble, the edge tab, the grip and the
 * strip all share [bg]; the foreground colours are chosen to read on it. The opacity setting
 * fades only [bg], never the icons.
 */
@Suppress("LongParameterList") // one colour per role, nothing to group
enum class AcTheme(
    val key: String,
    @StringRes val labelRes: Int,
    /** The glass behind everything. */
    val bg: Color,
    /** Icons, values and the bubble's text. */
    val fg: Color,
    /** Labels under icons, the grip dots, unlit arrows. */
    val dim: Color,
    /** An active control. */
    val on: Color,
    /** Soft wash behind an active button. */
    val glow: Color,
    val divider: Color,
    /** Empty fan bars. */
    val track: Color,
) {
    // The newer BYD dock: near-black glass, white line icons, blue for "on".
    DARK(
        "dark", R.string.ac_bubble_color_dark,
        bg = Color(0xEB0B0F14), fg = Color(0xFFE6E9EE), dim = Color(0xFF8A93A0), on = Color(0xFF3DA5FF),
        glow = Color(0x263DA5FF), divider = Color(0x26FFFFFF), track = Color(0x1AFFFFFF),
    ),
    GRAPHITE(
        "graphite", R.string.ac_bubble_color_graphite,
        bg = Color(0xEB2A3038), fg = Color(0xFFF2F4F7), dim = Color(0xFFA0A8B2), on = Color(0xFF5DB4FF),
        glow = Color(0x2A5DB4FF), divider = Color(0x2EFFFFFF), track = Color(0x24FFFFFF),
    ),
    // The stock car button's blue; "on" is a pale cyan so it still stands out on blue.
    BLUE(
        "blue", R.string.ac_bubble_color_blue,
        bg = Color(0xFF1C4696), fg = Color.White, dim = Color(0xB3FFFFFF), on = Color(0xFF9BE1FF),
        glow = Color(0x33FFFFFF), divider = Color(0x33FFFFFF), track = Color(0x33FFFFFF),
    ),
    STEEL(
        "steel", R.string.ac_bubble_color_steel,
        bg = Color(0xFF3F506B), fg = Color.White, dim = Color(0xFFAEB8C6), on = Color(0xFF8FD3FF),
        glow = Color(0x2A8FD3FF), divider = Color(0x2EFFFFFF), track = Color(0x24FFFFFF),
    ),
    SKY(
        "sky", R.string.ac_bubble_color_sky,
        bg = Color(0xFF9ED0FF), fg = Color(0xFF0E2340), dim = Color(0xFF3F5A7A), on = Color(0xFF0D4FD8),
        glow = Color(0x260D4FD8), divider = Color(0x330E2340), track = Color(0x260E2340),
    ),
    GREY(
        "grey", R.string.ac_bubble_color_grey,
        bg = Color(0xFFD6DBE2), fg = Color(0xFF1A2230), dim = Color(0xFF5A6472), on = Color(0xFF1B5FD6),
        glow = Color(0x261B5FD6), divider = Color(0x22000000), track = Color(0x1F000000),
    ),
    LIGHT(
        "light", R.string.ac_bubble_color_light,
        bg = Color(0xF7F4F6F9), fg = Color(0xFF1A2230), dim = Color(0xFF6B7482), on = Color(0xFF1B5FD6),
        glow = Color(0x1F1B5FD6), divider = Color(0x1A000000), track = Color(0x14000000),
    );

    /** [bg] at the user's opacity. */
    fun glass(opacity: Float): Color = bg.copy(alpha = bg.alpha * opacity)

    companion object {
        val DEFAULT = BLUE
        fun of(key: String?): AcTheme = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
