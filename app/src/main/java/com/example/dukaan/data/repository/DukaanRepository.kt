package com.example.dukaan.data.repository

import android.content.Context
import android.util.Log
import com.example.dukaan.data.local.*
import com.example.dukaan.data.model.*
import com.example.dukaan.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class DukaanRepository(context: Context) {
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
            SupabaseClient.createOrUpdateSupabaseUser(
                email = business.email,
                pass = "Password123!",
                role = UserRole.BUSINESS_ADMIN,
                name = business.ownerName,
                businessId = business.id
            )
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

    suspend fun updateBusinessStatus(
        businessId: String,
        status: BusinessStatus,
        performedBy: String
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getBusinessById(businessId) ?: return@withContext
        val updated = existing.copy(status = status.name)
        dao.updateBusiness(updated)

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
        dao.deleteBusinessById(businessId)
        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                action = "BUSINESS_DELETED",
                performedBy = performedBy,
                details = "Deleted business $businessId",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Employees Flow & Actions ---
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
            // Also create user in Supabase Auth if email is present
            if (employee.email.isNotBlank()) {
                SupabaseClient.createOrUpdateSupabaseUser(
                    email = employee.email,
                    pass = "Password123!",
                    role = UserRole.EMPLOYEE,
                    name = employee.fullName,
                    businessId = employee.businessId,
                    employeeId = employee.id
                )
            }
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

    suspend fun deleteEmployee(employeeId: String, businessId: String) = withContext(Dispatchers.IO) {
        dao.deleteEmployeeById(employeeId)
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
                photoUri = photoUri,
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

            // Try syncing to Supabase if not offline
            if (!isOfflineMode) {
                val json = JSONObject().apply {
                    put("id", eventEntity.id)
                    put("business_id", businessId)
                    put("employee_id", employeeId)
                    put("event_type", eventType.name)
                    put("timestamp", now)
                    put("formatted_time", eventEntity.formattedTime)
                    put("date_str", todayStr)
                    put("latitude", userLat)
                    put("longitude", userLng)
                    put("is_geofence_valid", isInsideGeofence)
                    put("status", status)
                }
                SupabaseClient.syncAttendanceToRemote(json)
            }

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
            val json = JSONObject().apply {
                put("id", item.id)
                put("business_id", item.businessId)
                put("employee_id", item.employeeId)
                put("event_type", item.eventType)
                put("timestamp", item.timestamp)
                put("formatted_time", item.formattedTime)
                put("date_str", item.dateStr)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("is_geofence_valid", item.isGeofenceValid)
                put("status", item.status)
            }
            val res = SupabaseClient.syncAttendanceToRemote(json)
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
        dao.insertLeave(leave.toEntity())
    }

    suspend fun updateLeave(leave: LeaveRequest) = withContext(Dispatchers.IO) {
        dao.updateLeave(leave.toEntity())
    }

    suspend fun deleteLeave(leaveId: String) = withContext(Dispatchers.IO) {
        dao.deleteLeaveById(leaveId)
    }

    suspend fun updateLeaveStatus(leaveId: String, status: LeaveStatus, comment: String) = withContext(Dispatchers.IO) {
        val list = dao.getLeavesForBusiness("").firstOrNull() ?: emptyList()
        val found = list.find { it.id == leaveId }
        if (found != null) {
            dao.updateLeave(found.copy(status = status.name, adminComment = comment))
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
        dao.insertAdvance(advance.toEntity())
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
        dao.updateAdvance(advance.toEntity())
    }

    suspend fun deleteAdvance(advanceId: String) = withContext(Dispatchers.IO) {
        dao.deleteAdvanceById(advanceId)
    }

    suspend fun updateAdvanceStatus(advanceId: String, newStatus: String) = withContext(Dispatchers.IO) {
        val advances = dao.getAdvancesForBusiness("").firstOrNull() ?: emptyList()
        val found = advances.find { it.id == advanceId }
        if (found != null) {
            dao.updateAdvance(found.copy(status = newStatus))
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
        dao.insertExpense(expense.toEntity())
    }

    suspend fun updateExpense(expense: ExpenseRecord) = withContext(Dispatchers.IO) {
        dao.updateExpense(expense.toEntity())
    }

    suspend fun deleteExpense(expenseId: String) = withContext(Dispatchers.IO) {
        dao.deleteExpenseById(expenseId)
    }

    suspend fun updateExpenseStatus(expenseId: String, status: ExpenseStatus) = withContext(Dispatchers.IO) {
        val list = dao.getExpensesForBusiness("").firstOrNull() ?: emptyList()
        val found = list.find { it.id == expenseId }
        if (found != null) {
            dao.updateExpense(found.copy(status = status.name))
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

    suspend fun sendSupportMessage(msg: SupportMessage) = withContext(Dispatchers.IO) {
        dao.insertSupportMessage(msg.toEntity())
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

    suspend fun getAgentById(id: String): Agent? = withContext(Dispatchers.IO) {
        dao.getAgentById(id)?.toModel()
    }

    suspend fun getAgentByCode(code: String): Agent? = withContext(Dispatchers.IO) {
        dao.getAgentByCode(code)?.toModel()
    }

    suspend fun getAgentByContact(contact: String): Agent? = withContext(Dispatchers.IO) {
        dao.getAgentByContact(contact)?.toModel()
    }

    suspend fun createAgent(
        name: String,
        phone: String,
        email: String,
        commissionPercent: Double = 20.0
    ): Agent = withContext(Dispatchers.IO) {
        val code = "AGT-${(1000..9999).random()}"
        val agent = Agent(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            phone = phone.trim(),
            email = email.trim(),
            agentCode = code,
            commissionPercent = commissionPercent,
            status = "ACTIVE",
            earnings = 0.0,
            createdAt = System.currentTimeMillis()
        )
        dao.insertAgent(agent.toEntity())
        agent
    }

    suspend fun updateAgent(agent: Agent) = withContext(Dispatchers.IO) {
        dao.updateAgent(agent.toEntity())
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
    status = BusinessStatus.valueOf(status),
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
    salaryType = SalaryType.valueOf(salaryType),
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
    eventType = AttendanceType.valueOf(eventType),
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
    status = LeaveStatus.valueOf(status),
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
    status = ExpenseStatus.valueOf(status),
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
