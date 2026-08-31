package com.happyman.livebridge.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.preference.PreferenceManager

class LiveBridgeNotificationListenerService : NotificationListenerService() {

    private val TAG = "LiveBridgeNLS"

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                Log.d(TAG, "Received ACTION_USER_PRESENT — re-evaluating active notifications")
                reprocessActiveNotifications()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Register receiver to know when the user unlocks the device
        try {
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            registerReceiver(userPresentReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register user-present receiver: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(userPresentReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            processPostedNotification(sbn)
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onNotificationPosted: ${t.message}", t)
        }
    }

    private fun processPostedNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        // 1) Always allow critical system packages (don't suppress)
        if (isCriticalSystemPackage(pkg)) {
            Log.d(TAG, "package $pkg is critical system package — skipping suppression checks")
            forwardNotification(sbn)
            return
        }

        // 2) Ignore ongoing/no-clear system events (Flashlight, Battery, etc.)
        val flags = notification.flags
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 || (flags and Notification.FLAG_NO_CLEAR) != 0) {
            Log.d(TAG, "Ignoring ongoing/no_clear notification from $pkg")
            // For system indicator UX, don't forward/suppress; treat as non-live-forwardable
            return
        }

        // 3) Respect "Sync DND mode" if user enabled it in app settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val syncDndEnabled = prefs.getBoolean("sync_dnd_enabled", true) // adjust key if yours differs

        if (syncDndEnabled) {
            // If the notification manager indicates current filter disallows this, enforce but allow exceptions per policy
            val filter = currentInterruptionFilter
            if (filter == INTERRUPTION_FILTER_NONE) {
                Log.d(TAG, "DND active (none) — suppressing notification from $pkg")
                return
            } else if (filter == INTERRUPTION_FILTER_PRIORITY) {
                // We're in Priority-only mode — allow only priority categories or notifications that match policy exceptions
                if (!isAllowedUnderDndPolicy(notification)) {
                    Log.d(TAG, "DND priority mode and notification is not allowed by policy — suppressing $pkg")
                    return
                } else {
                    Log.d(TAG, "DND priority mode but notification allowed by policy — letting $pkg through")
                }
            } else {
                // INTERRUPTION_FILTER_ALL — no suppression by DND
            }
        }

        // 4) Per-app + per-chat (conversation) live-notification restriction
        val perChatEnabled = prefs.getBoolean("live_notifications_per_chat_enabled", false)
        if (perChatEnabled) {
            val configuredPackage = prefs.getString("live_notifications_package", null)
            val configuredChatName = prefs.getString("live_notifications_chat_name", null)

            // If package is configured and does not match -> ignore
            if (!configuredPackage.isNullOrBlank()) {
                if (configuredPackage != pkg) {
                    Log.d(TAG, "Per-chat enabled but package mismatch (${configuredPackage} != ${pkg}) — ignoring")
                    return
                }
            }

            // If chat-name configured, try to extract the conversation/title
            if (!configuredChatName.isNullOrBlank()) {
                val extras = notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE)
                    ?: extras.getString("android.title")
                    ?: extras.getString("android.conversationTitle")
                    ?: extras.getString("android.subText")

                if (title == null) {
                    Log.d(TAG, "Per-chat enabled but notification title is null — ignoring")
                    return
                }

                // Exact match required (per request)
                if (configuredChatName != title) {
                    Log.d(TAG, "Per-chat enabled but chat name mismatch (configured='$configuredChatName' vs actual='$title') — ignoring")
                    return
                } else {
                    Log.d(TAG, "Per-chat match: package and chat title match — allowing")
                }
            }
        }

        // 5) If we reach here, the notification is allowed — forward / handle it
        forwardNotification(sbn)
    }

    private fun forwardNotification(sbn: StatusBarNotification) {
        // TODO: Replace this implementation with your app's logic to forward notifications to the island/pill.
        // For example, if your app broadcasts the notification to a Flutter side channel, call that here.
        // Example placeholder:
        Log.d(TAG, "Forwarding notification from ${sbn.packageName} - id=${sbn.id} - tag=${sbn.tag}")
        // If you already have a method like NotificationForwarder.forward(sbn), call it here.
    }

    private fun isCriticalSystemPackage(pkgName: String): Boolean {
        val criticalPrefixes = listOf(
            "com.android.systemui",
            "com.samsung.android.",
            "com.htc.",
            "com.oneplus."
        )
        // whitelist exact and prefix matches
        for (p in criticalPrefixes) {
            if (pkgName.startsWith(p)) return true
        }
        return false
    }

    private fun isAllowedUnderDndPolicy(notification: Notification): Boolean {
        // Conservative approach: allow if notification category is a message/call/alarm, or if it has people/contacts extras
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            // If we don't have NotificationManager access or no policy, be conservative and check notification cues
            val category = notification.category
            if (category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_ALARM) {
                return true
            }

            val extras = notification.extras
            // Check for people in notification extras (EXTRA_PEOPLE)
            if (extras != null) {
                val people = extras.get("android.people") ?: extras.getStringArrayList(Notification.EXTRA_PEOPLE)
                if (people != null) {
                    // If policy allows contacts or starred senders, a people field strongly indicates priority
                    nm?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            if (it.isNotificationPolicyAccessGranted) {
                                val policy = it.notificationPolicy
                                // If messages from anyone are allowed as priority, permit
                                if (policy.priorityMessageSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY) return true
                                // If contacts or starred are permitted, we do a best-effort: allow when people exists (exact contact resolution would require a contact lookup)
                                if (policy.priorityMessageSenders == NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS ||
                                    policy.priorityMessageSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED) {
                                    return true
                                }
                            }
                        }
                    }
                    // If no policy access available, treat presence of people as an indicator of a high-priority message
                    return true
                }
            }

            // As a fallback, allow high-priority notifications (importance/priority)
            // Older API: use Notification.priority
            if (notification.priority >= Notification.PRIORITY_HIGH) return true
            // Newer APIs use importance; but from here we don't have channel importance easily. We keep fallback conservative.
        } catch (e: Exception) {
            Log.w(TAG, "isAllowedUnderDndPolicy error: ${e.message}")
        }
        return false
    }

    private fun reprocessActiveNotifications() {
        // Re-evaluate active notifications on unlock — this allows previously masked "Hidden content" entries to be updated.
        try {
            val actives = activeNotifications
            if (actives != null) {
                for (sbn in actives) {
                    // Re-run our processing logic. This may forward an updated payload if the lock-state changed.
                    processPostedNotification(sbn)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reprocess active notifications: ${e.message}")
        }
    }
}
