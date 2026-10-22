package com.example.hackchaton_2026_crm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hackchaton_2026_crm.ui.today.TodayScreen
import kotlinx.serialization.Serializable

@Serializable object TodayRoute

@Composable
fun QuickLeadNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = TodayRoute) {
        composable<TodayRoute> {
            TodayScreen()
        }
    }
}
