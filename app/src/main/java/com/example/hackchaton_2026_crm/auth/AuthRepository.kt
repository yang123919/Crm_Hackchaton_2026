package com.example.hackchaton_2026_crm.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.hackchaton_2026_crm.data.User
import com.example.hackchaton_2026_crm.data.UserDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: User) : AuthState
}

sealed interface AuthResult {
    data class Success(val user: User) : AuthResult
    data class Failure(val message: String?, val fields: Map<AuthField, String> = emptyMap()) : AuthResult
}

/** Which account is signed in on this phone. */
interface SessionStore {
    val userId: Flow<Long?>
    suspend fun set(userId: Long?)
}

private val Context.sessionDataStore by preferencesDataStore(name = "session")

class DataStoreSessionStore(private val context: Context) : SessionStore {
    private val key = longPreferencesKey("user_id")
    override val userId: Flow<Long?> = context.sessionDataStore.data.map { it[key] }
    override suspend fun set(userId: Long?) {
        context.sessionDataStore.edit { if (userId == null) it.remove(key) else it[key] = userId }
    }
}

/**
 * Local accounts stored in Room (demo scope: no server). Passwords are PBKDF2-hashed with a
 * per-user salt, sign-in errors never say which half was wrong, and repeated failures are
 * throttled per email.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepository(
    private val dao: UserDao,
    private val session: SessionStore,
    scope: CoroutineScope,
    /** Called after every successful sign-in or registration, e.g. to set the salesperson name. */
    private val onSignedIn: suspend (User) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    val state: StateFlow<AuthState> = session.userId
        .flatMapLatest { id ->
            if (id == null) flowOf(AuthState.LoggedOut)
            else dao.observe(id).map { user -> user?.let(AuthState::LoggedIn) ?: AuthState.LoggedOut }
        }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    private class Attempts(var failures: Int = 0, var lockedUntil: Long = 0)
    private val attempts = HashMap<String, Attempts>()

    // Hashed against when the email is unknown, so unknown and wrong-password take equally long.
    private val dummySalt = PasswordHasher.newSalt()

    suspend fun register(name: String, email: String, password: String, confirm: String): AuthResult {
        val errors = AuthValidator.validateRegister(name, email, password, confirm)
        if (errors.isNotEmpty()) return AuthResult.Failure(null, errors)

        val normalized = AuthValidator.normalizeEmail(email)
        if (dao.findByEmail(normalized) != null) {
            return AuthResult.Failure(null, mapOf(AuthField.EMAIL to "An account with this email already exists"))
        }
        val salt = PasswordHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(password, salt) }
        val user = User(email = normalized, displayName = name.trim(), passwordHash = hash, salt = salt)
        val id = try {
            dao.insert(user)
        } catch (_: Exception) {
            // Unique index lost a race with another registration.
            return AuthResult.Failure(null, mapOf(AuthField.EMAIL to "An account with this email already exists"))
        }
        return signIn(user.copy(id = id))
    }

    suspend fun login(email: String, password: String): AuthResult {
        val errors = AuthValidator.validateLogin(email, password)
        if (errors.isNotEmpty()) return AuthResult.Failure(null, errors)

        val normalized = AuthValidator.normalizeEmail(email)
        val entry = attempts.getOrPut(normalized) { Attempts() }
        val wait = entry.lockedUntil - now()
        if (wait > 0) return AuthResult.Failure("Too many attempts. Try again in ${(wait + 999) / 1000} s.")

        val user = dao.findByEmail(normalized)
        val ok = withContext(Dispatchers.Default) {
            if (user != null) PasswordHasher.verify(password, user.salt, user.passwordHash)
            else PasswordHasher.hash(password, dummySalt).isEmpty()
        }
        if (user == null || !ok) {
            entry.failures++
            if (entry.failures >= MAX_FAILURES) {
                entry.failures = 0
                entry.lockedUntil = now() + LOCKOUT_MS
            }
            return AuthResult.Failure("Wrong email or password")
        }
        attempts.remove(normalized)
        return signIn(user)
    }

    suspend fun logout() = session.set(null)

    suspend fun currentUser(): User? = (state.first { it !is AuthState.Loading } as? AuthState.LoggedIn)?.user

    private suspend fun signIn(user: User): AuthResult {
        session.set(user.id)
        onSignedIn(user)
        return AuthResult.Success(user)
    }

    private companion object {
        const val MAX_FAILURES = 5
        const val LOCKOUT_MS = 30_000L
    }
}
