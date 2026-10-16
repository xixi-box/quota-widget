package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.currentState
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.MainActivity
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.averageRpmDay
import com.kuyermqi.quotawidget.domain.averageTpmDay
import com.kuyermqi.quotawidget.domain.formatNewApiAdminRate
import com.kuyermqi.quotawidget.domain.formatRequestCount
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.domain.formatTokenCount
import com.kuyermqi.quotawidget.domain.resolveCodexUsageSummaryWindowKind
import com.kuyermqi.quotawidget.domain.usage.usageWindowLabelRes
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCodexUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCodexUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCodexUsageWindowKind
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCombinedDisplayState
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCombinedRefreshPhase
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toNewApiSubscriptionUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toNewApiSubscriptionUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toNewApiAdminStats
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toAppThemeSettings
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toOpenCodeUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toOpenCodeUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toOpenCodeUsageWindowKind
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toZhipuUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toZhipuUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toZhipuUsageWindowKind
import com.kuyermqi.quotawidget.widget.usage.UsageCapsuleProgressBar
import com.kuyermqi.quotawidget.widget.usage.UsageProgressBar

/**
 * 全平台综合组件: one section per selected platform showing that platform's
 * headline value or current configuration state.
 */
class CombinedOverviewWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val openApp = actionStartActivity<MainActivity>()
        val emptyText = context.getString(R.string.widget_combined_empty)
        provideContent {
            val prefs = currentState<Preferences>()
            val hiddenPlatforms = prefs.toAppThemeSettings().hiddenPlatforms
            CompositionLocalProvider(LocalWidgetAndroidContext provides context) {
                GlanceTheme {
                    CombinedContent(
                        rows = prefs.toCombinedRows(context)
                            .filter { it.platformId !in hiddenPlatforms },
                        emptyText = emptyText,
                        openApp = openApp,
                    )
                }
            }
        }
    }
}

private data class CombinedWindowBar(
    val label: String,
    val usedPercent: Double?,
    val displayMode: UsageDisplayMode,
)

private data class CombinedRow(
    val platformId: String,
    val name: String,
    val value: String,
    val secondaryValue: String? = null,
    val detailValues: List<String> = emptyList(),
    val windows: List<CombinedWindowBar> = emptyList(),
    val usageProgressStyle: UsageProgressStyle = UsageProgressStyle.BAR,
    val refreshPhase: RefreshIconPhase = RefreshIconPhase.Idle,
    val updatedAtEpochMs: Long = 0L,
)

