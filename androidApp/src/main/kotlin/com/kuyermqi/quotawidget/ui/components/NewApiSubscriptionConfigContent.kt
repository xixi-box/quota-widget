package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind

@Composable
fun ColumnScope.NewApiSubscriptionConfigContent(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    accessToken: String,
    onAccessTokenChange: (String) -> Unit,
    userId: String,
    onUserIdChange: (String) -> Unit,
    windows: List<QuotaWindow>,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    isDirty: Boolean,
    isSaving: Boolean,
    saveError: String?,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.newapi_sub_base_url_label)) },
        placeholder = { Text(stringResource(R.string.newapi_sub_base_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = accessToken,
        onValueChange = onAccessTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.newapi_sub_token_label)) },
        supportingText = { Text(stringResource(R.string.newapi_sub_token_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = userId,
        onValueChange = onUserIdChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.newapi_sub_user_id_label)) },
        supportingText = { Text(stringResource(R.string.newapi_sub_user_id_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(16.dp))
    UsageDisplayPrefsSection(
        windows = windows,
        widgetWindowKind = UsageWindowKind.SUBSCRIPTION,
        onWidgetWindowKindChange = {},
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
        enabled = !isSaving,
        windowKindChoices = emptyList(),
        showWindowKindPicker = false,
        overviewKinds = windows.map { it.kind }.distinct(),
        showResetLabels = true,
        showProgressStyle = true,
    )
    if (!saveError.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = saveError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSave,
            enabled = isDirty && !isSaving,
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}
