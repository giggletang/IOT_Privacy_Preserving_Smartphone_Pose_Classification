package com.csc8223.posefl

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class SessionStore(private val ctx: Context) {
    val file get() = File(ctx.filesDir, "windows.csv")

    fun append(s: Sample) { file.appendText(s.toCsv() + System.lineSeparator()) }

    fun readAll(): List<Sample> =
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { Sample.fromCsv(it) }

    fun clear() { file.delete() }

    fun export(c: Context) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(Intent.createChooser(send, "Export CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class ModelStore(private val ctx: Context) {
    private fun f(name: String) = File(ctx.filesDir, "model_$name.txt")

    fun save(name: String, w: FloatArray) { f(name).writeText(w.joinToString(",")) }

    fun load(name: String): Mlp? {
        val file = f(name)
        if (!file.exists()) return null
        return Mlp(file.readText().split(",").map { it.toFloat() }.toFloatArray())
    }
}