private fun Preferences.toCombinedRows(context: Context): List<CombinedRow> =
    PlatformRegistry.platforms.map { platform ->
        val platformId = platform.id
        val name = PlatformRegistry.displayName(platformId)
        val state = toCombinedDisplayState(platformId)
        val phase = toCombinedRefreshPhase(platformId)
        when (platformId) {
            PlatformIds.OPENCODE_GO -> usageRow(
                context = context,
                platformId = platformId,
                name = name,
                state = state,
                phase = phase,
                windowKind = toOpenCodeUsageWindowKind(),
                displayMode = toOpenCodeUsageDisplayMode(),
                progressStyle = toOpenCodeUsageProgressStyle(),
            )
            PlatformIds.CODEX -> usageRow(
                context = context,
                platformId = platformId,
                name = name,
                state = state,
                phase = phase,
                windowKind = toCodexUsageWindowKind(),
                displayMode = toCodexUsageDisplayMode(),
                progressStyle = toCodexUsageProgressStyle(),
            )
            PlatformIds.ZHIPU -> usageRow(
                context = context,
                platformId = platformId,
                name = name,
                state = state,
                phase = phase,
                windowKind = toZhipuUsageWindowKind(),
                displayMode = toZhipuUsageDisplayMode(),
                progressStyle = toZhipuUsageProgressStyle(),
            )
            PlatformIds.NEW_API_SUBSCRIPTION -> usageRow(
                context = context,
                platformId = platformId,
                name = name,
                state = state,
                phase = phase,
                windowKind = UsageWindowKind.SUBSCRIPTION,
                displayMode = toNewApiSubscriptionUsageDisplayMode(),
                progressStyle = toNewApiSubscriptionUsageProgressStyle(),
            )
            PlatformIds.NEW_API -> {
                val stats = toNewApiAdminStats()
                val value = if (stats != null) {
                    context.getString(
                        R.string.widget_combined_admin_day,
                        formatTokenCount(stats.tokensDay),
                    )
                } else {
                    state.displayValue(context)
                }
                CombinedRow(
                    platformId, name, value,
                    secondaryValue = stats?.let {
                        context.getString(
                            R.string.widget_combined_admin_month,
                            formatTokenCount(it.tokensMonth),
                        )
                    },
                    detailValues = stats?.let {
                        listOf(
                            context.getString(
                                R.string.widget_combined_admin_requests_rpm,
                                formatRequestCount(it.requestsDay),
                                formatNewApiAdminRate(it.averageRpmDay()),
                            ),
                            context.getString(
                                R.string.widget_combined_admin_tpm,
                                formatNewApiAdminRate(it.averageTpmDay()),
                            ),
                        )
                    }.orEmpty(),
                    refreshPhase = phase,
                    updatedAtEpochMs = stats?.updatedAtEpochMs ?: 0L,
                )
            }
            else -> CombinedRow(
                platformId = platformId,
                name = name,
                value = if (state is WidgetDisplayState.Success) {
                    state.snapshot.primaryDisplay.ifBlank {
                        context.getString(R.string.usage_unavailable)
                    }
                } else {
                    state.displayValue(context)
                },
                refreshPhase = phase,
                updatedAtEpochMs = if (state is WidgetDisplayState.Success) {
                    state.snapshot.updatedAtEpochMs
                } else {
                    System.currentTimeMillis()
                },
            )
        }
    }

private fun usageRow(
    context: Context,
    platformId: String,
    name: String,
    state: WidgetDisplayState,
    phase: RefreshIconPhase,
    windowKind: UsageWindowKind,
    displayMode: UsageDisplayMode,
    progressStyle: UsageProgressStyle,
): CombinedRow {
    if (state !is WidgetDisplayState.Success) {
        return CombinedRow(platformId, name, state.displayValue(context), refreshPhase = phase)
    }
    val effectiveKind = if (platformId == PlatformIds.CODEX) {
        resolveCodexUsageSummaryWindowKind(state.snapshot.windows, windowKind) ?: windowKind
    } else {
        windowKind
    }
    val quotaKind = effectiveKind.toQuotaWindowKind()
    val usedPercent = state.snapshot.windows
        .find { it.kind == quotaKind }
        ?.usedPercent
    val bars = state.snapshot.windows
        .filter { it.usedPercent != null }
        .map { window ->
            CombinedWindowBar(
                label = context.getString(usageWindowLabelRes(window.kind, displayMode)),
                usedPercent = window.usedPercent,
                displayMode = displayMode,
            )
        }
    return CombinedRow(
        platformId = platformId,
        name = name,
        value = usedPercent?.let { formatUsageDisplayPercent(it, displayMode) }
            ?: context.getString(R.string.usage_unavailable),
        windows = bars,
        usageProgressStyle = progressStyle,
        refreshPhase = phase,
        updatedAtEpochMs = state.snapshot.updatedAtEpochMs,
    )
}

private fun WidgetDisplayState.displayValue(context: Context): String = when (this) {
    WidgetDisplayState.NotConfigured -> context.getString(R.string.widget_not_configured)
    WidgetDisplayState.Loading -> context.getString(R.string.widget_loading_balance)
    WidgetDisplayState.NeedsReauth -> context.getString(R.string.widget_needs_reauth)
    is WidgetDisplayState.Error -> context.getString(R.string.widget_fetch_failed)
    is WidgetDisplayState.Success -> snapshot.primaryDisplay.ifBlank {
        context.getString(R.string.usage_unavailable)
    }
}

private enum class CombinedDensity {
    Comfortable,
    Compact,
    Dense,
}

