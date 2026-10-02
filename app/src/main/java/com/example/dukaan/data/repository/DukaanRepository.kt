package com.example.dukaan.data.repository

import android.content.Context
import android.util.Log
import com.example.dukaan.data.local.*
import com.example.dukaan.data.model.*
import com.example.dukaan.data.remote.SupabaseClient
import com.example.dukaan.service.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

private fun Throwable.isCancellation(): Boolean {
    var curr: Throwable? = this
    while (curr != null) {
        if (curr is java.util.concurrent.CancellationException ||
            curr is kotlinx.coroutines.CancellationException ||
            curr.javaClass.name.contains("Cancellation") ||
            curr.message?.contains("composition", ignoreCase = true) == true
        ) {
            return true
        }
        curr = curr.cause
    }
    return false
}

class DukaanRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = DukaanDatabase.getDatabase(context).dukaanDao()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    companion object {
        private const val TAG = "DukaanRepository"
        const val SUPERADMIN_EMAIL = "superadmin@orakle.in"
        const val SUPERADMIN_CODE = "ADMIN-001"
    }

    // --- Geofence Distance Calculation (Haversine in Meters) ---
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // --- Clean Real Database Init (No Fake Demo Data) ---
    suspend fun cleanDemoDataIfPresent() = withContext(Dispatchers.IO) {
        val biz = dao.getBusinessById("biz-sharma-10284")
        if (biz != null) {
            dao.deleteBusinessById("biz-sharma-10284")
            dao.deleteEmployeeById("emp-rahul-01")
            dao.deleteEmployeeById("emp-suresh-02")
            dao.deleteEmployeeById("emp-amit-03")
        }
    }

    // --- Businesses Flow & Actions ---
    fun getAllBusinesses(): Flow<List<Business>> {
        return dao.getAllBusinesses().map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun getBusinessById(id: String): Business? = withContext(Dispatchers.IO) {
        dao.getBusinessById(id)?.toModel()
    }

    suspend fun registerBusiness(business: Business): Business = withContext(Dispatchers.IO) {
        dao.insertBusiness(business.toEntity())
        if (business.email.isNotBlank()) {
            try {
                SupabaseClient.registerShopInSupabase(business.toEntity(), "Password123!")
            } catch (e: Exception) {
                Log.e("DukaanRepository", "Failed to sync onboarded business to Supabase", e)
            }
        }
        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = business.id,
                action = "BUSINESS_REGISTERED",
                performedBy = "Superadmin",
                details = "Registered ${business.name} (${business.businessCode})",
                timestamp = System.currentTimeMillis()
            )
        )
        business
    }

    suspend fun registerBusiness(
        name: String,
        ownerName: String,
        phone: String,
        email: String,
        password: String = "123456",
        businessType: String,
        address: String,
        city: String,
        state: String,
        pincode: String,
        lat: Double,
        lng: Double,
        geofenceRadius: Int,
        plan: String,
        agentCode: String = "",
        workingDays: String = "MON,TUE,WED,THU,FRI,SAT"
    ): Business = withContext(Dispatchers.IO) {
        val randomNum = (10000..99999).random()
        val code = "ODK-$randomNum"
        val id = "biz-$randomNum"
        val monthlyPrice = when (plan) {
            "BASIC" -> 149.0
            "PLUS" -> 249.0
            "BUSINESS" -> 499.0
            else -> 149.0
        }
        val empLimit = when (plan) {
            "BASIC" -> 2
            "PLUS" -> 5
            "BUSINESS" -> 10
            else -> 2
        }
        val eventLimit = when (plan) {
            "BASIC" -> 4
            "PLUS" -> 6
            "BUSINESS" -> 8
            else -> 4
        }

        val entity = BusinessEntity(
            id = id,
            businessCode = code,
            name = name,
            ownerName = ownerName,
            phone = phone,
            email = email,
            password = password,
            businessType = businessType,
            address = address,
            city = city,
            state = state,
            pincode = pincode,
            latitude = lat,
            longitude = lng,
            geofenceRadiusMeters = geofenceRadius,
            plan = plan,
            monthlyPrice = monthlyPrice,
            employeeLimit = empLimit,
            dailyEventLimitPerEmployee = eventLimit,
            status = "PENDING",
            shiftStart = "09:00 AM",
            shiftEnd = "07:00 PM",
            graceMinutes = 15,
            overtimeThresholdMinutes = 30,
            agentCode = agentCode.trim(),
            workingDays = workingDays,
            createdAt = System.currentTimeMillis()
        )
        dao.insertBusiness(entity)

        // Live Cloud Registration to Supabase
        try {
            val supResult = SupabaseClient.registerShopInSupabase(entity, password)
            if (supResult.isSuccess) {
                Log.i("DukaanRepository", "Live registered shop in Supabase: ${entity.name}")
            } else {
                Log.w("DukaanRepository", "Supabase live shop registration returned error: ${supResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Failed to sync shop registration to Supabase", e)
        }

        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = id,
                action = "BUSINESS_REGISTERED",
                performedBy = ownerName,
                details = "Registered $name under plan $plan",
                timestamp = System.currentTimeMillis()
            )
        )

        entity.toModel()
    }

    /**
     * Synchronizes all registered businesses live from Supabase into the local database.
     * Enables Superadmins and users across devices to see up-to-date registered shops immediately.
     * Prunes any deleted shops and their staff so removed shops disappear instantly across all devices.
     */
    suspend fun syncBusinessesFromSupabase(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val result = SupabaseClient.fetchAllShopsFromSupabase()
            if (result.isSuccess) {
                val remoteShops = result.getOrNull().orEmpty()
                val remoteShopIds = remoteShops.map { it.id }.toSet()
                val remoteShopCodes = remoteShops.map { it.businessCode }.toSet()

                for (shop in remoteShops) {
                    dao.insertBusiness(shop)
                }

                // Prune local shops and their cascading data that no longer exist in Supabase
                val localShops = dao.getAllBusinessesList()
                for (local in localShops) {
                    if (!remoteShopIds.contains(local.id) && !remoteShopCodes.contains(local.businessCode)) {
                        dao.deleteBusinessById(local.id)
                        dao.deleteEmployeesByBusinessId(local.id)
                        dao.deleteAttendanceByBusinessId(local.id)
                        dao.deleteLeavesByBusinessId(local.id)
                        dao.deleteAdvancesByBusinessId(local.id)
                        dao.deleteExpensesByBusinessId(local.id)
                        Log.i("DukaanRepository", "Pruned deleted shop from local database: ${local.id} (${local.name})")
                    }
                }

                Log.i("DukaanRepository", "Synced ${remoteShops.size} shops live from Supabase")
                Result.success(remoteShops.size)
            } else {
                Log.w("DukaanRepository", "Failed to fetch shops from Supabase: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            if (e.isCancellation()) {
                Log.d("DukaanRepository", "syncBusinesses cancelled")
                return@withContext Result.failure(e)
            }
            Log.e("DukaanRepository", "Error syncing businesses from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun updateBusinessStatus(
        businessId: String,
        status: BusinessStatus,
        performedBy: String
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getBusinessById(businessId) ?: return@withContext
        val updated = existing.copy(status = status.name)
        dao.updateBusiness(updated)

        // Sync live status to Supabase
        try {
            SupabaseClient.updateShopStatusInSupabase(businessId, status.name)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Error syncing status to Supabase", e)
        }

        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                action = "STATUS_CHANGED",
                performedBy = performedBy,
                details = "Status changed to ${status.name}",
                timestamp = System.currentTimeMillis()
            )
        )

        // If newly approved/activated and referred by an agent, record pending commission for this cycle
        if (status == BusinessStatus.APPROVED || status == BusinessStatus.ACTIVE) {
            if (existing.agentCode.isNotBlank()) {
                val agent = dao.getAgentByCode(existing.agentCode)
                if (agent != null) {
                    val existingComms = dao.getAllCommissions().firstOrNull().orEmpty()
                    val alreadyRecorded = existingComms.any { it.businessId == businessId }
                    if (!alreadyRecorded && existing.monthlyPrice > 0) {
                        val commAmount = existing.monthlyPrice * (agent.commissionPercent / 100.0)
                        val comm = CommissionEntity(
                            id = UUID.randomUUID().toString(),
                            agentId = agent.id,
                            businessId = existing.id,
                            shopName = existing.name,
                            subscriptionAmount = existing.monthlyPrice,
                            commissionPercent = agent.commissionPercent,
                            commissionAmount = commAmount,
                            status = "PENDING",
                            paymentDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()),
                            createdAt = System.currentTimeMillis()
                        )
                        dao.insertCommission(comm)
                        NotificationHelper.showNotification(
                            context = appContext,
                            title = "Agent Commission Credited!",
                            message = "Referred shop '${existing.name}' approved! ₹${commAmount.toInt()} commission added to your ledger.",
                            targetRole = UserRole.AGENT
                        )
                    }
                }
            }
        }
    }

    suspend fun updateBusinessPlanConfig(
        businessId: String,
        plan: String,
        monthlyPrice: Double,
        employeeLimit: Int,
        dailyEventLimit: Int,
        performedBy: String
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getBusinessById(businessId) ?: return@withContext
        val updated = existing.copy(
            plan = plan,
            monthlyPrice = monthlyPrice,
            employeeLimit = employeeLimit,
            dailyEventLimitPerEmployee = dailyEventLimit
        )
        dao.updateBusiness(updated)

        // Sync live plan config to Supabase
        try {
            SupabaseClient.updateShopPlanInSupabase(businessId, plan, monthlyPrice, employeeLimit, dailyEventLimit)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Error syncing plan to Supabase", e)
        }

        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                action = "PLAN_OVERRIDDEN",
                performedBy = performedBy,
                details = "Plan set to $plan, ₹$monthlyPrice/mo, $employeeLimit emp, $dailyEventLimit events/day",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateBusiness(business: Business, performedBy: String = "Admin") = withContext(Dispatchers.IO) {
        dao.updateBusiness(business.toEntity())
        try {
            SupabaseClient.updateShopInSupabase(business.toEntity())
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Failed to sync updated business to Supabase", e)
        }
        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = business.id,
                action = "BUSINESS_UPDATED",
                performedBy = performedBy,
                details = "Updated details for ${business.name}",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBusiness(businessId: String, performedBy: String = "Superadmin") = withContext(Dispatchers.IO) {
        // Cascade delete from local Room: Business + Staff + Punches + Leaves + Advances + Expenses
        dao.deleteBusinessById(businessId)
        dao.deleteEmployeesByBusinessId(businessId)
        dao.deleteAttendanceByBusinessId(businessId)
        dao.deleteLeavesByBusinessId(businessId)
        dao.deleteAdvancesByBusinessId(businessId)
        dao.deleteExpensesByBusinessId(businessId)

        // Cascade delete from Supabase: Shop User + All Employee Users
        try {
            SupabaseClient.deleteShopAndEmployeesFromSupabase(businessId)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Error deleting shop and staff from Supabase", e)
        }

        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                action = "BUSINESS_DELETED",
                performedBy = performedBy,
                details = "Deleted business $businessId and all staff accounts",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Employees Flow & Actions ---
    fun getAllEmployees(): Flow<List<Employee>> {
        return dao.getAllEmployees().map { list ->
            list.map { it.toModel() }
        }
    }

    fun getEmployeesForBusiness(businessId: String): Flow<List<Employee>> {
        return dao.getEmployeesForBusiness(businessId).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun getEmployeeById(id: String): Employee? = withContext(Dispatchers.IO) {
        dao.getEmployeeById(id)?.toModel()
    }

    suspend fun saveEmployee(employee: Employee, isEdit: Boolean) = withContext(Dispatchers.IO) {
        val entity = employee.toEntity()
        if (isEdit) {
            dao.updateEmployee(entity)
        } else {
            dao.insertEmployee(entity)
        }

        // Live Cloud Sync of Employee to Supabase
        try {
            val pass = if (employee.password.isNotBlank()) employee.password else "Password123!"
            SupabaseClient.registerEmployeeInSupabase(entity, pass)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Failed to sync employee to Supabase", e)
        }

        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = employee.businessId,
                action = if (isEdit) "EMPLOYEE_UPDATED" else "EMPLOYEE_ADDED",
                performedBy = "Admin",
                details = "${employee.fullName} (${employee.employeeCode})",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun syncEmployeesAndAttendanceFromSupabase(businessId: String) = withContext(Dispatchers.IO) {
        try {
            val result = SupabaseClient.fetchFullShopDataFromSupabase(businessId)
            if (result.isSuccess) {
                val data = result.getOrNull()
                if (data != null) {
                    val remoteEmpIds = data.employees.map { it.id }.toSet()
                    for (emp in data.employees) { dao.insertEmployee(emp) }
                    for (p in data.punches) { dao.insertAttendanceEvent(p) }
                    for (l in data.leaves) { dao.insertLeave(l) }
                    for (e in data.expenses) { dao.insertExpense(e) }
                    for (a in data.advances) { dao.insertAdvance(a) }

                    // Prune local staff for this shop if deleted in Supabase
                    val localEmps = dao.getEmployeesForBusinessList(businessId)
                    for (localEmp in localEmps) {
                        if (!remoteEmpIds.contains(localEmp.id)) {
                            dao.deleteEmployeeById(localEmp.id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e.isCancellation()) {
                Log.d("DukaanRepository", "syncEmployeesAndAttendanceFromSupabase cancelled")
                return@withContext
            }
            Log.e("DukaanRepository", "syncEmployeesAndAttendanceFromSupabase failed", e)
        }
    }

    suspend fun syncAllEmployeesFromSupabase() = withContext(Dispatchers.IO) {
        try {
            val empResult = SupabaseClient.fetchAllEmployeesFromSupabase(null)
            if (empResult.isSuccess) {
                val list = empResult.getOrNull() ?: emptyList()
                val remoteEmpIds = list.map { it.id }.toSet()
                for (emp in list) {
                    dao.insertEmployee(emp)
                }
                val localEmps = dao.getAllEmployeesList()
                for (localEmp in localEmps) {
                    if (!remoteEmpIds.contains(localEmp.id)) {
                        dao.deleteEmployeeById(localEmp.id)
                    }
                }
            }
        } catch (e: Exception) {
            if (e.isCancellation()) {
                Log.d("DukaanRepository", "syncAllEmployeesFromSupabase cancelled")
                return@withContext
            }
            Log.e("DukaanRepository", "syncAllEmployeesFromSupabase failed", e)
        }
    }

    suspend fun deleteEmployee(employeeId: String, businessId: String) = withContext(Dispatchers.IO) {
        dao.deleteEmployeeById(employeeId)
        try {
            SupabaseClient.deleteEmployeeFromSupabase(employeeId, businessId)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Failed to delete employee from Supabase", e)
        }
        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                action = "EMPLOYEE_DELETED",
                performedBy = "Admin",
                details = "Deleted employee $employeeId",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateEmployeeProfile(
        employeeId: String,
        phone: String,
        address: String,
        emergencyContact: String,
        bankAccount: String,
        bankIfsc: String
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getEmployeeById(employeeId) ?: return@withContext
        val updated = existing.copy(
            phone = phone,
            address = address,
            emergencyContact = emergencyContact,
            bankAccount = bankAccount,
            bankIfsc = bankIfsc
        )
        dao.updateEmployee(updated)
        try {
            val pass = if (updated.password.isNotBlank()) updated.password else "Password123!"
            SupabaseClient.registerEmployeeInSupabase(updated, pass)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Failed to sync employee profile update to Supabase", e)
        }
        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = existing.businessId,
                action = "PROFILE_UPDATED",
                performedBy = existing.fullName,
                details = "Updated phone, address, and bank details",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Smart Attendance Punch with Geofence & Limits ---
    suspend fun recordAttendancePunch(
        businessId: String,
        employeeId: String,
        eventType: AttendanceType,
        userLat: Double,
        userLng: Double,
        photoUri: String,
        method: String = "LIVE_GPS_PHOTO",
        isOfflineMode: Boolean = false
    ): Result<AttendanceEvent> = withContext(Dispatchers.IO) {
        try {
            val business = dao.getBusinessById(businessId)?.toModel()
                ?: return@withContext Result.failure(Exception("Business not found"))
            val employee = dao.getEmployeeById(employeeId)?.toModel()
                ?: return@withContext Result.failure(Exception("Employee not found"))

            if (business.status != BusinessStatus.APPROVED && business.status != BusinessStatus.ACTIVE) {
                return@withContext Result.failure(Exception("Business account is ${business.status.name}. Please contact admin."))
            }

            if (employee.status != "ACTIVE") {
                return@withContext Result.failure(Exception("Employee account status is ${employee.status}"))
            }

            val now = System.currentTimeMillis()
            val todayStr = dateFormat.format(Date(now))
            val todayEvents = dao.getAttendanceForEmployeeToday(employeeId, todayStr)

            // Check Daily Event Limit
            if (todayEvents.size >= business.dailyEventLimitPerEmployee) {
                return@withContext Result.failure(
                    Exception("Daily limit reached (${business.dailyEventLimitPerEmployee} events/day for ${business.plan} plan). Cannot punch further today.")
                )
            }

            // Check Duplicate punch within 60 seconds
            val lastEvent = todayEvents.lastOrNull()
            if (lastEvent != null && (now - lastEvent.timestamp) < 60_000 && lastEvent.eventType == eventType.name) {
                return@withContext Result.failure(Exception("This attendance entry was already recorded a moment ago."))
            }

            // Calculate Distance from Business Coordinates
            val distanceMeters = calculateDistanceMeters(
                lat1 = business.latitude,
                lon1 = business.longitude,
                lat2 = userLat,
                lon2 = userLng
            )

            val isInsideGeofence = distanceMeters <= business.geofenceRadiusMeters
            val status = if (isInsideGeofence) "VERIFIED" else "OUTSIDE_GEOFENCE"

            var finalPhotoUri = photoUri
            if (!isOfflineMode && photoUri.isNotBlank() && !photoUri.startsWith("http") && !photoUri.startsWith("QR_PASS")) {
                val localFile = java.io.File(photoUri)
                if (localFile.exists()) {
                    val bucketUrl = SupabaseClient.uploadAttendancePhotoToSupabase(businessId, employeeId, localFile)
                    if (bucketUrl != null) {
                        finalPhotoUri = bucketUrl
                    }
                }
            }

            val eventEntity = AttendanceEventEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                employeeId = employeeId,
                eventType = eventType.name,
                timestamp = now,
                formattedTime = timeFormat.format(Date(now)),
                dateStr = todayStr,
                latitude = userLat,
                longitude = userLng,
                distanceFromShopMeters = distanceMeters,
                isGeofenceValid = isInsideGeofence,
                photoUri = finalPhotoUri,
                verificationMethod = method,
                status = status,
                isOffline = isOfflineMode,
                syncStatus = if (isOfflineMode) "PENDING_SYNC" else "SYNCED"
            )

            dao.insertAttendanceEvent(eventEntity)

            // Udhaar / Advance Deduction on IN event:
            if (eventType == AttendanceType.IN && isInsideGeofence) {
                handleUdhaarDeductionForDay(businessId, employeeId, todayStr, eventEntity.id)
            }

            // Try syncing live to Supabase if not offline
            if (!isOfflineMode) {
                try {
                    SupabaseClient.recordPunchInSupabase(businessId, employeeId, eventEntity)
                } catch (e: Exception) {
                    Log.e("DukaanRepository", "Failed to sync punch live to Supabase", e)
                }
            }

            // Role-separated notifications: Employee only sees their own confirmation, Admin sees staff punch
            NotificationHelper.showEmployeeNotification(
                context = appContext,
                title = "Duty ${eventType.name} Recorded",
                message = "Your attendance punch ${eventType.name} was saved at ${eventEntity.formattedTime} (${status.replace("_", " ")})"
            )
            NotificationHelper.showAdminNotification(
                context = appContext,
                title = "Staff ${eventType.name}: ${employee.fullName}",
                message = "${employee.fullName} punched ${eventType.name} at ${eventEntity.formattedTime} (${status.replace("_", " ")})"
            )

            Result.success(eventEntity.toModel())
        } catch (e: Exception) {
            Log.e(TAG, "recordAttendancePunch failed", e)
            Result.failure(e)
        }
    }

    private suspend fun handleUdhaarDeductionForDay(
        businessId: String,
        employeeId: String,
        dateStr: String,
        eventId: String
    ) {
        val activeAdvance = dao.getActiveAdvanceForEmployee(employeeId) ?: return
        if (activeAdvance.remainingAmount <= 0) return

        val deductionAmount = min(activeAdvance.dailyDeductionAmount, activeAdvance.remainingAmount)
        val newRemaining = activeAdvance.remainingAmount - deductionAmount
        val updatedAdvance = activeAdvance.copy(
            remainingAmount = newRemaining,
            status = if (newRemaining <= 0) "COMPLETED" else "ACTIVE"
        )
        dao.updateAdvance(updatedAdvance)

        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                action = "UDHAAR_DEDUCTION",
                performedBy = "System",
                details = "Deducted ₹$deductionAmount from advance for day $dateStr (Remaining: ₹$newRemaining)",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Offline Sync Queue ---
    suspend fun syncPendingAttendance(): Int = withContext(Dispatchers.IO) {
        val pending = dao.getPendingSyncAttendance()
        var syncedCount = 0
        for (item in pending) {
            val res = SupabaseClient.recordPunchInSupabase(item.businessId, item.employeeId, item)
            if (res.isSuccess) {
                dao.updateAttendanceEvent(item.copy(syncStatus = "SYNCED", isOffline = false))
                syncedCount++
            }
        }
        syncedCount
    }

    fun getAttendanceForBusiness(businessId: String): Flow<List<AttendanceEvent>> {
        return dao.getAttendanceForBusiness(businessId).map { list -> list.map { it.toModel() } }
    }

    fun getAttendanceForEmployee(employeeId: String): Flow<List<AttendanceEvent>> {
        return dao.getAttendanceForEmployee(employeeId).map { list -> list.map { it.toModel() } }
    }

    // --- Leaves ---
    fun getLeavesForBusiness(businessId: String): Flow<List<LeaveRequest>> {
        return dao.getLeavesForBusiness(businessId).map { list -> list.map { it.toModel() } }
    }

    fun getLeavesForEmployee(employeeId: String): Flow<List<LeaveRequest>> {
        return dao.getLeavesForEmployee(employeeId).map { list -> list.map { it.toModel() } }
    }

    suspend fun applyLeave(leave: LeaveRequest) = withContext(Dispatchers.IO) {
        val entity = leave.toEntity()
        dao.insertLeave(entity)
        try {
            SupabaseClient.recordLeaveInSupabase(entity)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "applyLeave sync failed", e)
        }
        // Targeted notifications: Employee gets confirmation, Admin gets the request
        val empName = dao.getEmployeeById(leave.employeeId)?.fullName ?: "Staff Member"
        NotificationHelper.showEmployeeNotification(
            context = appContext,
            title = "Leave Request Sent",
            message = "Applied for ${leave.leaveType} leave (${leave.startDate} to ${leave.endDate}, ${leave.days} days)."
        )
        NotificationHelper.showAdminNotification(
            context = appContext,
            title = "New Leave Request: $empName",
            message = "$empName requested ${leave.days} day(s) of ${leave.leaveType} leave."
        )
    }

    suspend fun updateLeave(leave: LeaveRequest) = withContext(Dispatchers.IO) {
        val entity = leave.toEntity()
        dao.updateLeave(entity)
        try {
            SupabaseClient.recordLeaveInSupabase(entity)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "updateLeave sync failed", e)
        }
    }

    suspend fun deleteLeave(leaveId: String) = withContext(Dispatchers.IO) {
        dao.deleteLeaveById(leaveId)
    }

    suspend fun updateLeaveStatus(leaveId: String, status: LeaveStatus, comment: String) = withContext(Dispatchers.IO) {
        val found = dao.getLeaveById(leaveId)
        if (found != null) {
            val updated = found.copy(status = status.name, adminComment = comment)
            dao.updateLeave(updated)
            try {
                SupabaseClient.recordLeaveInSupabase(updated)
            } catch (e: Exception) {
                Log.e("DukaanRepository", "updateLeaveStatus sync failed", e)
            }
            NotificationHelper.showEmployeeNotification(
                context = appContext,
                title = "Leave Request ${status.name}",
                message = "Your leave application has been ${status.name.lowercase()} by Admin."
            )
            NotificationHelper.showAdminNotification(
                context = appContext,
                title = "Leave Status Updated",
                message = "Marked leave application as ${status.name}."
            )
        }
    }

    // --- Advances / Udhaar ---
    fun getAdvancesForBusiness(businessId: String): Flow<List<AdvanceUdhaar>> {
        return dao.getAdvancesForBusiness(businessId).map { list -> list.map { it.toModel() } }
    }

    fun getAdvancesForEmployee(employeeId: String): Flow<List<AdvanceUdhaar>> {
        return dao.getAdvancesForEmployee(employeeId).map { list -> list.map { it.toModel() } }
    }

    suspend fun saveAdvance(advance: AdvanceUdhaar) = withContext(Dispatchers.IO) {
        val entity = advance.toEntity()
        dao.insertAdvance(entity)
        try {
            SupabaseClient.recordAdvanceInSupabase(entity)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "saveAdvance sync failed", e)
        }
        NotificationHelper.showEmployeeNotification(
            context = appContext,
            title = "Udhaar / Advance Credited",
            message = "Advance of ₹${advance.totalAmount.toInt()} has been issued to your account."
        )
        NotificationHelper.showAdminNotification(
            context = appContext,
            title = "Advance Issued",
            message = "Advance / Udhaar of ₹${advance.totalAmount.toInt()} recorded."
        )
        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = advance.businessId,
                action = "UDHAAR_ISSUED",
                performedBy = "Admin",
                details = "Issued ₹${advance.totalAmount} to employee ${advance.employeeId}",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateAdvance(advance: AdvanceUdhaar) = withContext(Dispatchers.IO) {
        val entity = advance.toEntity()
        dao.updateAdvance(entity)
        try {
            SupabaseClient.recordAdvanceInSupabase(entity)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "updateAdvance sync failed", e)
        }
    }

    suspend fun deleteAdvance(advanceId: String) = withContext(Dispatchers.IO) {
        dao.deleteAdvanceById(advanceId)
    }

    suspend fun updateAdvanceStatus(advanceId: String, newStatus: String) = withContext(Dispatchers.IO) {
        val found = dao.getAdvanceById(advanceId)
        if (found != null) {
            val updated = found.copy(status = newStatus)
            dao.updateAdvance(updated)
            try {
                SupabaseClient.recordAdvanceInSupabase(updated)
            } catch (e: Exception) {
                Log.e("DukaanRepository", "updateAdvanceStatus sync failed", e)
            }
        }
    }

    // --- Expenses ---
    fun getExpensesForBusiness(businessId: String): Flow<List<ExpenseRecord>> {
        return dao.getExpensesForBusiness(businessId).map { list -> list.map { it.toModel() } }
    }

    fun getExpensesForEmployee(employeeId: String): Flow<List<ExpenseRecord>> {
        return dao.getExpensesForEmployee(employeeId).map { list -> list.map { it.toModel() } }
    }

    suspend fun submitExpense(expense: ExpenseRecord) = withContext(Dispatchers.IO) {
        val entity = expense.toEntity()
        dao.insertExpense(entity)
        try {
            SupabaseClient.recordExpenseInSupabase(entity)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "submitExpense sync failed", e)
        }
    }

    suspend fun updateExpense(expense: ExpenseRecord) = withContext(Dispatchers.IO) {
        val entity = expense.toEntity()
        dao.updateExpense(entity)
        try {
            SupabaseClient.recordExpenseInSupabase(entity)
        } catch (e: Exception) {
            Log.e("DukaanRepository", "updateExpense sync failed", e)
        }
    }

    suspend fun deleteExpense(expenseId: String) = withContext(Dispatchers.IO) {
        dao.deleteExpenseById(expenseId)
    }

    suspend fun updateExpenseStatus(expenseId: String, status: ExpenseStatus) = withContext(Dispatchers.IO) {
        val found = dao.getExpenseById(expenseId)
        if (found != null) {
            val updated = found.copy(status = status.name)
            dao.updateExpense(updated)
            try {
                SupabaseClient.recordExpenseInSupabase(updated)
            } catch (e: Exception) {
                Log.e("DukaanRepository", "updateExpenseStatus sync failed", e)
            }
        }
    }

    // --- Documents ---
    fun getDocumentsForBusiness(businessId: String): Flow<List<EmployeeDocument>> {
        return dao.getDocumentsForBusiness(businessId).map { list -> list.map { it.toModel() } }
    }

    fun getDocumentsForEmployee(employeeId: String): Flow<List<EmployeeDocument>> {
        return dao.getDocumentsForEmployee(employeeId).map { list -> list.map { it.toModel() } }
    }

    suspend fun saveDocument(doc: EmployeeDocument) = withContext(Dispatchers.IO) {
        dao.insertDocument(doc.toEntity())
        NotificationHelper.showNotification(
            context = appContext,
            title = "Document Uploaded",
            message = "${doc.docType} (${doc.docName}) saved securely."
        )
    }

    suspend fun updateDocument(doc: EmployeeDocument) = withContext(Dispatchers.IO) {
        dao.updateDocument(doc.toEntity())
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        dao.deleteDocumentById(docId)
    }

    // --- Support Messages ---
    fun getSupportMessages(businessId: String): Flow<List<SupportMessage>> {
        return dao.getSupportMessages(businessId).map { list -> list.map { it.toModel() } }
    }

    fun getAllSupportMessages(): Flow<List<SupportMessage>> {
        return dao.getAllSupportMessages().map { list -> list.map { it.toModel() } }
    }

    suspend fun sendSupportMessage(msg: SupportMessage) = withContext(Dispatchers.IO) {
        dao.insertSupportMessage(msg.toEntity())
    }

    suspend fun clearCompletedLeaves(businessId: String) = withContext(Dispatchers.IO) {
        dao.clearCompletedLeaves(businessId)
    }

    suspend fun clearEmployeeCompletedLeaves(employeeId: String) = withContext(Dispatchers.IO) {
        dao.clearEmployeeCompletedLeaves(employeeId)
    }

    suspend fun clearSupportMessages(businessId: String) = withContext(Dispatchers.IO) {
        dao.clearSupportMessages(businessId)
    }

    suspend fun updateEmployeeAvatar(employeeId: String, imageFile: java.io.File): String? = withContext(Dispatchers.IO) {
        val found = dao.getEmployeeById(employeeId) ?: return@withContext null
        val remoteUrl = SupabaseClient.uploadAvatarToSupabase(employeeId, imageFile)
        val finalUrl = remoteUrl ?: imageFile.absolutePath
        val updated = found.copy(photoUrl = finalUrl)
        dao.updateEmployee(updated)
        try {
            SupabaseClient.registerEmployeeInSupabase(updated, updated.password)
        } catch (_: Exception) {}
        finalUrl
    }

    suspend fun updateBusinessLogo(businessId: String, imageFile: java.io.File): String? = withContext(Dispatchers.IO) {
        val found = dao.getBusinessById(businessId) ?: return@withContext null
        val remoteUrl = SupabaseClient.uploadAvatarToSupabase(businessId, imageFile)
        val finalUrl = remoteUrl ?: imageFile.absolutePath
        val prefs = appContext.getSharedPreferences("dukaan_logos", Context.MODE_PRIVATE)
        prefs.edit().putString("logo_$businessId", finalUrl).apply()
        try {
            SupabaseClient.updateShopInSupabase(found)
        } catch (_: Exception) {}
        finalUrl
    }

    fun getBusinessLogo(businessId: String): String? {
        val prefs = appContext.getSharedPreferences("dukaan_logos", Context.MODE_PRIVATE)
        return prefs.getString("logo_$businessId", null)
    }

    // --- Audit Logs ---
    fun getAuditLogs(businessId: String): Flow<List<AuditLog>> {
        return dao.getAuditLogs(businessId).map { list -> list.map { it.toModel() } }
    }

    suspend fun getBusinessByContact(contact: String): Business? = withContext(Dispatchers.IO) {
        dao.getBusinessByContact(contact)?.toModel()
    }

    suspend fun getEmployeeByContact(contact: String): Employee? = withContext(Dispatchers.IO) {
        dao.getEmployeeByContact(contact)?.toModel()
    }

    // --- Agents (Requirement 24-28) ---
    fun getAllAgents(): Flow<List<Agent>> = dao.getAllAgents().map { list -> list.map { it.toModel() } }

    suspend fun seedDefaultAgentIfEmpty() = withContext(Dispatchers.IO) {
        val existing = dao.getAllAgentsList()
        if (existing.isEmpty()) {
            val defaultAgent = AgentEntity(
                id = "agent-default-01",
                name = "Vikram Singh (Field Partner)",
                phone = "9876543210",
                email = "agent@dukaan.in",
                password = "123456",
                agentCode = "AGT-1001",
                commissionPercent = 20.0,
                status = "ACTIVE",
                earnings = 0.0,
                createdAt = System.currentTimeMillis()
            )
            dao.insertAgent(defaultAgent)
            Log.i("DukaanRepository", "Seeded default agent: AGT-1001")
        }
    }

    suspend fun syncAgentsFromSupabase(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val result = SupabaseClient.fetchAllAgentsFromSupabase()
            if (result.isSuccess) {
                val remoteAgents = result.getOrNull() ?: emptyList()
                for (agent in remoteAgents) {
                    dao.insertAgent(agent)
                }
                Log.i("DukaanRepository", "Synced ${remoteAgents.size} agents from Supabase")
                Result.success(remoteAgents.size)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("DukaanRepository", "Error syncing agents from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun getAgentById(id: String): Agent? = withContext(Dispatchers.IO) {
        dao.getAgentById(id)?.toModel()
    }

    suspend fun getAgentByCode(code: String): Agent? = withContext(Dispatchers.IO) {
        val raw = code.removePrefix("DUKAAN_AGENT:").trim()
        if (raw.isBlank()) return@withContext null
        val upper = raw.uppercase()
        val withoutHyphens = upper.replace("-", "").replace(" ", "")
        val digitsOnly = raw.filter { it.isDigit() }

        // 1. Direct DAO check
        var entity = dao.getAgentByCode(upper)
            ?: dao.getAgentByContact(raw)
            ?: dao.getAgentByContact(upper)

        // 2. Flexible in-memory lookup across all local agents
        if (entity == null) {
            val all = dao.getAllAgentsList()
            entity = all.find { ag ->
                ag.agentCode.equals(upper, ignoreCase = true) ||
                ag.agentCode.replace("-", "").replace(" ", "").equals(withoutHyphens, ignoreCase = true) ||
                (digitsOnly.length >= 10 && ag.phone.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10))) ||
                ag.email.equals(raw, ignoreCase = true) ||
                ag.id == raw
            }
        }

        // 3. Sync from Supabase if not found locally, then retry
        if (entity == null) {
            syncAgentsFromSupabase()
            entity = dao.getAgentByCode(upper)
                ?: dao.getAgentByContact(raw)
                ?: dao.getAgentByContact(upper)
            if (entity == null) {
                val all = dao.getAllAgentsList()
                entity = all.find { ag ->
                    ag.agentCode.equals(upper, ignoreCase = true) ||
                    ag.agentCode.replace("-", "").replace(" ", "").equals(withoutHyphens, ignoreCase = true) ||
                    (digitsOnly.length >= 10 && ag.phone.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10))) ||
                    ag.email.equals(raw, ignoreCase = true) ||
                    ag.id == raw
                }
            }
        }

        // 4. Fallback default seed (AGT-1001)
        if (entity == null && (withoutHyphens == "AGT1001" || digitsOnly.endsWith("9876543210") || raw.contains("agent@dukaan.in", ignoreCase = true))) {
            seedDefaultAgentIfEmpty()
            entity = dao.getAgentByCode("AGT-1001")
        }

        entity?.toModel()
    }

    suspend fun getAgentByContact(contact: String): Agent? = withContext(Dispatchers.IO) {
        val raw = contact.removePrefix("DUKAAN_AGENT:").trim()
        if (raw.isBlank()) return@withContext null
        val upper = raw.uppercase()
        val withoutHyphens = upper.replace("-", "").replace(" ", "")
        val digitsOnly = raw.filter { it.isDigit() }

        var entity = dao.getAgentByContact(raw)
            ?: dao.getAgentByCode(upper)
            ?: dao.getAgentByContact(upper)

        if (entity == null) {
            val all = dao.getAllAgentsList()
            entity = all.find { ag ->
                ag.agentCode.equals(upper, ignoreCase = true) ||
                ag.agentCode.replace("-", "").replace(" ", "").equals(withoutHyphens, ignoreCase = true) ||
                (digitsOnly.length >= 10 && ag.phone.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10))) ||
                ag.email.equals(raw, ignoreCase = true) ||
                ag.id == raw
            }
        }

        if (entity == null) {
            syncAgentsFromSupabase()
            entity = dao.getAgentByContact(raw)
                ?: dao.getAgentByCode(upper)
                ?: dao.getAgentByContact(upper)
            if (entity == null) {
                val all = dao.getAllAgentsList()
                entity = all.find { ag ->
                    ag.agentCode.equals(upper, ignoreCase = true) ||
                    ag.agentCode.replace("-", "").replace(" ", "").equals(withoutHyphens, ignoreCase = true) ||
                    (digitsOnly.length >= 10 && ag.phone.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10))) ||
                    ag.email.equals(raw, ignoreCase = true) ||
                    ag.id == raw
                }
            }
        }

        if (entity == null && (withoutHyphens == "AGT1001" || digitsOnly.endsWith("9876543210") || raw.contains("agent@dukaan.in", ignoreCase = true))) {
            seedDefaultAgentIfEmpty()
            entity = dao.getAgentByContact("9876543210") ?: dao.getAgentByCode("AGT-1001")
        }

        entity?.toModel()
    }

    suspend fun createAgent(
        name: String,
        phone: String,
        email: String,
        password: String = "123456",
        commissionPercent: Double = 20.0,
        customCode: String? = null
    ): Agent = withContext(Dispatchers.IO) {
        val cleanCode = customCode?.removePrefix("DUKAAN_AGENT:")?.trim()?.uppercase()
        val code = if (!cleanCode.isNullOrBlank()) cleanCode else "AGT-${(1000..9999).random()}"
        val agent = Agent(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            phone = phone.trim(),
            email = email.trim(),
            password = password.ifBlank { "123456" },
            agentCode = code,
            commissionPercent = commissionPercent,
            status = "ACTIVE",
            earnings = 0.0,
            createdAt = System.currentTimeMillis()
        )
        dao.insertAgent(agent.toEntity())
        // Sync agent live to Supabase
        try {
            SupabaseClient.registerAgentInSupabase(agent.toEntity(), agent.password)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register agent in Supabase", e)
        }
        agent
    }

    suspend fun updateAgent(agent: Agent) = withContext(Dispatchers.IO) {
        dao.updateAgent(agent.toEntity())
        try {
            SupabaseClient.registerAgentInSupabase(agent.toEntity(), agent.password)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update agent in Supabase", e)
        }
    }

    suspend fun deleteAgent(agentId: String) = withContext(Dispatchers.IO) {
        dao.deleteAgentById(agentId)
    }

    // --- Commissions (Requirement 25-26) ---
    fun getAllCommissions(): Flow<List<CommissionRecord>> = dao.getAllCommissions().map { list -> list.map { it.toModel() } }

    fun getCommissionsForAgent(agentId: String): Flow<List<CommissionRecord>> = dao.getCommissionsForAgent(agentId).map { list -> list.map { it.toModel() } }

    suspend fun confirmSubscriptionPayment(
        businessId: String,
        amount: Double,
        months: Int = 1
    ) = withContext(Dispatchers.IO) {
        val biz = dao.getBusinessById(businessId)
        if (biz != null) {
            if (biz.agentCode.isNotBlank()) {
                val agent = dao.getAgentByCode(biz.agentCode)
                if (agent != null) {
                    val commAmount = amount * (agent.commissionPercent / 100.0)
                    val comm = CommissionEntity(
                        id = UUID.randomUUID().toString(),
                        agentId = agent.id,
                        businessId = biz.id,
                        shopName = biz.name,
                        subscriptionAmount = amount,
                        commissionPercent = agent.commissionPercent,
                        commissionAmount = commAmount,
                        status = "PENDING",
                        paymentDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()),
                        createdAt = System.currentTimeMillis()
                    )
                    dao.insertCommission(comm)
                }
            }
            dao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = biz.id,
                    action = "PAYMENT_CONFIRMED",
                    performedBy = "Superadmin",
                    details = "Subscription payment of ₹${amount.toInt()} marked as PAID ($months months)",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun markCommissionPaid(commissionId: String) = withContext(Dispatchers.IO) {
        val comms = dao.getAllCommissions().firstOrNull() ?: emptyList()
        val found = comms.find { it.id == commissionId }
        if (found != null) {
            dao.updateCommission(found.copy(status = "PAID"))
            val agent = dao.getAgentById(found.agentId)
            if (agent != null) {
                dao.updateAgent(agent.copy(earnings = agent.earnings + found.commissionAmount))
            }
        }
    }

    // --- Separate Punches System (Requirement 11-12) ---
    fun getPunchesForBusiness(businessId: String): Flow<List<PunchRecord>> = dao.getPunchesForBusiness(businessId).map { list -> list.map { it.toModel() } }

    fun getPunchesForEmployee(employeeId: String): Flow<List<PunchRecord>> = dao.getPunchesForEmployee(employeeId).map { list -> list.map { it.toModel() } }

    suspend fun recordPunch(
        businessId: String,
        employeeId: String,
        punchType: AttendanceType,
        location: String = "",
        photoUri: String = ""
    ): PunchRecord = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val punch = PunchRecord(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            employeeId = employeeId,
            punchType = punchType,
            timestamp = now,
            timeStr = timeFormat.format(Date(now)),
            dateStr = dateFormat.format(Date(now)),
            photoUri = photoUri,
            location = location,
            status = "VALID"
        )
        dao.insertPunch(punch.toEntity())
        punch
    }
}

