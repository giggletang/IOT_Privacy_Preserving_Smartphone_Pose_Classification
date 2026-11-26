package com.csc8223.posefl

import kotlin.math.sqrt

data class Sample(
    val session: String, val split: String, val label: Int,
    val am: FloatArray, val gm: FloatArray,      // accel mean, gravity mean
    val amed: FloatArray, val gmed: FloatArray,  // accel median, gravity median
    val astd: Float, val ts: Long
) {
    fun toCsv(): String =
        (listOf(session, split, label) + am.toList() + gm.toList() +
                amed.toList() + gmed.toList() + listOf(astd, ts)).joinToString(",")

    companion object {
        fun fromCsv(line: String): Sample? = try {
            val p = line.split(",")
            fun f(i: Int) = p[i].toFloat()
            fun v(i: Int) = floatArrayOf(f(i), f(i + 1), f(i + 2))
            Sample(p[0], p[1], p[2].toInt(), v(3), v(6), v(9), v(12), f(15), p[16].toLong())
        } catch (e: Exception) { null }
    }
}

object Features {
    private fun mean(x: List<FloatArray>) =
        FloatArray(3) { i -> (x.sumOf { it[i].toDouble() } / x.size).toFloat() }

    private fun median(x: List<FloatArray>) =
        FloatArray(3) { i -> x.map { it[i] }.sorted()[x.size / 2] }

    private fun stdMag(a: List<FloatArray>): Float {
        val m = a.map { sqrt((it[0] * it[0] + it[1] * it[1] + it[2] * it[2]).toDouble()) }
        val mu = m.average()
        return sqrt(m.sumOf { (it - mu) * (it - mu) } / m.size).toFloat()
    }

    fun summarize(g: List<FloatArray>, a: List<FloatArray>,
                  session: String, split: String, label: Int): Sample =
        Sample(session, split, label, mean(a), mean(g), median(a), median(g),
            stdMag(a), System.currentTimeMillis())

    private fun unit(v: FloatArray): FloatArray {
        val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / mag, v[1] / mag, v[2] / mag)
    }

    private fun linear(a: FloatArray, g: FloatArray) =
        floatArrayOf((a[0] - g[0]) / G, (a[1] - g[1]) / G, (a[2] - g[2]) / G)

    /** Returns 6 features, or null if the window is rejected (ROBUST only). */
    fun vector(s: Sample, cfg: FeatureConfig): FloatArray? = when (cfg) {
        FeatureConfig.BASELINE ->
            floatArrayOf(*s.am.map { it / G }.toFloatArray(), *s.gm.map { it / G }.toFloatArray())
        FeatureConfig.GRAV_NORM ->
            floatArrayOf(*unit(s.gm), *linear(s.am, s.gm))
        FeatureConfig.ROBUST ->
            if (s.astd > STD_MAX) null
            else floatArrayOf(*unit(s.gmed), *linear(s.amed, s.gmed))
    }
}