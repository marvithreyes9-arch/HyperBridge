package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.models.HyperIslandData
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

class ProgressTranslator(context: Context) : BaseTranslator(context) {

    private val FINISH_KEYWORDS = listOf("downloaded", "completed", "finished", "installed", "done")

    fun translate(sbn: StatusBarNotification, title: String, picKey: String): HyperIslandData {
        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", title)
        val extras = sbn.notification.extras
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val textContent = (extras.getString(Notification.EXTRA_TEXT) ?: "")

        val percent = if (max > 0) (current * 100) / max else 0
        val isTextFinished = FINISH_KEYWORDS.any { textContent.contains(it, ignoreCase = true) }
        val isFinished = percent >= 100 || isTextFinished

        // 1. Define Display Strings
        val displayTitle = title // e.g. "WhatsApp.apk"
        val displayContent = if (isFinished) "Download Complete" else "$percent% • $textContent"

        val tickKey = "${picKey}_tick"
        val hiddenKey = "hidden_pixel"
        val greenColor = "#34C759"
        val blueColor = "#007AFF"

        builder.addPicture(resolveIcon(sbn, picKey))
        builder.addPicture(getTransparentPicture(hiddenKey))

        if (isFinished) {
            builder.addPicture(getColoredPicture(tickKey, android.R.drawable.checkbox_on_background, greenColor))
        }

        val actions = extractBridgeActions(sbn)
        val actionKeys = actions.map { it.action.key }

        // 2. Expanded View
        builder.setChatInfo(
            title = displayTitle,
            content = displayContent, // e.g. "Download Complete" or "50% • 2MB/s"
            pictureKey = picKey,
            actionKeys = actionKeys
        )

        if (!isFinished) builder.setProgressBar(percent, blueColor)

        // 3. Big Island View
        if (isFinished) {
            // FINISHED: [ Text ] --- [ Tick ]
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(1, PicInfo(1, hiddenKey), TextInfo("", "")), // Empty Left
                right = ImageTextInfoRight(
                    1,
                    PicInfo(1, tickKey), // Tick Icon
                    TextInfo(displayTitle, "Completed") // Title + "Completed"
                )
            )
            builder.setSmallIslandIcon(tickKey)
        } else {
            // DOWNLOADING: [ Icon ] --- [ Title / Content ]
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    1,
                    PicInfo(1, picKey),
                    TextInfo("", "")
                ),
                right = ImageTextInfoRight(
                    1,
                    PicInfo(1, hiddenKey), // Transparent
                    TextInfo(displayTitle, displayContent) // Matches Base Info
                )
            )

            if (!indeterminate) {
                builder.setBigIslandProgressCircle(picKey, "", percent, blueColor)
                builder.setSmallIslandCircularProgress(picKey, percent, blueColor)
            } else {
                builder.setSmallIslandIcon(picKey)
            }
        }

        actions.forEach {
            builder.addAction(it.action)
            it.actionImage?.let { pic -> builder.addPicture(pic) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}