package com.kuyermqi.quotawidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only entry so refresh paths can be driven from adb:
 *
 * Single platform:
 *   adb shell am broadcast -a com.kuyermqi.quotawidget.ACTION_DEBUG_REFRESH \
 *     -n com.kuyermqi.quotawidget/.widget.DebugRefreshReceiver --es platform_id zhipu
 * All platforms (the 总览 refresh-all path):
 *   ... --es platform_id all
 * Un-stick: cancel queued refresh work and force every phase Idle:
 *   ... --es command force_idle
 */
class DebugRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val command = intent?.getStringExtra(EXTRA_COMMAND)
        val platformId = intent?.getStringExtra(EXTRA_PLATFORM_ID) ?: PlatformIds.OPENCODE_GO
        Log.i(TAG, "debug broadcast received action=${intent?.action} " +
            "command=$command platform=$platformId")
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (command) {
                    COMMAND_FORCE_IDLE -> {
                        cancelRefreshWork(appContext)
                        WidgetRefreshCoordinator.forceIdle(appContext)
                    }
                    else -> when (platformId) {
                        VALUE_ALL -> WidgetRefreshCoordinator.beginUserRefreshAll(appContext)
                        else -> WidgetRefreshCoordinator.beginUserRefresh(appContext, platformId)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "debug refresh failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun cancelRefreshWork(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(BalanceRefreshWorker.UNIQUE_WORK_NAME)
        workManager.cancelUniqueWork(WidgetRefreshCoordinator.USER_REFRESH_ALL_WORK_NAME)
        refreshableWorkNames.forEach { name ->
            workManager.cancelUniqueWork(name)
        }
        Log.i(TAG, "cancelled queued refresh work")
    }

    companion object {
        private const val TAG = "QuotaRefresh"
        const val ACTION = "com.kuyermqi.quotawidget.ACTION_DEBUG_REFRESH"
        const val EXTRA_PLATFORM_ID = "platform_id"
        const val EXTRA_COMMAND = "command"
        const val COMMAND_FORCE_IDLE = "force_idle"
        const val VALUE_ALL = "all"

        private val refreshableWorkNames = listOf(
            PlatformIds.DEEPSEEK,
            PlatformIds.OPENCODE_GO,
            PlatformIds.CODEX,
            PlatformIds.NEW_API,
            PlatformIds.ZHIPU,
            PlatformIds.NEW_API_SUBSCRIPTION,
            PlatformIds.FREE_MODELS,
        ).map { BalanceRefreshWorker.userRefreshWorkName(it) }
    }
}
