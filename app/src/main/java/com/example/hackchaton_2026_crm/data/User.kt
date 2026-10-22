package com.example.hackchaton_2026_crm.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A salesperson account on this phone. [passwordHash] is PBKDF2 output, never the password. */
@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val salt: String,
    val createdAt: Instant = Instant.now(),
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observe(id: Long): Flow<User?>

    @Insert
    suspend fun insert(user: User): Long
}