// --- Extension Mappers ---
fun BusinessEntity.toModel() = Business(
    id = id,
    businessCode = businessCode,
    name = name,
    ownerName = ownerName,
    phone = phone,
    email = email,
    password = password,
    businessType = businessType,
    address = address,
    city = city,
    state = state,
    pincode = pincode,
    latitude = latitude,
    longitude = longitude,
    geofenceRadiusMeters = geofenceRadiusMeters,
    plan = plan,
    monthlyPrice = monthlyPrice,
    employeeLimit = employeeLimit,
    dailyEventLimitPerEmployee = dailyEventLimitPerEmployee,
    status = BusinessStatus.values().firstOrNull { it.name.equals(status, ignoreCase = true) } ?: BusinessStatus.PENDING,
    shiftStart = shiftStart,
    shiftEnd = shiftEnd,
    graceMinutes = graceMinutes,
    overtimeThresholdMinutes = overtimeThresholdMinutes,
    agentCode = agentCode,
    workingDays = workingDays,
    createdAt = createdAt
)

fun Business.toEntity() = BusinessEntity(
    id = id,
    businessCode = businessCode,
    name = name,
    ownerName = ownerName,
    phone = phone,
    email = email,
    password = password,
    businessType = businessType,
    address = address,
    city = city,
    state = state,
    pincode = pincode,
    latitude = latitude,
    longitude = longitude,
    geofenceRadiusMeters = geofenceRadiusMeters,
    plan = plan,
    monthlyPrice = monthlyPrice,
    employeeLimit = employeeLimit,
    dailyEventLimitPerEmployee = dailyEventLimitPerEmployee,
    status = status.name,
    shiftStart = shiftStart,
    shiftEnd = shiftEnd,
    graceMinutes = graceMinutes,
    overtimeThresholdMinutes = overtimeThresholdMinutes,
    agentCode = agentCode,
    workingDays = workingDays,
    createdAt = createdAt
)

