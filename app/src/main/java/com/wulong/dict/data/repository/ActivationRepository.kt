package com.wulong.dict.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ActivationRepository(private val serverUrl: String) {

    data class ActivateResult(val ok: Boolean, val msg: String)

    suspend fun activate(inviteNo: String, code: String, deviceId: String): ActivateResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("invite_no", inviteNo)
                    put("code", code)
                    put("device_id", deviceId)
                }
                val conn = URL("$serverUrl/activate").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val httpCode = conn.responseCode
                val raw = if (httpCode in 200..299)
                    conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                else
                    conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "{}"

                val json = JSONObject(raw)
                ActivateResult(json.optBoolean("ok"), json.optString("msg", "密钥无效，请联系管理员"))
            } catch (e: Exception) {
                ActivateResult(false, "无法连接服务器，请检查网络后重试")
            }
        }
}
