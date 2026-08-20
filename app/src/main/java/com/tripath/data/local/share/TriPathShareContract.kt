package com.tripath.data.local.share

import android.net.Uri

/**
 * The read-only surface TriPath exposes to LiftPath.
 *
 * LiftPath's fatigue model only knows about lifting; everything it learns about cardio load,
 * recovery and energy comes through here. This file is duplicated verbatim in LiftPath
 * (`com.liftpath.helpers.TriPathContract`) — two sideloaded apps, no shared module — so
 * [CONTRACT_VERSION] is what lets either side notice the other is stale instead of silently
 * reading absent columns.
 *
 * **Bump [CONTRACT_VERSION] in BOTH files whenever a column is added, removed or re-typed.**
 */
object TriPathShareContract {

    const val AUTHORITY = "com.tripath.share"

    /** Only this package may query the provider; see TriPathShareProvider.assertCallerAllowed. */
    const val CONSUMER_PACKAGE = "com.liftpath"

    const val CONTRACT_VERSION = 1

    /** Cheap liveness + version probe. One row, always. */
    const val PATH_HANDSHAKE = "handshake"

    /** One row per calendar day in the requested range, joined across every data source. */
    const val PATH_DAYS = "days"

    /** One row per non-ignored workout session in the requested range. */
    const val PATH_WORKOUTS = "workouts"

    /** Inclusive ISO-8601 date bounds (`yyyy-MM-dd`) for [PATH_DAYS] and [PATH_WORKOUTS]. */
    const val QUERY_FROM = "from"
    const val QUERY_TO = "to"

    val URI_HANDSHAKE: Uri = Uri.parse("content://$AUTHORITY/$PATH_HANDSHAKE")
    val URI_DAYS: Uri = Uri.parse("content://$AUTHORITY/$PATH_DAYS")
    val URI_WORKOUTS: Uri = Uri.parse("content://$AUTHORITY/$PATH_WORKOUTS")

    object Handshake {
        const val CONTRACT_VERSION = "contract_version"
        const val APP_VERSION_NAME = "app_version_name"
        const val WORKOUT_COUNT = "workout_count"
        const val LATEST_WORKOUT_DATE = "latest_workout_date"
        const val LATEST_WELLNESS_DATE = "latest_wellness_date"

        val COLUMNS = arrayOf(
            CONTRACT_VERSION,
            APP_VERSION_NAME,
            WORKOUT_COUNT,
            LATEST_WORKOUT_DATE,
            LATEST_WELLNESS_DATE
        )
    }

    object Days {
        const val DATE = "date"

        /** Summed Training Stress Score for the day. */
        const val TSS = "tss"

        /** Banister Chronic Training Load (fitness), 42-day EWMA. */
        const val CTL = "ctl"

        /** Banister Acute Training Load (fatigue), 7-day EWMA. */
        const val ATL = "atl"

        /** Training Stress Balance (form) = ctl − atl. */
        const val TSB = "tsb"

        const val INTAKE_KCAL = "intake_kcal"

        /** TDEE: resting baseline + training burn. Null when demographics/weight are incomplete. */
        const val EXPENDITURE_KCAL = "expenditure_kcal"

        /** intake − expenditure. Null when either side is missing. */
        const val BALANCE_KCAL = "balance_kcal"

        /** Forward-filled last known body weight on or before the day. */
        const val WEIGHT_KG = "weight_kg"

        const val SLEEP_MINUTES = "sleep_minutes"
        const val SLEEP_SCORE = "sleep_score"

        /** Morning HRV (RMSSD, ms) from the day's wellness log. */
        const val HRV_RMSSD = "hrv_rmssd"

        /** Subjective soreness, 1–10. Higher is worse. */
        const val SORENESS = "soreness"

        /** Subjective mood, 1–10. Higher is better. */
        const val MOOD = "mood"

        val COLUMNS = arrayOf(
            DATE, TSS, CTL, ATL, TSB,
            INTAKE_KCAL, EXPENDITURE_KCAL, BALANCE_KCAL, WEIGHT_KG,
            SLEEP_MINUTES, SLEEP_SCORE, HRV_RMSSD, SORENESS, MOOD
        )
    }

    object Workouts {
        /**
         * Health Connect `ExerciseSessionRecord.metadata.id`. LiftPath stores the same string as
         * `ExternalActivity.id`, so the two sources deduplicate on an exact match.
         */
        const val CONNECT_ID = "connect_id"

        const val DATE = "date"

        /** `WorkoutType` name: RUN, BIKE, SWIM, STRENGTH, WALK, HIKE, OTHER. */
        const val TYPE = "type"

        const val DURATION_MINUTES = "duration_minutes"
        const val AVG_HR = "avg_hr"
        const val CALORIES = "calories"
        const val TSS = "tss"
        const val DISTANCE_M = "distance_m"

        /** JSON object of zone name → seconds, e.g. `{"Z1":300,"Z4":600}`. */
        const val HR_ZONE_JSON = "hr_zone_json"

        /** Real session bounds from the raw Health Connect record; null if it was pruned. */
        const val START_MILLIS = "start_millis"
        const val END_MILLIS = "end_millis"

        val COLUMNS = arrayOf(
            CONNECT_ID, DATE, TYPE, DURATION_MINUTES, AVG_HR, CALORIES, TSS,
            DISTANCE_M, HR_ZONE_JSON, START_MILLIS, END_MILLIS
        )
    }
}
