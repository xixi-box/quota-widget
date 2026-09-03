package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.content.res.Configuration
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
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.NewApiAdminStats
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.averageRpmDay
import com.kuyermqi.quotawidget.domain.averageTpmDay
import com.kuyermqi.quotawidget.domain.formatNewApiAdminRate
import com.kuyermqi.quotawidget.domain.formatRequestCount
import com.kuyermqi.quotawidget.domain.formatTokenCount
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toNewApiAdminStats

/**
 * NewAPI admin platform usage widget: day/month token totals, realtime
 * RPM/TPM, and the 24h/30d token curves (drawn as bitmaps — Glance has no
 * Canvas composable).
 */
class NewApiAdminWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.NEW_API) { _, refreshPhase, openApp, platformTitle ->
            val prefs = currentState<Preferences>()
            Box(
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
                NewApiAdminCard(
                    platformTitle = platformTitle,
                    refreshPhase = refreshPhase,
                    openApp = openApp,
                    stats = prefs.toNewApiAdminStats(),
                    nightMode = isNightMode(context),
                    density = context.resources.displayMetrics.density,
                    comfortable = true,
                    fillHeight = true,
                )
            }
        }
    }
}

private fun isNightMode(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

/**
 * Card content without its own background; reused by the combined overview
 * widget. [comfortable] picks roomier paddings; [fillHeight] pushes the footer
 * to the card bottom (single widget).
 */
@Composable
internal fun NewApiAdminCard(
    platformTitle: String,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
    stats: NewApiAdminStats?,
    nightMode: Boolean,
    density: Float,
    comfortable: Boolean,
    fillHeight: Boolean,
    densityScale: Float = 1f,
    statusTitleSizeSp: Float = 18f,
) {
    val s = densityScale
    val outerPad = (if (comfortable) 14.dp else 10.dp) * s
    val topPad = (if (comfortable) 12.dp else 8.dp) * s
    val bottomPad = (if (comfortable) 16.dp else 10.dp) * s
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .then(if (fillHeight) GlanceModifier.fillMaxSize() else GlanceModifier)
            .padding(start = outerPad, top = topPad, end = outerPad, bottom = bottomPad),
    ) {
        WidgetHeader(PlatformIds.NEW_API, platformTitle, refreshPhase, openApp, scale = densityScale)
        if (fillHeight) {
            Spacer(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .clickableNoRipple(openApp),
            )
        } else {
            Spacer(modifier = GlanceModifier.height(6.dp * s))
        }
        if (stats == null) {
            Text(
                text = contextString(R.string.widget_not_configured),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = statusTitleSizeSp.sp * s,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickableNoRipple(openApp),
            )
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = contextString(R.string.new_api_admin_stat_day),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp * s,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = formatTokenCount(stats.tokensDay),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 26.sp * s,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = "${contextString(R.string.new_api_admin_stat_requests_day)} " +
                            formatRequestCount(stats.requestsDay),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp * s,
                        ),
                        maxLines = 1,
                    )
                }
                Column(modifier = GlanceModifier.padding(start = 10.dp * s)) {
                    Text(
                        text = contextString(R.string.new_api_admin_stat_month),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp * s,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = formatTokenCount(stats.tokensMonth),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 18.sp * s,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = "RPM ${formatNewApiAdminRate(stats.averageRpmDay())}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp * s,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = "TPM ${formatNewApiAdminRate(stats.averageTpmDay())}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp * s,
                        ),
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(6.dp * s))
            ChartRow(stats, nightMode, density, s, openApp)
            Spacer(modifier = GlanceModifier.height(4.dp * s))
            Text(
                text = "更新于 " + WidgetDateFormatter.formatUpdatedAt(stats.updatedAtEpochMs),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp * s,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickableNoRipple(openApp),
            )
        }
    }
}

@Composable
private fun ChartRow(
    stats: NewApiAdminStats,
    nightMode: Boolean,
    density: Float,
    scale: Float,
    openApp: Action,
) {
    val lineColor = TokenCurveBitmap.lineColor(nightMode)
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiniChart(
            values = stats.daySeries,
            caption = contextString(R.string.widget_new_api_admin_chart_24),
            lineColor = lineColor,
            density = density,
            scale = scale,
            openApp = openApp,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(10.dp * scale))
        MiniChart(
            values = stats.monthSeries,
            caption = contextString(R.string.widget_new_api_admin_chart_30),
            lineColor = lineColor,
            density = density,
            scale = scale,
            openApp = openApp,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun MiniChart(
    values: List<Long>,
    caption: String,
    lineColor: Int,
    density: Float,
    scale: Float,
    openApp: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(
            text = caption,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp * scale,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(2.dp * scale))
        val chartHeight = 34.dp * scale
        val bitmap = TokenCurveBitmap.build(
            values = values,
            widthPx = (150 * density * scale).toInt().coerceAtLeast(2),
            heightPx = (34 * density * scale).toInt().coerceAtLeast(2),
            lineColor = lineColor,
        )
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(chartHeight)
                .clickableNoRipple(openApp),
        )
    }
}

class NewApiAdminWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewApiAdminWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
