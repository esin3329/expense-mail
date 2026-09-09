package com.expensemail.android

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class GmailApiException(message: String) : Exception(message)

class GmailClient(private val tokenProvider: () -> String) {
    companion object {
        private const val baseUrl = "https://gmail.googleapis.com/gmail/v1"
        private const val sendPath = "/users/me/messages/send"
        private const val draftPath = "/users/me/drafts"
    }

    fun send(sender: String, recipient: String, subject: String, body: String, files: List<EvidencePart>): String {
        val raw = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MimeMessage.build(sender, recipient, subject, body, files).toByteArray(StandardCharsets.UTF_8))
        return post(sendPath, JSONObject().put("raw", raw))
    }

    fun createDraft(sender: String, recipient: String, subject: String, body: String, files: List<EvidencePart>): String {
        val raw = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MimeMessage.build(sender, recipient, subject, body, files).toByteArray(StandardCharsets.UTF_8))
        val message = JSONObject().put("message", JSONObject().put("raw", raw))
        return post(draftPath, message)
    }

    fun profileEmail(): String {
        val response = request("GET", "/users/me/profile", null)
        return response.optString("emailAddress").takeIf { it.isNotBlank() }
            ?: throw GmailApiException("Gmail 계정 이메일을 확인할 수 없습니다.")
    }

    private fun post(path: String, payload: JSONObject): String {
        return request("POST", path, payload).optString("id").takeIf { it.isNotBlank() }
            ?: throw GmailApiException("Gmail 결과에 ID가 없습니다. 결과를 확인해 주세요.")
    }

    private fun request(method: String, path: String, payload: JSONObject?): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = payload != null
            setRequestProperty("Authorization", "Bearer ${tokenProvider()}")
            setRequestProperty("Accept", "application/json")
            if (payload != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            if (payload != null) OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(payload.toString()) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.let { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader -> reader.readText() } } ?: ""
            if (responseCode !in 200..299) throw GmailApiException("Gmail 요청이 거부되었습니다. ($responseCode)")
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
