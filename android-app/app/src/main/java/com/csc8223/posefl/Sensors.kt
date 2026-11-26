package com.csc8223.posefl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class SensorCollector(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val grav = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var g = FloatArray(3)
    private var lp = FloatArray(3)
    private var seeded = false

    /** (gravity, accel, timestampNanos) */
    var onSample: ((FloatArray, FloatArray, Long) -> Unit)? = null

    fun start() {
        sm.registerListener(this, acc, 20_000)   // 20,000 us = 50 Hz (a hint, not a guarantee)
        grav?.let { sm.registerListener(this, it, 20_000) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GRAVITY -> g = e.values.clone()
            Sensor.TYPE_ACCELEROMETER -> {
                val a = e.values.clone()
                if (grav == null) {   // fallback: low-pass filter the accelerometer
                    if (!seeded) { lp = a.clone(); seeded = true }
                    for (i in 0..2) lp[i] = 0.8f * lp[i] + 0.2f * a[i]
                    g = lp.clone()
                }
                onSample?.invoke(g.clone(), a, e.timestamp)
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
}

/** Collects samples and emits one window per second (non-overlapping, so no leakage). */
class WindowBuilder(private val onWindow: (List<FloatArray>, List<FloatArray>) -> Unit) {
    private val g = ArrayList<FloatArray>()
    private val a = ArrayList<FloatArray>()
    private var start = 0L

    fun reset() { g.clear(); a.clear() }

    fun add(gv: FloatArray, av: FloatArray, t: Long) {
        if (g.isEmpty()) start = t
        g.add(gv); a.add(av)
        if (t - start >= WINDOW_NS) {
            if (g.size >= 20) onWindow(g.toList(), a.toList())  // skip windows with too few samples
            reset()
        }
    }
}