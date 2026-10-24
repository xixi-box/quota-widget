package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.DEFAULT_CUSTOM_SEED_COLOR_ARGB
import com.kuyermqi.quotawidget.domain.DEFAULT_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.NewApiAdminStats
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.decodeQuotaWindows
import com.kuyermqi.quotawidget.domain.encodeQuotaWindows
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.CodexSettings
import com.kuyermqi.quotawidget.settings.FreeModelEntry
import com.kuyermqi.quotawidget.settings.NewApiSubscriptionSettings
import com.kuyermqi.quotawidget.settings.OpenCodeGoSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.ZhipuSettings

/**
 * Glance [update] often recomposes without re-entering [GlanceAppWidget.provideGlance].
 * Widget UI must therefore read from Glance state ([currentState]), which we sync from
 * the app repository before each update.
 */
object WidgetGlanceState {
    private const val TAG = "QuotaRefresh"

    val refreshPhaseKey = stringPreferencesKey("qw_refresh_phase")
    val statusKey = stringPreferencesKey("qw_status")
    val errorKey = stringPreferencesKey("qw_error")
    val platformIdKey = stringPreferencesKey("qw_platform_id")
    val platformNameKey = stringPreferencesKey("qw_platform_name")
    val currencyKey = stringPreferencesKey("qw_currency")
    val totalKey = stringPreferencesKey("qw_total")
    val formattedKey = stringPreferencesKey("qw_formatted")
    val windowsKey = stringPreferencesKey("qw_windows")
    val updatedAtKey = longPreferencesKey("qw_updated_at")
    val unlimitedKey = booleanPreferencesKey("qw_unlimited")
    val usedDisplayKey = stringPreferencesKey("qw_used_display")
    val emptyLimitedQuotaKey = booleanPreferencesKey("qw_empty_limited_quota")
    val hiddenPlatformsKey = stringSetPreferencesKey("qw_hidden_platforms")
    val tokenExpiredKey = booleanPreferencesKey("qw_token_expired")
    val quotaOverspentKey = booleanPreferencesKey("qw_quota_overspent")
    val darkThemeModeKey = stringPreferencesKey("qw_dark_theme_mode")
    val themeColorModeKey = stringPreferencesKey("qw_theme_color_mode")
    val seedColorKey = intPreferencesKey("qw_seed_color")
    val openCodeWindowKindKey = stringPreferencesKey("qw_opencode_window_kind")
    val openCodeUsageDisplayModeKey = stringPreferencesKey("qw_opencode_usage_display")
    val openCodeUsageProgressStyleKey = stringPreferencesKey("qw_opencode_usage_progress_style")
    val codexWindowKindKey = stringPreferencesKey("qw_codex_window_kind")
    val codexUsageDisplayModeKey = stringPreferencesKey("qw_codex_usage_display")
    val codexUsageProgressStyleKey = stringPreferencesKey("qw_codex_usage_progress_style")
    val freeModelsKey = stringPreferencesKey("qw_free_models_v3")
    val freeModelsIdsKey = stringPreferencesKey("qw_free_models_ids_v3")
    val freeModelsUpdatedAtKey = longPreferencesKey("qw_free_models_updated_at_v3")
    val zhipuWindowKindKey = stringPreferencesKey("qw_zhipu_window_kind")
    val zhipuUsageDisplayModeKey = stringPreferencesKey("qw_zhipu_usage_display")
    val zhipuUsageProgressStyleKey = stringPreferencesKey("qw_zhipu_usage_progress_style")
    val newApiSubscriptionUsageDisplayModeKey =
        stringPreferencesKey("qw_newapi_sub_usage_display")
    val newApiSubscriptionUsageProgressStyleKey =
        stringPreferencesKey("qw_newapi_sub_usage_progress_style")
    val newApiAdminTokensDayKey = longPreferencesKey("qw_new_api_admin_tokens_day")
    val newApiAdminTokensMonthKey = longPreferencesKey("qw_new_api_admin_tokens_month")
    val newApiAdminRequestsDayKey = longPreferencesKey("qw_new_api_admin_requests_day")
    val newApiAdminRequestsMonthKey = longPreferencesKey("qw_new_api_admin_requests_month")
    val newApiAdminRpmKey = longPreferencesKey("qw_new_api_admin_rpm")
    val newApiAdminTpmKey = longPreferencesKey("qw_new_api_admin_tpm")
    val newApiAdminUpdatedAtKey = longPreferencesKey("qw_new_api_admin_updated_at")
    val newApiAdminDaySeriesKey = stringPreferencesKey("qw_new_api_admin_day_series")
    val newApiAdminMonthSeriesKey = stringPreferencesKey("qw_new_api_admin_month_series")

