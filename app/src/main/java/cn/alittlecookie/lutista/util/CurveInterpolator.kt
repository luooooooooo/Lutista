package cn.alittlecookie.lutista.util

import cn.alittlecookie.lutista.data.model.FloatPoint

/**
 * Natural cubic spline interpolation — Numerical Recipes algorithm.
 * Same as GIMP/darktable/Photoshop curve tools.
 *
 * Includes strict NaN/overflow protection at every stage.
 */
object CurveInterpolator {

    fun interpolate(points: List<FloatPoint>, x: Float): Float {
        if (points.isEmpty()) return x
        if (points.size == 1) return points[0].y.coerceIn(0f, 1f)
        if (x <= points.first().x) return points.first().y.coerceIn(0f, 1f)
        if (x >= points.last().x) return points.last().y.coerceIn(0f, 1f)

        // 2 points → linear
        if (points.size == 2) {
            val w = points[1].x - points[0].x
            if (w == 0f) return points[0].y.coerceIn(0f, 1f)
            val y = points[0].y + ((x - points[0].x) / w) * (points[1].y - points[0].y)
            return y.coerceIn(0f, 1f)
        }

        val n = points.size - 1
        val xs = FloatArray(points.size) { points[it].x }
        val ys = FloatArray(points.size) { points[it].y }

        // Compute second derivatives (Natural Cubic Spline — Numerical Recipes)
        val y2 = FloatArray(n + 1)
        val u = FloatArray(n)

        y2[0] = 0f
        u[0] = 0f

        for (i in 1 until n) {
            val h_prev = xs[i] - xs[i - 1]
            val h_next = xs[i + 1] - xs[i]
            val h_sum = h_prev + h_next
            if (h_sum == 0f) continue  // skip degenerate segments
            val sig = h_prev / h_sum
            val p = sig * y2[i - 1] + 2f
            if (p == 0f) continue  // avoid division by zero
            y2[i] = (sig - 1f) / p
            val d = 6f * ((ys[i + 1] - ys[i]) / h_next - (ys[i] - ys[i - 1]) / h_prev) / h_sum
            u[i] = (d - sig * u[i - 1]) / p
        }

        y2[n] = 0f

        for (k in n - 1 downTo 0) {
            y2[k] = y2[k] * y2[k + 1] + u[k]
        }

        // Find segment
        var seg = 0
        for (i in 0 until n) {
            if (x >= xs[i] && x <= xs[i + 1]) { seg = i; break }
        }

        val h = xs[seg + 1] - xs[seg]
        if (h == 0f) return ys[seg].coerceIn(0f, 1f)
        val a = (xs[seg + 1] - x) / h
        val b = (x - xs[seg]) / h

        val cubic = ((a * a * a - a) * y2[seg] + (b * b * b - b) * y2[seg + 1]) * h * h / 6f
        val y = a * ys[seg] + b * ys[seg + 1] + cubic

        // Strict clamp — never return NaN or out-of-range
        return when {
            y.isNaN() -> (a * ys[seg] + b * ys[seg + 1]).coerceIn(0f, 1f)  // fallback to linear
            else -> y.coerceIn(0f, 1f)
        }
    }
}
