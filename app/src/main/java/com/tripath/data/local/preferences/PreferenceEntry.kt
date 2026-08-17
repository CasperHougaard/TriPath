package com.tripath.data.local.preferences

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A single DataStore preference captured in a portable, type-tagged form.
 *
 * DataStore keys are typed ([androidx.datastore.preferences.core.intPreferencesKey] and friends),
 * but a backup file only carries text. The [type] tag records which key factory to use when the
 * value is written back, so an `Int` preference is never restored as a `String` — which DataStore
 * would reject at read time with a ClassCastException.
 */
data class PreferenceEntry(
    val key: String,
    val type: String,
    val value: String
) {
    companion object {
        const val TYPE_BOOLEAN = "b"
        const val TYPE_INT = "i"
        const val TYPE_LONG = "l"
        const val TYPE_FLOAT = "f"
        const val TYPE_DOUBLE = "d"
        const val TYPE_STRING = "s"
        const val TYPE_STRING_SET = "ss"

        /**
         * Build an entry from a raw DataStore value, or null if the value is of a type this
         * app has never stored (in which case there is nothing meaningful to round-trip).
         */
        fun of(key: String, value: Any?): PreferenceEntry? = when (value) {
            is Boolean -> PreferenceEntry(key, TYPE_BOOLEAN, value.toString())
            is Int -> PreferenceEntry(key, TYPE_INT, value.toString())
            is Long -> PreferenceEntry(key, TYPE_LONG, value.toString())
            is Float -> PreferenceEntry(key, TYPE_FLOAT, value.toString())
            is Double -> PreferenceEntry(key, TYPE_DOUBLE, value.toString())
            is String -> PreferenceEntry(key, TYPE_STRING, value)
            is Set<*> -> PreferenceEntry(
                key,
                TYPE_STRING_SET,
                Json.encodeToString(value.filterIsInstance<String>().toSet())
            )
            else -> null
        }
    }
}
