package com.tripath.data.model

/**
 * Where the *future* half of every forward-looking figure comes from.
 *
 * CTL/ATL projection, carbohydrate preloading and projected readiness all need an answer to "what
 * training is coming?". Letting each of them answer separately is how Coach, Fuel and Planner end
 * up believing three different things about next week, so they all read
 * [com.tripath.domain.ProjectionSource], and this is its one switch.
 */
enum class ProjectionMode(val label: String, val description: String) {
    /** Future days come from the planner. Accurate exactly to the extent the plan is filled in. */
    PLANNED(
        label = "Use my plan",
        description = "Project from planned sessions"
    ),

    /**
     * Future days assume training carries on as it has been. The default while the planner is
     * incomplete, because an empty plan projects a fitness collapse that isn't going to happen.
     */
    RECENT_PATTERN(
        label = "Assume I keep training",
        description = "Project from the last 8 weeks"
    );

    companion object {
        val DEFAULT = RECENT_PATTERN

        fun fromName(name: String?): ProjectionMode? = entries.firstOrNull { it.name == name }
    }
}