private data class CombinedLayout(
    val columns: Int,
    val density: CombinedDensity,
)

private fun resolveCombinedLayout(
    widthDp: Float,
    heightDp: Float,
    itemCount: Int,
): CombinedLayout {
    val needsMinimumHeightGrid = heightDp < 150f && itemCount >= 4
    val columns = if ((widthDp >= 220f || needsMinimumHeightGrid) && itemCount >= 4) 2 else 1
    val rowCount = ((itemCount + columns - 1) / columns).coerceAtLeast(1)
    val availablePerRow = (heightDp - 24f - (rowCount - 1) * 6f) / rowCount
    val density = when {
        availablePerRow >= 120f -> CombinedDensity.Comfortable
        availablePerRow >= 84f -> CombinedDensity.Compact
        else -> CombinedDensity.Dense
    }
    return CombinedLayout(columns = columns, density = density)
}

private data class CombinedDimensions(
    val outerHorizontal: Dp,
    val outerTop: Dp,
    val outerBottom: Dp,
    val rowGap: Dp,
    val columnGap: Dp,
    val cardHorizontal: Dp,
    val cardVertical: Dp,
    val cardTextGap: Dp,
    val nameSize: TextUnit,
    val valueSize: TextUnit,
    val contentScale: Float = 1f,
    val verticalScale: Float = 1f,
)

private fun combinedDimensions(density: CombinedDensity): CombinedDimensions = when (density) {
    CombinedDensity.Comfortable -> CombinedDimensions(
        outerHorizontal = 14.dp,
        outerTop = 12.dp,
        outerBottom = 14.dp,
        rowGap = 8.dp,
        columnGap = 8.dp,
        cardHorizontal = 10.dp,
        cardVertical = 8.dp,
        cardTextGap = 3.dp,
        nameSize = 10.sp,
        valueSize = 12.sp,
    )
    CombinedDensity.Compact -> CombinedDimensions(
        outerHorizontal = 12.dp,
        outerTop = 10.dp,
        outerBottom = 10.dp,
        rowGap = 6.dp,
        columnGap = 6.dp,
        cardHorizontal = 8.dp,
        cardVertical = 5.dp,
        cardTextGap = 2.dp,
        nameSize = 9.sp,
        valueSize = 11.sp,
    )
    CombinedDensity.Dense -> CombinedDimensions(
        outerHorizontal = 10.dp,
        outerTop = 8.dp,
        outerBottom = 8.dp,
        rowGap = 4.dp,
        columnGap = 4.dp,
        cardHorizontal = 6.dp,
        cardVertical = 3.dp,
        cardTextGap = 1.dp,
        nameSize = 8.sp,
        valueSize = 10.sp,
    )
}

