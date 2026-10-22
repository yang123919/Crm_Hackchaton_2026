package com.example.hackchaton_2026_crm

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.example.hackchaton_2026_crm.reminder.Reminders
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.hackchaton_2026_crm.ui.navigation.QuickLeadNavHost
import com.example.hackchaton_2026_crm.ui.theme.QuickLeadTheme

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Daily follow-up reminders need this on Android 13+.
        if (savedInstanceState == null && !Reminders.canNotify(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            QuickLeadTheme {
                QuickLeadNavHost()
            }
        }
    }
}
