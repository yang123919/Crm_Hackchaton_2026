package com.example.hackchaton_2026_crm.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hackchaton_2026_crm.QuickLeadApp
import com.example.hackchaton_2026_crm.ui.Intents
import com.example.hackchaton_2026_crm.ui.lead.LeadDetailScreen
import com.example.hackchaton_2026_crm.ui.today.TodayScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object TodayRoute
@Serializable data class LeadDetailRoute(val leadId: Long)

@Composable
fun QuickLeadNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = (context.applicationContext as QuickLeadApp).container.leadRepository

    // Until the AI WhatsApp draft exists, open a blank chat with the customer.
    val openWhatsApp: (Long) -> Unit = { leadId ->
        scope.launch { repository.getLead(leadId)?.let { Intents.whatsapp(context, it.mobile) } }
    }

    NavHost(navController = navController, startDestination = TodayRoute) {
        composable<TodayRoute> {
            TodayScreen(
                onCapture = { Toast.makeText(context, "Capture comes in step 3", Toast.LENGTH_SHORT).show() },
                onOpenLead = { navController.navigate(LeadDetailRoute(it)) },
                onWhatsApp = openWhatsApp,
                onOpenSettings = {},
                onOpenLeads = null,
            )
        }
        composable<LeadDetailRoute> {
            LeadDetailScreen(
                onBack = { navController.popBackStack() },
                onWhatsApp = openWhatsApp,
                onVoiceUpdate = {},
            )
        }
    }
}
