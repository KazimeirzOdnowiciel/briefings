package dev.lukat.briefing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

/**
 * The fold, as the system reports it.
 *
 * A foldable like the OnePlus Open reports a FoldingFeature with VERTICAL orientation;
 * `separating` is false for a continuous fold (no physical gap) and true when the app is
 * actually broken across two panels. Either way it means "you have two panes of room".
 */
data class FoldInfo(
    val vertical: Boolean,
    val separating: Boolean,
    val positionDp: Int,
)

private fun Context.activityOrNull(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun rememberFoldInfo(): FoldInfo? {
    val context = LocalContext.current
    val activity = remember(context) { context.activityOrNull() }
    var fold by remember { mutableStateOf<FoldInfo?>(null) }

    LaunchedEffect(activity) {
        val host = activity ?: return@LaunchedEffect
        val density = context.resources.displayMetrics.density
        runCatching {
            WindowInfoTracker.getOrCreate(host).windowLayoutInfo(host).collect { info: WindowLayoutInfo ->
                val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                fold = feature?.let {
                    val bounds = it.bounds
                    FoldInfo(
                        vertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
                        separating = it.isSeparating,
                        positionDp = (bounds.centerX() / density).toInt(),
                    )
                }
            }
        }
    }
    return fold
}
