package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.ServerDao
import com.example.data.dao.PayloadDao
import com.example.data.dao.EmergencyContactDao
import com.example.data.dao.TunnelLogDao
import com.example.data.entity.ServerEntity
import com.example.data.entity.PayloadConfigEntity
import com.example.data.entity.EmergencyContactEntity
import com.example.data.entity.TunnelLogEntity

@Database(
    entities = [
        ServerEntity::class,
        PayloadConfigEntity::class,
        EmergencyContactEntity::class,
        TunnelLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun payloadDao(): PayloadDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun tunnelLogDao(): TunnelLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifeline_tunnel_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
