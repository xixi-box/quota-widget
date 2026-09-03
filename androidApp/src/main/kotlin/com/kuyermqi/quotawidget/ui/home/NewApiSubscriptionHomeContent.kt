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
import com.kuyermqi.quotawidget.settings.NewApiSubscriptionSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.NewApiSubscriptionConfigContent
import com.kuyermqi.quotawidget.ui.usage.formatUsageWindowSummary
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class NewApiSubscriptionHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var saved by mutableStateOf(NewApiSubscriptionSettings())
        internal set
    var draftBaseUrl by mutableStateOf("")
        internal set
    var draftAccessToken by mutableStateOf("")
        internal set
    var draftUserId by mutableStateOf("")
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
            draftBaseUrl.trim().trimEnd('/') != saved.baseUrl ||
                draftAccessToken != saved.accessToken ||
                draftUserId.trim() != saved.userId ||
                draftUsageDisplayMode != saved.usageDisplayMode ||
                draftUsageProgressStyle != saved.usageProgressStyle
            )

    fun applyLoaded(settings: NewApiSubscriptionSettings) {
        saved = settings
        draftBaseUrl = settings.baseUrl
        draftAccessToken = settings.accessToken
        draftUserId = settings.userId
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
            is WidgetDisplayState.Success -> formatUsageWindowSummary(
                resources = resources,
                windows = widgetState.snapshot.windows,
                windowKind = UsageWindowKind.SUBSCRIPTION,
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
fun rememberNewApiSubscriptionHomeState(
    settingsRepository: PlatformSettingsRepository,
): NewApiSubscriptionHomeState = remember(settingsRepository) {
    NewApiSubscriptionHomeState(settingsRepository)
}

@Composable
fun NewApiSubscriptionHomeEffects(state: NewApiSubscriptionHomeState): WidgetDisplayState {
    val observed by state.repository().observeWidgetState(PlatformIds.NEW_API_SUBSCRIPTION)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    val resources = LocalResources.current
    LaunchedEffect(observed, state.saved.usageDisplayMode) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastWindows = success.snapshot.windows
        state.lastDisplay = formatUsageWindowSummary(
            resources = resources,
            windows = success.snapshot.windows,
            windowKind = UsageWindowKind.SUBSCRIPTION,
            usageDisplayMode = state.saved.usageDisplayMode,
            fallback = resources.getString(R.string.usage_unavailable),
        )
    }
    return observed
}

@Composable
fun ColumnScope.NewApiSubscriptionHomeContent(
    state: NewApiSubscriptionHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    NewApiSubscriptionConfigContent(
        baseUrl = state.draftBaseUrl,
        onBaseUrlChange = {
            state.draftBaseUrl = it
            state.saveError = null
        },
        accessToken = state.draftAccessToken,
        onAccessTokenChange = {
            state.draftAccessToken = it
            state.saveError = null
        },
        userId = state.draftUserId,
        onUserIdChange = { value ->
            if (value.isEmpty() || value.all { it.isDigit() }) {
                state.draftUserId = value
                state.saveError = null
            }
        },
        windows = state.lastWindows,
        usageDisplayMode = state.draftUsageDisplayMode,
        onUsageDisplayModeChange = {
            state.draftUsageDisplayMode = it
            state.saveError = null
        },
        usageProgressStyle = state.draftUsageProgressStyle,
        onUsageProgressStyleChange = {
            state.draftUsageProgressStyle = it
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
                    val next = NewApiSubscriptionSettings(
                        baseUrl = state.draftBaseUrl.trim().trimEnd('/'),
                        accessToken = state.draftAccessToken.trim(),
                        userId = state.draftUserId.trim(),
                        usageDisplayMode = state.draftUsageDisplayMode,
                        usageProgressStyle = state.draftUsageProgressStyle,
                    )
                    state.repository().saveNewApiSubscriptionSettings(next)
                    state.saved = next
                    state.draftBaseUrl = next.baseUrl
                    state.draftAccessToken = next.accessToken
                    state.draftUserId = next.userId
                    if (!next.isConfigured) {
                        state.lastDisplay = null
                        state.lastWindows = emptyList()
                    }
                    state.saveError = when (
                        val result = onRefreshPlatform(PlatformIds.NEW_API_SUBSCRIPTION)
                    ) {
                        is WidgetDisplayState.Error -> result.message
                        else -> null
                    }
                    WidgetGlanceState.syncAndUpdate(context, "newapi_sub_save")
                } finally {
                    state.isSaving = false
                }
            }
        },
    )
}
