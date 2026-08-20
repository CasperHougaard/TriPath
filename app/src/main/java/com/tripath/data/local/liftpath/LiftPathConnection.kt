package com.tripath.data.local.liftpath

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor

/**
 * Tracks whether LiftPath is installed, whether the athlete has turned the integration on, and
 * the outcome of the last handshake — mirrors LiftPath's own `TriPathConnection` so both apps
 * follow the same "installed AND enabled AND last handshake ok" rule for [isActive].
 */
object LiftPathConnection {

    const val PREFS_NAME = "liftpath_settings"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_HANDSHAKE_OK = "handshake_ok"
    private const val KEY_HANDSHAKE_TIME = "handshake_time"
    private const val KEY_CONTRACT_VERSION = "contract_version_seen"
    private const val KEY_SCHEMA_HASH = "schema_hash_seen"
    private const val KEY_APP_VERSION = "app_version_seen"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"

    data class Handshake(
        val contractVersion: Int,
        val schemaHash: String?,
        val capabilities: List<String>,
        val appVersionName: String?,
        val sessionCount: Int,
        val latestSessionDate: String?
    ) {
        /** True when both the contract version and the column-level schema agree. */
        val versionMatches: Boolean
            get() = contractVersion == LiftPathShareContract.CONTRACT_VERSION &&
                schemaHash == LiftPathShareContract.schemaHash()

        fun hasCapability(token: String): Boolean = capabilities.contains(token)
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(LiftPathShareContract.PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Installed, enabled, and the last handshake succeeded — the same three-part rule as LiftPath's. */
    fun isActive(context: Context): Boolean =
        isInstalled(context) && isEnabled(context) && prefs(context).getBoolean(KEY_HANDSHAKE_OK, false)

    fun lastSyncTime(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC_TIME, 0L)

    fun markSynced(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC_TIME, at).apply()
    }

    /** Queries the handshake row and records the outcome for [isActive]/[isSchemaStale] to read later. */
    fun handshake(context: Context): Handshake? {
        val cursor = context.contentResolver.query(
            LiftPathShareContract.URI_HANDSHAKE, null, null, null, null
        ) ?: run {
            recordHandshake(context, ok = false)
            return null
        }
        return cursor.use {
            if (!it.moveToFirst()) {
                recordHandshake(context, ok = false)
                return null
            }
            val result = Handshake(
                contractVersion = it.optInt(LiftPathShareContract.Handshake.CONTRACT_VERSION) ?: -1,
                schemaHash = it.optString(LiftPathShareContract.Handshake.SCHEMA_HASH),
                capabilities = it.optString(LiftPathShareContract.Handshake.CAPABILITIES)
                    ?.split(",")
                    ?.filter { token -> token.isNotBlank() }
                    ?: emptyList(),
                appVersionName = it.optString(LiftPathShareContract.Handshake.APP_VERSION_NAME),
                sessionCount = it.optInt(LiftPathShareContract.Handshake.SESSION_COUNT) ?: 0,
                latestSessionDate = it.optString(LiftPathShareContract.Handshake.LATEST_SESSION_DATE)
            )
            recordHandshake(context, ok = true, result)
            result
        }
    }

    private fun recordHandshake(context: Context, ok: Boolean, handshake: Handshake? = null) {
        prefs(context).edit().apply {
            putBoolean(KEY_HANDSHAKE_OK, ok)
            putLong(KEY_HANDSHAKE_TIME, System.currentTimeMillis())
            if (handshake != null) {
                putInt(KEY_CONTRACT_VERSION, handshake.contractVersion)
                putString(KEY_SCHEMA_HASH, handshake.schemaHash)
                putString(KEY_APP_VERSION, handshake.appVersionName)
            }
        }.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Cursor.optString(name: String): String? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.optInt(name: String): Int? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getInt(i)
    }
}
