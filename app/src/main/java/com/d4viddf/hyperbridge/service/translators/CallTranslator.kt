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

class CallTranslator(context: Context) : BaseTranslator(context) {

    fun translate(sbn: StatusBarNotification, picKey: String): HyperIslandData {
        val extras = sbn.notification.extras

        // 1. Extract Caller Data
        // Title is usually Caller Name
        val callerName = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown Caller"
        // Text is usually "Incoming voice call" or "00:12"
        val callStatus = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Call"

        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", callerName)

        // 2. Resources
        val hiddenKey = "hidden_pixel"
        // resolveIcon will prioritize LargeIcon (Contact Photo) -> SmallIcon -> AppIcon
        builder.addPicture(resolveIcon(sbn, picKey))
        builder.addPicture(getTransparentPicture(hiddenKey))

        val actions = extractBridgeActions(sbn)
        val actionKeys = actions.map { it.action.key }

        // 3. Expanded View (Shade)
        builder.setBaseInfo(
            title = callerName,
            content = callStatus,
            pictureKey = picKey,
            actionKeys = actionKeys
        )

        // 4. Big Island (Split Layout)
        // Left: Contact Photo
        // Right: Name + Status
        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(
                1,
                PicInfo(1, picKey),
                TextInfo("", "") // Icon only
            ),
            right = ImageTextInfoRight(
                1,
                PicInfo(1, hiddenKey), // Spacer
                TextInfo(callerName, callStatus) // "Mom" \n "Incoming..."
            )
        )

        builder.setSmallIslandIcon(picKey)

        // 5. Actions (Crucial for calls)
        actions.forEach {
            builder.addAction(it.action)
            it.actionImage?.let { pic -> builder.addPicture(pic) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}