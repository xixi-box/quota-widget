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

@Composable
fun ColumnScope.NewApiConfigContent(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    adminToken: String,
    onAdminTokenChange: (String) -> Unit,
    adminUserId: String,
    onAdminUserIdChange: (String) -> Unit,
    isDirty: Boolean,
    isSaving: Boolean,
    saveError: String?,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.new_api_base_url_label)) },
        placeholder = { Text(stringResource(R.string.new_api_base_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = adminToken,
        onValueChange = onAdminTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.new_api_admin_token_label)) },
        supportingText = { Text(stringResource(R.string.new_api_admin_token_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = adminUserId,
        onValueChange = onAdminUserIdChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.new_api_admin_user_id_label)) },
        supportingText = { Text(stringResource(R.string.new_api_admin_user_id_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !isSaving,
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
