package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.opencode.OpenCodeFreeModelsClient
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.refresh.BalanceRefreshResult
import com.kuyermqi.quotawidget.refresh.SingleFlight
import com.kuyermqi.quotawidget.settings.FreeModelsCache
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WidgetRefreshCoordinator {
    private const val TAG = "QuotaRefresh"
    private const val STALE_SPIN_TIMEOUT_MS = 45_000L
    private const val MIN_SPINNER_VISIBLE_MS = 450L
    const val USER_REFRESH_ALL_WORK_NAME = "quota_balance_user_refresh_all"

    private val widgetUpdateMutex = Mutex()
    private val backgroundRefreshFlight = SingleFlight<BalanceRefreshResult>()

    /**
     * ActionCallback entry: enqueue work only. Glance updates happen in the worker
     * via [WidgetGlanceState.syncAndUpdate] so recomposition sees fresh Glance state.
     */
    suspend fun beginUserRefresh(context: Context, platformId: String) {
        val app = context.applicationContext as QuotaWidgetApp
        val settings = app.settingsRepository
        val phase = settings.getRefreshIconPhase(platformId)
        Log.i(TAG, "beginUserRefresh platform=$platformId phase=$phase")
        if (phase == RefreshIconPhase.Spinning || phase == RefreshIconPhase.Settling) {
            val startedAt = settings.getRefreshStartedAtEpochMs(platformId)
            val age = System.currentTimeMillis() - startedAt
            if (startedAt > 0L && age in 0 until STALE_SPIN_TIMEOUT_MS) {
                Log.w(TAG, "beginUserRefresh ignored; still refreshing platform=$platformId ageMs=$age")
                return
            }
            Log.w(TAG, "beginUserRefresh stale phase=$phase platform=$platformId ageMs=$age; continuing")
        }

        settings.setRefreshStartedAtEpochMs(platformId, System.currentTimeMillis())
        settings.setRefreshIconPhase(platformId, RefreshIconPhase.Spinning)
        updateWidgetSerialized(context, "spinning-pre-enqueue")
        Log.i(TAG, "beginUserRefresh enqueued user work platform=$platformId")

        WorkManager.getInstance(context).enqueueUniqueWork(
            BalanceRefreshWorker.userRefreshWorkName(platformId),
            ExistingWorkPolicy.REPLACE,
            BalanceRefreshWork.oneTime(
                userInitiated = true,
                expedited = true,
                platformId = platformId,
            ),
        )
    }

    suspend fun runUserRefresh(context: Context, platformId: String): BalanceRefreshResult {
        val app = context.applicationContext as QuotaWidgetApp
        val settings = app.settingsRepository
        val startedAt = settings.getRefreshStartedAtEpochMs(platformId).takeIf { it > 0L }
            ?: System.currentTimeMillis().also {
                settings.setRefreshStartedAtEpochMs(platformId, it)
            }
        Log.i(TAG, "runUserRefresh start platform=$platformId")
        return try {
            settings.setRefreshIconPhase(platformId, RefreshIconPhase.Spinning)
            updateWidgetSerialized(context, "spinning")

            val result = if (platformId == PlatformIds.FREE_MODELS) {
                refreshFreeModels(app)
            } else {
                app.refreshInteractor.refresh(platformId)
            }
            logResult(platformId, result)
            Log.i(TAG, "runUserRefresh network done platform=$platformId")

            val elapsed = System.currentTimeMillis() - startedAt
            val remain = MIN_SPINNER_VISIBLE_MS - elapsed
            if (remain > 0L) {
                delay(remain)
            }
            result
        } catch (t: Throwable) {
            Log.e(TAG, "runUserRefresh failed platform=$platformId", t)
            throw t
        } finally {
            settings.setRefreshIconPhase(platformId, RefreshIconPhase.Idle)
            Log.i(TAG, "runUserRefresh finally phase=Idle platform=$platformId")
            updateWidgetSerialized(context, "idle")
        }
    }

    /** 免费模型 has no quota provider; the button re-fetches the official pricing page. */
    private suspend fun refreshFreeModels(app: QuotaWidgetApp): BalanceRefreshResult {
        val client = OpenCodeFreeModelsClient()
        return try {
            val models = client.fetchFreeModels()
            app.settingsRepository.saveFreeModelsCache(
                FreeModelsCache(models = models, updatedAtEpochMs = System.currentTimeMillis()),
            )
            BalanceRefreshResult.Completed(WidgetDisplayState.NotConfigured)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "refreshFreeModels failed", e)
            BalanceRefreshResult.TransientFailure(WidgetDisplayState.NotConfigured)
        } finally {
            client.close()
        }
    }

    suspend fun runBackgroundRefresh(context: Context): BalanceRefreshResult =
        backgroundRefreshFlight.run {
            runBackgroundRefreshOnce(context.applicationContext)
        }

    /**
     * 总览 refresh-all: every sub-card's button triggers this, so one tap spins
     * all cards and refreshes every configured platform plus the free-model
     * catalog. Spinning phases are only set for platforms that actually have
     * something to refresh; the free-model card spins unconditionally.
     */
    suspend fun beginUserRefreshAll(context: Context) {
        val app = context.applicationContext as QuotaWidgetApp
        val settings = app.settingsRepository
        val ids = refreshablePlatformIds(app)
        val now = System.currentTimeMillis()
        val busy = ids.any { id ->
            val phase = settings.getRefreshIconPhase(id)
            val startedAt = settings.getRefreshStartedAtEpochMs(id)
            (phase == RefreshIconPhase.Spinning || phase == RefreshIconPhase.Settling) &&
                startedAt > 0L && now - startedAt < STALE_SPIN_TIMEOUT_MS
        }
        if (busy) {
            Log.i(TAG, "beginUserRefreshAll ignored; a user refresh is already running")
            return
        }
        ids.forEach { id ->
            settings.setRefreshStartedAtEpochMs(id, now)
            settings.setRefreshIconPhase(id, RefreshIconPhase.Spinning)
        }
        updateWidgetSerialized(context, "spinning-all-pre-enqueue")
        Log.i(TAG, "beginUserRefreshAll enqueued work count=${ids.size}")
        WorkManager.getInstance(context).enqueueUniqueWork(
            USER_REFRESH_ALL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            BalanceRefreshWork.oneTime(userInitiated = true, expedited = true),
        )
    }

    suspend fun runUserRefreshAll(context: Context): BalanceRefreshResult {
        val app = context.applicationContext as QuotaWidgetApp
        val settings = app.settingsRepository
        val ids = refreshablePlatformIds(app)
        val startedAt = System.currentTimeMillis()
        Log.i(TAG, "runUserRefreshAll start count=${ids.size}")
        return try {
            val background = runBackgroundRefresh(context)
            val freeModels = refreshFreeModels(app)
            val result = if (freeModels is BalanceRefreshResult.TransientFailure &&
                freeModels.retryable
            ) {
                freeModels
            } else {
                background
            }
            logResult("all", result)
            val remain = MIN_SPINNER_VISIBLE_MS - (System.currentTimeMillis() - startedAt)
            if (remain > 0L) {
                delay(remain)
            }
            result
        } catch (t: Throwable) {
            Log.e(TAG, "runUserRefreshAll failed", t)
            throw t
        } finally {
            ids.forEach { settings.setRefreshIconPhase(it, RefreshIconPhase.Idle) }
            Log.i(TAG, "runUserRefreshAll finally phase=Idle")
            updateWidgetSerialized(context, "idle-all")
        }
    }

    /** Platforms with something to refresh, mirroring refreshAllConfigured(). */
    private suspend fun refreshablePlatformIds(app: QuotaWidgetApp): List<String> {
        val settings = app.settingsRepository
        return buildList {
            if (settings.getDeepSeekSettings().apiKey.isNotBlank()) {
                add(PlatformIds.DEEPSEEK)
            }
            if (settings.getOpenCodeGoSettings().isConfigured) {
                add(PlatformIds.OPENCODE_GO)
            }
            if (settings.getCodexSettings().isConfigured) {
                add(PlatformIds.CODEX)
            }
            if (settings.getNewApiSettings().isConfigured) {
                add(PlatformIds.NEW_API)
            }
            if (settings.getZhipuSettings().isConfigured) {
                add(PlatformIds.ZHIPU)
            }
            if (settings.getNewApiSubscriptionSettings().isConfigured) {
                add(PlatformIds.NEW_API_SUBSCRIPTION)
            }
            add(PlatformIds.FREE_MODELS)
        }
    }

    private suspend fun runBackgroundRefreshOnce(context: Context): BalanceRefreshResult {
        val app = context.applicationContext as QuotaWidgetApp
        Log.i(TAG, "runBackgroundRefresh start")
        return try {
            val results = app.refreshInteractor.refreshAllConfigured()
            results.forEachIndexed { index, result ->
                // Prefer logging OpenCode / DeepSeek errors for diagnosis.
                logResult("configured[$index]", result)
            }
            Log.i(TAG, "runBackgroundRefresh done count=${results.size}")
            results.firstOrNull { result ->
                result is BalanceRefreshResult.TransientFailure && result.retryable
            } ?: results.lastOrNull()
                ?: BalanceRefreshResult.Completed(WidgetDisplayState.NotConfigured)
        } finally {
            updateWidgetSerialized(context, "background-refresh")
        }
    }

    suspend fun forceIdle(context: Context, platformId: String? = null) {
        val app = context.applicationContext as QuotaWidgetApp
        if (platformId == null) {
            app.settingsRepository.clearAllRefreshIconPhases()
            Log.i(TAG, "forceIdle all platforms")
        } else {
            app.settingsRepository.setRefreshIconPhase(platformId, RefreshIconPhase.Idle)
            Log.i(TAG, "forceIdle platform=$platformId")
        }
        updateWidgetSerialized(context, "forceIdle")
    }

    private fun logResult(platformId: String, result: BalanceRefreshResult) {
        when (val state = when (result) {
            is BalanceRefreshResult.Completed -> result.state
            is BalanceRefreshResult.TransientFailure -> result.retained
        }) {
            is WidgetDisplayState.Error ->
                Log.e(TAG, "refresh error platform=$platformId message=${state.message}")
            WidgetDisplayState.NeedsReauth ->
                Log.w(TAG, "refresh needsReauth platform=$platformId")
            is WidgetDisplayState.Success ->
                Log.i(TAG, "refresh success platform=$platformId display=${state.snapshot.primaryDisplay}")
            else ->
                Log.i(TAG, "refresh done platform=$platformId state=${state::class.simpleName}")
        }
    }

    private suspend fun updateWidgetSerialized(context: Context, reason: String) {
        widgetUpdateMutex.withLock {
            WidgetGlanceState.syncAndUpdate(context, reason)
        }
    }
}
