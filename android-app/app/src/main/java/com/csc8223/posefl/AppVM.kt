package com.csc8223.posefl

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = SessionStore(app)
    private val models = ModelStore(app)

    var mode by mutableStateOf("idle")          // idle | record | infer
    var label by mutableStateOf(0)
    var split by mutableStateOf("train")
    var session by mutableStateOf("A_s01")
    var cfg by mutableStateOf(FeatureConfig.GRAV_NORM)
    var count by mutableStateOf(0)
    var summary by mutableStateOf("")
    var gravity by mutableStateOf("-")
    var probs by mutableStateOf(FloatArray(6))
    var pred by mutableStateOf(-1)
    var inferModel by mutableStateOf("global")  // global | local
    var serverUrl by mutableStateOf("http://100.x.y.z:8000")
    var clientId by mutableStateOf("A")
    val log = mutableStateListOf<String>()

    private val collector = SensorCollector(app)
    private val builder = WindowBuilder { g, a -> onWindow(g, a) }
    private var tick = 0

    init {
        collector.onSample = { g, a, t ->
            if (++tick % 10 == 0) gravity = "x=%.1f  y=%.1f  z=%.1f".format(g[0], g[1], g[2])
            if (mode != "idle") builder.add(g, a, t)
        }
        collector.start()
        refreshSummary()
    }

    override fun onCleared() { collector.stop() }

    private fun say(s: String) { viewModelScope.launch(Dispatchers.Main) { log.add(0, s) } }

    private fun onWindow(g: List<FloatArray>, a: List<FloatArray>) {
        val s = Features.summarize(g, a, session, split, label)
        if (mode == "record") {
            store.append(s); count++
        } else if (mode == "infer") {
            val x = Features.vector(s, cfg) ?: return
            val m = models.load(inferModel) ?: run { say("No $inferModel model yet"); return }
            val p = m.forward(x)
            probs = p
            pred = p.indices.maxByOrNull { p[it] }!!
        }
    }

    fun startRecord() { count = 0; builder.reset(); mode = "record" }
    fun startInfer() { builder.reset(); mode = "infer" }
    fun stop() { mode = "idle"; refreshSummary() }

    fun clearData() { store.clear(); refreshSummary() }

    fun refreshSummary() {
        val all = store.readAll()
        summary = if (all.isEmpty()) "No data saved yet" else
            all.groupBy { it.split }.entries.joinToString("\n") { (sp, list) ->
                val per = (0 until 6).joinToString(" ") { k -> list.count { it.label == k }.toString() }
                "$sp: ${list.size} windows  [per class: $per]"
            }
    }

    private fun dataset(sp: String): List<Pair<FloatArray, Int>> =
        store.readAll().filter { it.split == sp }
            .mapNotNull { s -> Features.vector(s, cfg)?.let { it to s.label } }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            try { block() } catch (e: Exception) { say("Error: ${e.message}") }
        }
    }

    /** Independently initialized local-only model: 30 SGD epochs on this device's train windows. */
    fun trainLocal() = work {
        val d = dataset("train")
        if (d.isEmpty()) { say("No train data for ${cfg.name}"); return@work }
        val m = Mlp(Mlp.initWeights(42))
        val loss = Trainer.train(m, d, epochs = 30, lr = 0.05f)
        models.save("local", m.w)
        say("Local model trained on ${d.size} windows, loss %.4f".format(loss))
    }

    /** One FL round: download global, train 3 epochs, upload. */
    fun runRound() = work {
        val d = dataset("train")
        if (d.isEmpty()) { say("No train data for ${cfg.name}"); return@work }
        val client = FlClient(serverUrl, clientId)
        val (round, w) = client.getModel()
        models.save("global", w)
        val m = Mlp(w.copyOf())
        val loss = Trainer.train(m, d, epochs = 3, lr = 0.05f)
        client.postUpdate(round, m.w, d.size)
        say("Round $round uploaded (${d.size} windows, loss %.4f). Wait for the next round, then Refresh global.".format(loss))
    }

    fun refreshGlobal() = work {
        val (round, w) = FlClient(serverUrl, clientId).getModel()
        models.save("global", w)
        say("Global model (server round $round) saved on device")
    }

    fun evaluate(name: String) = work {
        val m = models.load(name) ?: run { say("No $name model"); return@work }
        say("[$name, ${cfg.name}]\n" + Trainer.report(m, dataset("test")))
    }
}