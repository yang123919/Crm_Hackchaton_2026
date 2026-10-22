package com.example.hackchaton_2026_crm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.hackchaton_2026_crm.ui.navigation.QuickLeadNavHost
import com.example.hackchaton_2026_crm.ui.theme.QuickLeadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuickLeadTheme {
                QuickLeadNavHost()
            }
        }
    }
}
