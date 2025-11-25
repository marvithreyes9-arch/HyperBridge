package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.models.HyperIslandData
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

class ProgressTranslator(context: Context) : BaseTranslator(context) {

    // Lazy load keywords from XML resources
    private val finishKeywords by lazy {
        context.resources.getStringArray(R.array.progress_finish_keywords).toList()
    }

    fun translate(sbn: StatusBarNotification, title: String, picKey: String): HyperIslandData {
        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", title)
        val extras = sbn.notification.extras

        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val textContent = (extras.getString(Notification.EXTRA_TEXT) ?: "")

        val percent = if (max > 0) (current * 100) / max else 0

        // Check localized keywords
        val isTextFinished = finishKeywords.any { textContent.contains(it, ignoreCase = true) }
        val isFinished = percent >= 100 || isTextFinished

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

        // Localized Strings
        val strDownloadComplete = context.getString(R.string.status_download_complete)
        val strPending = context.getString(R.string.status_pending)
        val strFinished = context.getString(R.string.status_finished)
        val strDownloading = context.getString(R.string.status_downloading)

        // Expanded Info
        builder.setChatInfo(
            title = title,
            content = if (isFinished) strDownloadComplete else "${if(indeterminate) strPending else "$percent%"} • $textContent",
            pictureKey = picKey,
            actionKeys = actionKeys
        )

        if (!isFinished) builder.setProgressBar(percent, blueColor)

        // Big Island
        if (isFinished) {
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(1, PicInfo(1, hiddenKey), TextInfo("", "")),
                right = ImageTextInfoRight(
                    1,
                    PicInfo(1, tickKey),
                    TextInfo(strFinished, title)
                )
            )
            builder.setSmallIslandIcon(tickKey)
        } else {
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    1,
                    PicInfo(1, picKey),
                    TextInfo("", "")
                ),
                right = ImageTextInfoRight(
                    1,
                    PicInfo(1, hiddenKey),
                    TextInfo(strDownloading, "$percent%")
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