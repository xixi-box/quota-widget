package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.kuyermqi.quotawidget.platform.PlatformIds

/**
 * Must stay lightweight: Glance invokes this via a short-lived trampoline.
 * Heavy work is delegated to WorkManager.
 */
class RefreshBalanceAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val platformId = parameters[PLATFORM_ID_KEY]
            ?: PlatformIds.DEEPSEEK
        Log.i("QuotaRefresh", "RefreshBalanceAction platform=$platformId glanceId=$glanceId")
        if (parameters[REFRESH_ALL_KEY] == true) {
            // 总览 sub-cards: one tap refreshes every card.
            WidgetRefreshCoordinator.beginUserRefreshAll(context)
        } else {
            WidgetRefreshCoordinator.beginUserRefresh(context, platformId)
        }
    }

    companion object {
        val PLATFORM_ID_KEY = ActionParameters.Key<String>("platform_id")
        val REFRESH_ALL_KEY = ActionParameters.Key<Boolean>("refresh_all")
    }
}
