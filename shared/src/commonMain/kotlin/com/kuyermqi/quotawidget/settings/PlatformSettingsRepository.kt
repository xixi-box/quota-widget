package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.NewApiAdminStats
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import kotlinx.coroutines.flow.Flow

data class DeepSeekSettings(
    val apiKey: String = "",
    val currency: CurrencyPreference = CurrencyPreference.CNY,
)

data class OpenCodeGoSettings(
    val workspaceId: String = "",
    val workspaceName: String = "",
    val authCookie: String = "",
    val widgetWindowKind: UsageWindowKind = UsageWindowKind.ROLLING,
    val usageDisplayMode: UsageDisplayMode = UsageDisplayMode.USED,
    val usageProgressStyle: UsageProgressStyle = UsageProgressStyle.BAR,
) {
    val isConfigured: Boolean
        get() = workspaceId.isNotBlank() && authCookie.isNotBlank()
}

data class CodexSettings(
    val accessToken: String = "",
    val refreshToken: String = "",
    val idToken: String = "",
    val accountId: String = "",
    val expiresAtEpochMs: Long = 0L,
    val email: String = "",
    val planType: String = "",
    val widgetWindowKind: UsageWindowKind = UsageWindowKind.WEEKLY,
    val usageDisplayMode: UsageDisplayMode = UsageDisplayMode.USED,
    val usageProgressStyle: UsageProgressStyle = UsageProgressStyle.BAR,
) {
    val isConfigured: Boolean
        get() = accessToken.isNotBlank() &&
            refreshToken.isNotBlank() &&
            accountId.isNotBlank()
}

data class NewApiSettings(
    val baseUrl: String = "",
    /** Admin system access token: unlocks platform-wide usage stats. */
    val adminToken: String = "",
    /** Admin user id sent as the required `New-Api-User` header. */
    val adminUserId: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && isAdminConfigured

    val isAdminConfigured: Boolean
        get() = adminToken.isNotBlank() && adminUserId.isNotBlank()
}

data class ZhipuSettings(
    val apiKey: String = "",
    val widgetWindowKind: UsageWindowKind = UsageWindowKind.ROLLING,
    val usageDisplayMode: UsageDisplayMode = UsageDisplayMode.USED,
    val usageProgressStyle: UsageProgressStyle = UsageProgressStyle.BAR,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()
}

data class NewApiSubscriptionSettings(
    val baseUrl: String = "",
    /** User system access token (console → personal settings), NOT the sk- key. */
    val accessToken: String = "",
    val userId: String = "",
    val usageDisplayMode: UsageDisplayMode = UsageDisplayMode.USED,
    val usageProgressStyle: UsageProgressStyle = UsageProgressStyle.BAR,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && accessToken.isNotBlank() && userId.isNotBlank()
}

/** Cached models.dev free-model catalog (shared by the app UI and widgets). */
data class FreeModelsCache(
    val models: List<String> = emptyList(),
    val updatedAtEpochMs: Long = 0L,
) {
    val isEmpty: Boolean
        get() = models.isEmpty()
}

/** NewAPI common default: 500_000 quota = 1 USD. */
const val DEFAULT_NEW_API_QUOTA_PER_USD = 500_000L

interface PlatformSettingsRepository {
    fun observeDeepSeekSettings(): Flow<DeepSeekSettings>
    suspend fun getDeepSeekSettings(): DeepSeekSettings
    suspend fun saveDeepSeekSettings(settings: DeepSeekSettings)

    fun observeOpenCodeGoSettings(): Flow<OpenCodeGoSettings>
    suspend fun getOpenCodeGoSettings(): OpenCodeGoSettings
    suspend fun saveOpenCodeGoSettings(settings: OpenCodeGoSettings)
    suspend fun clearOpenCodeGoSettings()

    fun observeCodexSettings(): Flow<CodexSettings>
    suspend fun getCodexSettings(): CodexSettings
    suspend fun saveCodexSettings(settings: CodexSettings)
    suspend fun clearCodexSettings()

    fun observeNewApiSettings(): Flow<NewApiSettings>
    suspend fun getNewApiSettings(): NewApiSettings
    suspend fun saveNewApiSettings(settings: NewApiSettings)
    suspend fun clearNewApiSettings()

    fun observeZhipuSettings(): Flow<ZhipuSettings>
    suspend fun getZhipuSettings(): ZhipuSettings
    suspend fun saveZhipuSettings(settings: ZhipuSettings)
    suspend fun clearZhipuSettings()

    fun observeNewApiSubscriptionSettings(): Flow<NewApiSubscriptionSettings>
    suspend fun getNewApiSubscriptionSettings(): NewApiSubscriptionSettings
    suspend fun saveNewApiSubscriptionSettings(settings: NewApiSubscriptionSettings)
    suspend fun clearNewApiSubscriptionSettings()

    suspend fun saveNewApiAdminStats(stats: NewApiAdminStats)
    suspend fun getNewApiAdminStats(): NewApiAdminStats?
    fun observeNewApiAdminStats(): Flow<NewApiAdminStats?>

    fun observeWidgetState(platformId: String): Flow<WidgetDisplayState>
    suspend fun getWidgetState(platformId: String): WidgetDisplayState
    suspend fun saveWidgetSuccess(platformId: String, snapshot: QuotaSnapshot)
    suspend fun saveWidgetError(platformId: String, message: String)
    suspend fun saveWidgetLoading(platformId: String)
    suspend fun saveWidgetNotConfigured(platformId: String)
    suspend fun saveWidgetNeedsReauth(platformId: String)

    suspend fun getRefreshIconPhase(platformId: String): RefreshIconPhase
    suspend fun setRefreshIconPhase(platformId: String, phase: RefreshIconPhase)
    suspend fun getRefreshStartedAtEpochMs(platformId: String): Long
    suspend fun setRefreshStartedAtEpochMs(platformId: String, epochMs: Long)
    suspend fun clearAllRefreshIconPhases()

    suspend fun isPlatformTipDismissed(): Boolean
    suspend fun setPlatformTipDismissed(dismissed: Boolean)

    suspend fun isOemBackgroundTipDismissed(): Boolean
    suspend fun setOemBackgroundTipDismissed(dismissed: Boolean)

    suspend fun getUpdateIgnoredVersion(): String?
    suspend fun setUpdateIgnoredVersion(version: String?)

    fun observeAppSettings(): Flow<AppSettings>
    suspend fun getAppSettings(): AppSettings
    suspend fun saveAppSettings(settings: AppSettings)

    fun observeFreeModelsCache(): Flow<FreeModelsCache>
    suspend fun getFreeModelsCache(): FreeModelsCache
    suspend fun saveFreeModelsCache(cache: FreeModelsCache)
}
