package com.example.dukaan.service

import android.content.Context
import android.util.Log
import com.example.dukaan.data.repository.DukaanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object LocalBackupManager {
    private const val TAG = "LocalBackupManager"
    private const val PREFS_NAME = "dukaan_backup_prefs"
    private const val KEY_LAST_BACKUP_DATE = "last_backup_date"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_time"

    /**
     * Checks if a backup has already been performed today. If not, performs a full local daily backup.
     */
    suspend fun performDailyBackupIfDue(context: Context, repository: DukaanRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val lastDate = prefs.getString(KEY_LAST_BACKUP_DATE, "")

            if (lastDate != todayStr) {
                val file = createFullBackupFile(context, repository, "daily_$todayStr")
                prefs.edit()
                    .putString(KEY_LAST_BACKUP_DATE, todayStr)
                    .putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis())
                    .apply()
                pruneOldBackups(context, maxKeepDays = 7)
                Log.i(TAG, "Automatic daily backup completed: ${file?.absolutePath}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Daily backup check/execution error", e)
            false
        }
    }

    /**
     * Triggers an immediate manual full database snapshot.
     */
    suspend fun performManualBackup(context: Context, repository: DukaanRepository): Result<File> = withContext(Dispatchers.IO) {
        try {
            val timeStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val file = createFullBackupFile(context, repository, "manual_$timeStr")
            if (file != null) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_LAST_BACKUP_DATE, todayStr)
                    .putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis())
                    .apply()
                Result.success(file)
            } else {
                Result.failure(Exception("Could not write backup file"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manual backup error", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a JSON snapshot file containing all tables from the Room DB.
     */
    private suspend fun createFullBackupFile(context: Context, repository: DukaanRepository, prefix: String): File? {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())
        root.put("exported_date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // 1. Businesses
        val businesses = repository.getAllBusinesses().firstOrNull().orEmpty()
        val bizArray = JSONArray()
        for (b in businesses) {
            bizArray.put(JSONObject().apply {
                put("id", b.id)
                put("businessCode", b.businessCode)
                put("name", b.name)
                put("ownerName", b.ownerName)
                put("phone", b.phone)
                put("email", b.email)
                put("businessType", b.businessType)
                put("status", b.status.name)
                put("plan", b.plan)
                put("agentCode", b.agentCode)
                put("latitude", b.latitude)
                put("longitude", b.longitude)
                put("geofenceRadiusMeters", b.geofenceRadiusMeters)
                put("shiftStart", b.shiftStart)
                put("shiftEnd", b.shiftEnd)
                put("workingDays", b.workingDays)
            })
        }
        root.put("businesses", bizArray)

        // 2. Employees
        val employees = repository.getAllEmployees().firstOrNull().orEmpty()
        val empArray = JSONArray()
        for (e in employees) {
            empArray.put(JSONObject().apply {
                put("id", e.id)
                put("businessId", e.businessId)
                put("employeeCode", e.employeeCode)
                put("fullName", e.fullName)
                put("phone", e.phone)
                put("email", e.email)
                put("designation", e.designation)
                put("monthlySalary", e.monthlySalary)
                put("salaryType", e.salaryType.name)
                put("status", e.status)
            })
        }
        root.put("employees", empArray)

        // 3. Agents
        val agents = repository.getAllAgents().firstOrNull().orEmpty()
        val agArray = JSONArray()
        for (a in agents) {
            agArray.put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("phone", a.phone)
                put("email", a.email)
                put("agentCode", a.agentCode)
                put("commissionPercent", a.commissionPercent)
                put("earnings", a.earnings)
                put("status", a.status)
            })
        }
        root.put("agents", agArray)

        // 4. Commissions
        val comms = repository.getAllCommissions().firstOrNull().orEmpty()
        val commArray = JSONArray()
        for (c in comms) {
            commArray.put(JSONObject().apply {
                put("id", c.id)
                put("agentId", c.agentId)
                put("businessId", c.businessId)
                put("shopName", c.shopName)
                put("subscriptionAmount", c.subscriptionAmount)
                put("commissionAmount", c.commissionAmount)
                put("status", c.status)
                put("createdAt", c.createdAt)
            })
        }
        root.put("commissions", commArray)

        val backupDir = File(context.getExternalFilesDir("backups") ?: context.filesDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val backupFile = File(backupDir, "dukaan_backup_${prefix}.json")
        FileWriter(backupFile).use { writer ->
            writer.write(root.toString(2))
        }
        return backupFile
    }

    /**
     * Lists existing local backup files sorted newest first.
     */
    fun getBackupFiles(context: Context): List<File> {
        val backupDir = File(context.getExternalFilesDir("backups") ?: context.filesDir, "backups")
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    /**
     * Returns formatted timestamp of last backup.
     */
    fun getLastBackupTimeFormatted(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
        return if (time > 0) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(time))
        } else {
            "Never (First backup pending)"
        }
    }

    /**
     * Deletes backups older than maxKeepDays to avoid filling disk space.
     */
    private fun pruneOldBackups(context: Context, maxKeepDays: Int = 7) {
        val files = getBackupFiles(context)
        val cutoff = System.currentTimeMillis() - (maxKeepDays * 24L * 60 * 60 * 1000)
        for (file in files) {
            if (file.lastModified() < cutoff) {
                file.delete()
                Log.d(TAG, "Pruned old backup: ${file.name}")
            }
        }
    }
}
