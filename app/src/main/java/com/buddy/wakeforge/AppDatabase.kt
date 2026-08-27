package com.buddy.wakeforge

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [AlarmEvent::class, MissionLog::class],
    version = 4, // bumped for AlarmEvent.ringtoneUri — see fallbackToDestructiveMigration below
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun missionLogDao(): MissionLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): AppDatabase {
            val appContext = context.applicationContext
            val passphrase = SecureKeyManager.getOrCreateDatabasePassphrase(appContext)
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(appContext, AppDatabase::class.java, "wakeforge.db")
                .openHelperFactory(factory) // <- this is what actually encrypts the file on disk (SQLCipher/AES-256)
                // No real users yet on this MVP, so a schema bump can safely wipe local
                // data rather than write a migration. Replace with a real Migration
                // once this ships to anyone, so upgrades don't silently delete alarms.
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
