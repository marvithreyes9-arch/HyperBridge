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
import io.github.d4viddf.hyperisland_kit.models.TimerInfo

class TimerTranslator(context: Context) : BaseTranslator(context) {

    fun translate(sbn: StatusBarNotification, picKey: String): HyperIslandData {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Timer"

        // Notification uses 'when' field as the base time
        val baseTime = sbn.notification.`when`
        val now = System.currentTimeMillis()

        // Detect Type
        // If 'when' is in the future -> Countdown.
        // If 'when' is in the past (or usesChronometer is set) -> Stopwatch.
        val isCountdown = baseTime > now

        // Library Timer Types: 1 = Stopwatch (Up), -1 = Countdown (Down)
        val timerType = if (isCountdown) -1 else 1

        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", title)

        // Resources
        val hiddenKey = "hidden_pixel"
        builder.addPicture(resolveIcon(sbn, picKey))
        builder.addPicture(getTransparentPicture(hiddenKey))

        val actions = extractBridgeActions(sbn)

        // Base Info (Expanded) - Shows the native ticking timer
        builder.setChatInfo(
            title = title,
            timer = TimerInfo(
                timerType,
                baseTime,
                if(isCountdown) baseTime - now else now - baseTime, // Duration
                now
            ),
            pictureKey = picKey,
            actionKeys = actions.map { it.action.key }
        )

        // Big Island
        if (isCountdown) {
            // NATIVE COUNTDOWN UI
            // This usually renders a nice centered timer on the island
            builder.setBigIslandCountdown(baseTime, picKey)
        } else {
            // STOPWATCH SPLIT UI
            // Left: Icon
            // Right: "Stopwatch" / "Active"
            // Note: We can't render a ticking string in text fields easily,
            // so we show the status text.
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(1, PicInfo(1, picKey), TextInfo("", "")),
                right = ImageTextInfoRight(1, PicInfo(1, hiddenKey), TextInfo(title, "Active"))
            )
        }

        builder.setSmallIslandIcon(picKey)

        actions.forEach {
            builder.addAction(it.action)
            it.actionImage?.let { pic -> builder.addPicture(pic) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}