package com.tripath.ui.components.musclemap

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.tripath.R
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.muscleLoadColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The front-and-back body diagram, each muscle group tinted by how loaded it is.
 *
 * [freshnessByGroup] is keyed by [com.tripath.domain.strain.MuscleGroups] display name — the map
 * [com.tripath.domain.strain.StrainState.muscleFreshness] already produces. Groups that are absent,
 * and groups that are simply clear, both get the neutral fill; see
 * [com.tripath.ui.theme.muscleLoadColor] for why that conflation is deliberate.
 *
 * Compositing is real work (see [MuscleMapRenderer]), so it happens in [produceState] off the main
 * thread. Until the first composite lands the untinted base is shown rather than a spinner — the
 * body is the bulk of the image either way, so it reads as the tint arriving rather than as a load.
 */
@Composable
fun MuscleMap(
    freshnessByGroup: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = TriPathTheme.colors

    // Resolved outside the coroutine: the renderer works in ARGB ints and must not touch the theme.
    val maskColors = remember(freshnessByGroup, colors) {
        MuscleMapRenderer.maskColors(
            groupColors = MuscleMapAssets.displayableGroups.associateWith { group ->
                muscleLoadColor(freshnessByGroup[group], colors).toArgb()
            },
            // Most loaded painted last, so it wins any shared mask.
            rank = { group -> freshnessByGroup[group]?.let { 100 - it } ?: 0 }
        )
    }

    val composite by produceState<ImageBitmap?>(initialValue = null, maskColors) {
        value = withContext(Dispatchers.Default) {
            MuscleMapRenderer.render(context, maskColors).asImageBitmap()
        }
    }

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        val current = composite
        if (current == null) {
            Image(
                painter = painterResource(R.drawable.muscle_base),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Image(
                bitmap = current,
                contentDescription = "Body diagram shaded by how loaded each muscle group is",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
