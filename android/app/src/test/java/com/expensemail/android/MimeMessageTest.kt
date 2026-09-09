package com.expensemail.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class MimeMessageTest {
    private val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a)

    @Test
    fun draftOmitsRecipientButKeepsKoreanTextAndBytes() {
        val mime = MimeMessage.build("owner@gmail.com", "", "8월 출장비 증빙자료", "8월 출장비 증빙자료입니다.", listOf(EvidencePart("evidence.png", "image/png", bytes)))
        assertFalse(Regex("(?m)^To:").containsMatchIn(mime))
        assertTrue(mime.contains("Subject: =?UTF-8?B?"))
        val encoded = Base64.getEncoder().encodeToString(bytes)
        assertTrue(mime.contains(encoded))
    }

    @Test
    fun sendIncludesExactlyOneRecipientAndOriginalAttachment() {
        val mime = MimeMessage.build("owner@gmail.com", "finance@example.com", "정산", "내용", listOf(EvidencePart("evidence.png", "image/png", bytes)))
        assertEquals(1, Regex("(?m)^To:").findAll(mime).count())
        val subject = Regex("Subject: =\\?UTF-8\\?B\\?([^?]+)\\?=").find(mime)!!.groupValues[1]
        assertEquals("정산", String(Base64.getDecoder().decode(subject), StandardCharsets.UTF_8))
        assertTrue(mime.contains(Base64.getEncoder().encodeToString(bytes)))
    }
}
