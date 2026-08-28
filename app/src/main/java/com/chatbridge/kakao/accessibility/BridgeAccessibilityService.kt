package com.chatbridge.kakao.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chatbridge.kakao.ChatBridgeApp
import com.chatbridge.kakao.data.AppDatabase
import com.chatbridge.kakao.data.SendStatus
import com.chatbridge.kakao.data.scrape.ScrapeDao
import com.chatbridge.kakao.data.scrape.ScrapeJob
import com.chatbridge.kakao.data.scrape.ScrapeStatus
import com.chatbridge.kakao.data.scrape.ScrapeTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val KAKAO_PACKAGE = "com.kakao.talk"

/**
 * None of KakaoTalk, BankSalad, X, Instagram, or TikTok offer an API for what this app needs
 * (sending a KakaoTalk reply, reading a BankSalad ledger, reading someone's recent posts), so this
 * service drives each app's real UI through accessibility actions instead. It is inherently
 * heuristic (text/content-description matching, fixed wait times) and will need adjustment
 * whenever one of these apps changes its layout — see the README for the tradeoffs.
 */
class BridgeAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobLock = Mutex()
    private var isBusy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { tryRunNext() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != KAKAO_PACKAGE && ScrapeTarget.entries.none { it.packageName == pkg }) return
        scope.launch { tryRunNext() }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /** Runs one pending job (KakaoTalk reply takes priority, then scrape jobs), then chains into
     * the next one so a single notification tap can work through several queued jobs in a row. */
    private suspend fun tryRunNext() {
        if (jobLock.isLocked || isBusy) return
        val ranAJob = jobLock.withLock {
            if (isBusy) return@withLock false
            isBusy = true
            try {
                runNextKakaoReply() || runNextScrapeJob()
            } finally {
                isBusy = false
            }
        }
        if (ranAJob) tryRunNext()
    }

    // ---- KakaoTalk replies (menu 1) ----

    private suspend fun runNextKakaoReply(): Boolean {
        val repo = (application as ChatBridgeApp).repository
        val pending = repo.nextPendingOutgoing() ?: return false
        deliverKakaoReply(pending.id, pending.chatName, pending.body)
        return true
    }

    private suspend fun deliverKakaoReply(messageId: Long, chatName: String, body: String) {
        val repo = (application as ChatBridgeApp).repository

        if (!launchApp(KAKAO_PACKAGE)) {
            repo.updateStatus(messageId, SendStatus.FAILED)
            return
        }

        val chatOpened = waitUntil(timeoutMs = 6_000) {
            findNodeByText(rootInActiveWindow, chatName)?.also { node ->
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } != null
        }
        if (!chatOpened) {
            repo.updateStatus(messageId, SendStatus.FAILED)
            return
        }

        val inputField = waitForNode(timeoutMs = 6_000) { findEditableNode(rootInActiveWindow) }
        if (inputField == null) {
            repo.updateStatus(messageId, SendStatus.FAILED)
            return
        }

        val textSet = inputField.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, body)
            }
        )
        if (!textSet) {
            repo.updateStatus(messageId, SendStatus.FAILED)
            return
        }
        delay(300)

        val sendButton = findNodeByDescriptionContains(rootInActiveWindow, "전송", "Send")
        val sent = sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        repo.updateStatus(messageId, if (sent) SendStatus.SENT else SendStatus.FAILED)
    }

    // ---- BankSalad / SNS scraping (menus 4-5) ----

    private suspend fun runNextScrapeJob(): Boolean {
        val scrapeDao = AppDatabase.getInstance(this).scrapeDao()
        val job = scrapeDao.nextPending() ?: return false
        scrapeDao.updateStatus(job.id, ScrapeStatus.RUNNING)
        runScrapeJob(job, scrapeDao)
        return true
    }

    private suspend fun runScrapeJob(job: ScrapeJob, scrapeDao: ScrapeDao) {
        val launched = if (job.target == ScrapeTarget.BANKSALAD) {
            launchApp(job.target.packageName)
        } else {
            launchProfileDeepLink(job.target, job.detail.orEmpty())
        }
        if (!launched) {
            scrapeDao.complete(job.id, ScrapeStatus.FAILED, null)
            return
        }

        if (job.target == ScrapeTarget.BANKSALAD) {
            waitUntil(timeoutMs = 5_000) {
                findNodeByTextContains(rootInActiveWindow, "가계부")?.also {
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } != null
            }
        }

        delay(SCRAPE_SETTLE_DELAY_MS)
        val texts = collectVisibleText(rootInActiveWindow)
        if (texts.isEmpty()) {
            scrapeDao.complete(job.id, ScrapeStatus.FAILED, null)
            return
        }

        val app = application as ChatBridgeApp
        if (job.target == ScrapeTarget.BANKSALAD) {
            app.financeRepository.onScrapeCompleted(texts.joinToString("\n"))
        } else {
            app.snsRepository.onScrapeCompleted(job.target, job.detail.orEmpty(), texts)
        }
        scrapeDao.complete(job.id, ScrapeStatus.DONE, texts.joinToString("\n").take(2_000))
    }

    private fun launchProfileDeepLink(platform: ScrapeTarget, handle: String): Boolean {
        if (handle.isBlank()) return false
        val uri = when (platform) {
            ScrapeTarget.SNS_X -> "https://x.com/$handle"
            ScrapeTarget.SNS_INSTAGRAM -> "https://www.instagram.com/$handle"
            ScrapeTarget.SNS_TIKTOK -> "https://www.tiktok.com/@$handle"
            ScrapeTarget.BANKSALAD -> return false
        }
        return try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(platform.packageName)
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- shared helpers ----

    private fun launchApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        return true
    }

    private suspend fun waitUntil(timeoutMs: Long, intervalMs: Long = 300, check: () -> Boolean): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            if (check()) return true
            delay(intervalMs)
            waited += intervalMs
        }
        return false
    }

    private suspend fun waitForNode(
        timeoutMs: Long,
        intervalMs: Long = 300,
        find: () -> AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var waited = 0L
        while (waited < timeoutMs) {
            find()?.let { return it }
            delay(intervalMs)
            waited += intervalMs
        }
        return null
    }

    private fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        root ?: return null
        if (root.text?.toString() == text || root.contentDescription?.toString() == text) return root
        for (i in 0 until root.childCount) {
            findNodeByText(root.getChild(i), text)?.let { return it }
        }
        return null
    }

    private fun findNodeByTextContains(root: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
        root ?: return null
        val text = root.text?.toString().orEmpty()
        val desc = root.contentDescription?.toString().orEmpty()
        if (root.isClickable && (text.contains(needle) || desc.contains(needle))) return root
        for (i in 0 until root.childCount) {
            findNodeByTextContains(root.getChild(i), needle)?.let { return it }
        }
        return null
    }

    private fun findNodeByDescriptionContains(root: AccessibilityNodeInfo?, vararg needles: String): AccessibilityNodeInfo? {
        root ?: return null
        val desc = root.contentDescription?.toString().orEmpty()
        if (root.isClickable && needles.any { desc.contains(it, ignoreCase = true) }) return root
        for (i in 0 until root.childCount) {
            findNodeByDescriptionContains(root.getChild(i), *needles)?.let { return it }
        }
        return null
    }

    private fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        if (root.isEditable || root.className == "android.widget.EditText") return root
        for (i in 0 until root.childCount) {
            findEditableNode(root.getChild(i))?.let { return it }
        }
        return null
    }

    private fun collectVisibleText(
        root: AccessibilityNodeInfo?,
        out: MutableList<String> = mutableListOf()
    ): List<String> {
        root ?: return out
        if (out.size >= MAX_TEXT_NODES) return out
        val text = root.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text !in out) out += text
        for (i in 0 until root.childCount) {
            if (out.size >= MAX_TEXT_NODES) break
            collectVisibleText(root.getChild(i), out)
        }
        return out
    }

    private companion object {
        const val SCRAPE_SETTLE_DELAY_MS = 2_500L
        const val MAX_TEXT_NODES = 150
    }
}
