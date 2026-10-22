package com.example.hackchaton_2026_crm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hackchaton_2026_crm.ui.theme.Mint
import com.example.hackchaton_2026_crm.ui.theme.MintLight
import com.example.hackchaton_2026_crm.ui.theme.Navy
import kotlinx.coroutines.delay

/**
 * Same name and parameters as Material's TopAppBar, so screens only swap the import: every screen
 * gets the deep-navy bar from the visual style guide, with white title and icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    androidx.compose.material3.TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Navy,
            scrolledContainerColor = Navy,
            titleContentColor = androidx.compose.ui.graphics.Color.White,
            navigationIconContentColor = androidx.compose.ui.graphics.Color.White,
            actionIconContentColor = androidx.compose.ui.graphics.Color.White,
        ),
    )
}

/**
 * Same name and parameters as Material's Button: mint with navy text (high contrast), 14dp corners.
 * Screens only swap the import.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Mint,
            contentColor = Navy,
            disabledContainerColor = Mint.copy(alpha = 0.35f),
            disabledContentColor = Navy.copy(alpha = 0.45f),
        ),
        content = content,
    )
}

/** "Quick" in white, "Lead" in mint, as on the login screen. [quick] is the colour of the first word. */
@Composable
fun QuickLeadWordmark(
    modifier: Modifier = Modifier,
    size: TextUnit = 22.sp,
    quick: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = quick)) { append("Quick") }
            withStyle(SpanStyle(color = Mint)) { append("Lead") }
        },
        modifier = modifier,
        fontSize = size,
        fontWeight = FontWeight.ExtraBold,
    )
}

/** Circle with the customer's first letter, for the profile header. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, diameter: Dp = 56.dp) {
    Box(
        modifier.size(diameter).background(MintLight, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = Navy,
        )
    }
}

/**
 * A loading spinner that only appears if loading takes longer than [delayMs]. Local data usually
 * arrives within a frame or two; showing a spinner for that long reads as the screen "flashing".
 */
@Composable
fun DelayedProgress(modifier: Modifier = Modifier, delayMs: Long = 300) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }
    if (visible) CircularProgressIndicator(modifier)
}
