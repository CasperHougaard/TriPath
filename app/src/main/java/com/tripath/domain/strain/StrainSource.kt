package com.tripath.domain.strain

/**
 * Which of the two data sources behind a strain figure it was built from.
 *
 * The app's default is [BOTH] everywhere — a body does not care which app logged the session — and
 * the split exists for one screen: the freshness detail view, where an athlete asking "why does the
 * model think my legs are wrecked" needs to be able to separate "because of my lifting" from
 * "because of my riding".
 *
 * ## Why two flags rather than a filter per call site
 * The awkward part of splitting these sources is that strength training arrives *twice* — once as a
 * Health Connect `STRENGTH` record carrying a duration and a flat per-hour TSS, and once as
 * LiftPath's individual sets. [StrainAnalytics] already drops the former wherever the latter exists,
 * because the set-level version knows which muscles did the work.
 *
 * Expressing the source as these two flags makes that rule fall out for free rather than needing a
 * second copy of it:
 *
 * - [TRI_PATH] leaves the lift-set map empty, so nothing is superseded and the Health Connect
 *   strength record scores at its own estimate — the only view of that session this mode has.
 * - [LIFT_PATH] never runs the workout loop at all, so there is nothing to supersede.
 * - [BOTH] sees both, and the existing dedup applies unchanged.
 *
 * The consequence is worth stating plainly: on a lifting day, [BOTH] is **not** the sum of the other
 * two, precisely because it discards the Health Connect record in favour of the sets.
 */
enum class StrainSource(val label: String, val detail: String) {

    LIFT_PATH(
        label = "LiftPath",
        detail = "Set-level lifting detail only — rides, runs and swims excluded"
    ),

    TRI_PATH(
        label = "TriPath",
        detail = "Health Connect sessions only — a lifting day counts as its duration, " +
            "with no per-muscle detail"
    ),

    BOTH(
        label = "Both",
        detail = "Everything, strength counted once — LiftPath's sets replace the " +
            "Health Connect record for that day"
    );

    /** Whether Health Connect workout sessions contribute. */
    val includesWorkouts: Boolean get() = this != LIFT_PATH

    /** Whether LiftPath's set-level detail contributes — and therefore whether dedup can fire. */
    val includesLiftDetail: Boolean get() = this != TRI_PATH
}
