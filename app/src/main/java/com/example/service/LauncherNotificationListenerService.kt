package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens for active system notifications so the launcher can show MIUI notification
 * count badges (e.g. "20", "1", "99+") and dots on Workspace and Dock icons
 * while keeping the App Drawer completely badge-free.
 */
class LauncherNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveNotificationPackages()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshActiveNotificationPackages()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshActiveNotificationPackages()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _activePackages.value = emptySet()
        _activeNotificationCounts.value = emptyMap()
    }

    private fun refreshActiveNotificationPackages() {
        try {
            val active = activeNotifications ?: emptyArray()
            val validNotifications = active.filter { sbn ->
                // Ignore ongoing foreground service notifications unless they carry a number/clearable alert
                !sbn.isOngoing || sbn.notification.number > 0
            }

            val groupedByPkg = validNotifications.groupBy { it.packageName.orEmpty() }
            val countsMap = mutableMapOf<String, Int>()

            for ((pkg, items) in groupedByPkg) {
                if (pkg.isBlank()) continue
                // Exclude group summary notifications when child notifications exist to avoid double counting
                val nonSummary = items.filter { sbn ->
                    (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
                }
                val effectiveItems = nonSummary.ifEmpty { items }
                val totalCount = effectiveItems.sumOf { sbn ->
                    sbn.notification.number.coerceAtLeast(1)
                }
                if (totalCount > 0) {
                    countsMap[pkg] = totalCount
                }
            }

            _activeNotificationCounts.value = countsMap
            _activePackages.value = countsMap.keys
        } catch (_: SecurityException) {
            _activePackages.value = emptySet()
            _activeNotificationCounts.value = emptyMap()
        } catch (_: Exception) {
            // Ignore transient binder errors
        }
    }

    companion object {
        private val _activePackages = MutableStateFlow<Set<String>>(emptySet())
        val activePackages: StateFlow<Set<String>> = _activePackages.asStateFlow()

        private val _activeNotificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val activeNotificationCounts: StateFlow<Map<String, Int>> = _activeNotificationCounts.asStateFlow()

        /**
         * Resolves the notification count for a package on the Workspace or Dock:
         * - Uses live system notification count when > 0, and returns 0 by default when no notification is active.
         */
        fun resolveNotificationBadgeCount(
            packageName: String,
            activeCounts: Map<String, Int>
        ): Int {
            return (activeCounts[packageName] ?: 0).coerceAtLeast(0)
        }

        fun isNotificationAccessGranted(context: Context): Boolean {
            return try {
                val cn = ComponentName(context, LauncherNotificationListenerService::class.java)
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                flat.split(":").any { ComponentName.unflattenFromString(it) == cn }
            } catch (_: Exception) {
                false
            }
        }
    }
}
