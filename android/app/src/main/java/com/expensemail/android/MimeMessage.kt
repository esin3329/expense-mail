package com.expensemail.android

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

data class EvidencePart(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

object MimeMessage {
    private const val lineLength = 76

    fun build(
        sender: String,
        recipient: String,
        subject: String,
        body: String,
        files: List<EvidencePart>,
    ): String {
        require(files.isNotEmpty()) { "사진을 한 장 이상 첨부해야 합니다." }
        val boundary = "expense_${UUID.randomUUID()}"
        val out = StringBuilder()
            .append("From: ").append(sender).append("\r\n")
        if (recipient.isNotBlank()) out.append("To: ").append(recipient).append("\r\n")
        out.append("Subject: ").append(encodedHeader(subject)).append("\r\n")
            .append("MIME-Version: 1.0\r\n")
            .append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n")
            .append("--").append(boundary).append("\r\n")
            .append("Content-Type: text/plain; charset=UTF-8\r\n")
            .append("Content-Transfer-Encoding: base64\r\n\r\n")
            .append(wrapped(encode(body.toByteArray(StandardCharsets.UTF_8)))).append("\r\n")

        files.forEachIndexed { index, file ->
            require(file.mimeType == "image/jpeg" || file.mimeType == "image/png") { "JPG 또는 PNG만 첨부할 수 있습니다." }
            val extension = if (file.mimeType == "image/png") "png" else "jpg"
            out.append("--").append(boundary).append("\r\n")
                .append("Content-Type: ").append(file.mimeType).append("\r\n")
                .append("Content-Disposition: attachment; filename=\"evidence-").append(index + 1).append('.').append(extension).append("\"\r\n")
                .append("Content-Transfer-Encoding: base64\r\n\r\n")
                .append(wrapped(encode(file.bytes))).append("\r\n")
        }
        out.append("--").append(boundary).append("--\r\n")
        return out.toString()
    }

    private fun encodedHeader(value: String): String =
        "=?UTF-8?B?${encode(value.toByteArray(StandardCharsets.UTF_8))}?="

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun wrapped(value: String): String = value.chunked(lineLength).joinToString("\r\n")
}