@Composable
private fun CombinedContent(
    rows: List<CombinedRow>,
    emptyText: String,
    openApp: Action,
) {
    val size = LocalSize.current
    val layout = resolveCombinedLayout(size.width.value, size.height.value, rows.size)
    val dimensions = combinedDimensions(layout.density)
    val rowCount = ((rows.size + layout.columns - 1) / layout.columns).coerceAtLeast(1)
    val cardHeight = (
        (
            size.height.value -
                dimensions.outerTop.value -
                dimensions.outerBottom.value -
                dimensions.rowGap.value * (rowCount - 1)
            ) / rowCount
        ).coerceAtLeast(0f).dp
    val cardWidth = (
        (
            size.width.value -
                dimensions.outerHorizontal.value * 2f -
                dimensions.columnGap.value * (layout.columns - 1)
            ) / layout.columns
        ).coerceAtLeast(0f)
    // Small cards still shrink to avoid clipping. Large comfortable cards may
    // grow, but width remains the limiting factor for labels and long metrics.
    val shrinkScale = (cardHeight.value / 80f).coerceIn(0.85f, 1f)
    val growthScale = if (layout.density == CombinedDensity.Comfortable) {
        minOf(
            (cardHeight.value / 180f).coerceIn(1f, 1.25f),
            (cardWidth / 116f).coerceIn(1f, 1.18f),
        )
    } else {
        1f
    }
    val contentScale = shrinkScale * growthScale
    val verticalScale = if (layout.density == CombinedDensity.Comfortable) {
        (cardHeight.value / 150f).coerceIn(1f, 1.5f)
    } else {
        1f
    }
    val scaledDimensions = dimensions.copy(
        cardVertical = dimensions.cardVertical * contentScale,
        cardTextGap = dimensions.cardTextGap * verticalScale,
        nameSize = (dimensions.nameSize.value * contentScale).sp,
        valueSize = (dimensions.valueSize.value * contentScale).sp,
        contentScale = contentScale,
        verticalScale = verticalScale,
    )
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
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    start = dimensions.outerHorizontal,
                    top = dimensions.outerTop,
                    end = dimensions.outerHorizontal,
                    bottom = dimensions.outerBottom,
                ),
        ) {
            if (rows.isEmpty()) {
                Text(
                    text = emptyText,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                    maxLines = 2,
                )
            } else {
                rows.chunked(layout.columns).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .then(
                                if (rowIndex > 0) {
                                    GlanceModifier.padding(top = dimensions.rowGap)
                                } else {
                                    GlanceModifier
                                },
                            ),
                    ) {
                        rowItems.forEachIndexed { columnIndex, row ->
                            if (columnIndex > 0) {
                                Spacer(modifier = GlanceModifier.width(dimensions.columnGap))
                            }
                            CombinedPlatformCard(
                                row = row,
                                stacked = layout.columns > 1 && layout.density != CombinedDensity.Dense,
                                showValue = true,
                                showProgress = layout.density != CombinedDensity.Dense,
                                density = layout.density,
                                dimensions = scaledDimensions,
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .height(cardHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CombinedPlatformCard(
    row: CombinedRow,
    stacked: Boolean,
    showValue: Boolean,
    showProgress: Boolean,
    density: CombinedDensity,
    dimensions: CombinedDimensions,
    modifier: GlanceModifier,
) {
    val cardModifier = modifier
        .background(GlanceTheme.colors.secondaryContainer)
        .cornerRadius(10.dp)
        .padding(
            horizontal = dimensions.cardHorizontal,
            vertical = dimensions.cardVertical,
        )
    val hasBars = showProgress && row.windows.any { it.usedPercent != null }
    val expanded = dimensions.contentScale > 1.01f || dimensions.verticalScale > 1.01f
    if (stacked || hasBars) {
        Column(modifier = cardModifier) {
            CombinedPlatformHeader(row, dimensions, density)
            Spacer(
                modifier = if (expanded) {
                    GlanceModifier.defaultWeight()
                } else {
                    GlanceModifier.height(dimensions.cardTextGap)
                },
            )
            Column {
                if (row.windows.isNotEmpty()) {
                    row.windows.forEach { bar ->
                        CombinedUsageWindow(
                            bar = bar,
                            progressStyle = row.usageProgressStyle,
                            showProgress = hasBars,
                            density = density,
                            dimensions = dimensions,
                        )
                    }
                } else {
                    CombinedPlatformValue(row.value, dimensions)
                    row.secondaryValue?.let { secondaryValue ->
                        Spacer(modifier = GlanceModifier.height(dimensions.cardTextGap))
                        CombinedPlatformValue(secondaryValue, dimensions)
                    }
                    row.detailValues.forEach { detail ->
                        Spacer(modifier = GlanceModifier.height(dimensions.cardTextGap))
                        CombinedPlatformDetail(detail, dimensions)
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
            if (density == CombinedDensity.Comfortable && row.updatedAtEpochMs > 0L) {
                if (!expanded) {
                    Spacer(modifier = GlanceModifier.height(dimensions.cardTextGap))
                }
                Text(
                    text = WidgetDateFormatter.formatUpdatedAt(row.updatedAtEpochMs),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = (dimensions.nameSize.value * 0.85f).sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    } else {
        Row(
            modifier = cardModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CombinedPlatformName(
                name = row.name,
                dimensions = dimensions,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (showValue) {
                CombinedPlatformValue(row.value, dimensions)
            }
            CombinedRefreshButton(row, density, dimensions.contentScale)
        }
    }
}

@Composable
private fun CombinedUsageWindow(
    bar: CombinedWindowBar,
    progressStyle: UsageProgressStyle,
    showProgress: Boolean,
    density: CombinedDensity,
    dimensions: CombinedDimensions,
) {
    val usedPercent = bar.usedPercent ?: return
    val percentText = formatUsageDisplayPercent(usedPercent, bar.displayMode)
    val bottomGap = (if (density == CombinedDensity.Comfortable) 3.dp else 1.dp) *
        dimensions.verticalScale
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = bottomGap),
    ) {
        if (showProgress && progressStyle == UsageProgressStyle.CAPSULE) {
            UsageCapsuleProgressBar(
                label = bar.label,
                percentText = percentText,
                usedPercent = usedPercent,
                usageDisplayMode = bar.displayMode,
                height = (if (density == CombinedDensity.Comfortable) 24.dp else 16.dp) *
                    dimensions.contentScale,
                cornerRadius = (if (density == CombinedDensity.Comfortable) 7.dp else 5.dp) *
                    dimensions.contentScale,
                labelSize = dimensions.nameSize,
                percentSize = dimensions.valueSize,
            )
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CombinedPlatformLabel(
                    label = bar.label,
                    dimensions = dimensions,
                    modifier = GlanceModifier.defaultWeight(),
                )
                CombinedPlatformValue(
                    value = percentText,
                    dimensions = dimensions,
                )
            }
            if (showProgress) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                UsageProgressBar(
                    usedPercent = usedPercent,
                    usageDisplayMode = bar.displayMode,
                )
            }
        }
    }
}

@Composable
private fun CombinedPlatformHeader(
    row: CombinedRow,
    dimensions: CombinedDimensions,
    density: CombinedDensity,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CombinedPlatformName(
            name = row.name,
            dimensions = dimensions,
            modifier = GlanceModifier.defaultWeight(),
        )
        CombinedRefreshButton(row, density, dimensions.contentScale)
    }
}

@Composable
private fun CombinedRefreshButton(
    row: CombinedRow,
    density: CombinedDensity,
    contentScale: Float,
) {
    val hitSize = (when (density) {
        CombinedDensity.Comfortable -> 24.dp
        CombinedDensity.Compact -> 22.dp
        CombinedDensity.Dense -> 20.dp
    }) * contentScale
    val iconSize = (when (density) {
        CombinedDensity.Comfortable -> 18.dp
        CombinedDensity.Compact -> 16.dp
        CombinedDensity.Dense -> 14.dp
    }) * contentScale
    RefreshButton(
        platformId = row.platformId,
        phase = row.refreshPhase,
        hitSize = hitSize,
        iconSize = iconSize,
        spinnerSize = iconSize,
    )
}

@Composable
private fun CombinedPlatformName(
    name: String,
    dimensions: CombinedDimensions,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text = name,
        style = TextStyle(
            color = GlanceTheme.colors.onSecondaryContainer,
            fontSize = dimensions.nameSize,
        ),
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun CombinedPlatformValue(
    value: String,
    dimensions: CombinedDimensions,
) {
    Text(
        text = value,
        style = TextStyle(
            color = GlanceTheme.colors.onSecondaryContainer,
            fontSize = dimensions.valueSize,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
}

@Composable
private fun CombinedPlatformDetail(
    value: String,
    dimensions: CombinedDimensions,
) {
    Text(
        text = value,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = dimensions.nameSize,
        ),
        maxLines = if (dimensions.verticalScale > 1.01f) 2 else 1,
    )
}

@Composable
private fun CombinedPlatformLabel(
    label: String,
    dimensions: CombinedDimensions,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text = label,
        style = TextStyle(
            color = GlanceTheme.colors.onSecondaryContainer,
            fontSize = dimensions.nameSize,
        ),
        maxLines = 1,
        modifier = modifier,
    )
}

class CombinedOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CombinedOverviewWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
