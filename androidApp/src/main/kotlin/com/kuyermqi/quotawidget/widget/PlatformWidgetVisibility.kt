package com.kuyermqi.quotawidget.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.kuyermqi.quotawidget.platform.PlatformIds

/**
 * 平台组合: receivers of hidden platforms are disabled so launchers stop
 * offering their widgets in the picker. Platforms that already have widgets
 * placed on a launcher stay enabled — disabling the receiver would freeze
 * those widgets, which the app must never do.
 */
object PlatformWidgetVisibility {
    private val receiversByPlatform: Map<String, List<Class<out GlanceAppWidgetReceiver>>> = mapOf(
        PlatformIds.DEEPSEEK to listOf(
            DeepSeekBalanceWidgetReceiver::class.java,
            DeepSeekBalanceCompactWidgetReceiver::class.java,
        ),
        PlatformIds.OPENCODE_GO to listOf(
            OpenCodeGoWidgetReceiver::class.java,
            OpenCodeGoCompactWidgetReceiver::class.java,
            OpenCodeGoOverviewWidgetReceiver::class.java,
        ),
        PlatformIds.CODEX to listOf(
            CodexWidgetReceiver::class.java,
            CodexCompactWidgetReceiver::class.java,
            CodexOverviewWidgetReceiver::class.java,
        ),
        PlatformIds.NEW_API to listOf(
            NewApiAdminWidgetReceiver::class.java,
        ),
        PlatformIds.ZHIPU to listOf(
            ZhipuWidgetReceiver::class.java,
            ZhipuCompactWidgetReceiver::class.java,
            ZhipuOverviewWidgetReceiver::class.java,
        ),
        PlatformIds.NEW_API_SUBSCRIPTION to listOf(
            NewApiSubscriptionWidgetReceiver::class.java,
            NewApiSubscriptionCompactWidgetReceiver::class.java,
        ),
    )

    private val widgetsByPlatform: Map<String, List<Class<out GlanceAppWidget>>> = mapOf(
        PlatformIds.DEEPSEEK to listOf(
            DeepSeekBalanceWidget::class.java,
            DeepSeekBalanceCompactWidget::class.java,
        ),
        PlatformIds.OPENCODE_GO to listOf(
            OpenCodeGoWidget::class.java,
            OpenCodeGoCompactWidget::class.java,
            OpenCodeGoOverviewWidget::class.java,
        ),
        PlatformIds.CODEX to listOf(
            CodexWidget::class.java,
            CodexCompactWidget::class.java,
            CodexOverviewWidget::class.java,
        ),
        PlatformIds.NEW_API to listOf(
            NewApiAdminWidget::class.java,
        ),
        PlatformIds.ZHIPU to listOf(
            ZhipuWidget::class.java,
            ZhipuCompactWidget::class.java,
            ZhipuOverviewWidget::class.java,
        ),
        PlatformIds.NEW_API_SUBSCRIPTION to listOf(
            NewApiSubscriptionWidget::class.java,
            NewApiSubscriptionCompactWidget::class.java,
        ),
    )

    suspend fun applyVisibility(context: Context, hiddenPlatforms: Set<String>) {
        val manager = GlanceAppWidgetManager(context)
        val packageManager = context.packageManager
        for ((platformId, receivers) in receiversByPlatform) {
            val hasPlacedWidgets = widgetsByPlatform[platformId].orEmpty().any { widgetClass ->
                platformHasPlacedWidgets(manager, widgetClass)
            }
            val enabled = platformId !in hiddenPlatforms || hasPlacedWidgets
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            for (receiver in receivers) {
                runCatching {
                    packageManager.setComponentEnabledSetting(
                        ComponentName(context, receiver),
                        state,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }

    private suspend fun platformHasPlacedWidgets(
        manager: GlanceAppWidgetManager,
        widgetClass: Class<out GlanceAppWidget>,
    ): Boolean = runCatching {
        manager.getGlanceIds(widgetClass).isNotEmpty()
    }.getOrDefault(false)
}