fun EmployeeEntity.toModel() = Employee(
    id = id,
    businessId = businessId,
    employeeCode = employeeCode,
    fullName = fullName,
    photoUrl = photoUrl,
    phone = phone,
    email = email,
    password = password,
    address = address,
    designation = designation,
    joiningDate = joiningDate,
    salaryType = SalaryType.values().firstOrNull { it.name.equals(salaryType, ignoreCase = true) } ?: SalaryType.MONTHLY,
    monthlySalary = monthlySalary,
    dailyWage = dailyWage,
    customDailyRate = customDailyRate,
    bankAccount = bankAccount,
    bankIfsc = bankIfsc,
    emergencyContact = emergencyContact,
    status = status
)

fun Employee.toEntity() = EmployeeEntity(
    id = id,
    businessId = businessId,
    employeeCode = employeeCode,
    fullName = fullName,
    photoUrl = photoUrl,
    phone = phone,
    email = email,
    password = password,
    address = address,
    designation = designation,
    joiningDate = joiningDate,
    salaryType = salaryType.name,
    monthlySalary = monthlySalary,
    dailyWage = dailyWage,
    customDailyRate = customDailyRate,
    bankAccount = bankAccount,
    bankIfsc = bankIfsc,
    emergencyContact = emergencyContact,
    status = status
)

