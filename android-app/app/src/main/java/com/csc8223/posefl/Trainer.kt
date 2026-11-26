package com.csc8223.posefl

import java.util.Random

object Trainer {
    /** Per-sample SGD. Returns the mean loss of the last epoch. */
    fun train(m: Mlp, data: List<Pair<FloatArray, Int>>, epochs: Int, lr: Float, seed: Long = 1): Float {
        val rng = Random(seed)
        val g = FloatArray(Mlp.N)
        var last = 0f
        repeat(epochs) {
            var total = 0f
            for (idx in data.indices.shuffled(rng)) {
                val (x, y) = data[idx]
                total += m.sgdStep(x, y, lr, g)
            }
            last = total / data.size
        }
        return last
    }

    fun confusion(m: Mlp, data: List<Pair<FloatArray, Int>>): Array<IntArray> {
        val c = Array(6) { IntArray(6) }
        for ((x, y) in data) c[y][m.predict(x)]++
        return c
    }

    /** Overall accuracy, per-class accuracy, macro-F1 and the confusion matrix as text. */
    fun report(m: Mlp, data: List<Pair<FloatArray, Int>>): String {
        if (data.isEmpty()) return "No test data"
        val c = confusion(m, data)
        val correct = (0 until 6).sumOf { c[it][it] }
        val sb = StringBuilder("Accuracy %.1f%% (%d windows)\n".format(100.0 * correct / data.size, data.size))
        var f1sum = 0.0; var classes = 0
        for (k in 0 until 6) {
            val tp = c[k][k].toDouble()
            val fn = (0 until 6).sumOf { c[k][it] } - tp
            val fp = (0 until 6).sumOf { c[it][k] } - tp
            val support = tp + fn
            if (support > 0) {
                val prec = if (tp + fp > 0) tp / (tp + fp) else 0.0
                val rec = tp / support
                f1sum += if (prec + rec > 0) 2 * prec * rec / (prec + rec) else 0.0
                classes++
                sb.append("  %s: %.0f%%\n".format(POSES[k], 100 * rec))
            }
        }
        sb.append("Macro-F1 %.3f\n".format(if (classes > 0) f1sum / classes else 0.0))
        sb.append("Confusion (rows true, cols predicted):\n")
        c.forEach { sb.append(it.joinToString(" ") { v -> v.toString().padStart(3) }).append('\n') }
        return sb.toString()
    }
}