package com.csc8223.posefl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FlClient(private val base: String, private val clientId: String) {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun getModel(): Pair<Int, FloatArray> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("$base/model").build()).execute().use { r ->
            check(r.isSuccessful) { "GET /model failed: HTTP ${r.code}" }
            val o = JSONObject(r.body!!.string())
            val arr = o.getJSONArray("weights")
            check(arr.length() == Mlp.N) { "Expected ${Mlp.N} weights, got ${arr.length()}" }
            o.getInt("round") to FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        }
    }

    suspend fun postUpdate(round: Int, w: FloatArray, n: Int) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("client_id", clientId)
            .put("round", round)
            .put("n_samples", n)
            .put("weights", JSONArray(w.map { it.toDouble() }))
            .toString()
            .toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url("$base/update").post(body).build()).execute().use { r ->
            check(r.isSuccessful) { "POST /update failed: HTTP ${r.code}" }
        }
    }
}