fun AttendanceEventEntity.toModel() = AttendanceEvent(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    eventType = AttendanceType.values().firstOrNull { it.name.equals(eventType, ignoreCase = true) } ?: AttendanceType.IN,
    timestamp = timestamp,
    formattedTime = formattedTime,
    dateStr = dateStr,
    latitude = latitude,
    longitude = longitude,
    distanceFromShopMeters = distanceFromShopMeters,
    isGeofenceValid = isGeofenceValid,
    photoUri = photoUri,
    verificationMethod = verificationMethod,
    status = status,
    isOffline = isOffline,
    syncStatus = syncStatus
)

fun LeaveRequestEntity.toModel() = LeaveRequest(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    leaveType = leaveType,
    startDate = startDate,
    endDate = endDate,
    days = days,
    reason = reason,
    status = LeaveStatus.values().firstOrNull { it.name.equals(status, ignoreCase = true) } ?: LeaveStatus.PENDING,
    adminComment = adminComment,
    createdAt = createdAt
)

fun LeaveRequest.toEntity() = LeaveRequestEntity(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    leaveType = leaveType,
    startDate = startDate,
    endDate = endDate,
    days = days,
    reason = reason,
    status = status.name,
    adminComment = adminComment,
    createdAt = createdAt
)

fun AdvanceUdhaarEntity.toModel() = AdvanceUdhaar(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    totalAmount = totalAmount,
    remainingAmount = remainingAmount,
    dailyDeductionAmount = dailyDeductionAmount,
    deductionMethod = deductionMethod,
    status = status,
    reason = reason,
    createdAt = createdAt
)

