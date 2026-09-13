package com.expensemail.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val requestCamera = 1001
        private const val requestGallery = 1002
        private const val requestAuth = 1003
        private const val maxFiles = 30
        private const val maxBytes = 18_000_000L
        private const val gmailSendScope = "https://www.googleapis.com/auth/gmail.send"
        private const val gmailComposeScope = "https://www.googleapis.com/auth/gmail.compose"
    }

    private data class LocalAttachment(val uri: Uri, val name: String, val mimeType: String, val size: Long, val hash: String)
    private data class Submission(val month: String, val recipient: String, val subject: String, val body: String, val files: List<LocalAttachment>)

    private lateinit var cameraButton: Button
    private lateinit var galleryButton: Button
    private lateinit var submitButton: Button
    private lateinit var monthInput: EditText
    private lateinit var recipientInput: EditText
    private lateinit var subjectInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var attachmentSummary: TextView
    private lateinit var attachmentList: LinearLayout
    private lateinit var gmailStatus: TextView
    private lateinit var statusText: TextView
    private lateinit var resetButton: Button

    private val executor = Executors.newSingleThreadExecutor()
    private val preferences by lazy { getSharedPreferences("expense_mail", MODE_PRIVATE) }
    private val attachments = mutableListOf<LocalAttachment>()
    private var cameraUri: Uri? = null
    private var reading = false
    private var inFlight = false
    private var uncertain = false
    @Volatile private var networkStarted = false
    private var pendingSubmission: Submission? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraButton = findViewById(R.id.cameraButton)
        galleryButton = findViewById(R.id.galleryButton)
        submitButton = findViewById(R.id.submitButton)
        monthInput = findViewById(R.id.monthInput)
        recipientInput = findViewById(R.id.recipientInput)
        subjectInput = findViewById(R.id.subjectInput)
        bodyInput = findViewById(R.id.bodyInput)
        attachmentSummary = findViewById(R.id.attachmentSummary)
        attachmentList = findViewById(R.id.attachmentList)
        gmailStatus = findViewById(R.id.gmailStatus)
        statusText = findViewById(R.id.statusText)
        resetButton = findViewById(R.id.resetButton)
        val rootScroll = findViewById<View>(R.id.rootScroll)
        val baseLeft = rootScroll.paddingLeft
        val baseTop = rootScroll.paddingTop
        val baseRight = rootScroll.paddingRight
        val baseBottom = rootScroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(baseLeft, baseTop + bars.top, baseRight, baseBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(rootScroll)

        val month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date())
        monthInput.setText(month)
        recipientInput.setText(preferences.getString("recipient", "") ?: "")
        subjectInput.setText("${month.substring(5)}월 출장비 증빙자료")
        bodyInput.setText("${month.substring(5)}월 출장비 증빙자료입니다.")
        if (preferences.getBoolean("attempt_in_flight", false)) {
            uncertain = true
            setStatus("이전 Gmail 작업 결과가 확인되지 않았습니다. 보낸편지함 또는 임시보관함을 확인한 뒤 새로 시작해 주세요.")
        }
        updateSubmitLabel()

        cameraButton.setOnClickListener { openCamera() }
        galleryButton.setOnClickListener { openGallery() }
        submitButton.setOnClickListener { validateAndReview() }
        resetButton.setOnClickListener {
            uncertain = false
            preferences.edit().putBoolean("attempt_in_flight", false).apply()
            setStatus("새 작업을 시작할 수 있습니다.")
            updateUi()
        }
        recipientInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateSubmitLabel()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateUi()
    }

    private fun openCamera() {
        val directory = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "evidence_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) == null) {
            setStatus("카메라 앱을 찾을 수 없습니다.")
            return
        }
        startActivityForResult(intent, requestCamera)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
        }
        startActivityForResult(intent, requestGallery)
    }

    @Deprecated("Legacy activity result is used to keep the APK dependency-light.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            requestCamera -> {
                val uri = cameraUri
                cameraUri = null
                if (resultCode == RESULT_OK && uri != null) ingestUris(listOf(uri))
            }
            requestGallery -> if (resultCode == RESULT_OK && data != null) ingestUris(extractUris(data))
            requestAuth -> {
                if (resultCode != RESULT_OK || data == null) {
                    cancelPending("Gmail 권한 승인이 취소되었습니다.")
                } else {
                    try {
                        val result = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
                        if (result.hasResolution()) launchAuthorizationResolution(result)
                        else executePending(result.getAccessToken())
                    } catch (error: Exception) {
                        cancelPending(authorizationFailure(error))
                    }
                }
            }
        }
    }

    private fun extractUris(data: Intent): List<Uri> {
        val result = mutableListOf<Uri>()
        val clipData = data.clipData
        if (clipData != null) for (index in 0 until clipData.itemCount) result += clipData.getItemAt(index).uri
        data.data?.let { if (result.none { existing -> existing == it }) result += it }
        return result.distinct()
    }

    private fun ingestUris(uris: List<Uri>) {
        if (uris.isEmpty() || reading || inFlight || uncertain) return
        reading = true
        updateUi()
        setStatus("사진을 확인하고 있습니다.")
        executor.execute {
            val additions = mutableListOf<LocalAttachment>()
            val errors = mutableListOf<String>()
            var total = attachments.sumOf { it.size }
            for (uri in uris.distinct()) {
                if (attachments.size + additions.size >= maxFiles) {
                    errors += "사진은 최대 ${maxFiles}장까지 선택할 수 있습니다."
                    break
                }
                try {
                    val mimeType = normalizedMime(uri)
                    if (mimeType == null) throw IllegalArgumentException("JPG 또는 PNG 사진만 선택할 수 있습니다.")
                    val (size, hash) = hashUri(uri, mimeType)
                    if (size <= 0) throw IllegalArgumentException("빈 사진 파일입니다.")
                    if (total + size > maxBytes) throw IllegalArgumentException("전체 18 MB까지 첨부할 수 있습니다.")
                    if (attachments.any { it.hash == hash } || additions.any { it.hash == hash }) continue
                    additions += LocalAttachment(uri, displayName(uri), mimeType, size, hash)
                    total += size
                } catch (error: Exception) {
                    errors += "${displayName(uri)}: ${error.message ?: "사진을 읽을 수 없습니다."}"
                }
            }
            runOnUiThread {
                reading = false
                attachments += additions
                renderAttachments()
                updateUi()
                setStatus(if (errors.isEmpty()) "사진 ${attachments.size}장이 준비되었습니다." else errors.joinToString("\n"))
            }
        }
    }

    private fun hashUri(uri: Uri, mimeType: String): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(8)
        var headerBytes = 0
        var size = 0L
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (headerBytes < header.size) {
                    val copied = minOf(read, header.size - headerBytes)
                    buffer.copyInto(header, headerBytes, 0, copied)
                    headerBytes += copied
                }
                size += read
                if (size > maxBytes) throw IllegalArgumentException("사진은 전체 18 MB까지 첨부할 수 있습니다.")
                digest.update(buffer, 0, read)
            }
        } ?: throw IllegalArgumentException("사진을 열 수 없습니다.")
        val valid = if (mimeType == "image/png") {
            header.contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        } else {
            headerBytes >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte()
        }
        if (!valid) throw IllegalArgumentException("사진 형식이 올바르지 않습니다. JPG 또는 PNG 원본을 선택해 주세요.")
        return size to digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun normalizedMime(uri: Uri): String? {
        val fromResolver = contentResolver.getType(uri)?.lowercase(Locale.ROOT)
        if (fromResolver == "image/jpeg" || fromResolver == "image/jpg") return "image/jpeg"
        if (fromResolver == "image/png") return "image/png"
        val name = displayName(uri).lowercase(Locale.ROOT)
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            else -> null
        }
    }

    private fun displayName(uri: Uri): String {
        var name: String? = null
        val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use { if (it.moveToFirst()) name = it.getString(0) }
        return name?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "evidence.jpg")
    }

    private fun renderAttachments() {
        attachmentList.removeAllViews()
        attachments.forEachIndexed { index, attachment ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val name = TextView(this).apply {
                text = "${attachment.name} (${attachment.size / 1_000_000.0f} MB)"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val remove = Button(this).apply {
                text = "삭제"
                isAllCaps = false
                setOnClickListener {
                    if (!inFlight && !reading && !uncertain) {
                        attachments.removeAt(index)
                        renderAttachments()
                        updateUi()
                    }
                }
            }
            row.addView(name)
            row.addView(remove)
            attachmentList.addView(row)
        }
        attachmentSummary.text = if (attachments.isEmpty()) "사진을 아직 선택하지 않았습니다." else "사진 ${attachments.size}장 · ${attachments.sumOf { it.size } / 1_000_000.0f} MB / 18 MB"
    }

    private fun validateAndReview() {
        if (inFlight || reading || uncertain) return
        if (attachments.isEmpty()) {
            setStatus("사진을 먼저 선택해 주세요.")
            return
        }
        val month = monthInput.text.toString().trim()
        val recipient = recipientInput.text.toString().trim()
        val subject = subjectInput.text.toString().trim()
        val body = bodyInput.text.toString()
        if (!Regex("^20\\d{2}-(0[1-9]|1[0-2])$").matches(month)) {
            monthInput.error = "YYYY-MM 형식으로 입력해 주세요."
            return
        }
        if (recipient.isNotEmpty() && (!Patterns.EMAIL_ADDRESS.matcher(recipient).matches() || recipient.contains(','))) {
            recipientInput.error = "이메일 주소 한 개를 입력해 주세요."
            return
        }
        if (subject.isBlank()) { subjectInput.error = "제목을 입력해 주세요."; return }
        if (body.isBlank()) { bodyInput.error = "내용을 입력해 주세요."; return }
        preferences.edit().putString("recipient", recipient).apply()
        val submission = Submission(month, recipient, subject, body, attachments.toList())
        val automatic = recipient.isNotEmpty()
        val detail = buildString {
            append("정산 월: ").append(month).append("\n")
            append("첨부: ").append(attachments.size).append("장\n")
            append(if (automatic) "등록된 이메일이 있어 Gmail로 자동 발송합니다." else "받는 이메일이 없어 Gmail 임시보관함에만 저장합니다. 발송하지 않습니다.")
        }
        AlertDialog.Builder(this)
            .setTitle(if (automatic) "Gmail로 자동 발송할까요?" else "테스트 초안을 저장할까요?")
            .setMessage(detail)
            .setNegativeButton("돌아가기", null)
            .setPositiveButton(if (automatic) "확인하고 발송" else "초안 저장") { _, _ ->
                pendingSubmission = submission
                beginGoogleFlow()
            }
            .show()
    }

    private fun beginGoogleFlow() {
        if (pendingSubmission == null || inFlight || uncertain) return
        inFlight = true
        networkStarted = false
        updateUi()
        val requestedScopes = if (pendingSubmission?.recipient?.isBlank() == true) {
            listOf(Scope(gmailComposeScope))
        } else {
            listOf(Scope(gmailSendScope))
        }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) launchAuthorizationResolution(result)
                else executePending(result.getAccessToken())
            }
            .addOnFailureListener { error -> cancelPending(authorizationFailure(error)) }
    }

    private fun authorizationFailure(error: Exception): String {
        return when ((error as? ApiException)?.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR -> "Google 앱 등록 정보가 맞지 않습니다. Google Cloud에 패키지명 com.expensemail.android와 release APK의 SHA-1을 등록해 주세요."
            CommonStatusCodes.NETWORK_ERROR -> "인터넷 연결을 확인한 후 다시 시도해 주세요."
            else -> "Google 계정 또는 Gmail 권한을 확인할 수 없습니다."
        }
    }

    private fun launchAuthorizationResolution(result: AuthorizationResult) {
        val pendingIntent = result.pendingIntent
        if (pendingIntent == null) {
            cancelPending("Gmail 권한 요청을 시작할 수 없습니다.")
            return
        }
        try {
            startIntentSenderForResult(pendingIntent.intentSender, requestAuth, null, 0, 0, 0)
        } catch (error: Exception) {
            cancelPending("Gmail 권한 요청을 시작할 수 없습니다.")
        }
    }

    private fun executePending(accessToken: String?) {
        val submission = pendingSubmission ?: return
        if (accessToken.isNullOrBlank()) {
            cancelPending("Gmail 액세스 권한을 받지 못했습니다.")
            return
        }
        executor.execute {
            try {
                val client = GmailClient { accessToken }
                val sender = client.profileEmail()
                val parts = submission.files.map { attachment ->
                    val bytes = contentResolver.openInputStream(attachment.uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("${attachment.name}을 읽을 수 없습니다.")
                    EvidencePart(attachment.name, attachment.mimeType, bytes)
                }
                networkStarted = true
                preferences.edit().putBoolean("attempt_in_flight", true).apply()
                val id = if (submission.recipient.isBlank()) {
                    client.createDraft(sender, submission.recipient, submission.subject, submission.body, parts)
                } else {
                    client.send(sender, submission.recipient, submission.subject, submission.body, parts)
                }
                runOnUiThread {
                    inFlight = false
                    pendingSubmission = null
                    networkStarted = false
                    preferences.edit().putBoolean("attempt_in_flight", false).apply()
                    if (submission.recipient.isBlank()) {
                        setStatus("테스트 초안을 Gmail 임시보관함에 저장했습니다. 발송되지 않았습니다. ($id)")
                    } else {
                        attachments.clear()
                        renderAttachments()
                        setStatus("${submission.recipient}으로 Gmail 자동 발송했습니다. ($id)")
                    }
                    updateUi()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    uncertain = networkStarted
                    inFlight = false
                    pendingSubmission = null
                    networkStarted = false
                    setStatus(if (uncertain) "Gmail 결과를 확인할 수 없습니다. 보낸편지함 또는 임시보관함을 확인한 뒤 앱을 다시 시작해 주세요. 자동 재시도하지 않습니다." else (error.message ?: "Gmail 연결에 실패했습니다."))
                    updateUi()
                }
            }
        }
    }

    private fun cancelPending(message: String) {
        inFlight = false
        pendingSubmission = null
        networkStarted = false
        setStatus(message)
        updateUi()
    }

    private fun updateSubmitLabel() {
        if (::submitButton.isInitialized && !inFlight) submitButton.text = if (recipientInput.text.toString().trim().isEmpty()) "테스트 초안 저장" else "이메일 있으면 자동 발송"
    }

    private fun updateUi() {
        val locked = reading || inFlight || uncertain
        cameraButton.isEnabled = !locked
        galleryButton.isEnabled = !locked
        submitButton.isEnabled = !locked && attachments.isNotEmpty()
        resetButton.visibility = if (uncertain) View.VISIBLE else View.GONE
        if (inFlight) submitButton.text = "Gmail 처리 중…"
        else if (uncertain) submitButton.text = "Gmail 상태 확인 필요"
        else updateSubmitLabel()
        gmailStatus.text = if (uncertain) "Gmail 상태를 확인한 뒤 앱을 다시 시작하세요." else "Gmail 권한은 제출할 때 요청합니다."
    }

    private fun setStatus(message: String) {
        statusText.text = message
        statusText.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