    private object Status {
        const val NOT_CONFIGURED = "not_configured"
        const val LOADING = "loading"
        const val SUCCESS = "success"
        const val ERROR = "error"
        const val NEEDS_REAUTH = "needs_reauth"
    }

    private data class Target(
        val widget: GlanceAppWidget,
        val clazz: Class<out GlanceAppWidget>,
        val platformId: String,
    )

    private fun targets(): List<Target> = listOf(
        Target(DeepSeekBalanceWidget(), DeepSeekBalanceWidget::class.java, PlatformIds.DEEPSEEK),
        Target(
            DeepSeekBalanceCompactWidget(),
            DeepSeekBalanceCompactWidget::class.java,
            PlatformIds.DEEPSEEK,
        ),
        Target(OpenCodeGoWidget(), OpenCodeGoWidget::class.java, PlatformIds.OPENCODE_GO),
        Target(
            OpenCodeFreeModelsWidget(),
            OpenCodeFreeModelsWidget::class.java,
            PlatformIds.OPENCODE_GO,
        ),
        Target(
            OpenCodeGoCompactWidget(),
            OpenCodeGoCompactWidget::class.java,
            PlatformIds.OPENCODE_GO,
        ),
        Target(
            OpenCodeGoOverviewWidget(),
            OpenCodeGoOverviewWidget::class.java,
            PlatformIds.OPENCODE_GO,
        ),
        Target(CodexWidget(), CodexWidget::class.java, PlatformIds.CODEX),
        Target(CodexCompactWidget(), CodexCompactWidget::class.java, PlatformIds.CODEX),
        Target(CodexOverviewWidget(), CodexOverviewWidget::class.java, PlatformIds.CODEX),
        Target(NewApiAdminWidget(), NewApiAdminWidget::class.java, PlatformIds.NEW_API),
        Target(ZhipuWidget(), ZhipuWidget::class.java, PlatformIds.ZHIPU),
        Target(ZhipuCompactWidget(), ZhipuCompactWidget::class.java, PlatformIds.ZHIPU),
        Target(ZhipuOverviewWidget(), ZhipuOverviewWidget::class.java, PlatformIds.ZHIPU),
        Target(
            NewApiSubscriptionWidget(),
            NewApiSubscriptionWidget::class.java,
            PlatformIds.NEW_API_SUBSCRIPTION,
        ),
        Target(
            NewApiSubscriptionCompactWidget(),
            NewApiSubscriptionCompactWidget::class.java,
            PlatformIds.NEW_API_SUBSCRIPTION,
        ),
    )