fun AdvanceUdhaar.toEntity() = AdvanceUdhaarEntity(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    totalAmount = totalAmount,
    remainingAmount = remainingAmount,
    dailyDeductionAmount = dailyDeductionAmount,
    deductionMethod = deductionMethod,
    status = status,
    reason = reason,
    createdAt = createdAt
)

fun ExpenseRecordEntity.toModel() = ExpenseRecord(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    category = category,
    amount = amount,
    description = description,
    date = date,
    receiptUri = receiptUri,
    status = ExpenseStatus.values().firstOrNull { it.name.equals(status, ignoreCase = true) } ?: ExpenseStatus.PENDING,
    createdAt = createdAt
)

fun ExpenseRecord.toEntity() = ExpenseRecordEntity(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    category = category,
    amount = amount,
    description = description,
    date = date,
    receiptUri = receiptUri,
    status = status.name,
    createdAt = createdAt
)

fun EmployeeDocumentEntity.toModel() = EmployeeDocument(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    docType = docType,
    docName = docName,
    driveFileId = driveFileId,
    verificationStatus = verificationStatus,
    expiryDate = expiryDate,
    uploadDate = uploadDate
)

fun EmployeeDocument.toEntity() = EmployeeDocumentEntity(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    docType = docType,
    docName = docName,
    driveFileId = driveFileId,
    verificationStatus = verificationStatus,
    expiryDate = expiryDate,
    uploadDate = uploadDate
)

