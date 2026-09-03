package com.kuyermqi.quotawidget.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.NewApiAdminStats
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.averageRpmDay
import com.kuyermqi.quotawidget.domain.averageTpmDay
import com.kuyermqi.quotawidget.domain.formatNewApiAdminRate
import com.kuyermqi.quotawidget.domain.formatRequestCount
import com.kuyermqi.quotawidget.domain.formatTokenCount
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.NewApiSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.NewApiConfigContent
import com.kuyermqi.quotawidget.ui.components.TokenLineChart
import com.kuyermqi.quotawidget.widget.WidgetDateFormatter
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class NewApiHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var saved by mutableStateOf(NewApiSettings())
        internal set
    var draftBaseUrl by mutableStateOf("")
        internal set
    var draftAdminToken by mutableStateOf("")
        internal set
    var draftAdminUserId by mutableStateOf("")
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
                draftAdminToken != saved.adminToken ||
                draftAdminUserId.trim() != saved.adminUserId
            )

    fun applyLoaded(settings: NewApiSettings) {
        saved = settings
        draftBaseUrl = settings.baseUrl
        draftAdminToken = settings.adminToken
        draftAdminUserId = settings.adminUserId
        loaded = true
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

@Composable
fun rememberNewApiHomeState(
    settingsRepository: PlatformSettingsRepository,
): NewApiHomeState = remember(settingsRepository) {
    NewApiHomeState(settingsRepository)
}

@Composable
fun NewApiHomeEffects(state: NewApiHomeState) {
    // Keeps admin stats fresh in-app: each save/refresh persists them and this
    // flow re-emits the stored values.
    state.repository().observeNewApiAdminStats()
        .collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        val latest = state.repository().getNewApiSettings()
        state.applyLoaded(latest)
    }
}

@Composable
fun ColumnScope.NewApiHomeContent(
    state: NewApiHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (state.saved.isAdminConfigured) {
        val adminStats by state.repository().observeNewApiAdminStats()
            .collectAsStateWithLifecycle(initialValue = null)
        adminStats?.let { stats ->
            NewApiAdminStatsPanel(stats)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (adminStats == null) {
            Text(
                text = stringResource(R.string.new_api_admin_home_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    NewApiConfigContent(
        baseUrl = state.draftBaseUrl,
        onBaseUrlChange = {
            state.draftBaseUrl = it
            state.saveError = null
        },
        adminToken = state.draftAdminToken,
        onAdminTokenChange = {
            state.draftAdminToken = it
            state.saveError = null
        },
        adminUserId = state.draftAdminUserId,
        onAdminUserIdChange = { value ->
            if (value.isEmpty() || value.all { it.isDigit() }) {
                state.draftAdminUserId = value
                state.saveError = null
            }
        },
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        saveError = state.saveError,
        onSave = {
            scope.launch {
                val next = NewApiSettings(
                    baseUrl = state.draftBaseUrl.trim().trimEnd('/'),
                    adminToken = state.draftAdminToken.trim(),
                    adminUserId = state.draftAdminUserId.trim(),
                )
                state.isSaving = true
                state.saveError = null
                try {
                    state.repository().saveNewApiSettings(next)
                    state.saved = next
                    state.draftBaseUrl = next.baseUrl
                    state.draftAdminToken = next.adminToken
                    state.draftAdminUserId = next.adminUserId
                    onRefreshPlatform(PlatformIds.NEW_API)
                    WidgetGlanceState.syncAndUpdate(context, "new_api_save")
                } finally {
                    state.isSaving = false
                }
            }
        },
    )
}

/** Admin visualization: headline stats, RPM/TPM, and two token curve charts. */
@Composable
private fun NewApiAdminStatsPanel(stats: NewApiAdminStats) {
    Column {
        Text(
            text = stringResource(R.string.new_api_admin_home_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdminStatCell(
                label = stringResource(R.string.new_api_admin_stat_day),
                value = formatTokenCount(stats.tokensDay),
                modifier = Modifier.weight(1f),
            )
            AdminStatCell(
                label = stringResource(R.string.new_api_admin_stat_month),
                value = formatTokenCount(stats.tokensMonth),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdminStatCell(
                label = stringResource(R.string.new_api_admin_stat_requests_day),
                value = formatRequestCount(stats.requestsDay),
                modifier = Modifier.weight(1f),
            )
            AdminStatCell(
                label = stringResource(R.string.new_api_admin_stat_rpm),
                value = formatNewApiAdminRate(stats.averageRpmDay()),
                modifier = Modifier.weight(1f),
            )
            AdminStatCell(
                label = stringResource(R.string.new_api_admin_stat_tpm),
                value = formatNewApiAdminRate(stats.averageTpmDay()),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        TokenLineChart(
            values = stats.daySeries,
            title = stringResource(R.string.new_api_admin_chart_day),
            startLabel = stringResource(R.string.new_api_admin_chart_range_day),
            endLabel = stringResource(R.string.new_api_admin_chart_now),
        )
        Spacer(modifier = Modifier.height(12.dp))
        TokenLineChart(
            values = stats.monthSeries,
            title = stringResource(R.string.new_api_admin_chart_month),
            startLabel = stringResource(R.string.new_api_admin_chart_range_month),
            endLabel = stringResource(R.string.new_api_admin_chart_today),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = WidgetDateFormatter.formatUpdatedAt(stats.updatedAtEpochMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdminStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
