package com.example.dukaan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BusinessEntity::class,
        EmployeeEntity::class,
        AttendanceEventEntity::class,
        LeaveRequestEntity::class,
        AdvanceUdhaarEntity::class,
        ExpenseRecordEntity::class,
        EmployeeDocumentEntity::class,
        SupportMessageEntity::class,
        AuditLogEntity::class,
        AgentEntity::class,
        CommissionEntity::class,
        PunchRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DukaanDatabase : RoomDatabase() {
    abstract fun dukaanDao(): DukaanDao

    companion object {
        @Volatile
        private var INSTANCE: DukaanDatabase? = null

        fun getDatabase(context: Context): DukaanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DukaanDatabase::class.java,
                    "orakle_dukaan_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
