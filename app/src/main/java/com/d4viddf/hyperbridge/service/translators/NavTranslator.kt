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

class NavTranslator(context: Context) : BaseTranslator(context) {

    fun translate(sbn: StatusBarNotification, picKey: String): HyperIslandData {
        val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = sbn.notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // Logic to split "Distance" and "Instruction"
        var instruction = ""
        var distance = ""
        val eta = subText

        if (text.isNotEmpty() && title.isNotEmpty()) {
            if (title.length >= text.length) {
                instruction = title
                distance = text
            } else {
                instruction = text
                distance = title
            }
        } else {
            instruction = title.ifEmpty { text }
        }

        // Construction
        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", instruction)
        val hiddenKey = "hidden_pixel"

        // Resources
        builder.addPicture(resolveIcon(sbn, picKey)) // Main Icon
        builder.addPicture(getTransparentPicture(hiddenKey)) // Dummy for layout

        // Expanded Notification Shade
        builder.setBaseInfo(
            title = instruction,
            content = if (eta.isNotEmpty()) "$distance • $eta" else distance,
            pictureKey = picKey,
            actionKeys = extractBridgeActions(sbn).map { it.action.key }
        )

        // Big Island (The Fix)
        builder.setBigIslandInfo(
            // LEFT: [ Arrow Icon ] [ Distance \n ETA ]
            left = ImageTextInfoLeft(
                1,
                PicInfo(1, picKey),
                TextInfo(distance, eta) // Top: Distance, Bottom: Time
            ),
            // RIGHT: [ Instruction ] [ Hidden Icon ]
            right = ImageTextInfoRight(
                2,
                null, // We still use hidden key to occupy the "Icon" slot on the far right
                TextInfo(eta, null) // Text appears to the left of the hidden icon
            )
        )

        builder.setSmallIslandIcon(picKey)

        extractBridgeActions(sbn).forEach {
            builder.addAction(it.action)
            it.actionImage?.let { pic -> builder.addPicture(pic) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}