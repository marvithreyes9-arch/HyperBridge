package com.d4viddf.hyperbridge.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.d4viddf.hyperbridge.ui.AppInfo

@Composable
fun AppListItem(
    app: AppInfo,
    isSimple: Boolean,
    onToggle: (Boolean) -> Unit,
    onSettingsClick: (() -> Unit)? = null
) {
    // Using M3 ListItem for better native alignment
    ListItem(
        headlineContent = {
            Text(app.name, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = if (!isSimple) {
            { Text(app.packageName, maxLines = 1) }
        } else null,
        leadingContent = {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)) // M3 style squaring
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.isBridged && onSettingsClick != null) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Configure",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Switch(
                    checked = app.isBridged,
                    onCheckedChange = onToggle
                )
            }
        },
        modifier = Modifier.clickable { onToggle(!app.isBridged) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}