    suspend fun syncAndUpdate(context: Context, reason: String) {
        val app = context.applicationContext as QuotaWidgetApp
        val repo = app.settingsRepository
        val appSettings = repo.getAppSettings()
        val openCodeSettings = repo.getOpenCodeGoSettings()
        val codexSettings = repo.getCodexSettings()
        val newApiAdminStats = repo.getNewApiAdminStats()
        val zhipuSettings = repo.getZhipuSettings()
        val freeModels = repo.getFreeModelsCache()
        val newApiSubscriptionSettings = repo.getNewApiSubscriptionSettings()
        Log.i(TAG, "syncAndUpdate reason=$reason")

        val manager = GlanceAppWidgetManager(context)
        var updated = 0
        for (target in targets()) {
            val display = repo.getWidgetState(target.platformId)
            val phase = repo.getRefreshIconPhase(target.platformId)
            val ids = manager.getGlanceIds(target.clazz)
            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        write(
                            phase = phase,
                            display = display,
                            appSettings = appSettings,
                            openCodeSettings = openCodeSettings.takeIf {
                                target.platformId == PlatformIds.OPENCODE_GO
                            },
                            codexSettings = codexSettings.takeIf {
                                target.platformId == PlatformIds.CODEX
                            },
                            newApiAdminStats = newApiAdminStats.takeIf {
                                target.platformId == PlatformIds.NEW_API
                            },
                            zhipuSettings = zhipuSettings.takeIf {
                                target.platformId == PlatformIds.ZHIPU
                            },
                            freeModels = freeModels.models,
                            newApiSubscriptionSettings = newApiSubscriptionSettings.takeIf {
                                target.platformId == PlatformIds.NEW_API_SUBSCRIPTION
                            },
                        )
                    }
                }
                target.widget.update(context, id)
                updated++
                Log.i(
                    TAG,
                    "syncAndUpdate applied id=$id type=${target.clazz.simpleName} " +
                        "platform=${target.platformId} phase=$phase reason=$reason",
                )
            }
        }
        val combinedStates = mutableMapOf<String, WidgetDisplayState>()
        val combinedPhases = mutableMapOf<String, RefreshIconPhase>()
        PlatformRegistry.platforms.forEach { platform ->
            combinedStates[platform.id] = repo.getWidgetState(platform.id)
            combinedPhases[platform.id] = repo.getRefreshIconPhase(platform.id)
        }
        val combinedIds = manager.getGlanceIds(CombinedOverviewWidget::class.java)
        combinedIds.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[hiddenPlatformsKey] = appSettings.hiddenPlatforms
                    this[darkThemeModeKey] = appSettings.darkThemeMode.name
                    this[themeColorModeKey] = appSettings.themeColorMode.name
                    this[seedColorKey] = appSettings.customSeedColorArgb
                    this[openCodeWindowKindKey] = openCodeSettings.widgetWindowKind.name
                    this[openCodeUsageDisplayModeKey] = openCodeSettings.usageDisplayMode.name
                    this[openCodeUsageProgressStyleKey] = openCodeSettings.usageProgressStyle.name
                    this[codexWindowKindKey] = codexSettings.widgetWindowKind.name
                    this[codexUsageDisplayModeKey] = codexSettings.usageDisplayMode.name
                    this[codexUsageProgressStyleKey] = codexSettings.usageProgressStyle.name
                    this[zhipuWindowKindKey] = zhipuSettings.widgetWindowKind.name
                    this[zhipuUsageDisplayModeKey] = zhipuSettings.usageDisplayMode.name
                    this[zhipuUsageProgressStyleKey] = zhipuSettings.usageProgressStyle.name
                    this[newApiSubscriptionUsageDisplayModeKey] =
                        newApiSubscriptionSettings.usageDisplayMode.name
                    this[newApiSubscriptionUsageProgressStyleKey] =
                        newApiSubscriptionSettings.usageProgressStyle.name
                    writeNewApiAdminStats(newApiAdminStats)
                    if (freeModels.models.isNotEmpty()) {
                        this[freeModelsKey] = freeModels.models.joinToString("\n") { it.name }
                        this[freeModelsIdsKey] = freeModels.models.joinToString("\n") { it.modelId }
                    }
                    this[freeModelsUpdatedAtKey] = freeModels.updatedAtEpochMs
                    this[combinedStringKey(PlatformIds.FREE_MODELS, "refresh_phase")] =
                        repo.getRefreshIconPhase(PlatformIds.FREE_MODELS).name
                    PlatformRegistry.platforms.forEach { platform ->
                        writeCombinedPlatform(
                            platformId = platform.id,
                            phase = combinedPhases.getValue(platform.id),
                            display = combinedStates.getValue(platform.id),
                        )
                    }
                }
            }
            CombinedOverviewWidget().update(context, id)
            updated++
        }
        if (updated == 0) {
            Log.w(TAG, "syncAndUpdate no glance ids")
        }
    }

    /**
     * Always copy the latest repository state into this Glance id.
     * Returns the display state that was written (from the app repository).
     */
    suspend fun syncFromRepository(
        context: Context,
        id: GlanceId,
        repo: PlatformSettingsRepository,
        platformId: String,
    ): WidgetDisplayState {
        val phase = repo.getRefreshIconPhase(platformId)
        val display = repo.getWidgetState(platformId)
        val appSettings = repo.getAppSettings()
        val openCodeSettings = if (platformId == PlatformIds.OPENCODE_GO) {
            repo.getOpenCodeGoSettings()
        } else {
            null
        }
        val codexSettings = if (platformId == PlatformIds.CODEX) {
            repo.getCodexSettings()
        } else {
            null
        }
        val newApiAdminStats = if (platformId == PlatformIds.NEW_API) {
            repo.getNewApiAdminStats()
        } else {
            null
        }
        val zhipuSettings = if (platformId == PlatformIds.ZHIPU) {
            repo.getZhipuSettings()
        } else {
            null
        }
        val newApiSubscriptionSettings = if (platformId == PlatformIds.NEW_API_SUBSCRIPTION) {
            repo.getNewApiSubscriptionSettings()
        } else {
            null
        }
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                write(
                    phase,
                    display,
                    appSettings,
                    openCodeSettings,
                    codexSettings,
                    zhipuSettings,
                    newApiAdminStats,
                    newApiSubscriptionSettings,
                )
            }
        }
        val verify = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        Log.i(
            TAG,
            "syncFromRepository id=$id platform=$platformId repo=${display::class.simpleName} " +
                "glanceStatus=${verify[statusKey]} glanceFormatted=${verify[formattedKey]}",
        )
        return display
    }

    fun Preferences.toRefreshPhase(): RefreshIconPhase =
        RefreshIconPhase.fromStorage(this[refreshPhaseKey])

    fun Preferences.toCombinedRefreshPhase(platformId: String): RefreshIconPhase =
        RefreshIconPhase.fromStorage(this[combinedStringKey(platformId, "refresh_phase")])

    /**
     * Free models as display name + callable model id. Both lists are stored
     * newline-separated and index-aligned.
     */
    fun Preferences.toFreeModels(): List<FreeModelEntry> {
        val names = this[freeModelsKey]
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (names.isEmpty()) return emptyList()
        val ids = this[freeModelsIdsKey]
            ?.split('\n')
            ?.map { it.trim() }
            .orEmpty()
        return names.mapIndexed { index, name ->
            FreeModelEntry(name = name, modelId = ids.getOrNull(index).orEmpty())
        }
    }

    fun Preferences.toFreeModelsUpdatedAt(): Long =
        this[freeModelsUpdatedAtKey] ?: 0L

    fun Preferences.toAppThemeSettings(): AppSettings = AppSettings(
        darkThemeMode = DarkThemeMode.fromStorage(this[darkThemeModeKey]),
        themeColorMode = ThemeColorMode.fromStorage(this[themeColorModeKey]),
        customSeedColorArgb = this[seedColorKey] ?: DEFAULT_CUSTOM_SEED_COLOR_ARGB,
        refreshIntervalMinutes = DEFAULT_REFRESH_INTERVAL_MINUTES,
        hiddenPlatforms = this[hiddenPlatformsKey].orEmpty(),
    )

    fun Preferences.toOpenCodeUsageWindowKind(): UsageWindowKind =
        UsageWindowKind.fromStorage(this[openCodeWindowKindKey])

    fun Preferences.toOpenCodeUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[openCodeUsageDisplayModeKey])

    fun Preferences.toOpenCodeUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[openCodeUsageProgressStyleKey])

    fun Preferences.toCodexUsageWindowKind(): UsageWindowKind =
        this[codexWindowKindKey]
            ?.let { UsageWindowKind.fromStorage(it) }
            ?: UsageWindowKind.WEEKLY

    fun Preferences.toCodexUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[codexUsageDisplayModeKey])

    fun Preferences.toCodexUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[codexUsageProgressStyleKey])

    fun Preferences.toZhipuUsageWindowKind(): UsageWindowKind =
        UsageWindowKind.fromStorage(this[zhipuWindowKindKey])

    fun Preferences.toZhipuUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[zhipuUsageDisplayModeKey])

    fun Preferences.toZhipuUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[zhipuUsageProgressStyleKey])

    fun Preferences.toNewApiSubscriptionUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[newApiSubscriptionUsageDisplayModeKey])

    fun Preferences.toNewApiSubscriptionUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[newApiSubscriptionUsageProgressStyleKey])

    fun Preferences.toNewApiAdminStats(): NewApiAdminStats? {
        val updatedAt = this[newApiAdminUpdatedAtKey] ?: return null
        if (updatedAt <= 0L) return null
        return NewApiAdminStats(
            tokensDay = this[newApiAdminTokensDayKey] ?: 0L,
            tokensMonth = this[newApiAdminTokensMonthKey] ?: 0L,
            requestsDay = this[newApiAdminRequestsDayKey] ?: 0L,
            requestsMonth = this[newApiAdminRequestsMonthKey] ?: 0L,
            rpm = this[newApiAdminRpmKey] ?: 0L,
            tpm = this[newApiAdminTpmKey] ?: 0L,
            daySeries = parseSeries(this[newApiAdminDaySeriesKey]),
            monthSeries = parseSeries(this[newApiAdminMonthSeriesKey]),
            updatedAtEpochMs = updatedAt,
        )
    }

    private fun parseSeries(joined: String?): List<Long> =
        joined?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()

    fun Preferences.toDisplayState(): WidgetDisplayState {
        return when (this[statusKey]) {
            Status.LOADING -> WidgetDisplayState.Loading
            Status.NEEDS_REAUTH -> WidgetDisplayState.NeedsReauth
            Status.ERROR -> WidgetDisplayState.Error(this[errorKey] ?: "刷新失败")
            Status.SUCCESS -> {
                val platformId = this[platformIdKey] ?: PlatformIds.DEEPSEEK
                val currency = CurrencyPreference.fromStorage(this[currencyKey])
                val total = this[totalKey] ?: "0"
                val primary = this[formattedKey]
                    ?: if (platformId == PlatformIds.DEEPSEEK) {
                        formatBalance(currency, total)
                    } else {
                        ""
                    }
                val windows = decodeQuotaWindows(this[windowsKey])
                    ?: if (platformId == PlatformIds.DEEPSEEK) {
                        listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE))
                    } else {
                        emptyList()
                    }
                WidgetDisplayState.Success(
                    QuotaSnapshot(
                        platformId = platformId,
                        platformName = this[platformNameKey]
                            ?: PlatformRegistry.displayName(platformId),
                        windows = windows,
                        primaryDisplay = primary,
                        updatedAtEpochMs = this[updatedAtKey] ?: 0L,
                        currency = currency,
                        totalBalance = total,
                        unlimitedQuota = this[unlimitedKey] == true,
                        usedDisplay = this[usedDisplayKey].orEmpty(),
                        emptyLimitedQuota = this[emptyLimitedQuotaKey] == true,
                        tokenExpired = this[tokenExpiredKey] == true,
                        quotaOverspent = this[quotaOverspentKey] == true,
                    ),
                )
            }
            else -> WidgetDisplayState.NotConfigured
        }
    }

    fun Preferences.toCombinedDisplayState(platformId: String): WidgetDisplayState {
        val key: (String) -> Preferences.Key<String> = { suffix ->
            combinedStringKey(platformId, suffix)
        }
        return when (this[key("status")]) {
            Status.LOADING -> WidgetDisplayState.Loading
            Status.NEEDS_REAUTH -> WidgetDisplayState.NeedsReauth
            Status.ERROR -> WidgetDisplayState.Error(this[key("error")] ?: "刷新失败")
            Status.SUCCESS -> WidgetDisplayState.Success(
                QuotaSnapshot(
                    platformId = platformId,
                    platformName = this[key("platform_name")]
                        ?: PlatformRegistry.displayName(platformId),
                    windows = decodeQuotaWindows(this[key("windows")]).orEmpty(),
                    primaryDisplay = this[key("formatted")].orEmpty(),
                    updatedAtEpochMs = this[combinedLongKey(platformId, "updated_at")] ?: 0L,
                    currency = CurrencyPreference.fromStorage(this[key("currency")]),
                    totalBalance = this[key("total")] ?: "0",
                    unlimitedQuota = this[combinedBooleanKey(platformId, "unlimited")] == true,
                    usedDisplay = this[key("used_display")].orEmpty(),
                    emptyLimitedQuota =
                        this[combinedBooleanKey(platformId, "empty_limited_quota")] == true,
                    tokenExpired = this[combinedBooleanKey(platformId, "token_expired")] == true,
                    quotaOverspent = this[combinedBooleanKey(platformId, "quota_overspent")] == true,
                ),
            )
            else -> WidgetDisplayState.NotConfigured
        }
    }

    private fun MutablePreferences.write(
        phase: RefreshIconPhase,
        display: WidgetDisplayState,
        appSettings: AppSettings,
        openCodeSettings: OpenCodeGoSettings?,
        codexSettings: CodexSettings?,
        zhipuSettings: ZhipuSettings? = null,
        newApiAdminStats: NewApiAdminStats? = null,
        newApiSubscriptionSettings: NewApiSubscriptionSettings? = null,
        freeModels: List<FreeModelEntry> = emptyList(),
    ) {
        this[refreshPhaseKey] = phase.name
        this[darkThemeModeKey] = appSettings.darkThemeMode.name
        this[themeColorModeKey] = appSettings.themeColorMode.name
        this[seedColorKey] = appSettings.customSeedColorArgb
        this[hiddenPlatformsKey] = appSettings.hiddenPlatforms
        if (openCodeSettings != null) {
            this[openCodeWindowKindKey] = openCodeSettings.widgetWindowKind.name
            this[openCodeUsageDisplayModeKey] = openCodeSettings.usageDisplayMode.name
            this[openCodeUsageProgressStyleKey] = openCodeSettings.usageProgressStyle.name
        }
        if (codexSettings != null) {
            this[codexWindowKindKey] = codexSettings.widgetWindowKind.name
            this[codexUsageDisplayModeKey] = codexSettings.usageDisplayMode.name
            this[codexUsageProgressStyleKey] = codexSettings.usageProgressStyle.name
        }
        if (freeModels.isNotEmpty()) {
            this[freeModelsKey] = freeModels.joinToString("\n") { it.name }
            this[freeModelsIdsKey] = freeModels.joinToString("\n") { it.modelId }
        }
        if (zhipuSettings != null) {
            this[zhipuWindowKindKey] = zhipuSettings.widgetWindowKind.name
            this[zhipuUsageDisplayModeKey] = zhipuSettings.usageDisplayMode.name
            this[zhipuUsageProgressStyleKey] = zhipuSettings.usageProgressStyle.name
        }
        if (newApiSubscriptionSettings != null) {
            this[newApiSubscriptionUsageDisplayModeKey] = newApiSubscriptionSettings.usageDisplayMode.name
            this[newApiSubscriptionUsageProgressStyleKey] =
                newApiSubscriptionSettings.usageProgressStyle.name
        }
        writeNewApiAdminStats(newApiAdminStats)
        when (display) {
            WidgetDisplayState.NotConfigured -> {
                this[statusKey] = Status.NOT_CONFIGURED
                remove(errorKey)
            }
            WidgetDisplayState.Loading -> {
                this[statusKey] = Status.LOADING
                remove(errorKey)
            }
            WidgetDisplayState.NeedsReauth -> {
                this[statusKey] = Status.NEEDS_REAUTH
                remove(errorKey)
            }
            is WidgetDisplayState.Error -> {
                this[statusKey] = Status.ERROR
                this[errorKey] = display.message
            }
            is WidgetDisplayState.Success -> {
                this[statusKey] = Status.SUCCESS
                this[platformIdKey] = display.snapshot.platformId
                this[platformNameKey] = display.snapshot.platformName
                this[currencyKey] = display.snapshot.currency.name
                this[totalKey] = display.snapshot.totalBalance
                this[formattedKey] = display.snapshot.primaryDisplay
                this[windowsKey] = encodeQuotaWindows(display.snapshot.windows)
                this[updatedAtKey] = display.snapshot.updatedAtEpochMs
                this[unlimitedKey] = display.snapshot.unlimitedQuota
                this[emptyLimitedQuotaKey] = display.snapshot.emptyLimitedQuota
                this[tokenExpiredKey] = display.snapshot.tokenExpired
                this[quotaOverspentKey] = display.snapshot.quotaOverspent
                if (display.snapshot.usedDisplay.isBlank()) {
                    remove(usedDisplayKey)
                } else {
                    this[usedDisplayKey] = display.snapshot.usedDisplay
                }
                remove(errorKey)
            }
        }
    }

    private fun MutablePreferences.writeNewApiAdminStats(stats: NewApiAdminStats?) {
        if (stats == null) {
            remove(newApiAdminUpdatedAtKey)
            return
        }
        this[newApiAdminTokensDayKey] = stats.tokensDay
        this[newApiAdminTokensMonthKey] = stats.tokensMonth
        this[newApiAdminRequestsDayKey] = stats.requestsDay
        this[newApiAdminRequestsMonthKey] = stats.requestsMonth
        this[newApiAdminRpmKey] = stats.rpm
        this[newApiAdminTpmKey] = stats.tpm
        this[newApiAdminUpdatedAtKey] = stats.updatedAtEpochMs
        this[newApiAdminDaySeriesKey] = stats.daySeries.joinToString(",")
        this[newApiAdminMonthSeriesKey] = stats.monthSeries.joinToString(",")
    }

    private fun MutablePreferences.writeCombinedPlatform(
        platformId: String,
        phase: RefreshIconPhase,
        display: WidgetDisplayState,
    ) {
        val key: (String) -> Preferences.Key<String> = { suffix ->
            combinedStringKey(platformId, suffix)
        }
        this[key("refresh_phase")] = phase.name
        when (display) {
            WidgetDisplayState.NotConfigured -> this[key("status")] = Status.NOT_CONFIGURED
            WidgetDisplayState.Loading -> this[key("status")] = Status.LOADING
            WidgetDisplayState.NeedsReauth -> this[key("status")] = Status.NEEDS_REAUTH
            is WidgetDisplayState.Error -> {
                this[key("status")] = Status.ERROR
                this[key("error")] = display.message
            }
            is WidgetDisplayState.Success -> {
                val snapshot = display.snapshot
                this[key("status")] = Status.SUCCESS
                this[key("platform_name")] = snapshot.platformName
                this[key("currency")] = snapshot.currency.name
                this[key("total")] = snapshot.totalBalance
                this[key("formatted")] = snapshot.primaryDisplay
                this[key("windows")] = encodeQuotaWindows(snapshot.windows)
                this[combinedLongKey(platformId, "updated_at")] = snapshot.updatedAtEpochMs
                this[combinedBooleanKey(platformId, "unlimited")] = snapshot.unlimitedQuota
                this[combinedBooleanKey(platformId, "empty_limited_quota")] =
                    snapshot.emptyLimitedQuota
                this[combinedBooleanKey(platformId, "token_expired")] = snapshot.tokenExpired
                this[combinedBooleanKey(platformId, "quota_overspent")] = snapshot.quotaOverspent
                if (snapshot.usedDisplay.isBlank()) {
                    remove(key("used_display"))
                } else {
                    this[key("used_display")] = snapshot.usedDisplay
                }
                remove(key("error"))
            }
        }
    }

    private fun combinedStringKey(platformId: String, suffix: String) =
        stringPreferencesKey("qw_combined_${platformId}_$suffix")

    private fun combinedLongKey(platformId: String, suffix: String) =
        longPreferencesKey("qw_combined_${platformId}_$suffix")

    private fun combinedBooleanKey(platformId: String, suffix: String) =
        booleanPreferencesKey("qw_combined_${platformId}_$suffix")
}
