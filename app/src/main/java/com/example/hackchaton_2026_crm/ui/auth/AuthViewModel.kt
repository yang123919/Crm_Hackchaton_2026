package com.example.hackchaton_2026_crm.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hackchaton_2026_crm.auth.AuthField
import com.example.hackchaton_2026_crm.auth.AuthRepository
import com.example.hackchaton_2026_crm.auth.AuthResult
import com.example.hackchaton_2026_crm.ui.containerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val errors: Map<AuthField, String> = emptyMap(),
    /** Not tied to a field, e.g. "Wrong email or password". */
    val formError: String? = null,
    val loading: Boolean = false,
)

/**
 * Drives both Login and Register. On success the repository's session flips and the nav host
 * moves to Today, so this never navigates itself.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setName(v: String) = edit(AuthField.NAME) { copy(name = v) }
    fun setEmail(v: String) = edit(AuthField.EMAIL) { copy(email = v) }
    fun setPassword(v: String) = edit(AuthField.PASSWORD) { copy(password = v) }
    fun setConfirm(v: String) = edit(AuthField.CONFIRM) { copy(confirm = v) }

    private fun edit(field: AuthField, change: AuthUiState.() -> AuthUiState) =
        _state.update { it.change().copy(errors = it.errors - field, formError = null) }

    fun login() = submit { s -> repository.login(s.email, s.password) }

    fun register() = submit { s -> repository.register(s.name, s.email, s.password, s.confirm) }

    private fun submit(call: suspend (AuthUiState) -> AuthResult) {
        val current = _state.value
        if (current.loading) return
        _state.update { it.copy(loading = true, errors = emptyMap(), formError = null) }
        viewModelScope.launch {
            val result = call(current)
            _state.update {
                when (result) {
                    // Keep loading=true: the screen is about to be replaced; don't flash the form.
                    is AuthResult.Success -> it
                    is AuthResult.Failure -> it.copy(
                        loading = false,
                        errors = result.fields,
                        formError = result.message,
                        // A failed sign-in clears the password so it must be retyped.
                        password = if (result.message != null) "" else it.password,
                    )
                }
            }
        }
    }

    companion object {
        val Factory = containerFactory { AuthViewModel(it.authRepository) }
    }
}
