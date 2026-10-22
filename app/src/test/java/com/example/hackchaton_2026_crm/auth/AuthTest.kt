package com.example.hackchaton_2026_crm.auth

import com.example.hackchaton_2026_crm.data.User
import com.example.hackchaton_2026_crm.data.UserDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeUserDao : UserDao {
    val users = MutableStateFlow<List<User>>(emptyList())
    override suspend fun findByEmail(email: String) = users.value.firstOrNull { it.email == email }
    override fun observe(id: Long): Flow<User?> = users.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun insert(user: User): Long {
        val id = users.value.size + 1L
        users.value = users.value + user.copy(id = id)
        return id
    }
}

private class FakeSession : SessionStore {
    val current = MutableStateFlow<Long?>(null)
    override val userId: Flow<Long?> = current
    override suspend fun set(userId: Long?) { current.value = userId }
}

class PasswordHasherTest {
    @Test fun sameSaltSamePassword_sameHash_otherwiseDifferent() {
        val salt = PasswordHasher.newSalt()
        assertEquals(PasswordHasher.hash("abc12345", salt), PasswordHasher.hash("abc12345", salt))
        assertNotEquals(PasswordHasher.hash("abc12345", salt), PasswordHasher.hash("abc12346", salt))
        assertNotEquals(PasswordHasher.hash("abc12345", salt), PasswordHasher.hash("abc12345", PasswordHasher.newSalt()))
    }

    @Test fun verify_acceptsOnlyTheRightPassword() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("correct horse 9", salt)
        assertTrue(PasswordHasher.verify("correct horse 9", salt, hash))
        assertFalse(PasswordHasher.verify("correct horse 8", salt, hash))
        assertFalse(hash.contains("correct"))
    }
}

class AuthValidatorTest {
    @Test fun email() {
        assertNull(AuthValidator.validateEmail("  Kelvin@CS-Group.my "))
        assertEquals("Enter your email", AuthValidator.validateEmail(" "))
        assertTrue(AuthValidator.validateEmail("kelvin@") != null)
        assertTrue(AuthValidator.validateEmail("kelvin example.com") != null)
    }

    @Test fun password() {
        assertNull(AuthValidator.validatePassword("abcdefg1"))
        assertTrue(AuthValidator.validatePassword("abc1") != null) // too short
        assertTrue(AuthValidator.validatePassword("abcdefgh") != null) // no number
        assertTrue(AuthValidator.validatePassword("12345678") != null) // no letter
    }

    @Test fun register_reportsEveryBadField() {
        assertEquals(
            setOf(AuthField.NAME, AuthField.EMAIL, AuthField.PASSWORD, AuthField.CONFIRM),
            AuthValidator.validateRegister("K", "nope", "short", "different").keys,
        )
        assertTrue(AuthValidator.validateRegister("Kelvin Lee", "k@x.my", "goodpass1", "goodpass1").isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private val dao = FakeUserDao()
    private val session = FakeSession()
    private var clock = 1_000L
    private val signedIn = mutableListOf<String>()

    private fun TestScope.repo() = AuthRepository(
        dao, session, backgroundScope, onSignedIn = { signedIn += it.displayName }, now = { clock },
    )

    @Test fun register_thenLogin_roundTrip() = runTest {
        val repo = repo()
        val reg = repo.register("Kelvin Lee", "  Kelvin@CS-Group.my ", "goodpass1", "goodpass1")
        assertTrue(reg is AuthResult.Success)
        assertEquals("kelvin@cs-group.my", dao.users.value.single().email)
        assertFalse(dao.users.value.single().passwordHash.contains("goodpass1"))
        assertEquals(listOf("Kelvin Lee"), signedIn)
        assertTrue(repo.state.first { it is AuthState.LoggedIn } is AuthState.LoggedIn)

        repo.logout()
        assertTrue(repo.state.first { it is AuthState.LoggedOut } is AuthState.LoggedOut)

        val login = repo.login("KELVIN@cs-group.my", "goodpass1")
        assertTrue(login is AuthResult.Success)
        assertEquals(2, signedIn.size)
    }

    @Test fun register_rejectsDuplicateEmail() = runTest {
        val repo = repo()
        repo.register("Kelvin", "k@x.my", "goodpass1", "goodpass1")
        val again = repo.register("Someone Else", "K@X.MY", "otherpass2", "otherpass2")
        assertTrue(again is AuthResult.Failure && AuthField.EMAIL in again.fields)
        assertEquals(1, dao.users.value.size)
    }

    @Test fun login_wrongPasswordAndUnknownEmail_lookTheSame() = runTest {
        val repo = repo()
        repo.register("Kelvin", "k@x.my", "goodpass1", "goodpass1")
        repo.logout()
        val wrong = repo.login("k@x.my", "badpass99") as AuthResult.Failure
        val unknown = repo.login("nobody@x.my", "badpass99") as AuthResult.Failure
        assertEquals(wrong.message, unknown.message)
        assertEquals("Wrong email or password", wrong.message)
        assertNull(session.current.value)
    }

    @Test fun login_locksAfterFiveFailures_thenRecovers() = runTest {
        val repo = repo()
        repo.register("Kelvin", "k@x.my", "goodpass1", "goodpass1")
        repo.logout()
        repeat(5) { repo.login("k@x.my", "badpass99") }
        // Even the right password is refused while locked.
        val locked = repo.login("k@x.my", "goodpass1") as AuthResult.Failure
        assertTrue(locked.message!!.startsWith("Too many attempts"))
        clock += 31_000
        assertTrue(repo.login("k@x.my", "goodpass1") is AuthResult.Success)
    }
}
