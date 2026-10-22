package com.example.hackchaton_2026_crm.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object Intents {
    fun dial(context: Context, mobile: String) {
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$mobile")), "No phone app found")
    }

    /** Opens WhatsApp (or the browser fallback) with an optional pre-filled message. */
    fun whatsapp(context: Context, mobile: String, text: String? = null) {
        val url = buildString {
            append("https://wa.me/").append(mobile)
            if (!text.isNullOrBlank()) append("?text=").append(Uri.encode(text))
        }
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "WhatsApp not available")
    }

    private fun start(context: Context, intent: Intent, error: String) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }
}
