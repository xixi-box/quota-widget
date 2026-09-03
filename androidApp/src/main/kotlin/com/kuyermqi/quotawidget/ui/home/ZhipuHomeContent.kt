package com.kuyermqi.quotawidget.ui.home

import android.content.res.Resources
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.ZhipuSettings
import com.kuyermqi.quotawidget.ui.components.ZhipuConfigContent
import com.kuyermqi.quotawidget.ui.usage.formatUsageWindowSummaryWithFallback
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class ZhipuHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var saved by mutableStateOf(ZhipuSettings())
        internal set
    var draftApiKey by mutableStateOf("")
        internal set
    var draftWindowKind by mutableStateOf(UsageWindowKind.ROLLING)
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var draftUsageProgressStyle by mutableStateOf(UsageProgressStyle.BAR)
        internal set
    var lastDisplay by mutableStateOf<String?>(null)
        internal set
    var lastWindows by mutableStateOf<List<QuotaWindow>>(emptyList())
        internal set
    var isSaving by mutableStateOf(false)
        internal set
    var saveError by mutableStateOf<String?>(null)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    val isConfigured: Boolean
        get() = saved.isConfigured

    val isDirty: Boolean
        get() = loaded && (
            draftApiKey != saved.apiKey ||
                draftWindowKind != saved.widgetWindowKind ||
                draftUsageDisplayMode != saved.usageDisplayMode ||
                draftUsageProgressStyle != saved.usageProgressStyle
            )

    fun applyLoaded(settings: ZhipuSettings) {
        saved = settings
        draftApiKey = settings.apiKey
        draftWindowKind = settings.widgetWindowKind
        draftUsageDisplayMode = settings.usageDisplayMode
        draftUsageProgressStyle = settings.usageProgressStyle
        loaded = true
    }

    fun summaryLabel(
        resources: Resources,
        widgetState: WidgetDisplayState,
        loadingMsg: String,
        reauthMsg: String,
    ): String? {
        if (!isConfigured) return null
        return when (widgetState) {
            is WidgetDisplayState.Success -> formatUsageWindowSummaryWithFallback(
                resources = resources,
                windows = widgetState.snapshot.windows,
                windowKind = saved.widgetWindowKind,
                usageDisplayMode = saved.usageDisplayMode,
                fallback = resources.getString(R.string.usage_unavailable),
            )
            WidgetDisplayState.Loading -> lastDisplay ?: loadingMsg
            is WidgetDisplayState.Error -> lastDisplay
            WidgetDisplayState.NeedsReauth -> reauthMsg
            WidgetDisplayState.NotConfigured -> null
        }
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

@Composable
fun rememberZhipuHomeState(
    settingsRepository: PlatformSettingsRepository,
): ZhipuHomeState = remember(settingsRepository) {
    ZhipuHomeState(settingsRepository)
}

@Composable
fun ZhipuHomeEffects(state: ZhipuHomeState): WidgetDisplayState {
    val observed by state.repository().observeWidgetState(PlatformIds.ZHIPU)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    val resources = LocalResources.current
    LaunchedEffect(observed, state.saved.widgetWindowKind, state.saved.usageDisplayMode) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastWindows = success.snapshot.windows
        state.lastDisplay = formatUsageWindowSummaryWithFallback(
            resources = resources,
            windows = success.snapshot.windows,
            windowKind = state.saved.widgetWindowKind,
            usageDisplayMode = state.saved.usageDisplayMode,
            fallback = resources.getString(R.string.usage_unavailable),
        )
    }
    return observed
}

@Composable
fun ColumnScope.ZhipuHomeContent(
    state: ZhipuHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ZhipuConfigContent(
        apiKey = state.draftApiKey,
        onApiKeyChange = {
            state.draftApiKey = it
            state.saveError = null
        },
        windows = state.lastWindows,
        widgetWindowKind = state.draftWindowKind,
        onWidgetWindowKindChange = { kind ->
            state.draftWindowKind = kind
            state.saveError = null
        },
        usageDisplayMode = state.draftUsageDisplayMode,
        onUsageDisplayModeChange = { mode ->
            state.draftUsageDisplayMode = mode
            state.saveError = null
        },
        usageProgressStyle = state.draftUsageProgressStyle,
        onUsageProgressStyleChange = { style ->
            state.draftUsageProgressStyle = style
            state.saveError = null
        },
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        saveError = state.saveError,
        onSave = {
            scope.launch {
                state.isSaving = true
                state.saveError = null
                try {
                    val next = ZhipuSettings(
                        apiKey = state.draftApiKey.trim(),
                        widgetWindowKind = state.draftWindowKind,
                        usageDisplayMode = state.draftUsageDisplayMode,
                        usageProgressStyle = state.draftUsageProgressStyle,
                    )
                    state.repository().saveZhipuSettings(next)
                    state.saved = next
                    state.draftApiKey = next.apiKey
                    if (!next.isConfigured) {
                        state.lastDisplay = null
                        state.lastWindows = emptyList()
                    }
                    state.saveError = when (
                        val result = onRefreshPlatform(PlatformIds.ZHIPU)
                    ) {
                        is WidgetDisplayState.Error -> result.message
                        else -> null
                    }
                    WidgetGlanceState.syncAndUpdate(context, "zhipu_save")
                } finally {
                    state.isSaving = false
                }
            }
        },
    )
}
