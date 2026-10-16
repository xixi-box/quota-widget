package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.opencode.OpenCodeGoClient
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

class OpenCodeGoQuotaProvider(
    private val client: OpenCodeGoClient = OpenCodeGoClient(),
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.OPENCODE_GO)
            ?: error("OpenCode Go platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getOpenCodeGoSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        val settings = repo.getOpenCodeGoSettings()
        require(settings.isConfigured) { "OpenCode Go 未登录" }
        // New console sessions carry a console_session cookie; legacy sessions
        // carry auth=. Route accordingly.
        val consoleSession = OpenCodeGoClient.extractConsoleSession(settings.authCookie)
        return if (consoleSession != null) {
            client.fetchConsoleQuota(consoleSession)
        } else {
            client.fetchQuota(
                workspaceId = settings.workspaceId,
                authCookie = settings.authCookie,
            )
        }
    }
}
