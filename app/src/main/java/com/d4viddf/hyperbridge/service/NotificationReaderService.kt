package com.d4viddf.hyperbridge.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.service.translators.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationReaderService : NotificationListenerService() {

    private val TAG = "HyperBridgeService"
    private val ISLAND_CHANNEL_ID = "hyper_bridge_island_channel"

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var allowedPackageSet: Set<String> = emptySet()
    private val activeTranslations = mutableMapOf<String, Int>()
    private val lastUpdateMap = mutableMapOf<String, Long>()
    private val UPDATE_INTERVAL_MS = 200L

    // Preferences
    private lateinit var preferences: AppPreferences

    // Translators
    private lateinit var callTranslator: CallTranslator
    private lateinit var navTranslator: NavTranslator
    private lateinit var timerTranslator: TimerTranslator
    private lateinit var progressTranslator: ProgressTranslator
    private lateinit var standardTranslator: StandardTranslator

    override fun onCreate() {
        super.onCreate()
        createIslandChannel()
        preferences = AppPreferences(this)
        callTranslator = CallTranslator(this)
        navTranslator = NavTranslator(this)
        timerTranslator = TimerTranslator(this)
        progressTranslator = ProgressTranslator(this)
        standardTranslator = StandardTranslator(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            preferences.allowedPackagesFlow.collectLatest { allowedPackageSet = it }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            // 1. Filter System
            if (shouldIgnore(it.packageName)) return

            // 2. Filter Junk (Duplicates)
            if (isJunkNotification(it)) return

            // 3. Permission & Rate Limit
            if (isAppAllowed(it.packageName)) {
                if (shouldRateLimit(it)) return

                // 4. ASYNC PROCESSING (To check Configs)
                serviceScope.launch {
                    processAndPost(it)
                }
            }
        }
    }

    private fun isJunkNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val extras = notification.extras

        // Block Group Summaries
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return true

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        val isSpecial = notification.category == Notification.CATEGORY_TRANSPORT ||
                notification.category == Notification.CATEGORY_CALL ||
                notification.category == Notification.CATEGORY_NAVIGATION ||
                extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true

        if (!isSpecial) {
            // Empty content is junk
            if (title.isEmpty() && text.isEmpty()) return true
            // Placeholder where title equals app name and no text
            val appName = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { "" }
            if (title == appName && text.isEmpty()) return true
        }

        if (title.contains("running in background", true)) return true
        if (text.contains("tap for more info", true)) return true

        return false
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun processAndPost(sbn: StatusBarNotification) {
        // Determine Type
        val extras = sbn.notification.extras
        val isCall = sbn.notification.category == Notification.CATEGORY_CALL
        val isNavigation = sbn.notification.category == Notification.CATEGORY_NAVIGATION || sbn.packageName.contains("maps")
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val hasProgress = progressMax > 0 || extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val chronometerBase = sbn.notification.`when`
        val isTimer = (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) || sbn.notification.category == Notification.CATEGORY_ALARM) && chronometerBase > 0
        val isMedia = extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true

        val type = when {
            isCall -> NotificationType.CALL
            isNavigation -> NotificationType.NAVIGATION
            isTimer -> NotificationType.TIMER
            hasProgress -> NotificationType.PROGRESS
            isMedia -> NotificationType.MEDIA
            else -> NotificationType.STANDARD
        }

        // CHECK IF TYPE IS ALLOWED (Suspend call)
        val config = preferences.getAppConfig(sbn.packageName).first()
        if (!config.contains(type.name)) {
            return // User disabled this type for this app
        }

        // ROUTING
        val title = extras.getString(Notification.EXTRA_TITLE) ?: sbn.packageName
        val bridgeId = sbn.key.hashCode()
        val picKey = "pic_${bridgeId}"

        try {
            val data: HyperIslandData = when (type) {
                NotificationType.CALL -> callTranslator.translate(sbn, picKey)
                NotificationType.NAVIGATION -> navTranslator.translate(sbn, picKey)
                NotificationType.TIMER -> timerTranslator.translate(sbn, picKey)
                NotificationType.PROGRESS -> progressTranslator.translate(sbn, title, picKey)
                else -> standardTranslator.translate(sbn, picKey)
            }
            postNotification(sbn, bridgeId, data)
        } catch (e: Exception) {
            Log.e(TAG, "Translation Error", e)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun postNotification(sbn: StatusBarNotification, bridgeId: Int, data: HyperIslandData) {
        val notificationBuilder = NotificationCompat.Builder(this, ISLAND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("HyperBridge")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addExtras(data.resources)

        sbn.notification.contentIntent?.let { notificationBuilder.setContentIntent(it) }
        val notification = notificationBuilder.build()
        notification.extras.putString("miui.focus.param", data.jsonParam)

        NotificationManagerCompat.from(this).notify(bridgeId, notification)
        activeTranslations[sbn.key] = bridgeId
    }

    // ... (Keep onNotificationRemoved, shouldRateLimit, shouldIgnore from previous) ...
    // RE-PASTING HELPERS TO ENSURE COMPLETENESS
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            val key = it.key
            if (activeTranslations.containsKey(key)) {
                val hyperId = activeTranslations[key] ?: return
                try { NotificationManagerCompat.from(this).cancel(hyperId) } catch (e: Exception) {}
                activeTranslations.remove(key)
                lastUpdateMap.remove(key)
            }
        }
    }

    private fun shouldRateLimit(sbn: StatusBarNotification): Boolean {
        val key = sbn.key
        val now = System.currentTimeMillis()
        val lastTime = lastUpdateMap[key] ?: 0L
        if (now - lastTime > UPDATE_INTERVAL_MS) {
            lastUpdateMap[key] = now
            return false
        }
        if (sbn.notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return true
        lastUpdateMap[key] = now
        return false
    }

    private fun shouldIgnore(packageName: String): Boolean {
        return packageName == this.packageName || packageName == "android" || packageName == "com.android.systemui" || packageName.contains("miui.notification")
    }

    private fun createIslandChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ISLAND_CHANNEL_ID, "Active Islands", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun isAppAllowed(packageName: String): Boolean {
        return allowedPackageSet.contains(packageName)
    }
}