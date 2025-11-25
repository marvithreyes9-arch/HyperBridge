package com.d4viddf.hyperbridge.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.d4viddf.hyperbridge.models.NotificationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    companion object {
        private val ALLOWED_PACKAGES_KEY = stringSetPreferencesKey("allowed_packages")
        private val SETUP_COMPLETE_KEY = booleanPreferencesKey("setup_complete")
        private val LAST_VERSION_CODE_KEY = intPreferencesKey("last_version_code")
    }

    // --- GLOBAL SETTINGS ---
    val allowedPackagesFlow: Flow<Set<String>> = context.dataStore.data
        .map { it[ALLOWED_PACKAGES_KEY] ?: emptySet() }

    val isSetupComplete: Flow<Boolean> = context.dataStore.data
        .map { it[SETUP_COMPLETE_KEY] ?: false }

    suspend fun setSetupComplete(isComplete: Boolean) {
        context.dataStore.edit { it[SETUP_COMPLETE_KEY] = isComplete }
    }

    suspend fun toggleApp(packageName: String, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[ALLOWED_PACKAGES_KEY] ?: emptySet()
            preferences[ALLOWED_PACKAGES_KEY] = if (isEnabled) {
                currentSet + packageName
            } else {
                currentSet - packageName
            }
        }
    }

    // --- PER-APP CONFIGURATION ---

    // Returns the set of enabled types (e.g. "MEDIA", "CALL"). Defaults to ALL if not set.
    fun getAppConfig(packageName: String): Flow<Set<String>> {
        val key = stringSetPreferencesKey("config_$packageName")
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: NotificationType.entries.map { it.name }.toSet()
        }
    }

    suspend fun updateAppConfig(packageName: String, type: NotificationType, isEnabled: Boolean) {
        val key = stringSetPreferencesKey("config_$packageName")
        context.dataStore.edit { preferences ->
            val current = preferences[key] ?: NotificationType.entries.map { it.name }.toSet()
            preferences[key] = if (isEnabled) current + type.name else current - type.name
        }
    }

    // Helper for the Service to get config one-shot
    suspend fun isTypeAllowed(packageName: String, type: NotificationType): Boolean {
        val key = stringSetPreferencesKey("config_$packageName")
        var allowed = true
        context.dataStore.edit { preferences ->
            // We use edit block here just to access the snapshot safely, or use data.first()
            // But for performance in service, mapping flow is better.
            // See Service implementation for the actual usage.
        }
        return true // Placeholder, logic handled in Service via Flow
    }

    // Check saved version
    val lastSeenVersion: Flow<Int> = context.dataStore.data
        .map { it[LAST_VERSION_CODE_KEY] ?: 0 }

    // Update version after showing dialog
    suspend fun setLastSeenVersion(versionCode: Int) {
        context.dataStore.edit { it[LAST_VERSION_CODE_KEY] = versionCode }
    }
}