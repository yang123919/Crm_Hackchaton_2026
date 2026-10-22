package com.example.hackchaton_2026_crm.ui.navigation

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hackchaton_2026_crm.QuickLeadApp
import com.example.hackchaton_2026_crm.auth.AuthState
import com.example.hackchaton_2026_crm.data.LeadGroup
import com.example.hackchaton_2026_crm.ui.auth.LoginScreen
import com.example.hackchaton_2026_crm.ui.auth.RegisterScreen
import com.example.hackchaton_2026_crm.ui.capture.CaptureScreen
import com.example.hackchaton_2026_crm.ui.health.DataHealthScreen
import com.example.hackchaton_2026_crm.ui.lead.LeadDetailScreen
import com.example.hackchaton_2026_crm.ui.review.ReviewScreen
import com.example.hackchaton_2026_crm.ui.settings.SettingsScreen
import com.example.hackchaton_2026_crm.ui.today.LeadListScreen
import com.example.hackchaton_2026_crm.ui.today.TodayScreen
import com.example.hackchaton_2026_crm.ui.update.FollowUpScreen
import com.example.hackchaton_2026_crm.ui.whatsapp.WhatsAppSheet
import kotlinx.serialization.Serializable

/** Ignore a second navigation tap within this long of the last one. */
private const val NAV_DEBOUNCE_MS = 700L

@Serializable object LoginRoute
@Serializable object RegisterRoute
@Serializable object TodayRoute
@Serializable data class LeadDetailRoute(val leadId: Long)
@Serializable object CaptureRoute
@Serializable
data class ReviewRoute(
    val transcript: String,
    val startedAtMillis: Long,
    /** Read from a scanned IC, if any. Empty strings mean "not scanned". */
    val icName: String = "",
    val icNumber: String = "",
    val icAddress: String = "",
    val icGender: String = "",
)
@Serializable data class FollowUpRoute(val leadId: Long)
@Serializable object SettingsRoute
@Serializable data class LeadListRoute(val group: LeadGroup)
@Serializable object DataHealthRoute

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

    // A tap that lands while a screen is still animating in (or just after) would hit whatever is
    // under the finger on the new screen, e.g. the first customer card of a list just opened from a
    // counter. So user navigation is ignored until the current screen has settled.
    var lastNavAt by remember { mutableLongStateOf(0L) }
    fun settled() = navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
    fun go(route: Any) {
        val now = SystemClock.elapsedRealtime()
        if (!settled() || now - lastNavAt < NAV_DEBOUNCE_MS) return
        lastNavAt = now
        navController.navigate(route)
    }
    fun back() {
        if (settled()) navController.popBackStack()
    }

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

    // The default is a 700ms cross-fade, which leaves both screens' bars ghosting over each other and
    // reads as a "refresh". Short transitions keep the old screen from showing through.
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(160)) + slideInHorizontally(tween(220)) { it / 10 } },
        exitTransition = { fadeOut(tween(100)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = { fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { it / 10 } },
    ) {
        composable<LoginRoute> {
            LoginScreen(onGoToRegister = { go(RegisterRoute) })
        }
        composable<RegisterRoute> {
            RegisterScreen(onGoToLogin = { back() })
        }
        composable<TodayRoute> {
            TodayScreen(
                onCapture = { go(CaptureRoute) },
                onOpenLead = { go(LeadDetailRoute(it)) },
                onWhatsApp = openWhatsApp,
                onOpenSettings = { go(SettingsRoute) },
                onOpenDataHealth = { go(DataHealthRoute) },
                onOpenLeads = null,
                onOpenGroup = { go(LeadListRoute(it)) },
            )
        }
        composable<LeadListRoute> {
            LeadListScreen(
                onBack = { back() },
                onOpenLead = { go(LeadDetailRoute(it)) },
                onWhatsApp = openWhatsApp,
            )
        }
        composable<CaptureRoute> {
            CaptureScreen(
                onBack = { back() },
                onProcess = { transcript, startedAt, ic ->
                    go(
                        ReviewRoute(
                            transcript, startedAt,
                            icName = ic?.fullName.orEmpty(), icNumber = ic?.icNumber.orEmpty(),
                            icAddress = ic?.address.orEmpty(), icGender = ic?.gender.orEmpty(),
                        ),
                    )
                },
            )
        }
        composable<ReviewRoute> {
            // After saving (or opening the duplicate) the capture flow is done: Back goes to Today.
            val openLeadFromCapture: (Long) -> Unit = { id ->
                // "Open existing" tapped twice must not stack two detail pages.
                if (settled()) navController.navigate(LeadDetailRoute(id)) { popUpTo<TodayRoute>() }
            }
            ReviewScreen(
                onBack = { back() },
                onSaved = { id ->
                    Toast.makeText(context, "Customer saved", Toast.LENGTH_SHORT).show()
                    openLeadFromCapture(id)
                },
                onOpenExisting = openLeadFromCapture,
            )
        }
        composable<LeadDetailRoute> {
            LeadDetailScreen(
                onBack = { back() },
                onWhatsApp = openWhatsApp,
                onVoiceUpdate = { go(FollowUpRoute(it)) },
            )
        }
        composable<DataHealthRoute> {
            DataHealthScreen(onBack = { back() }, onOpenLead = { go(LeadDetailRoute(it)) })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { back() })
        }
        composable<FollowUpRoute> {
            FollowUpScreen(
                onBack = { back() },
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
