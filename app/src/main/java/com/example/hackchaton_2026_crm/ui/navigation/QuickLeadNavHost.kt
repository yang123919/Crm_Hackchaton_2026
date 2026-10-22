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
import com.example.hackchaton_2026_crm.ui.capture.CaptureScreen
import com.example.hackchaton_2026_crm.ui.lead.LeadDetailScreen
import com.example.hackchaton_2026_crm.ui.review.ReviewScreen
import com.example.hackchaton_2026_crm.ui.today.TodayScreen
import com.example.hackchaton_2026_crm.ui.update.FollowUpScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object TodayRoute
@Serializable data class LeadDetailRoute(val leadId: Long)
@Serializable object CaptureRoute
@Serializable data class ReviewRoute(val transcript: String, val startedAtMillis: Long)
@Serializable data class FollowUpRoute(val leadId: Long)

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
                onCapture = { navController.navigate(CaptureRoute) },
                onOpenLead = { navController.navigate(LeadDetailRoute(it)) },
                onWhatsApp = openWhatsApp,
                onOpenSettings = {},
                onOpenLeads = null,
            )
        }
        composable<CaptureRoute> {
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onProcess = { transcript, startedAt -> navController.navigate(ReviewRoute(transcript, startedAt)) },
            )
        }
        composable<ReviewRoute> {
            // After saving (or opening the duplicate) the capture flow is done: Back goes to Today.
            val openLeadFromCapture: (Long) -> Unit = { id ->
                navController.navigate(LeadDetailRoute(id)) { popUpTo<TodayRoute>() }
            }
            ReviewScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    Toast.makeText(context, "Customer saved", Toast.LENGTH_SHORT).show()
                    openLeadFromCapture(id)
                },
                onOpenExisting = openLeadFromCapture,
            )
        }
        composable<LeadDetailRoute> {
            LeadDetailScreen(
                onBack = { navController.popBackStack() },
                onWhatsApp = openWhatsApp,
                onVoiceUpdate = { navController.navigate(FollowUpRoute(it)) },
            )
        }
        composable<FollowUpRoute> {
            FollowUpScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    Toast.makeText(context, "Added to history", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
            )
        }
    }
}
