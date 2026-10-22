package com.example.hackchaton_2026_crm.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hackchaton_2026_crm.QuickLeadApp
import com.example.hackchaton_2026_crm.auth.AuthState
import com.example.hackchaton_2026_crm.ui.auth.LoginScreen
import com.example.hackchaton_2026_crm.ui.auth.RegisterScreen
import com.example.hackchaton_2026_crm.ui.capture.CaptureScreen
import com.example.hackchaton_2026_crm.ui.lead.LeadDetailScreen
import com.example.hackchaton_2026_crm.ui.review.ReviewScreen
import com.example.hackchaton_2026_crm.ui.settings.SettingsScreen
import com.example.hackchaton_2026_crm.ui.today.TodayScreen
import com.example.hackchaton_2026_crm.ui.update.FollowUpScreen
import com.example.hackchaton_2026_crm.ui.whatsapp.WhatsAppSheet
import kotlinx.serialization.Serializable

@Serializable object LoginRoute
@Serializable object RegisterRoute
@Serializable object TodayRoute
@Serializable data class LeadDetailRoute(val leadId: Long)
@Serializable object CaptureRoute
@Serializable data class ReviewRoute(val transcript: String, val startedAtMillis: Long)
@Serializable data class FollowUpRoute(val leadId: Long)
@Serializable object SettingsRoute

@Composable
fun QuickLeadNavHost() {
    val auth = (LocalContext.current.applicationContext as QuickLeadApp).container.authRepository
    val authState by auth.state.collectAsStateWithLifecycle()
    when (authState) {
        // Brief: reading the saved session from disk. Avoids flashing the sign-in form at a signed-in user.
        AuthState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        else -> AppNav(authState)
    }
}

@Composable
private fun AppNav(authState: AuthState) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Fixed at first composition; later sign-ins and sign-outs navigate explicitly below.
    val startDestination: Any = remember { if (authState is AuthState.LoggedIn) TodayRoute else LoginRoute }

    // WhatsApp draft sheet shown over any screen: (leadId, openId) so each opening drafts afresh.
    var whatsApp by rememberSaveable { mutableStateOf<Pair<Long, Int>?>(null) }
    var whatsAppOpens by rememberSaveable { mutableIntStateOf(0) }
    val openWhatsApp: (Long) -> Unit = { leadId -> whatsApp = leadId to ++whatsAppOpens }

    // The session is the single source of truth: signing in or out anywhere moves to the right screen.
    LaunchedEffect(authState) {
        val destination = navController.currentDestination ?: return@LaunchedEffect
        val onAuthScreen = destination.hasRoute<LoginRoute>() || destination.hasRoute<RegisterRoute>()
        when {
            authState is AuthState.LoggedIn && onAuthScreen ->
                navController.navigate(TodayRoute) { popUpTo<LoginRoute> { inclusive = true } }
            authState is AuthState.LoggedOut && !onAuthScreen -> {
                whatsApp = null
                navController.navigate(LoginRoute) { popUpTo<TodayRoute> { inclusive = true } }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<LoginRoute> {
            LoginScreen(onGoToRegister = { navController.navigate(RegisterRoute) })
        }
        composable<RegisterRoute> {
            RegisterScreen(onGoToLogin = { navController.popBackStack() })
        }
        composable<TodayRoute> {
            TodayScreen(
                onCapture = { navController.navigate(CaptureRoute) },
                onOpenLead = { navController.navigate(LeadDetailRoute(it)) },
                onWhatsApp = openWhatsApp,
                onOpenSettings = { navController.navigate(SettingsRoute) },
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
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
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

    whatsApp?.let { (leadId, openId) ->
        WhatsAppSheet(leadId = leadId, openId = openId, onDismiss = { whatsApp = null })
    }
}
