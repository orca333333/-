package com.chatbridge.kakao.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chatbridge.kakao.ChatBridgeApp
import com.chatbridge.kakao.data.SendStatus
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
 * KakaoTalk has no API to send a message on the user's behalf, so replies are delivered by
 * driving the real KakaoTalk UI through accessibility actions: open the chat room, type into its
 * input field, tap send. Node lookup is heuristic (text/content-description matching) and can
 * break whenever Kakao changes its layout — this is the tradeoff of there being no official API.
 */
class KakaoReplyAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobLock = Mutex()
    private var isDelivering = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { tryDeliverNext() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != KAKAO_PACKAGE) return
        scope.launch { tryDeliverNext() }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun tryDeliverNext() {
        if (jobLock.isLocked || isDelivering) return
        jobLock.withLock {
            if (isDelivering) return
            val repo = (application as ChatBridgeApp).repository
            val pending = repo.nextPendingOutgoing() ?: return
            isDelivering = true
            try {
                deliver(pending.id, pending.chatName, pending.body)
            } finally {
                isDelivering = false
            }
        }
    }

    private suspend fun deliver(messageId: Long, chatName: String, body: String) {
        val repo = (application as ChatBridgeApp).repository

        if (!launchKakaoTalk()) {
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

        val sendButton = findSendButton(rootInActiveWindow)
        val sent = sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        repo.updateStatus(messageId, if (sent) SendStatus.SENT else SendStatus.FAILED)
    }

    private fun launchKakaoTalk(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(KAKAO_PACKAGE) ?: return false
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

    private fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        if (root.isEditable || root.className == "android.widget.EditText") return root
        for (i in 0 until root.childCount) {
            findEditableNode(root.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findSendButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        val desc = root.contentDescription?.toString().orEmpty()
        if (root.isClickable && (desc.contains("전송") || desc.contains("Send", ignoreCase = true))) {
            return root
        }
        for (i in 0 until root.childCount) {
            findSendButton(root.getChild(i))?.let { return it }
        }
        return null
    }
}
