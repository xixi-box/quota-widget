package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toNewApiSubscriptionUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toNewApiSubscriptionUsageProgressStyle
import com.kuyermqi.quotawidget.widget.usage.UsagePercentCompactContent
import com.kuyermqi.quotawidget.widget.usage.UsagePercentWidgetContent

class NewApiSubscriptionWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.NEW_API_SUBSCRIPTION) { state, refreshPhase, openApp, platformTitle ->
            val prefs = currentState<Preferences>()
            UsagePercentWidgetContent(
                platformId = PlatformIds.NEW_API_SUBSCRIPTION,
                platformTitle = platformTitle,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = UsageWindowKind.SUBSCRIPTION,
                usageDisplayMode = prefs.toNewApiSubscriptionUsageDisplayMode(),
                usageProgressStyle = prefs.toNewApiSubscriptionUsageProgressStyle(),
            )
        }
    }
}

class NewApiSubscriptionCompactWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.NEW_API_SUBSCRIPTION) { state, refreshPhase, openApp, _ ->
            val prefs = currentState<Preferences>()
            UsagePercentCompactContent(
                platformId = PlatformIds.NEW_API_SUBSCRIPTION,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = UsageWindowKind.SUBSCRIPTION,
                usageDisplayMode = prefs.toNewApiSubscriptionUsageDisplayMode(),
                usageProgressStyle = prefs.toNewApiSubscriptionUsageProgressStyle(),
            )
        }
    }
}

class NewApiSubscriptionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewApiSubscriptionWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class NewApiSubscriptionCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewApiSubscriptionCompactWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
