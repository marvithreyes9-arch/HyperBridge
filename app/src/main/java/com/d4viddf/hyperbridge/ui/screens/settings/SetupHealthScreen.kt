package com.d4viddf.hyperbridge.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.util.isNotificationServiceEnabled
import com.d4viddf.hyperbridge.util.isPostNotificationsEnabled
import com.d4viddf.hyperbridge.util.openAutoStartSettings
import com.d4viddf.hyperbridge.util.openBatterySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupHealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isListenerGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isPostGranted by remember { mutableStateOf(isPostNotificationsEnabled(context)) }
    var isBatteryOptimized by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerGranted = isNotificationServiceEnabled(context)
                isPostGranted = isPostNotificationsEnabled(context)
                isBatteryOptimized = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_setup), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.app_health_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HealthSectionTitle(stringResource(R.string.req_permissions))

            HealthGroupCard {
                HealthItem(
                    title = stringResource(R.string.notif_access),
                    subtitle = stringResource(R.string.notif_access_desc),
                    icon = Icons.Default.NotificationsActive,
                    isGranted = isListenerGranted,
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                HealthItem(
                    title = stringResource(R.string.show_island),
                    subtitle = stringResource(R.string.perm_display_desc),
                    icon = Icons.Default.Visibility,
                    isGranted = isPostGranted,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        } catch (e: Exception) { }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            HealthSectionTitle(stringResource(R.string.device_optimization))

            HealthGroupCard {
                HealthItem(
                    title = stringResource(R.string.xiaomi_autostart),
                    subtitle = stringResource(R.string.autostart_desc),
                    icon = Icons.Default.RestartAlt,
                    isGranted = false,
                    forceAction = true,
                    onClick = { openAutoStartSettings(context) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                HealthItem(
                    title = stringResource(R.string.battery_unrestricted),
                    subtitle = stringResource(R.string.battery_desc),
                    icon = Icons.Default.BatteryAlert,
                    isGranted = isBatteryOptimized,
                    onClick = { openBatterySettings(context) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.recents_note),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun HealthSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun HealthGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
fun HealthItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGranted: Boolean,
    forceAction: Boolean = false,
    onClick: () -> Unit
) {
    val statusColor = if (isGranted) Color(0xFF34C759) else MaterialTheme.colorScheme.error
    val isActionable = !isGranted || forceAction
    val rowColor = if (isGranted && !forceAction) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .clickable { if (isActionable) onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isGranted && !forceAction) stringResource(R.string.status_active) else subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActionable) MaterialTheme.colorScheme.onSurfaceVariant else statusColor
            )
        }
        if (isGranted && !forceAction) {
            Icon(Icons.Default.CheckCircle, stringResource(R.string.perm_granted), tint = statusColor, modifier = Modifier.size(24.dp))
        } else {
            Icon(
                imageVector = if (forceAction) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Error,
                contentDescription = stringResource(R.string.action_needed),
                tint = if (forceAction) MaterialTheme.colorScheme.onSurfaceVariant else statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}