fun SupportMessageEntity.toModel() = SupportMessage(
    id = id,
    businessId = businessId,
    senderRole = senderRole,
    senderName = senderName,
    message = message,
    timestamp = timestamp,
    isRead = isRead
)

fun SupportMessage.toEntity() = SupportMessageEntity(
    id = id,
    businessId = businessId,
    senderRole = senderRole,
    senderName = senderName,
    message = message,
    timestamp = timestamp,
    isRead = isRead
)

fun AuditLogEntity.toModel() = AuditLog(
    id = id,
    businessId = businessId,
    action = action,
    performedBy = performedBy,
    details = details,
    timestamp = timestamp
)

fun AgentEntity.toModel() = Agent(
    id = id,
    name = name,
    phone = phone,
    email = email,
    password = password,
    agentCode = agentCode,
    commissionPercent = commissionPercent,
    status = status,
    earnings = earnings,
    createdAt = createdAt
)

fun Agent.toEntity() = AgentEntity(
    id = id,
    name = name,
    phone = phone,
    email = email,
    password = password,
    agentCode = agentCode,
    commissionPercent = commissionPercent,
    status = status,
    earnings = earnings,
    createdAt = createdAt
)

fun CommissionEntity.toModel() = CommissionRecord(
    id = id,
    agentId = agentId,
    businessId = businessId,
    shopName = shopName,
    subscriptionAmount = subscriptionAmount,
    commissionPercent = commissionPercent,
    commissionAmount = commissionAmount,
    status = status,
    paymentDate = paymentDate,
    createdAt = createdAt
)

fun CommissionRecord.toEntity() = CommissionEntity(
    id = id,
    agentId = agentId,
    businessId = businessId,
    shopName = shopName,
    subscriptionAmount = subscriptionAmount,
    commissionPercent = commissionPercent,
    commissionAmount = commissionAmount,
    status = status,
    paymentDate = paymentDate,
    createdAt = createdAt
)

fun PunchRecordEntity.toModel() = PunchRecord(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    punchType = AttendanceType.valueOf(punchType),
    timestamp = timestamp,
    timeStr = timeStr,
    dateStr = dateStr,
    photoUri = photoUri,
    location = location,
    status = status
)

fun PunchRecord.toEntity() = PunchRecordEntity(
    id = id,
    businessId = businessId,
    employeeId = employeeId,
    punchType = punchType.name,
    timestamp = timestamp,
    timeStr = timeStr,
    dateStr = dateStr,
    photoUri = photoUri,
    location = location,
    status = status
)
