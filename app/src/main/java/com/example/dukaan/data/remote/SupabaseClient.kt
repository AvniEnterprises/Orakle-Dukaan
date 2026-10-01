package com.example.dukaan.data.remote

import android.util.Log
import com.example.dukaan.data.local.AdvanceUdhaarEntity
import com.example.dukaan.data.local.AttendanceEventEntity
import com.example.dukaan.data.local.BusinessEntity
import com.example.dukaan.data.local.EmployeeEntity
import com.example.dukaan.data.local.ExpenseRecordEntity
import com.example.dukaan.data.local.LeaveRequestEntity
import com.example.dukaan.data.model.CurrentUser
import com.example.dukaan.data.model.UserRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ShopRemoteData(
    val employees: List<EmployeeEntity>,
    val punches: List<AttendanceEventEntity>,
    val leaves: List<LeaveRequestEntity>,
    val expenses: List<ExpenseRecordEntity>,
    val advances: List<AdvanceUdhaarEntity>
)

fun Throwable.isCancellation(): Boolean {
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

object SupabaseClient {
    private const val TAG = "SupabaseClient"
    const val SUPABASE_URL = "https://uzylcwkxlonqyjlhqpre.supabase.co"
    const val PUBLISHABLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV6eWxjd2t4bG9ucXlqbGhxcHJlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA1OTM0NzEsImV4cCI6MjEwNjE2OTQ3MX0.9uSwgmibSCpzg-BENerdnKQXs1HzNk3OYpaEzMPM09I"
    const val SECRET_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV6eWxjd2t4bG9ucXlqbGhxcHJlIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc5MDU5MzQ3MSwiZXhwIjoyMTA2MTY5NDcxfQ.PXAglLBtwkkJWJXkae8Ycg52GkCraRiFX7mOhJEUMO8"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Authenticates with Supabase using Email and Password.
     * Returns CurrentUser with role and metadata.
     */
    suspend fun signInWithEmail(email: String, pass: String): Result<CurrentUser> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/auth/v1/token?grant_type=password"
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", pass.trim())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val json = JSONObject(resStr)
                val userObj = json.getJSONObject("user")
                val userId = userObj.getString("id")
                val userEmail = userObj.optString("email", email)
                val meta = userObj.optJSONObject("user_metadata") ?: JSONObject()

                val roleStr = meta.optString("role", "")
                val role = when {
                    roleStr.equals("SUPERADMIN", ignoreCase = true) || userEmail.contains("superadmin", ignoreCase = true) -> UserRole.SUPERADMIN
                    roleStr.equals("EMPLOYEE", ignoreCase = true) -> UserRole.EMPLOYEE
                    else -> UserRole.BUSINESS_ADMIN
                }
                val name = meta.optString("name", meta.optString("owner_name", if (role == UserRole.EMPLOYEE) "Staff Member" else "Shop Owner"))
                val bizId = meta.optString("shop_id", meta.optString("business_id", userId))
                val empId = meta.optString("employee_id", if (role == UserRole.EMPLOYEE) userId else null)

                Result.success(
                    CurrentUser(
                        id = userId,
                        role = role,
                        email = userEmail,
                        name = name,
                        businessId = bizId,
                        employeeId = empId
                    )
                )
            } else {
                Log.w(TAG, "Auth response: ${response.code} $resStr")
                val errorMsg = try {
                    JSONObject(resStr).optString("error_description", "Invalid credentials")
                } catch (e: Exception) {
                    "Authentication failed (${response.code})"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmail error", e)
            Result.failure(e)
        }
    }

    /**
     * Registers a new shop in Supabase Auth using the Admin API.
     */
    suspend fun registerShopInSupabase(
        business: BusinessEntity,
        rawPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/auth/v1/admin/users"
            val userMeta = JSONObject().apply {
                put("role", "BUSINESS_ADMIN")
                put("status", business.status)
                put("shop_id", business.id)
                put("business_id", business.id)
                put("shop_name", business.name)
                put("business_code", business.businessCode)
                put("owner_name", business.ownerName)
                put("name", business.ownerName)
                put("phone", business.phone)
                put("email", business.email)
                put("password", rawPassword)
                put("business_type", business.businessType)
                put("address", business.address)
                put("city", business.city)
                put("state", business.state)
                put("pincode", business.pincode)
                put("latitude", business.latitude)
                put("longitude", business.longitude)
                put("geofence_radius", business.geofenceRadiusMeters)
                put("plan", business.plan)
                put("monthly_price", business.monthlyPrice)
                put("employee_limit", business.employeeLimit)
                put("daily_event_limit", business.dailyEventLimitPerEmployee)
                put("shift_start", business.shiftStart)
                put("shift_end", business.shiftEnd)
                put("agent_code", business.agentCode)
                put("working_days", business.workingDays)
                put("employee_count", 0)
                put("created_at", business.createdAt)
            }

            val bodyJson = JSONObject().apply {
                put("email", business.email.trim())
                put("password", rawPassword.trim())
                put("email_confirm", true)
                put("user_metadata", userMeta)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val json = JSONObject(resStr)
                val userId = json.optString("id", business.id)
                Log.i(TAG, "Shop successfully registered in Supabase: $userId")
                Result.success(userId)
            } else {
                Log.w(TAG, "Register shop in Supabase error: ${response.code} $resStr")
                if (resStr.contains("already registered", ignoreCase = true) || response.code == 422) {
                    val updateResult = updateShopMetadataByEmail(business.email, userMeta, rawPassword)
                    return@withContext updateResult
                }
                Result.failure(Exception("Supabase registration returned ${response.code}: $resStr"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerShopInSupabase error", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches all registered businesses/shops from Supabase live.
     */
    suspend fun fetchAllShopsFromSupabase(): Result<List<BusinessEntity>> = withContext(Dispatchers.IO) {
        try {
            val allUsers = fetchAllRawUsersFromSupabase()
            val list = mutableListOf<BusinessEntity>()

            // Count employees per shop
            val empCountMap = mutableMapOf<String, Int>()
            for (u in allUsers) {
                val meta = u.optJSONObject("user_metadata") ?: JSONObject()
                if (meta.optString("role").equals("EMPLOYEE", ignoreCase = true)) {
                    val bId = meta.optString("business_id", meta.optString("shop_id", ""))
                    if (bId.isNotBlank()) {
                        empCountMap[bId] = (empCountMap[bId] ?: 0) + 1
                    }
                }
            }

            for (userObj in allUsers) {
                val userId = userObj.optString("id")
                val email = userObj.optString("email")
                val meta = userObj.optJSONObject("user_metadata") ?: JSONObject()
                val role = meta.optString("role", "")

                if (role.equals("BUSINESS_ADMIN", ignoreCase = true) || meta.has("shop_name")) {
                    val shopId = meta.optString("shop_id", meta.optString("business_id", userId))
                    val code = meta.optString("business_code", "SHP-${shopId.take(6).uppercase()}")
                    val registeredStaffList = meta.optJSONArray("employees")
                    val metaStaffCount = registeredStaffList?.length() ?: meta.optInt("employee_count", 0)
                    val realStaffCount = maxOf(metaStaffCount, empCountMap[shopId] ?: 0, empCountMap[code] ?: 0)

                    val entity = BusinessEntity(
                        id = shopId,
                        businessCode = code,
                        name = meta.optString("shop_name", meta.optString("name", "Shop")),
                        ownerName = meta.optString("owner_name", meta.optString("name", "Owner")),
                        phone = meta.optString("phone", ""),
                        email = email,
                        password = meta.optString("password", "123456"),
                        businessType = meta.optString("business_type", "Retail"),
                        address = meta.optString("address", "Main Market"),
                        city = meta.optString("city", "Jaipur"),
                        state = meta.optString("state", "Rajasthan"),
                        pincode = meta.optString("pincode", "302001"),
                        latitude = meta.optDouble("latitude", 26.9124),
                        longitude = meta.optDouble("longitude", 75.7873),
                        geofenceRadiusMeters = meta.optInt("geofence_radius", 100),
                        plan = meta.optString("plan", "BASIC"),
                        monthlyPrice = meta.optDouble("monthly_price", 149.0),
                        employeeLimit = meta.optInt("employee_limit", 5),
                        dailyEventLimitPerEmployee = meta.optInt("daily_event_limit", 4),
                        status = meta.optString("status", "PENDING"),
                        shiftStart = meta.optString("shift_start", "09:00 AM"),
                        shiftEnd = meta.optString("shift_end", "07:00 PM"),
                        graceMinutes = meta.optInt("grace_minutes", 15),
                        overtimeThresholdMinutes = meta.optInt("overtime_threshold_minutes", 30),
                        agentCode = meta.optString("agent_code", ""),
                        workingDays = meta.optString("working_days", "MON,TUE,WED,THU,FRI,SAT"),
                        createdAt = meta.optLong("created_at", System.currentTimeMillis())
                    )
                    list.add(entity)
                }
            }
            Log.i(TAG, "Fetched ${list.size} shops live from Supabase")
            Result.success(list)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "fetchAllShopsFromSupabase exception", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the status of a shop in Supabase (e.g. APPROVED, SUSPENDED, REJECTED).
     * If SUSPENDED or REJECTED, cascade-suspends all employee accounts belonging to this shop.
     */
    suspend fun updateShopStatusInSupabase(shopId: String, newStatus: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val user = findSupabaseUserByShopId(shopId)
            var actualShopId = shopId
            if (user != null) {
                val userId = user.getString("id")
                val currentMeta = user.optJSONObject("user_metadata") ?: JSONObject()
                currentMeta.put("status", newStatus)
                actualShopId = currentMeta.optString("shop_id", shopId)

                val url = "$SUPABASE_URL/auth/v1/admin/users/$userId"
                val bodyJson = JSONObject().apply {
                    put("user_metadata", currentMeta)
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(bodyJson.toString().toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().close()
            }

            // Cascade update status to all employee users belonging to this shop
            val allUsers = fetchAllRawUsersFromSupabase()
            for (u in allUsers) {
                val m = u.optJSONObject("user_metadata") ?: JSONObject()
                val bId = m.optString("business_id", m.optString("shop_id", ""))
                if (m.optString("role").equals("EMPLOYEE", ignoreCase = true) &&
                    (bId.equals(shopId, ignoreCase = true) || bId.equals(actualShopId, ignoreCase = true))) {
                    val empUserId = u.getString("id")
                    m.put("status", if (newStatus.equals("APPROVED", ignoreCase = true) || newStatus.equals("ACTIVE", ignoreCase = true)) "ACTIVE" else "SUSPENDED")
                    val putReq = Request.Builder()
                        .url("$SUPABASE_URL/auth/v1/admin/users/$empUserId")
                        .addHeader("apikey", SECRET_KEY)
                        .addHeader("Authorization", "Bearer $SECRET_KEY")
                        .addHeader("Content-Type", "application/json")
                        .put(JSONObject().apply { put("user_metadata", m) }.toString().toRequestBody(jsonMediaType))
                        .build()
                    httpClient.newCall(putReq).execute().close()
                }
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "updateShopStatusInSupabase error", e)
            Result.failure(e)
        }
    }

    /**
     * Updates business plan & overrides in Supabase.
     */
    suspend fun updateShopPlanInSupabase(
        shopId: String,
        plan: String,
        price: Double,
        empLimit: Int,
        eventLimit: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val user = findSupabaseUserByShopId(shopId) ?: return@withContext Result.failure(Exception("Shop not found in Supabase"))
            val userId = user.getString("id")
            val currentMeta = user.optJSONObject("user_metadata") ?: JSONObject()
            currentMeta.put("plan", plan)
            currentMeta.put("monthly_price", price)
            currentMeta.put("employee_limit", empLimit)
            currentMeta.put("daily_event_limit", eventLimit)

            val url = "$SUPABASE_URL/auth/v1/admin/users/$userId"
            val bodyJson = JSONObject().apply {
                put("user_metadata", currentMeta)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .put(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val ok = response.isSuccessful
            response.close()
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates complete Shop Details (name, owner, phone, geofence, hours) in Supabase.
     */
    suspend fun updateShopInSupabase(business: BusinessEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val user = findSupabaseUserByShopId(business.id) ?: return@withContext Result.failure(Exception("Shop not found"))
            val userId = user.getString("id")
            val meta = user.optJSONObject("user_metadata") ?: JSONObject()
            meta.put("shop_name", business.name)
            meta.put("owner_name", business.ownerName)
            meta.put("name", business.ownerName)
            meta.put("phone", business.phone)
            meta.put("business_type", business.businessType)
            meta.put("address", business.address)
            meta.put("city", business.city)
            meta.put("state", business.state)
            meta.put("pincode", business.pincode)
            meta.put("latitude", business.latitude)
            meta.put("longitude", business.longitude)
            meta.put("geofence_radius", business.geofenceRadiusMeters)
            meta.put("shift_start", business.shiftStart)
            meta.put("shift_end", business.shiftEnd)
            meta.put("working_days", business.workingDays)
            meta.put("plan", business.plan)
            meta.put("monthly_price", business.monthlyPrice)
            meta.put("employee_limit", business.employeeLimit)
            meta.put("daily_event_limit", business.dailyEventLimitPerEmployee)
            meta.put("status", business.status)

            val url = "$SUPABASE_URL/auth/v1/admin/users/$userId"
            val body = JSONObject().apply {
                put("user_metadata", meta)
            }
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .put(body.toString().toRequestBody(jsonMediaType))
                .build()

            val res = httpClient.newCall(req).execute()
            val ok = res.isSuccessful
            res.close()
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * CASCADE DELETE: Deletes the shop user AND all employee accounts belonging to this shop from Supabase.
     */
    suspend fun deleteShopAndEmployeesFromSupabase(shopId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            var shopUserId: String? = null
            var actualShopId = shopId
            var shopCode = ""

            val shopUser = findSupabaseUserByShopId(shopId)
            if (shopUser != null) {
                shopUserId = shopUser.optString("id")
                val m = shopUser.optJSONObject("user_metadata") ?: JSONObject()
                actualShopId = m.optString("shop_id", m.optString("business_id", shopId))
                shopCode = m.optString("business_code", "")
            }

            // 1. Delete all Employees belonging to this shop
            val allUsers = fetchAllRawUsersFromSupabase()
            for (u in allUsers) {
                val m = u.optJSONObject("user_metadata") ?: JSONObject()
                val role = m.optString("role", "")
                val bId = m.optString("business_id", "")
                val sId = m.optString("shop_id", "")
                val bCode = m.optString("business_code", "")

                val isMatch = role.equals("EMPLOYEE", ignoreCase = true) && (
                    bId.equals(shopId, ignoreCase = true) ||
                    sId.equals(shopId, ignoreCase = true) ||
                    bId.equals(actualShopId, ignoreCase = true) ||
                    sId.equals(actualShopId, ignoreCase = true) ||
                    (shopUserId != null && (bId.equals(shopUserId, ignoreCase = true) || sId.equals(shopUserId, ignoreCase = true))) ||
                    (shopCode.isNotEmpty() && bCode.equals(shopCode, ignoreCase = true))
                )

                if (isMatch) {
                    val empUserId = u.getString("id")
                    val delReq = Request.Builder()
                        .url("$SUPABASE_URL/auth/v1/admin/users/$empUserId")
                        .addHeader("apikey", SECRET_KEY)
                        .addHeader("Authorization", "Bearer $SECRET_KEY")
                        .delete()
                        .build()
                    try {
                        httpClient.newCall(delReq).execute().close()
                        Log.i(TAG, "Cascade deleted employee user from Supabase: $empUserId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete employee user: $empUserId", e)
                    }
                }
            }

            // 2. Delete the Shop Owner user
            if (shopUserId != null) {
                val req = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$shopUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
                Log.i(TAG, "Deleted shop owner user from Supabase: $shopUserId")
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "deleteShopAndEmployeesFromSupabase error", e)
            Result.failure(e)
        }
    }

    /**
     * Registers an employee in Supabase Auth and updates the Shop's employee roster.
     */
    suspend fun registerEmployeeInSupabase(
        employee: EmployeeEntity,
        rawPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userEmail = if (employee.email.isNotBlank()) {
                employee.email.trim()
            } else {
                "${employee.phone.trim()}@employee.dukaan"
            }

            val meta = JSONObject().apply {
                put("role", "EMPLOYEE")
                put("employee_id", employee.id)
                put("business_id", employee.businessId)
                put("shop_id", employee.businessId)
                put("employee_code", employee.employeeCode)
                put("full_name", employee.fullName)
                put("name", employee.fullName)
                put("phone", employee.phone)
                put("email", userEmail)
                put("password", rawPassword)
                put("address", employee.address)
                put("designation", employee.designation)
                put("joining_date", employee.joiningDate)
                put("salary_type", employee.salaryType)
                put("monthly_salary", employee.monthlySalary)
                put("daily_wage", employee.dailyWage)
                put("custom_daily_rate", employee.customDailyRate)
                put("bank_account", employee.bankAccount)
                put("bank_ifsc", employee.bankIfsc)
                put("emergency_contact", employee.emergencyContact)
                put("status", employee.status)
            }

            val bodyJson = JSONObject().apply {
                put("email", userEmail)
                put("password", rawPassword.ifBlank { "123456" }.trim())
                put("email_confirm", true)
                put("user_metadata", meta)
            }

            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/admin/users")
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string().orEmpty()
            var createdUserId = employee.id

            if (response.isSuccessful) {
                val json = JSONObject(resStr)
                createdUserId = json.optString("id", employee.id)
                Log.i(TAG, "Employee user created in Supabase Auth: $createdUserId")
            } else {
                if (resStr.contains("already registered", ignoreCase = true) || response.code == 422) {
                    val updateRes = updateEmployeeMetadataByContact(employee.phone, userEmail, meta, rawPassword)
                    createdUserId = updateRes.getOrDefault(employee.id)
                } else {
                    Log.w(TAG, "Employee creation failed: ${response.code} $resStr")
                }
            }

            // Sync employee into the Shop's metadata roster
            addOrUpdateEmployeeInShopMetadata(employee.businessId, employee)

            Result.success(createdUserId)
        } catch (e: Exception) {
            Log.e(TAG, "registerEmployeeInSupabase error", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a single employee user from Supabase Auth and removes them from shop roster.
     */
    suspend fun deleteEmployeeFromSupabase(employeeId: String, businessId: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val user = findSupabaseUserByEmployeeId(employeeId)
            if (user != null) {
                val userId = user.getString("id")
                val req = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$userId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
            }

            // Remove from shop metadata if businessId provided or detected
            val bId = businessId ?: user?.optJSONObject("user_metadata")?.optString("business_id", "")
            if (!bId.isNullOrBlank()) {
                removeEmployeeFromShopMetadata(bId, employeeId)
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Records an attendance punch into Supabase on both the employee and shop user records.
     */
    suspend fun recordPunchInSupabase(
        businessId: String,
        employeeId: String,
        punch: AttendanceEventEntity
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val punchObj = JSONObject().apply {
                put("id", punch.id)
                put("business_id", businessId)
                put("employee_id", employeeId)
                put("event_type", punch.eventType)
                put("timestamp", punch.timestamp)
                put("formatted_time", punch.formattedTime)
                put("date_str", punch.dateStr)
                put("latitude", punch.latitude)
                put("longitude", punch.longitude)
                put("distance", punch.distanceFromShopMeters)
                put("is_geofence_valid", punch.isGeofenceValid)
                put("photo_uri", punch.photoUri)
                put("verification_method", punch.verificationMethod)
                put("status", punch.status)
            }

            // 1. Record on Employee user record
            val empUser = findSupabaseUserByEmployeeId(employeeId)
            if (empUser != null) {
                val empUserId = empUser.getString("id")
                val empMeta = empUser.optJSONObject("user_metadata") ?: JSONObject()
                val punches = empMeta.optJSONArray("punches") ?: JSONArray()
                // Upsert punch by ID
                var found = false
                for (i in 0 until punches.length()) {
                    if (punches.getJSONObject(i).optString("id") == punch.id) {
                        punches.put(i, punchObj)
                        found = true
                        break
                    }
                }
                if (!found) {
                    punches.put(punchObj)
                }
                empMeta.put("punches", punches)

                val putReq = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$empUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(JSONObject().apply { put("user_metadata", empMeta) }.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(putReq).execute().close()
            }

            // 2. Dual-record on Shop Owner user record
            val shopUser = findSupabaseUserByShopId(businessId)
            if (shopUser != null) {
                val shopUserId = shopUser.getString("id")
                val shopMeta = shopUser.optJSONObject("user_metadata") ?: JSONObject()
                val shopPunches = shopMeta.optJSONArray("punches") ?: JSONArray()
                var found = false
                for (i in 0 until shopPunches.length()) {
                    if (shopPunches.getJSONObject(i).optString("id") == punch.id) {
                        shopPunches.put(i, punchObj)
                        found = true
                        break
                    }
                }
                if (!found) {
                    shopPunches.put(punchObj)
                }
                shopMeta.put("punches", shopPunches)

                val putReq = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$shopUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(JSONObject().apply { put("user_metadata", shopMeta) }.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(putReq).execute().close()
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "recordPunchInSupabase error", e)
            Result.failure(e)
        }
    }

    /**
     * Records a Leave Request in Supabase (Employee + Shop).
     */
    suspend fun recordLeaveInSupabase(leave: LeaveRequestEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val lObj = JSONObject().apply {
                put("id", leave.id)
                put("business_id", leave.businessId)
                put("employee_id", leave.employeeId)
                put("leave_type", leave.leaveType)
                put("start_date", leave.startDate)
                put("end_date", leave.endDate)
                put("days", leave.days)
                put("reason", leave.reason)
                put("status", leave.status)
                put("admin_comment", leave.adminComment)
                put("created_at", leave.createdAt)
            }

            // Upsert on Employee
            val empUser = findSupabaseUserByEmployeeId(leave.employeeId)
            if (empUser != null) {
                val empUserId = empUser.getString("id")
                val meta = empUser.optJSONObject("user_metadata") ?: JSONObject()
                val leaves = meta.optJSONArray("leaves") ?: JSONArray()
                var found = false
                for (i in 0 until leaves.length()) {
                    if (leaves.getJSONObject(i).optString("id") == leave.id) {
                        leaves.put(i, lObj)
                        found = true
                        break
                    }
                }
                if (!found) leaves.put(lObj)
                meta.put("leaves", leaves)
                val req = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$empUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(JSONObject().apply { put("user_metadata", meta) }.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().close()
            }

            // Upsert on Shop
            val shopUser = findSupabaseUserByShopId(leave.businessId)
            if (shopUser != null) {
                val shopUserId = shopUser.getString("id")
                val meta = shopUser.optJSONObject("user_metadata") ?: JSONObject()
                val leaves = meta.optJSONArray("leaves") ?: JSONArray()
                var found = false
                for (i in 0 until leaves.length()) {
                    if (leaves.getJSONObject(i).optString("id") == leave.id) {
                        leaves.put(i, lObj)
                        found = true
                        break
                    }
                }
                if (!found) leaves.put(lObj)
                meta.put("leaves", leaves)
                val req = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$shopUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(JSONObject().apply { put("user_metadata", meta) }.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().close()
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Records an Expense claim in Supabase (Employee + Shop).
     */
    suspend fun recordExpenseInSupabase(expense: ExpenseRecordEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val eObj = JSONObject().apply {
                put("id", expense.id)
                put("business_id", expense.businessId)
                put("employee_id", expense.employeeId)
                put("category", expense.category)
                put("amount", expense.amount)
                put("description", expense.description)
                put("receipt_uri", expense.receiptUri)
                put("date", expense.date)
                put("status", expense.status)
                put("created_at", expense.createdAt)
            }

            // Employee
            val empUser = findSupabaseUserByEmployeeId(expense.employeeId)
            if (empUser != null) {
                val empUserId = empUser.getString("id")
                val meta = empUser.optJSONObject("user_metadata") ?: JSONObject()
                val list = meta.optJSONArray("expenses") ?: JSONArray()
                var found = false
                for (i in 0 until list.length()) {
                    if (list.getJSONObject(i).optString("id") == expense.id) {
                        list.put(i, eObj); found = true; break
                    }
                }
                if (!found) list.put(eObj)
                meta.put("expenses", list)
                val req = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$empUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(JSONObject().apply { put("user_metadata", meta) }.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().close()
            }

            // Shop
            val shopUser = findSupabaseUserByShopId(expense.businessId)
            if (shopUser != null) {
                val shopUserId = shopUser.getString("id")
                val meta = shopUser.optJSONObject("user_metadata") ?: JSONObject()
                val list = meta.optJSONArray("expenses") ?: JSONArray()
                var found = false
                for (i in 0 until list.length()) {
                    if (list.getJSONObject(i).optString("id") == expense.id) {
                        list.put(i, eObj); found = true; break
                    }
                }
                if (!found) list.put(eObj)
                meta.put("expenses", list)
                val req = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$shopUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(JSONObject().apply { put("user_metadata", meta) }.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().close()
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Records an Advance / Udhaar in Supabase (Employee + Shop).
     */
    suspend fun recordAdvanceInSupabase(advance: AdvanceUdhaarEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val aObj = JSONObject().apply {
                put("id", advance.id)
                put("business_id", advance.businessId)
                put("employee_id", advance.employeeId)
                put("total_amount", advance.totalAmount)
                put("daily_deduction_amount", advance.dailyDeductionAmount)
                put("remaining_amount", advance.remainingAmount)
                put("deduction_method", advance.deductionMethod)
                put("status", advance.status)
                put("reason", advance.reason)
                put("created_at", advance.createdAt)
            }

            // Employee
            val empUser = findSupabaseUserByEmployeeId(advance.employeeId)
            if (empUser != null) {
                val empUserId = empUser.getString("id")
                val meta = empUser.optJSONObject("user_metadata") ?: JSONObject()
                val list = meta.optJSONArray("advances") ?: JSONArray()
                var found = false
                for (i in 0 until list.length()) {
                    if (list.getJSONObject(i).optString("id") == advance.id) {
                        list.put(i, aObj); found = true; break
                    }
                }
                if (!found) list.put(aObj)
                meta.put("advances", list)
                val req = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$empUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(JSONObject().apply { put("user_metadata", meta) }.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().close()
            }

            // Shop
            val shopUser = findSupabaseUserByShopId(advance.businessId)
            if (shopUser != null) {
                val shopUserId = shopUser.getString("id")
                val meta = shopUser.optJSONObject("user_metadata") ?: JSONObject()
                val list = meta.optJSONArray("advances") ?: JSONArray()
                var found = false
                for (i in 0 until list.length()) {
                    if (list.getJSONObject(i).optString("id") == advance.id) {
                        list.put(i, aObj); found = true; break
                    }
                }
                if (!found) list.put(aObj)
                meta.put("advances", list)
                val req = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/admin/users/$shopUserId")
                    .addHeader("apikey", SECRET_KEY)
                    .addHeader("Authorization", "Bearer $SECRET_KEY")
                    .addHeader("Content-Type", "application/json")
                    .put(JSONObject().apply { put("user_metadata", meta) }.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().close()
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all employees live from Supabase.
     */
    suspend fun fetchAllEmployeesFromSupabase(businessId: String? = null): Result<List<EmployeeEntity>> = withContext(Dispatchers.IO) {
        try {
            val allUsers = fetchAllRawUsersFromSupabase()
            val list = mutableListOf<EmployeeEntity>()
            val seenEmpIds = mutableSetOf<String>()

            // 1. Scan direct employee users
            for (u in allUsers) {
                val meta = u.optJSONObject("user_metadata") ?: JSONObject()
                val role = meta.optString("role", "")
                val bId = meta.optString("business_id", meta.optString("shop_id", ""))
                val matchesBiz = businessId == null || bId.equals(businessId, ignoreCase = true)

                if (role.equals("EMPLOYEE", ignoreCase = true) && matchesBiz) {
                    val empId = meta.optString("employee_id", u.optString("id"))
                    if (!seenEmpIds.contains(empId)) {
                        seenEmpIds.add(empId)
                        list.add(
                            EmployeeEntity(
                                id = empId,
                                businessId = if (businessId != null && bId.isBlank()) businessId else bId,
                                employeeCode = meta.optString("employee_code", "EMP-${empId.take(4).uppercase()}"),
                                fullName = meta.optString("full_name", meta.optString("name", "Staff Member")),
                                photoUrl = meta.optString("photo_url", ""),
                                phone = meta.optString("phone", ""),
                                email = u.optString("email", ""),
                                password = meta.optString("password", "123456"),
                                address = meta.optString("address", "Staff Quarters"),
                                designation = meta.optString("designation", "Staff"),
                                joiningDate = meta.optString("joining_date", "2026-01-01"),
                                salaryType = meta.optString("salary_type", "MONTHLY"),
                                monthlySalary = meta.optDouble("monthly_salary", 15000.0),
                                dailyWage = meta.optDouble("daily_wage", 600.0),
                                customDailyRate = meta.optDouble("custom_daily_rate", 600.0),
                                bankAccount = meta.optString("bank_account", ""),
                                bankIfsc = meta.optString("bank_ifsc", ""),
                                emergencyContact = meta.optString("emergency_contact", ""),
                                status = meta.optString("status", "ACTIVE")
                            )
                        )
                    }
                }
            }

            // 2. Also check shop metadata "employees" array
            for (u in allUsers) {
                val meta = u.optJSONObject("user_metadata") ?: JSONObject()
                val shopId = meta.optString("shop_id", meta.optString("business_id", u.optString("id")))
                val matchesBiz = businessId == null || shopId.equals(businessId, ignoreCase = true)

                if (matchesBiz) {
                    val staffArray = meta.optJSONArray("employees") ?: JSONArray()
                    for (i in 0 until staffArray.length()) {
                        val e = staffArray.getJSONObject(i)
                        val empId = e.optString("id", e.optString("employee_id", ""))
                        if (empId.isNotBlank() && !seenEmpIds.contains(empId)) {
                            seenEmpIds.add(empId)
                            list.add(
                                EmployeeEntity(
                                    id = empId,
                                    businessId = shopId,
                                    employeeCode = e.optString("employee_code", "EMP-001"),
                                    fullName = e.optString("full_name", e.optString("name", "Staff")),
                                    photoUrl = e.optString("photo_url", ""),
                                    phone = e.optString("phone", ""),
                                    email = e.optString("email", ""),
                                    password = e.optString("password", "123456"),
                                    address = e.optString("address", ""),
                                    designation = e.optString("designation", "Staff"),
                                    joiningDate = e.optString("joining_date", "2026-01-01"),
                                    salaryType = e.optString("salary_type", "MONTHLY"),
                                    monthlySalary = e.optDouble("monthly_salary", 15000.0),
                                    dailyWage = e.optDouble("daily_wage", 600.0),
                                    customDailyRate = e.optDouble("custom_daily_rate", 600.0),
                                    bankAccount = e.optString("bank_account", ""),
                                    bankIfsc = e.optString("bank_ifsc", ""),
                                    emergencyContact = e.optString("emergency_contact", ""),
                                    status = e.optString("status", "ACTIVE")
                                )
                            )
                        }
                    }
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "fetchAllEmployeesFromSupabase error", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches complete data bundle (Employees, Punches, Leaves, Expenses, Advances) live for a business from Supabase.
     */
    suspend fun fetchFullShopDataFromSupabase(businessId: String): Result<ShopRemoteData> = withContext(Dispatchers.IO) {
        try {
            val allUsers = fetchAllRawUsersFromSupabase()
            val emps = mutableListOf<EmployeeEntity>()
            val punches = mutableListOf<AttendanceEventEntity>()
            val leaves = mutableListOf<LeaveRequestEntity>()
            val expenses = mutableListOf<ExpenseRecordEntity>()
            val advances = mutableListOf<AdvanceUdhaarEntity>()

            val seenEmpIds = mutableSetOf<String>()
            val seenPunchIds = mutableSetOf<String>()
            val seenLeaveIds = mutableSetOf<String>()
            val seenExpenseIds = mutableSetOf<String>()
            val seenAdvanceIds = mutableSetOf<String>()

            // 1. Collect from Employee Users
            for (u in allUsers) {
                val meta = u.optJSONObject("user_metadata") ?: JSONObject()
                val role = meta.optString("role", "")
                val bId = meta.optString("business_id", meta.optString("shop_id", ""))

                if (role.equals("EMPLOYEE", ignoreCase = true) && bId.equals(businessId, ignoreCase = true)) {
                    val empId = meta.optString("employee_id", u.optString("id"))
                    if (!seenEmpIds.contains(empId)) {
                        seenEmpIds.add(empId)
                        emps.add(
                            EmployeeEntity(
                                id = empId,
                                businessId = businessId,
                                employeeCode = meta.optString("employee_code", "EMP-001"),
                                fullName = meta.optString("full_name", meta.optString("name", "Staff Member")),
                                photoUrl = meta.optString("photo_url", ""),
                                phone = meta.optString("phone", ""),
                                email = u.optString("email", ""),
                                password = meta.optString("password", "123456"),
                                address = meta.optString("address", ""),
                                designation = meta.optString("designation", "Staff"),
                                joiningDate = meta.optString("joining_date", "2026-01-01"),
                                salaryType = meta.optString("salary_type", "MONTHLY"),
                                monthlySalary = meta.optDouble("monthly_salary", 15000.0),
                                dailyWage = meta.optDouble("daily_wage", 600.0),
                                customDailyRate = meta.optDouble("custom_daily_rate", 600.0),
                                bankAccount = meta.optString("bank_account", ""),
                                bankIfsc = meta.optString("bank_ifsc", ""),
                                emergencyContact = meta.optString("emergency_contact", ""),
                                status = meta.optString("status", "ACTIVE")
                            )
                        )
                    }

                    // Punches
                    val pArray = meta.optJSONArray("punches") ?: JSONArray()
                    for (i in 0 until pArray.length()) {
                        val p = pArray.getJSONObject(i)
                        val pId = p.optString("id", UUID.randomUUID().toString())
                        if (!seenPunchIds.contains(pId)) {
                            seenPunchIds.add(pId)
                            punches.add(
                                AttendanceEventEntity(
                                    id = pId,
                                    businessId = businessId,
                                    employeeId = empId,
                                    eventType = p.optString("event_type", "IN"),
                                    timestamp = p.optLong("timestamp", System.currentTimeMillis()),
                                    formattedTime = p.optString("formatted_time", "09:00 AM"),
                                    dateStr = p.optString("date_str", "2026-09-30"),
                                    latitude = p.optDouble("latitude", 26.9124),
                                    longitude = p.optDouble("longitude", 75.7873),
                                    distanceFromShopMeters = p.optDouble("distance", 10.0),
                                    isGeofenceValid = p.optBoolean("is_geofence_valid", true),
                                    photoUri = p.optString("photo_uri", ""),
                                    verificationMethod = p.optString("verification_method", "LIVE_GPS_PHOTO"),
                                    status = p.optString("status", "VERIFIED"),
                                    isOffline = false,
                                    syncStatus = "SYNCED"
                                )
                            )
                        }
                    }

                    // Leaves
                    val lArray = meta.optJSONArray("leaves") ?: JSONArray()
                    for (i in 0 until lArray.length()) {
                        val l = lArray.getJSONObject(i)
                        val lId = l.optString("id", UUID.randomUUID().toString())
                        if (!seenLeaveIds.contains(lId)) {
                            seenLeaveIds.add(lId)
                            leaves.add(
                                LeaveRequestEntity(
                                    id = lId,
                                    businessId = businessId,
                                    employeeId = empId,
                                    leaveType = l.optString("leave_type", "CASUAL"),
                                    startDate = l.optString("start_date", "2026-09-30"),
                                    endDate = l.optString("end_date", "2026-09-30"),
                                    days = l.optInt("days", 1),
                                    reason = l.optString("reason", "Personal"),
                                    status = l.optString("status", "PENDING"),
                                    adminComment = l.optString("admin_comment", ""),
                                    createdAt = l.optLong("created_at", System.currentTimeMillis())
                                )
                            )
                        }
                    }

                    // Expenses
                    val eArray = meta.optJSONArray("expenses") ?: JSONArray()
                    for (i in 0 until eArray.length()) {
                        val e = eArray.getJSONObject(i)
                        val eId = e.optString("id", UUID.randomUUID().toString())
                        if (!seenExpenseIds.contains(eId)) {
                            seenExpenseIds.add(eId)
                            expenses.add(
                                ExpenseRecordEntity(
                                    id = eId,
                                    businessId = businessId,
                                    employeeId = empId,
                                    category = e.optString("category", "General"),
                                    amount = e.optDouble("amount", 0.0),
                                    description = e.optString("description", ""),
                                    date = e.optString("date", "2026-09-30"),
                                    receiptUri = e.optString("receipt_uri", ""),
                                    status = e.optString("status", "PENDING"),
                                    createdAt = e.optLong("created_at", System.currentTimeMillis())
                                )
                            )
                        }
                    }

                    // Advances
                    val aArray = meta.optJSONArray("advances") ?: JSONArray()
                    for (i in 0 until aArray.length()) {
                        val a = aArray.getJSONObject(i)
                        val aId = a.optString("id", UUID.randomUUID().toString())
                        if (!seenAdvanceIds.contains(aId)) {
                            seenAdvanceIds.add(aId)
                            advances.add(
                                AdvanceUdhaarEntity(
                                    id = aId,
                                    businessId = businessId,
                                    employeeId = empId,
                                    totalAmount = a.optDouble("total_amount", 0.0),
                                    remainingAmount = a.optDouble("remaining_amount", 0.0),
                                    dailyDeductionAmount = a.optDouble("daily_deduction_amount", 0.0),
                                    deductionMethod = a.optString("deduction_method", "DAILY_SALARY_CUT"),
                                    status = a.optString("status", "ACTIVE"),
                                    reason = a.optString("reason", "Advance"),
                                    createdAt = a.optLong("created_at", System.currentTimeMillis())
                                )
                            )
                        }
                    }
                }
            }

            // 2. Also collect from Shop Owner user record (for items synced directly into shop)
            val shopUser = findSupabaseUserByShopId(businessId)
            if (shopUser != null) {
                val shopMeta = shopUser.optJSONObject("user_metadata") ?: JSONObject()

                // Roster
                val staffArray = shopMeta.optJSONArray("employees") ?: JSONArray()
                for (i in 0 until staffArray.length()) {
                    val e = staffArray.getJSONObject(i)
                    val empId = e.optString("id", e.optString("employee_id", ""))
                    if (empId.isNotBlank() && !seenEmpIds.contains(empId)) {
                        seenEmpIds.add(empId)
                        emps.add(
                            EmployeeEntity(
                                id = empId,
                                businessId = businessId,
                                employeeCode = e.optString("employee_code", "EMP-001"),
                                fullName = e.optString("full_name", e.optString("name", "Staff")),
                                photoUrl = e.optString("photo_url", ""),
                                phone = e.optString("phone", ""),
                                email = e.optString("email", ""),
                                password = e.optString("password", "123456"),
                                address = e.optString("address", ""),
                                designation = e.optString("designation", "Staff"),
                                joiningDate = e.optString("joining_date", "2026-01-01"),
                                salaryType = e.optString("salary_type", "MONTHLY"),
                                monthlySalary = e.optDouble("monthly_salary", 15000.0),
                                dailyWage = e.optDouble("daily_wage", 600.0),
                                customDailyRate = e.optDouble("custom_daily_rate", 600.0),
                                bankAccount = e.optString("bank_account", ""),
                                bankIfsc = e.optString("bank_ifsc", ""),
                                emergencyContact = e.optString("emergency_contact", ""),
                                status = e.optString("status", "ACTIVE")
                            )
                        )
                    }
                }

                // Punches
                val spArray = shopMeta.optJSONArray("punches") ?: JSONArray()
                for (i in 0 until spArray.length()) {
                    val p = spArray.getJSONObject(i)
                    val pId = p.optString("id", UUID.randomUUID().toString())
                    if (!seenPunchIds.contains(pId)) {
                        seenPunchIds.add(pId)
                        punches.add(
                            AttendanceEventEntity(
                                id = pId,
                                businessId = businessId,
                                employeeId = p.optString("employee_id", ""),
                                eventType = p.optString("event_type", "IN"),
                                timestamp = p.optLong("timestamp", System.currentTimeMillis()),
                                formattedTime = p.optString("formatted_time", "09:00 AM"),
                                dateStr = p.optString("date_str", "2026-09-30"),
                                latitude = p.optDouble("latitude", 26.9124),
                                longitude = p.optDouble("longitude", 75.7873),
                                distanceFromShopMeters = p.optDouble("distance", 10.0),
                                isGeofenceValid = p.optBoolean("is_geofence_valid", true),
                                photoUri = p.optString("photo_uri", ""),
                                verificationMethod = p.optString("verification_method", "LIVE_GPS_PHOTO"),
                                status = p.optString("status", "VERIFIED"),
                                isOffline = false,
                                syncStatus = "SYNCED"
                            )
                        )
                    }
                }

                // Leaves
                val slArray = shopMeta.optJSONArray("leaves") ?: JSONArray()
                for (i in 0 until slArray.length()) {
                    val l = slArray.getJSONObject(i)
                    val lId = l.optString("id", UUID.randomUUID().toString())
                    if (!seenLeaveIds.contains(lId)) {
                        seenLeaveIds.add(lId)
                        leaves.add(
                            LeaveRequestEntity(
                                id = lId,
                                businessId = businessId,
                                employeeId = l.optString("employee_id", ""),
                                leaveType = l.optString("leave_type", "CASUAL"),
                                startDate = l.optString("start_date", "2026-09-30"),
                                endDate = l.optString("end_date", "2026-09-30"),
                                days = l.optInt("days", 1),
                                reason = l.optString("reason", "Personal"),
                                status = l.optString("status", "PENDING"),
                                adminComment = l.optString("admin_comment", ""),
                                createdAt = l.optLong("created_at", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Expenses
                val seArray = shopMeta.optJSONArray("expenses") ?: JSONArray()
                for (i in 0 until seArray.length()) {
                    val e = seArray.getJSONObject(i)
                    val eId = e.optString("id", UUID.randomUUID().toString())
                    if (!seenExpenseIds.contains(eId)) {
                        seenExpenseIds.add(eId)
                        expenses.add(
                            ExpenseRecordEntity(
                                id = eId,
                                businessId = businessId,
                                employeeId = e.optString("employee_id", ""),
                                category = e.optString("category", "General"),
                                amount = e.optDouble("amount", 0.0),
                                description = e.optString("description", ""),
                                date = e.optString("date", "2026-09-30"),
                                receiptUri = e.optString("receipt_uri", ""),
                                status = e.optString("status", "PENDING"),
                                createdAt = e.optLong("created_at", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Advances
                val saArray = shopMeta.optJSONArray("advances") ?: JSONArray()
                for (i in 0 until saArray.length()) {
                    val a = saArray.getJSONObject(i)
                    val aId = a.optString("id", UUID.randomUUID().toString())
                    if (!seenAdvanceIds.contains(aId)) {
                        seenAdvanceIds.add(aId)
                        advances.add(
                            AdvanceUdhaarEntity(
                                id = aId,
                                businessId = businessId,
                                employeeId = a.optString("employee_id", ""),
                                totalAmount = a.optDouble("total_amount", 0.0),
                                remainingAmount = a.optDouble("remaining_amount", 0.0),
                                dailyDeductionAmount = a.optDouble("daily_deduction_amount", 0.0),
                                deductionMethod = a.optString("deduction_method", "DAILY_SALARY_CUT"),
                                status = a.optString("status", "ACTIVE"),
                                reason = a.optString("reason", "Advance"),
                                createdAt = a.optLong("created_at", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            Result.success(ShopRemoteData(emps, punches, leaves, expenses, advances))
        } catch (e: Exception) {
            if (e.isCancellation()) {
                Log.d(TAG, "fetchFullShopDataFromSupabase cancelled")
                return@withContext Result.failure(e)
            }
            Log.e(TAG, "fetchFullShopDataFromSupabase error", e)
            Result.failure(e)
        }
    }

    /**
     * Helper to add/update an employee in the Shop Owner's Supabase metadata.
     */
    private suspend fun addOrUpdateEmployeeInShopMetadata(businessId: String, employee: EmployeeEntity) = withContext(Dispatchers.IO) {
        try {
            val shopUser = findSupabaseUserByShopId(businessId) ?: return@withContext
            val shopUserId = shopUser.getString("id")
            val meta = shopUser.optJSONObject("user_metadata") ?: JSONObject()
            val employees = meta.optJSONArray("employees") ?: JSONArray()

            val empObj = JSONObject().apply {
                put("id", employee.id)
                put("employee_id", employee.id)
                put("employee_code", employee.employeeCode)
                put("full_name", employee.fullName)
                put("name", employee.fullName)
                put("phone", employee.phone)
                put("email", employee.email)
                put("password", employee.password)
                put("address", employee.address)
                put("designation", employee.designation)
                put("joining_date", employee.joiningDate)
                put("salary_type", employee.salaryType)
                put("monthly_salary", employee.monthlySalary)
                put("daily_wage", employee.dailyWage)
                put("custom_daily_rate", employee.customDailyRate)
                put("bank_account", employee.bankAccount)
                put("bank_ifsc", employee.bankIfsc)
                put("emergency_contact", employee.emergencyContact)
                put("status", employee.status)
            }

            var found = false
            for (i in 0 until employees.length()) {
                val item = employees.getJSONObject(i)
                if (item.optString("id") == employee.id || item.optString("employee_id") == employee.id || item.optString("phone") == employee.phone) {
                    employees.put(i, empObj)
                    found = true
                    break
                }
            }
            if (!found) {
                employees.put(empObj)
            }
            meta.put("employees", employees)
            meta.put("employee_count", employees.length())

            val req = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/admin/users/$shopUserId")
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .put(JSONObject().apply { put("user_metadata", meta) }.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "addOrUpdateEmployeeInShopMetadata error", e)
        }
    }

    /**
     * Helper to remove an employee from the Shop Owner's Supabase metadata.
     */
    private suspend fun removeEmployeeFromShopMetadata(businessId: String, employeeId: String) = withContext(Dispatchers.IO) {
        try {
            val shopUser = findSupabaseUserByShopId(businessId) ?: return@withContext
            val shopUserId = shopUser.getString("id")
            val meta = shopUser.optJSONObject("user_metadata") ?: JSONObject()
            val employees = meta.optJSONArray("employees") ?: JSONArray()
            val newEmployees = JSONArray()

            for (i in 0 until employees.length()) {
                val item = employees.getJSONObject(i)
                if (item.optString("id") != employeeId && item.optString("employee_id") != employeeId) {
                    newEmployees.put(item)
                }
            }
            meta.put("employees", newEmployees)
            meta.put("employee_count", newEmployees.length())

            val req = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/admin/users/$shopUserId")
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .put(JSONObject().apply { put("user_metadata", meta) }.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "removeEmployeeFromShopMetadata error", e)
        }
    }

    /**
     * Helper to fetch all raw users from Supabase Auth admin API.
     */
    private suspend fun fetchAllRawUsersFromSupabase(): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/auth/v1/admin/users?per_page=100"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchAllRawUsers error: ${response.code}")
                return@withContext emptyList()
            }

            val json = JSONObject(resStr)
            val usersArray = json.optJSONArray("users") ?: return@withContext emptyList()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until usersArray.length()) {
                list.add(usersArray.getJSONObject(i))
            }
            list
        } catch (e: Exception) {
            if (e.isCancellation()) {
                Log.d(TAG, "fetchAllRawUsersFromSupabase cancelled")
                return@withContext emptyList()
            }
            Log.e(TAG, "fetchAllRawUsersFromSupabase error", e)
            emptyList()
        }
    }

    suspend fun findSupabaseUserByShopId(shopId: String): JSONObject? = withContext(Dispatchers.IO) {
        val users = fetchAllRawUsersFromSupabase()
        for (u in users) {
            val meta = u.optJSONObject("user_metadata") ?: JSONObject()
            val uId = u.optString("id", "")
            val sId = meta.optString("shop_id", "")
            val bId = meta.optString("business_id", "")
            val bCode = meta.optString("business_code", "")

            if (uId.equals(shopId, ignoreCase = true) ||
                sId.equals(shopId, ignoreCase = true) ||
                bId.equals(shopId, ignoreCase = true) ||
                bCode.equals(shopId, ignoreCase = true)) {
                return@withContext u
            }
        }
        null
    }

    suspend fun findSupabaseUserByEmployeeId(empId: String): JSONObject? = withContext(Dispatchers.IO) {
        val users = fetchAllRawUsersFromSupabase()
        for (u in users) {
            val meta = u.optJSONObject("user_metadata") ?: JSONObject()
            val uId = u.optString("id", "")
            val eId = meta.optString("employee_id", "")
            val phone = meta.optString("phone", "")

            if (uId.equals(empId, ignoreCase = true) ||
                eId.equals(empId, ignoreCase = true) ||
                phone.equals(empId, ignoreCase = true)) {
                return@withContext u
            }
        }
        null
    }

    private suspend fun updateShopMetadataByEmail(email: String, meta: JSONObject, rawPass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val users = fetchAllRawUsersFromSupabase()
            var targetId: String? = null
            for (u in users) {
                if (u.optString("email").equals(email, ignoreCase = true)) {
                    targetId = u.optString("id")
                    break
                }
            }
            if (targetId == null) return@withContext Result.failure(Exception("User email not found"))

            val putUrl = "$SUPABASE_URL/auth/v1/admin/users/$targetId"
            val body = JSONObject().apply {
                put("user_metadata", meta)
                put("password", rawPass)
            }
            val putReq = Request.Builder()
                .url(putUrl)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .put(body.toString().toRequestBody(jsonMediaType))
                .build()
            val putRes = httpClient.newCall(putReq).execute()
            val ok = putRes.isSuccessful
            putRes.close()
            if (ok) {
                Result.success(targetId)
            } else {
                Result.failure(Exception("Failed to update user: ${putRes.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateEmployeeMetadataByContact(phone: String, email: String, meta: JSONObject, rawPass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val users = fetchAllRawUsersFromSupabase()
            var targetId: String? = null
            for (u in users) {
                val m = u.optJSONObject("user_metadata") ?: JSONObject()
                if (u.optString("email").equals(email, ignoreCase = true) || m.optString("phone") == phone) {
                    targetId = u.optString("id")
                    break
                }
            }
            if (targetId == null) return@withContext Result.failure(Exception("Employee not found"))

            val putUrl = "$SUPABASE_URL/auth/v1/admin/users/$targetId"
            val body = JSONObject().apply {
                put("user_metadata", meta)
                put("password", rawPass)
            }
            val putReq = Request.Builder()
                .url(putUrl)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .put(body.toString().toRequestBody(jsonMediaType))
                .build()
            val putRes = httpClient.newCall(putReq).execute()
            val ok = putRes.isSuccessful
            putRes.close()
            if (ok) {
                Result.success(targetId)
            } else {
                Result.failure(Exception("Failed to update employee: ${putRes.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads an attendance selfie photo to Supabase Storage bucket 'attendance_photos'.
     * Returns the publicly accessible URL of the uploaded image, or null on failure.
     */
    suspend fun uploadAttendancePhotoToSupabase(businessId: String, employeeId: String, photoFile: java.io.File): String? = withContext(Dispatchers.IO) {
        try {
            if (!photoFile.exists()) return@withContext null
            val fileName = "selfie_${businessId}_${employeeId}_${System.currentTimeMillis()}.jpg"

            // 1. Ensure bucket exists
            val bucketReq = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/bucket")
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .post(JSONObject().apply {
                    put("id", "attendance_photos")
                    put("name", "attendance_photos")
                    put("public", true)
                }.toString().toRequestBody(jsonMediaType))
                .build()
            try {
                httpClient.newCall(bucketReq).execute().close()
            } catch (_: Exception) {}

            // 2. Upload file bytes to Supabase Storage bucket
            val bytes = photoFile.readBytes()
            val uploadReq = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/attendance_photos/$fileName")
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "image/jpeg")
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()

            val resp = httpClient.newCall(uploadReq).execute()
            val isSuccess = resp.isSuccessful
            resp.close()

            if (isSuccess) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/attendance_photos/$fileName"
                Log.d(TAG, "Uploaded attendance selfie to Supabase bucket: $publicUrl")
                return@withContext publicUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Supabase storage bucket upload error: ${e.message}")
        }
        return@withContext null
    }

    /**
     * Uploads an avatar/profile picture to Supabase Storage bucket 'avatars'.
     */
    suspend fun uploadAvatarToSupabase(userId: String, imageFile: java.io.File): String? = withContext(Dispatchers.IO) {
        try {
            if (!imageFile.exists()) return@withContext null
            val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"

            val bucketReq = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/bucket")
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .post(JSONObject().apply {
                    put("id", "avatars")
                    put("name", "avatars")
                    put("public", true)
                }.toString().toRequestBody(jsonMediaType))
                .build()
            try {
                httpClient.newCall(bucketReq).execute().close()
            } catch (_: Exception) {}

            val bytes = imageFile.readBytes()
            val uploadReq = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/avatars/$fileName")
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "image/jpeg")
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()

            val resp = httpClient.newCall(uploadReq).execute()
            val isSuccess = resp.isSuccessful
            resp.close()

            if (isSuccess) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/avatars/$fileName"
                return@withContext publicUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Supabase avatar upload error: ${e.message}")
        }
        return@withContext null
    }
}
