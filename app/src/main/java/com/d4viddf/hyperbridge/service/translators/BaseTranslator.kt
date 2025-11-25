package com.d4viddf.hyperbridge.service.translators

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.toColorInt
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.models.BridgeAction
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperPicture
import androidx.core.graphics.createBitmap

abstract class BaseTranslator(protected val context: Context) {

    /**
     * Creates a colored version of a drawable resource (e.g., Green Tick).
     */
    protected fun getColoredPicture(key: String, resId: Int, colorHex: String): HyperPicture {
        val drawable = context.getDrawable(resId)?.mutate()
        val color = colorHex.toColorInt()

        drawable?.setTint(color)
        // Or for older APIs: drawable?.setColorFilter(color, PorterDuff.Mode.SRC_IN)

        val bitmap = drawable?.toBitmap() ?: createFallbackBitmap()
        return HyperPicture(key, bitmap)
    }

    protected fun getTransparentPicture(key: String): HyperPicture {
        val conf = Bitmap.Config.ARGB_8888
        val transparentBitmap = createBitmap(1, 1, conf)
        return HyperPicture(key, transparentBitmap)
    }

    // ... (Keep resolveIcon, extractBridgeActions, loadIconBitmap, getAppIconBitmap) ...
    // ... ensure you keep the existing code below ...

    protected fun resolveIcon(sbn: StatusBarNotification, picKey: String): HyperPicture {
        var bitmap: Bitmap? = null
        try {
            val largeIcon = sbn.notification.getLargeIcon()
            if (largeIcon != null) bitmap = loadIconBitmap(largeIcon)
            if (bitmap == null && sbn.notification.smallIcon != null) bitmap = loadIconBitmap(sbn.notification.smallIcon)
            if (bitmap == null) bitmap = getAppIconBitmap(sbn.packageName)
        } catch (e: Exception) { Log.e("HyperBridge", "Icon error", e) }

        return if (bitmap != null) HyperPicture(picKey, bitmap)
        else HyperPicture(picKey, context, R.drawable.ic_launcher_foreground)
    }

    protected fun extractBridgeActions(sbn: StatusBarNotification): List<BridgeAction> {
        val bridgeActions = mutableListOf<BridgeAction>()
        sbn.notification.actions?.forEachIndexed { index, androidAction ->
            if (!androidAction.title.isNullOrEmpty()) {
                val uniqueKey = "act_${sbn.key.hashCode()}_$index"
                var actionIcon: Icon? = null
                var hyperPic: HyperPicture? = null

                val originalIcon = androidAction.getIcon()
                if (originalIcon != null) {
                    val bitmap = loadIconBitmap(originalIcon)
                    if (bitmap != null) {
                        actionIcon = Icon.createWithBitmap(bitmap)
                        hyperPic = HyperPicture("${uniqueKey}_icon", bitmap)
                    }
                }

                val hyperAction = HyperAction(
                    key = uniqueKey,
                    title = androidAction.title.toString(),
                    icon = actionIcon,
                    pendingIntent = androidAction.actionIntent,
                    actionIntentType = 1
                )
                bridgeActions.add(BridgeAction(hyperAction, hyperPic))
            }
        }
        return bridgeActions
    }

    private fun loadIconBitmap(icon: Icon): Bitmap? = try { icon.loadDrawable(context)?.toBitmap() } catch (e: Exception) { null }

    private fun getAppIconBitmap(packageName: String): Bitmap? = try { context.packageManager.getApplicationIcon(packageName).toBitmap() } catch (e: Exception) { null }

    private fun createFallbackBitmap(): Bitmap = createBitmap(1, 1)

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && this.bitmap != null) return this.bitmap
        val width = if (intrinsicWidth > 0) intrinsicWidth else 1
        val height = if (intrinsicHeight > 0) intrinsicHeight else 1
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}