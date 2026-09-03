package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.newapi.NewApiSubscriptionClient
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.DEFAULT_NEW_API_QUOTA_PER_USD
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

class NewApiSubscriptionQuotaProvider(
    private val client: NewApiSubscriptionClient = NewApiSubscriptionClient(),
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.NEW_API_SUBSCRIPTION)
            ?: error("NewAPI subscription platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getNewApiSubscriptionSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        val settings = repo.getNewApiSubscriptionSettings()
        require(settings.isConfigured) { "NewAPI 订阅未配置 Base URL / 访问令牌 / 用户 ID" }
        return client.fetchSubscription(
            baseUrl = settings.baseUrl,
            accessToken = settings.accessToken,
            userId = settings.userId,
            quotaPerUsd = DEFAULT_NEW_API_QUOTA_PER_USD,
        )
    }
}
