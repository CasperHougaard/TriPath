package com.tripath.ui.components.musclemap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.LruCache
import androidx.annotation.DrawableRes
import com.tripath.R

/**
 * Composites the illustrated body diagram — `muscle_base` plus tinted `muscle_mask_*` overlays —
 * into one bitmap.
 *
 * Each mask is a transparent PNG whose opaque pixels get painted with a
 * `PorterDuffColorFilter(color, SRC_IN)`: the standard Android silhouette-tinting technique, and
 * the same one LiftPath uses, so the two apps' maps are pixel-identical.
 *
 * ## Memory, which is the whole design constraint here
 * The artwork is 960×960 and there are twenty masks. Decoded at full size as ARGB_8888 that is
 * roughly 74 MB of bitmaps, so:
 *
 *  - Everything decodes at [SAMPLE_SIZE], which must be the same for the base and every mask —
 *    masks are drawn at (0, 0) over the base and only line up if they share its dimensions.
 *  - Masks are decoded, drawn, and dropped rather than cached. One mask at a time is resident, so
 *    peak overhead is a single bitmap instead of twenty.
 *  - Finished composites *are* cached by colour, which is what makes stepping back and forth
 *    through days feel instant: a day already drawn costs nothing to revisit.
 *
 * [render] is therefore cheap on a cache hit and around twenty PNG decodes on a miss. Call it off
 * the main thread.
 */
internal object MuscleMapRenderer {

    /**
     * Decode downscale. 480×480 is comfortably past what a body diagram inside a card needs, and a
     * quarter of the memory of the source.
     */
    private const val SAMPLE_SIZE = 2

    /** Four composites — enough that scrubbing between recent days stays instant. */
    private const val COMPOSITE_CACHE_MAX_BYTES = 4 * 1024 * 1024

    private val decodeOptions = BitmapFactory.Options().apply { inSampleSize = SAMPLE_SIZE }

    @Volatile
    private var baseBitmap: Bitmap? = null

    private val compositeCache = object : LruCache<String, Bitmap>(COMPOSITE_CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Draws [maskColors] (mask drawable id to ARGB colour) over the base body, in list order, and
     * returns the composite. Identical calls are served from cache regardless of ordering.
     */
    fun render(context: Context, maskColors: List<Pair<Int, Int>>): Bitmap {
        val cacheKey = maskColors
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString(separator = "|") { (maskResId, color) -> "$maskResId:$color" }
        compositeCache.get(cacheKey)?.let { if (!it.isRecycled) return it }

        val result = base(context).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        maskColors.forEach { (maskResId, color) ->
            val mask = decode(context, maskResId) ?: return@forEach
            paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(mask, 0f, 0f, paint)
            // Not cached: twenty resident masks would cost more than the map is worth. See above.
            mask.recycle()
        }

        compositeCache.put(cacheKey, result)
        return result
    }

    /** The untinted body, retained — it is needed by every composite. */
    private fun base(context: Context): Bitmap =
        baseBitmap?.takeIf { !it.isRecycled }
            ?: decode(context, R.drawable.muscle_base)!!.also { baseBitmap = it }

    private fun decode(context: Context, @DrawableRes resId: Int): Bitmap? =
        BitmapFactory.decodeResource(context.resources, resId, decodeOptions)

    /** Releases everything cached, for a low-memory callback. */
    fun clearCaches() {
        baseBitmap = null
        compositeCache.evictAll()
    }

    /**
     * Flattens per-group colours down to the per-mask list [render] wants, with the most heavily
     * loaded group painted last so it wins any mask two groups share.
     *
     * Nothing shares a mask in [MuscleMapAssets] today, but the ordering costs nothing and stops a
     * future split (separate glutes and hamstrings, say) from rendering whichever group the map
     * happened to iterate last.
     */
    fun maskColors(
        groupColors: Map<String, Int>,
        rank: (String) -> Int
    ): List<Pair<Int, Int>> {
        val byMask = LinkedHashMap<Int, Int>()
        groupColors.entries
            .sortedBy { (group, _) -> rank(group) }
            .forEach { (group, color) ->
                MuscleMapAssets.maskResIds[group]?.forEach { maskResId -> byMask[maskResId] = color }
            }
        return byMask.toList()
    }
}
