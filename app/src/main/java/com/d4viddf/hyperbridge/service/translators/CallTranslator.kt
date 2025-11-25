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

class CallTranslator(context: Context) : BaseTranslator(context) {

    fun translate(sbn: StatusBarNotification, picKey: String): HyperIslandData {
        val extras = sbn.notification.extras
        val callerName = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: context.getString(R.string.caller_unknown)

        val callStatus = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: context.getString(R.string.status_incoming_call)

        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", callerName)
        val hiddenKey = "hidden_pixel"

        builder.addPicture(resolveIcon(sbn, picKey))
        builder.addPicture(getTransparentPicture(hiddenKey))

        val actions = extractBridgeActions(sbn)
        val actionKeys = actions.map { it.action.key }

        builder.setBaseInfo(
            title = callerName,
            content = callStatus,
            pictureKey = picKey,
            actionKeys = actionKeys
        )

        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(1, PicInfo(1, picKey), TextInfo("", "")),
            right = ImageTextInfoRight(1, PicInfo(1, hiddenKey), TextInfo(callerName, callStatus))
        )

        builder.setSmallIslandIcon(picKey)

        actions.forEach {
            builder.addAction(it.action)
            it.actionImage?.let { pic -> builder.addPicture(pic) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}