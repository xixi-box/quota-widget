package com.kuyermqi.quotawidget.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Copies a model id onto the clipboard from a widget row.
 *
 * Glance's own receiver hosts ActionCallbacks, so no manifest entry is needed;
 * the proguard rules already keep `ActionCallback` subclasses instantiable.
 */
class CopyTextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val text = parameters[TEXT_KEY]?.takeIf { it.isNotBlank() } ?: return
        val label = parameters[LABEL_KEY].orEmpty()
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Log.i(TAG, "copied label=$label text=$text")
    }

    companion object {
        private const val TAG = "QuotaRefresh"

        val TEXT_KEY = ActionParameters.Key<String>("copy_text")
        val LABEL_KEY = ActionParameters.Key<String>("copy_label")
    }
}
