package com.tripath.ui.components.musclemap

import com.tripath.R
import com.tripath.domain.strain.MuscleGroups

/**
 * Maps each of [MuscleGroups]' display groups to the body-diagram mask(s) that represent it.
 *
 * Mask artwork is adapted from github.com/MertenD/musclegroup-image-generator, used under its
 * Non-Commercial Source License, and is shared with LiftPath — the same PNGs, so a muscle looks the
 * same in both apps. TriPath is a personal, non-commercial project, which is what makes that
 * license-compliant; revisit if that ever changes. Assets live in `res/drawable-nodpi/`.
 *
 * ## Why this is keyed by group, not by muscle
 * LiftPath's own table maps its 24-value `TargetMuscle` enum onto these masks. TriPath never sees
 * that enum: [com.tripath.domain.strain.StrainTimeline.muscleFreshness] is already collapsed to the
 * ten user-facing groups, because freshness is only meaningful at the granularity the strain model
 * scores. So the mapping starts one level coarser, and several masks share a group.
 *
 * Two of LiftPath's finer distinctions are deliberately dropped rather than approximated. It paints
 * hip flexors onto the quadriceps mask and tibialis onto the calf mask, which works when the
 * highlight is "this exercise targets that"; here it would colour a whole quad from hip work and
 * report a freshness the model never computed for it. Groups keep only masks they actually own.
 */
internal object MuscleMapAssets {

    /** Display group -> masks painted with that group's colour. Later masks overpaint earlier. */
    val maskResIds: Map<String, List<Int>> = mapOf(
        MuscleGroups.CHEST to listOf(R.drawable.muscle_mask_chest),
        MuscleGroups.BACK to listOf(
            R.drawable.muscle_mask_latissimus,
            R.drawable.muscle_mask_back_upper,
            R.drawable.muscle_mask_back_lower,
            // Upper traps: the source artwork has no dedicated traps region, and LiftPath uses the
            // neck mask as its proxy. Kept for consistency between the two apps.
            R.drawable.muscle_mask_neck
        ),
        MuscleGroups.SHOULDERS to listOf(
            R.drawable.muscle_mask_shoulders,
            R.drawable.muscle_mask_shoulders_front,
            R.drawable.muscle_mask_shoulders_back
        ),
        MuscleGroups.ARMS to listOf(
            R.drawable.muscle_mask_biceps,
            R.drawable.muscle_mask_triceps
        ),
        MuscleGroups.FOREARMS to listOf(R.drawable.muscle_mask_forearms),
        MuscleGroups.CORE to listOf(
            R.drawable.muscle_mask_core_upper,
            R.drawable.muscle_mask_core_lower,
            R.drawable.muscle_mask_core_side
        ),
        MuscleGroups.QUADS to listOf(R.drawable.muscle_mask_quadriceps),
        MuscleGroups.HAMSTRINGS_GLUTES to listOf(
            R.drawable.muscle_mask_hamstring,
            R.drawable.muscle_mask_gluteus
        ),
        MuscleGroups.HIPS to listOf(
            R.drawable.muscle_mask_adductors,
            R.drawable.muscle_mask_abductors
        ),
        MuscleGroups.CALVES to listOf(R.drawable.muscle_mask_calfs)
    )

    /**
     * Groups the diagram can actually show, in head-to-toe order.
     *
     * [MuscleGroups.OTHER] is absent on purpose — it is the bucket for muscles the catalog did not
     * name, and there is no region of a body to paint for "we do not know where this was".
     */
    val displayableGroups: List<String> = MuscleGroups.all.filter { it in maskResIds }
}
