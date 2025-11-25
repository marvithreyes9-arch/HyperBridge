package com.d4viddf.hyperbridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.ui.AppCategory
import com.d4viddf.hyperbridge.ui.AppInfo
import com.d4viddf.hyperbridge.ui.AppListViewModel
import com.d4viddf.hyperbridge.ui.OnboardingScreen
import com.d4viddf.hyperbridge.ui.SortOption
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation()
                }
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val isSetupComplete by preferences.isSetupComplete.collectAsState(initial = null)

    when (isSetupComplete) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> OnboardingScreen { scope.launch { preferences.setSetupComplete(true) } }
        true -> HyperBridgeMainScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperBridgeMainScreen(viewModel: AppListViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val activeApps by viewModel.activeAppsState.collectAsState()
    val libraryApps by viewModel.libraryAppsState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState() // <-- OBSERVE LOADING

    var showConfigDialog by remember { mutableStateOf<AppInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Active Bridges" else "App Library", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        catch (e: Exception) {}
                    }) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    icon = { Icon(if(selectedTab==0) Icons.Filled.ToggleOn else Icons.Outlined.ToggleOff, null) },
                    label = { Text("Active") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    icon = { Icon(if(selectedTab==1) Icons.Filled.Apps else Icons.Outlined.Apps, null) },
                    label = { Text("Library") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedTab == 0) {
                ActiveAppsScreen(activeApps, isLoading, viewModel) { app -> showConfigDialog = app }
            } else {
                LibraryAppsScreen(libraryApps, isLoading, viewModel) { app -> showConfigDialog = app }
            }
        }

        if (showConfigDialog != null) {
            AppConfigDialog(app = showConfigDialog!!, viewModel = viewModel, onDismiss = { showConfigDialog = null })
        }
    }
}

// --- TAB 1: ACTIVE APPS ---
@Composable
fun ActiveAppsScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    viewModel: AppListViewModel,
    onConfig: (AppInfo) -> Unit
) {
    // LOADING CHECK
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (apps.isEmpty()) {
        // EMPTY CHECK
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.NotificationsOff, null, Modifier.size(64.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            Text("No Active Bridges", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Text("Go to Library to enable apps.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    } else {
        // LIST
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                AppListItem(
                    app = app,
                    isSimple = true,
                    onToggle = { viewModel.toggleApp(app.packageName, it) },
                    onSettingsClick = { onConfig(app) }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// --- TAB 2: LIBRARY ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAppsScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    viewModel: AppListViewModel,
    onConfig: (AppInfo) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    Column {
        // Search
        OutlinedTextField(
            value = searchQuery, onValueChange = { viewModel.searchQuery.value = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ viewModel.clearSearch() }) { Icon(Icons.Default.Clear, null) } },
            singleLine = true, shape = RoundedCornerShape(12.dp)
        )

        // Filters
        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppCategory.entries.toTypedArray()) { category ->
                FilterChip(
                    selected = selectedCategory == category, onClick = { viewModel.setCategory(category) },
                    label = { Text(category.label) },
                    leadingIcon = if (selectedCategory == category) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Sorting
        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AssistChip(
                    onClick = {
                        val next = if (sortOption == SortOption.NAME_AZ) SortOption.NAME_ZA else SortOption.NAME_AZ
                        viewModel.setSort(next)
                    },
                    label = { Text(if (sortOption == SortOption.NAME_AZ) "Sort: A-Z" else "Sort: Z-A") },
                    leadingIcon = { Icon(if (sortOption == SortOption.NAME_AZ) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, Modifier.size(16.dp)) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp).alpha(0.5f))

        // Content Area with Loading Check
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No apps found", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        isSimple = false,
                        onToggle = { viewModel.toggleApp(app.packageName, it) },
                        onSettingsClick = { onConfig(app) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun AppConfigDialog(app: AppInfo, viewModel: AppListViewModel, onDismiss: () -> Unit) {
    val config by viewModel.getAppConfig(app.packageName).collectAsState(initial = emptySet())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(bitmap = app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(app.name, style = MaterialTheme.typography.titleMedium)
                    Text("Filter Notifications", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        },
        text = {
            Column {
                NotificationType.entries.forEach { type ->
                    val isChecked = config.contains(type.name)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.updateAppConfig(app.packageName, type, !isChecked) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(type.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun AppListItem(app: AppInfo, isSimple: Boolean, onToggle: (Boolean) -> Unit, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f).clickable { onToggle(!app.isBridged) }, verticalAlignment = Alignment.CenterVertically) {
            Image(bitmap = app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(app.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (!isSimple) Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
            }
        }
        if (app.isBridged) {
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Tune, contentDescription = "Configure", tint = MaterialTheme.colorScheme.primary) }
        }
        Switch(
            checked = app.isBridged, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF34C759), checkedTrackColor = Color(0xFF34C759).copy(alpha = 0.3f))
        )
    }
}

// --- HELPERS ---
fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkgName)
}
fun isPostNotificationsEnabled(context: Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true
}
fun openAutoStartSettings(context: Context) {
    try {
        context.startActivity(Intent().apply { component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") })
    } catch (e: Exception) {
        try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri())) } catch (e2: Exception) {}
    }
}
fun openBatterySettings(context: Context) {
    try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }
    catch (e: Exception) { try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri())) } catch (e2: Exception) {} }
}