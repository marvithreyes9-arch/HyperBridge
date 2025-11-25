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

class StandardTranslator(context: Context) : BaseTranslator(context) {

    fun translate(sbn: StatusBarNotification, picKey: String): HyperIslandData {
        val extras = sbn.notification.extras

        // 1. Extract Raw Data
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: sbn.packageName
        val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: "" // <--- Status Chip Info
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""

        val isMedia = template.contains("MediaStyle")
        val isCall = sbn.notification.category == Notification.CATEGORY_CALL

        // 2. Format Display Text
        val displayTitle = rawTitle

        // Combine Text and SubText (Status Chip)
        // Example: "Incoming Call • +1 555-0199" OR "Arriving in 5 min • Uber X"
        val displayContent = when {
            isMedia -> "Now Playing"
            isCall && subText.isNotEmpty() -> "$rawText • $subText"
            subText.isNotEmpty() -> if (rawText.isNotEmpty()) "$rawText • $subText" else subText
            else -> rawText
        }

        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", displayTitle)

        // 3. Resources
        val hiddenKey = "hidden_pixel"
        builder.addPicture(resolveIcon(sbn, picKey))
        builder.addPicture(getTransparentPicture(hiddenKey))

        val actions = extractBridgeActions(sbn)
        val actionKeys = actions.map { it.action.key }

        // 4. Expanded View (Shade)
        builder.setBaseInfo(
            title = displayTitle,
            content = displayContent,
            pictureKey = picKey,
            actionKeys = actionKeys
        )

        // 5. Big Island (Popup)
        if (isMedia) {
            // Media: Art Left
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(1, PicInfo(1, picKey), TextInfo("", ""))
            )
        } else {
            // Standard/Live Update: [ Icon ] --- [ Title / Content ]
            builder.setBigIslandInfo(
                // Left: App Icon
                left = ImageTextInfoLeft(
                    1,
                    PicInfo(1, picKey),
                    TextInfo("", "")
                ),
                // Right: Title + Content (with SubText included)
                right = ImageTextInfoRight(
                    1,
                    PicInfo(1, hiddenKey), // Transparent Spacer
                    TextInfo(displayTitle, displayContent)
                )
            )
        }

        builder.setSmallIslandIcon(picKey)

        actions.forEach {
            builder.addAction(it.action)
            it.actionImage?.let { iconPic -> builder.addPicture(iconPic) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}