package dev.debugkit.layers.internal

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pixels back to dp, as the developer wrote them. Layout snaps every edge to a whole pixel, so
 * on densities that are not a whole multiple (2.625, 2.8125, …) 12dp is drawn as 34px, which
 * divides back to 12.09. A value within half a pixel of a whole dp is shown as that whole dp:
 * the pixel grid cannot tell them apart. Anything further off is a real fraction, as centring and
 * weights produce, and keeps one decimal.
 */
internal object DpFormat {

    /** `12`, `0.5`, `76.4`: no unit. */
    fun format(px: Float, density: Float): String {
        val dp = px / density
        val whole = dp.roundToInt()
        // Never snap a visible distance to 0: a 1px hairline is 0.3dp on a 3x screen, not nothing.
        val snaps = abs(px - whole * density) <= SNAP_PX && (whole != 0 || px == 0f)
        if (snaps) return whole.toString()
        return String.format(Locale.US, "%.1f", dp)
    }

    /**
     * Snapping an edge moves it by at most half a pixel. Allowing more would also round off real
     * fractions on whole densities, where nothing is snapped: 49px at 3x really is 16.3dp.
     */
    private const val SNAP_PX = 0.5f
}
