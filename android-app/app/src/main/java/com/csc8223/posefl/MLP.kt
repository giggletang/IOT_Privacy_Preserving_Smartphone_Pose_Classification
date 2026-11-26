package com.csc8223.posefl

import java.util.Random
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class Mlp(val w: FloatArray = FloatArray(N)) {
    companion object {
        const val IN = 6; const val H = 16; const val OUT = 6
        const val W1 = 0; const val B1 = 96; const val W2 = 112; const val B2 = 208
        const val N = 214

        fun initWeights(seed: Long): FloatArray {
            val r = Random(seed)
            val w = FloatArray(N)
            val s1 = sqrt(6f / (IN + H)); val s2 = sqrt(6f / (H + OUT))
            for (i in 0 until H * IN) w[W1 + i] = (r.nextFloat() * 2 - 1) * s1
            for (i in 0 until OUT * H) w[W2 + i] = (r.nextFloat() * 2 - 1) * s2
            return w
        }
    }

    private fun hidden(x: FloatArray, z1: FloatArray, h: FloatArray) {
        for (j in 0 until H) {
            var s = w[B1 + j]
            for (i in 0 until IN) s += w[W1 + j * IN + i] * x[i]
            z1[j] = s
            h[j] = if (s > 0f) s else 0f
        }
    }

    private fun probs(h: FloatArray): FloatArray {
        val z = FloatArray(OUT)
        for (k in 0 until OUT) {
            var s = w[B2 + k]
            for (j in 0 until H) s += w[W2 + k * H + j] * h[j]
            z[k] = s
        }
        val m = z.max()
        val e = FloatArray(OUT) { exp(z[it] - m) }
        val sum = e.sum()
        return FloatArray(OUT) { e[it] / sum }
    }

    fun forward(x: FloatArray): FloatArray {
        val z1 = FloatArray(H); val h = FloatArray(H)
        hidden(x, z1, h)
        return probs(h)
    }

    fun predict(x: FloatArray): Int = forward(x).let { p -> p.indices.maxByOrNull { p[it] }!! }

    /** Fills g with the cross-entropy gradient and returns the loss. */
    fun gradient(x: FloatArray, y: Int, g: FloatArray): Float {
        val z1 = FloatArray(H); val h = FloatArray(H)
        hidden(x, z1, h)
        val p = probs(h)
        val dz2 = FloatArray(OUT) { p[it] - if (it == y) 1f else 0f }
        g.fill(0f)
        val dh = FloatArray(H)
        for (k in 0 until OUT) {
            g[B2 + k] = dz2[k]
            for (j in 0 until H) {
                g[W2 + k * H + j] = dz2[k] * h[j]
                dh[j] += w[W2 + k * H + j] * dz2[k]
            }
        }
        for (j in 0 until H) {
            val dz1 = if (z1[j] > 0f) dh[j] else 0f
            g[B1 + j] = dz1
            for (i in 0 until IN) g[W1 + j * IN + i] = dz1 * x[i]
        }
        return -ln(p[y] + 1e-9f)
    }

    fun sgdStep(x: FloatArray, y: Int, lr: Float, g: FloatArray = FloatArray(N)): Float {
        val loss = gradient(x, y, g)
        for (i in 0 until N) w[i] -= lr * g[i]
        return loss
    }
}