package com.example.hackchaton_2026_crm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun localDateToString(date: LocalDate?): String? = date?.toString()
    @TypeConverter fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun instantToLong(instant: Instant?): Long? = instant?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

@Database(
    entities = [Lead::class, FollowUpEntry::class, CaptureMetric::class, User::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun leadDao(): LeadDao
    abstract fun userDao(): UserDao

    companion object {
        /** Adds the accounts table; existing customers and history are kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, " +
                        "`salt` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")
            }
        }

        /** Customers can now carry the IC number, address and gender read from a scanned MyKad. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `leads` ADD COLUMN `icNumber` TEXT")
                connection.execSQL("ALTER TABLE `leads` ADD COLUMN `address` TEXT")
                connection.execSQL("ALTER TABLE `leads` ADD COLUMN `gender` TEXT")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_leads_icNumber` ON `leads` (`icNumber`)")
            }
        }

        /** Customers can carry the driving licence type, classes and expiry needed to book a test drive. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `leads` ADD COLUMN `licenceType` TEXT")
                connection.execSQL("ALTER TABLE `leads` ADD COLUMN `licenceClasses` TEXT")
                connection.execSQL("ALTER TABLE `leads` ADD COLUMN `licenceValidTo` TEXT")
            }
        }

        /** Remembers whether the licence type was read from the photo (locked) or picked by the salesperson. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `leads` ADD COLUMN `licenceTypeFromPhoto` INTEGER")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "quicklead.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
