package com.example.hackchaton_2026_crm.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.auth.AuthField

@Composable
fun LoginScreen(
    onGoToRegister: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    AuthLayout(
        title = "Welcome back",
        subtitle = "Sign in to capture customers by voice.",
        loading = s.loading,
        formError = s.formError,
        primaryLabel = "Sign in",
        onPrimary = viewModel::login,
        switchLabel = "New here? Create an account",
        onSwitch = onGoToRegister,
    ) {
        EmailField(s.email, s.errors[AuthField.EMAIL], viewModel::setEmail, ImeAction.Next)
        PasswordField(
            "Password", s.password, s.errors[AuthField.PASSWORD], viewModel::setPassword,
            ImeAction.Done, onDone = viewModel::login,
        )
    }
}

@Composable
fun RegisterScreen(
    onGoToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    AuthLayout(
        title = "Create your account",
        subtitle = "Your name signs the WhatsApp messages you send.",
        loading = s.loading,
        formError = s.formError,
        primaryLabel = "Create account",
        onPrimary = viewModel::register,
        switchLabel = "Already registered? Sign in",
        onSwitch = onGoToLogin,
    ) {
        OutlinedTextField(
            value = s.name,
            onValueChange = viewModel::setName,
            label = { Text("Full name") },
            isError = s.errors[AuthField.NAME] != null,
            supportingText = s.errors[AuthField.NAME]?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(s.email, s.errors[AuthField.EMAIL], viewModel::setEmail, ImeAction.Next)
        PasswordField(
            "Password", s.password, s.errors[AuthField.PASSWORD], viewModel::setPassword,
            ImeAction.Next, hint = "At least 8 characters, letters and a number",
        )
        PasswordField(
            "Confirm password", s.confirm, s.errors[AuthField.CONFIRM], viewModel::setConfirm,
            ImeAction.Done, onDone = viewModel::register,
        )
    }
}

@Composable
private fun AuthLayout(
    title: String,
    subtitle: String,
    loading: Boolean,
    formError: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    switchLabel: String,
    onSwitch: () -> Unit,
    fields: @Composable () -> Unit,
) {
    val focus = LocalFocusManager.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("QuickLead", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            fields()

            formError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { focus.clearFocus(); onPrimary() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                else Text(primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onSwitch, enabled = !loading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(switchLabel)
            }
        }
    }
}

@Composable
private fun EmailField(value: String, error: String?, onChange: (String) -> Unit, ime: ImeAction) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Email") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ime),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    ime: ImeAction,
    onDone: () -> Unit = {},
    hint: String? = null,
) {
    val focus = LocalFocusManager.current
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(FocusDirection.Down) },
            onDone = { focus.clearFocus(); onDone() },
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
