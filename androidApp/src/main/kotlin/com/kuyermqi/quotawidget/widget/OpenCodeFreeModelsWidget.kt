package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.MainActivity
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.FreeModelEntry
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toFreeModels

/**
 * OpenCode free models: account-independent list from the official Go pricing
 * page, so the widget works without signing in. Tapping a row copies the
 * callable model id.
 */
class OpenCodeFreeModelsWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.OPENCODE_GO) { _, _, openApp, _ ->
            val prefs = currentState<Preferences>()
            FreeModelsWidgetContent(
                models = prefs.toFreeModels(),
                openApp = openApp,
            )
        }
    }
}

@Composable
private fun FreeModelsWidgetContent(
    models: List<FreeModelEntry>,
    openApp: Action,
) {
    // Shrink the type as the widget gets smaller, and cap the row count to what
    // RemoteViews comfortably renders.
    val heightDp = LocalSize.current.height.value
    val density = when {
        heightDp >= 250f -> WidgetDensity.Comfortable
        heightDp >= 170f -> WidgetDensity.Compact
        else -> WidgetDensity.Dense
    }
    androidx.glance.layout.Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .systemWidgetBackgroundCornerRadius()
            .clickableNoRipple(openApp),
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_rounded_bg),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    start = density.padding,
                    top = density.padding,
                    end = density.padding,
                    bottom = density.padding,
                ),
        ) {
            Text(
                text = contextString(R.string.widget_opencode_free_label),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = density.titleSize,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickableNoRipple(openApp),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (models.isEmpty()) {
                Text(
                    text = contextString(R.string.opencode_free_models_loading),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = density.rowSize,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.clickableNoRipple(openApp),
                )
            } else {
                models.take(density.maxRows).forEach { entry ->
                    val copy = actionRunCallback<CopyTextAction>(
                        parameters = actionParametersOf(
                            CopyTextAction.TEXT_KEY to entry.copyText,
                            CopyTextAction.LABEL_KEY to entry.name,
                        ),
                    )
                    Text(
                        text = entry.modelId.ifBlank { entry.name },
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = density.rowSize,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickableNoRipple(copy),
                    )
                    Spacer(GlanceModifier.height(density.rowGap))
                }
                if (models.size > density.maxRows) {
                    Text(
                        text = "+${models.size - density.maxRows}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = density.rowSize,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.clickableNoRipple(openApp),
                    )
                }
            }
        }
    }
}

private enum class WidgetDensity(
    val padding: androidx.compose.ui.unit.Dp,
    val titleSize: androidx.compose.ui.unit.TextUnit,
    val rowSize: androidx.compose.ui.unit.TextUnit,
    val rowGap: androidx.compose.ui.unit.Dp,
    val maxRows: Int,
) {
    Comfortable(14.dp, 14.sp, 12.sp, 4.dp, 8),
    Compact(12.dp, 13.sp, 11.sp, 3.dp, 6),
    Dense(10.dp, 12.sp, 10.sp, 2.dp, 4),
}

class OpenCodeFreeModelsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OpenCodeFreeModelsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
