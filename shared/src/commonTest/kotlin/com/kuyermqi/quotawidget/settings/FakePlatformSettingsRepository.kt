package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.NewApiAdminStats
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePlatformSettingsRepository(
    deepSeek: DeepSeekSettings = DeepSeekSettings(),
    openCode: OpenCodeGoSettings = OpenCodeGoSettings(),
    codex: CodexSettings = CodexSettings(),
    newApi: NewApiSettings = NewApiSettings(),
    zhipu: ZhipuSettings = ZhipuSettings(),
    widgetStates: Map<String, WidgetDisplayState> = emptyMap(),
) : PlatformSettingsRepository {
    private val deepSeekFlow = MutableStateFlow(deepSeek)
    private val openCodeFlow = MutableStateFlow(openCode)
    private val codexFlow = MutableStateFlow(codex)
    private val newApiFlow = MutableStateFlow(newApi)
    private val zhipuFlow = MutableStateFlow(zhipu)
    private val widgetFlows = mutableMapOf(
        PlatformIds.DEEPSEEK to MutableStateFlow(
            widgetStates[PlatformIds.DEEPSEEK] ?: WidgetDisplayState.NotConfigured,
        ),
        PlatformIds.OPENCODE_GO to MutableStateFlow(
            widgetStates[PlatformIds.OPENCODE_GO] ?: WidgetDisplayState.NotConfigured,
        ),
        PlatformIds.CODEX to MutableStateFlow(
            widgetStates[PlatformIds.CODEX] ?: WidgetDisplayState.NotConfigured,
        ),
        PlatformIds.NEW_API to MutableStateFlow(
            widgetStates[PlatformIds.NEW_API] ?: WidgetDisplayState.NotConfigured,
        ),
        PlatformIds.ZHIPU to MutableStateFlow(
            widgetStates[PlatformIds.ZHIPU] ?: WidgetDisplayState.NotConfigured,
        ),
    )
    private val refreshPhases = mutableMapOf<String, RefreshIconPhase>()
    private val refreshStartedAt = mutableMapOf<String, Long>()
    private val appSettingsFlow = MutableStateFlow(AppSettings())

    var platformTipDismissed: Boolean = false
    var oemBackgroundTipDismissed: Boolean = false
    var updateIgnoredVersion: String? = null

    override fun observeDeepSeekSettings(): Flow<DeepSeekSettings> = deepSeekFlow.asStateFlow()
    override suspend fun getDeepSeekSettings(): DeepSeekSettings = deepSeekFlow.value
    override suspend fun saveDeepSeekSettings(settings: DeepSeekSettings) {
        deepSeekFlow.value = settings
        if (settings.apiKey.isBlank()) {
            widgetFlow(PlatformIds.DEEPSEEK).value = WidgetDisplayState.NotConfigured
        }
    }

    override fun observeOpenCodeGoSettings(): Flow<OpenCodeGoSettings> = openCodeFlow.asStateFlow()
    override suspend fun getOpenCodeGoSettings(): OpenCodeGoSettings = openCodeFlow.value
    override suspend fun saveOpenCodeGoSettings(settings: OpenCodeGoSettings) {
        openCodeFlow.value = settings
        if (!settings.isConfigured) {
            widgetFlow(PlatformIds.OPENCODE_GO).value = WidgetDisplayState.NotConfigured
        }
    }

    override suspend fun clearOpenCodeGoSettings() {
        val current = openCodeFlow.value
        openCodeFlow.value = OpenCodeGoSettings(
            widgetWindowKind = current.widgetWindowKind,
            usageDisplayMode = current.usageDisplayMode,
            usageProgressStyle = current.usageProgressStyle,
        )
        widgetFlow(PlatformIds.OPENCODE_GO).value = WidgetDisplayState.NotConfigured
    }

    override fun observeCodexSettings(): Flow<CodexSettings> = codexFlow.asStateFlow()
    override suspend fun getCodexSettings(): CodexSettings = codexFlow.value
    override suspend fun saveCodexSettings(settings: CodexSettings) {
        codexFlow.value = settings
        if (!settings.isConfigured) {
            widgetFlow(PlatformIds.CODEX).value = WidgetDisplayState.NotConfigured
        }
    }

    override suspend fun clearCodexSettings() {
        val current = codexFlow.value
        codexFlow.value = CodexSettings(
            widgetWindowKind = current.widgetWindowKind,
            usageDisplayMode = current.usageDisplayMode,
            usageProgressStyle = current.usageProgressStyle,
        )
        widgetFlow(PlatformIds.CODEX).value = WidgetDisplayState.NotConfigured
    }

    override fun observeNewApiSettings(): Flow<NewApiSettings> = newApiFlow.asStateFlow()
    override suspend fun getNewApiSettings(): NewApiSettings = newApiFlow.value
    override suspend fun saveNewApiSettings(settings: NewApiSettings) {
        newApiFlow.value = settings
        if (!settings.isConfigured) {
            widgetFlow(PlatformIds.NEW_API).value = WidgetDisplayState.NotConfigured
        }
    }

    override suspend fun clearNewApiSettings() {
        newApiFlow.value = NewApiSettings()
        widgetFlow(PlatformIds.NEW_API).value = WidgetDisplayState.NotConfigured
    }

    override fun observeZhipuSettings(): Flow<ZhipuSettings> = zhipuFlow.asStateFlow()
    override suspend fun getZhipuSettings(): ZhipuSettings = zhipuFlow.value
    override suspend fun saveZhipuSettings(settings: ZhipuSettings) {
        zhipuFlow.value = settings
        if (!settings.isConfigured) {
            widgetFlow(PlatformIds.ZHIPU).value = WidgetDisplayState.NotConfigured
        }
    }

    override suspend fun clearZhipuSettings() {
        val current = zhipuFlow.value
        zhipuFlow.value = ZhipuSettings(
            widgetWindowKind = current.widgetWindowKind,
            usageDisplayMode = current.usageDisplayMode,
            usageProgressStyle = current.usageProgressStyle,
        )
        widgetFlow(PlatformIds.ZHIPU).value = WidgetDisplayState.NotConfigured
    }

    private val newApiAdminStatsFlow = MutableStateFlow<NewApiAdminStats?>(null)

    override suspend fun saveNewApiAdminStats(stats: NewApiAdminStats) {
        newApiAdminStatsFlow.value = stats
    }

    override suspend fun getNewApiAdminStats(): NewApiAdminStats? = newApiAdminStatsFlow.value

    override fun observeNewApiAdminStats(): Flow<NewApiAdminStats?> =
        newApiAdminStatsFlow.asStateFlow()

    private val newApiSubscriptionFlow = MutableStateFlow(NewApiSubscriptionSettings())

    override fun observeNewApiSubscriptionSettings(): Flow<NewApiSubscriptionSettings> =
        newApiSubscriptionFlow.asStateFlow()

    override suspend fun getNewApiSubscriptionSettings(): NewApiSubscriptionSettings =
        newApiSubscriptionFlow.value

    override suspend fun saveNewApiSubscriptionSettings(settings: NewApiSubscriptionSettings) {
        newApiSubscriptionFlow.value = settings
        if (!settings.isConfigured) {
            widgetFlow(PlatformIds.NEW_API_SUBSCRIPTION).value = WidgetDisplayState.NotConfigured
        }
    }

    override suspend fun clearNewApiSubscriptionSettings() {
        val current = newApiSubscriptionFlow.value
        newApiSubscriptionFlow.value = NewApiSubscriptionSettings(
            usageDisplayMode = current.usageDisplayMode,
            usageProgressStyle = current.usageProgressStyle,
        )
        widgetFlow(PlatformIds.NEW_API_SUBSCRIPTION).value = WidgetDisplayState.NotConfigured
    }

    override fun observeWidgetState(platformId: String): Flow<WidgetDisplayState> =
        widgetFlow(platformId).asStateFlow()

    override suspend fun getWidgetState(platformId: String): WidgetDisplayState =
        widgetFlow(platformId).value

    override suspend fun saveWidgetSuccess(platformId: String, snapshot: QuotaSnapshot) {
        widgetFlow(platformId).value = WidgetDisplayState.Success(snapshot)
    }

    override suspend fun saveWidgetError(platformId: String, message: String) {
        widgetFlow(platformId).value = WidgetDisplayState.Error(message)
    }

    override suspend fun saveWidgetLoading(platformId: String) {
        widgetFlow(platformId).value = WidgetDisplayState.Loading
    }

    override suspend fun saveWidgetNotConfigured(platformId: String) {
        widgetFlow(platformId).value = WidgetDisplayState.NotConfigured
    }

    override suspend fun saveWidgetNeedsReauth(platformId: String) {
        widgetFlow(platformId).value = WidgetDisplayState.NeedsReauth
    }

    override suspend fun getRefreshIconPhase(platformId: String): RefreshIconPhase =
        refreshPhases[platformId] ?: RefreshIconPhase.Idle

    override suspend fun setRefreshIconPhase(platformId: String, phase: RefreshIconPhase) {
        refreshPhases[platformId] = phase
    }

    override suspend fun getRefreshStartedAtEpochMs(platformId: String): Long =
        refreshStartedAt[platformId] ?: 0L

    override suspend fun setRefreshStartedAtEpochMs(platformId: String, epochMs: Long) {
        refreshStartedAt[platformId] = epochMs
    }

    override suspend fun clearAllRefreshIconPhases() {
        refreshPhases.clear()
    }

    override suspend fun isPlatformTipDismissed(): Boolean = platformTipDismissed
    override suspend fun setPlatformTipDismissed(dismissed: Boolean) {
        platformTipDismissed = dismissed
    }

    override suspend fun isOemBackgroundTipDismissed(): Boolean = oemBackgroundTipDismissed
    override suspend fun setOemBackgroundTipDismissed(dismissed: Boolean) {
        oemBackgroundTipDismissed = dismissed
    }

    override suspend fun getUpdateIgnoredVersion(): String? = updateIgnoredVersion
    override suspend fun setUpdateIgnoredVersion(version: String?) {
        updateIgnoredVersion = version
    }

    override fun observeAppSettings(): Flow<AppSettings> = appSettingsFlow.asStateFlow()

    private val freeModelsFlow = MutableStateFlow(FreeModelsCache())

    override fun observeFreeModelsCache(): Flow<FreeModelsCache> = freeModelsFlow.asStateFlow()

    override suspend fun getFreeModelsCache(): FreeModelsCache = freeModelsFlow.value

    override suspend fun saveFreeModelsCache(cache: FreeModelsCache) {
        freeModelsFlow.value = cache
    }
    override suspend fun getAppSettings(): AppSettings = appSettingsFlow.value
    override suspend fun saveAppSettings(settings: AppSettings) {
        appSettingsFlow.value = settings
    }

    private fun widgetFlow(platformId: String): MutableStateFlow<WidgetDisplayState> =
        widgetFlows.getOrPut(platformId) { MutableStateFlow(WidgetDisplayState.NotConfigured) }
}
