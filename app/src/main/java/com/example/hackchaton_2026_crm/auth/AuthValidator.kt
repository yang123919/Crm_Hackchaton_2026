package com.example.hackchaton_2026_crm.auth

enum class AuthField { NAME, EMAIL, PASSWORD, CONFIRM }

object AuthValidator {
    const val MIN_PASSWORD = 8

    private val email = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]{2,}$""")

    fun normalizeEmail(raw: String) = raw.trim().lowercase()

    fun validateEmail(raw: String): String? = when {
        raw.isBlank() -> "Enter your email"
        !email.matches(normalizeEmail(raw)) -> "That doesn't look like an email address"
        else -> null
    }

    fun validatePassword(password: String): String? = when {
        password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters"
        password.none(Char::isLetter) || password.none(Char::isDigit) -> "Use letters and at least one number"
        else -> null
    }

    fun validateRegister(name: String, email: String, password: String, confirm: String): Map<AuthField, String> =
        buildMap {
            if (name.trim().length < 2) put(AuthField.NAME, "Enter your name")
            validateEmail(email)?.let { put(AuthField.EMAIL, it) }
            validatePassword(password)?.let { put(AuthField.PASSWORD, it) }
            if (password != confirm) put(AuthField.CONFIRM, "Passwords don't match")
        }

    /** For sign-in only presence is checked; the password rules apply when it is created. */
    fun validateLogin(email: String, password: String): Map<AuthField, String> = buildMap {
        validateEmail(email)?.let { put(AuthField.EMAIL, it) }
        if (password.isEmpty()) put(AuthField.PASSWORD, "Enter your password")
    }
}
