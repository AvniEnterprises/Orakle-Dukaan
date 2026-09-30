package com.example.dukaan.data.remote

import android.util.Log
import com.example.dukaan.data.local.BusinessEntity
import com.example.dukaan.data.local.EmployeeEntity
import com.example.dukaan.data.model.CurrentUser
import com.example.dukaan.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SupabaseClient {
    private const val TAG = "SupabaseClient"
    const val SUPABASE_URL = "https://uzylcwkxlonqyjlhqpre.supabase.co"
    const val PUBLISHABLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV6eWxjd2t4bG9ucXlqbGhxcHJlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA1OTM0NzEsImV4cCI6MjEwNjE2OTQ3MX0.9uSwgmibSCpzg-BENerdnKQXs1HzNk3OYpaEzMPM09I"
    const val SECRET_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV6eWxjd2t4bG9ucXlqbGhxcHJlIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc5MDU5MzQ3MSwiZXhwIjoyMTA2MTY5NDcxfQ.PXAglLBtwkkJWJXkae8Ycg52GkCraRiFX7mOhJEUMO8"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
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
                    roleStr.equals("EMPLOYEE", ignoreCase = true) || userEmail.contains("rahul", ignoreCase = true) -> UserRole.EMPLOYEE
                    else -> UserRole.BUSINESS_ADMIN
                }
                val name = meta.optString("name", meta.optString("owner_name", "Shop Owner"))
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
     * Stores full shop metadata inside user_metadata so any Superadmin device can see and manage it live.
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
                put("agent_code", business.agentCode)
                put("working_days", business.workingDays)
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
                // If user already exists, update user_metadata
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
            val url = "$SUPABASE_URL/auth/v1/admin/users?per_page=100"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val json = JSONObject(resStr)
                val usersArray = json.optJSONArray("users") ?: JSONArray()
                val list = mutableListOf<BusinessEntity>()

                for (i in 0 until usersArray.length()) {
                    val userObj = usersArray.getJSONObject(i)
                    val userId = userObj.optString("id")
                    val email = userObj.optString("email")
                    val meta = userObj.optJSONObject("user_metadata") ?: JSONObject()
                    val role = meta.optString("role", "")

                    if (role.equals("BUSINESS_ADMIN", ignoreCase = true) || meta.has("shop_name")) {
                        val shopId = meta.optString("shop_id", userId)
                        val entity = BusinessEntity(
                            id = shopId,
                            businessCode = meta.optString("business_code", "SHP-${shopId.take(6).uppercase()}"),
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
                            monthlyPrice = meta.optDouble("monthly_price", 499.0),
                            employeeLimit = meta.optInt("employee_limit", 5),
                            dailyEventLimitPerEmployee = meta.optInt("daily_event_limit", 4),
                            status = meta.optString("status", "PENDING"),
                            shiftStart = meta.optString("shift_start", "09:00 AM"),
                            shiftEnd = meta.optString("shift_end", "07:00 PM"),
                            graceMinutes = meta.optInt("grace_minutes", 15),
                            overtimeThresholdMinutes = meta.optInt("overtime_threshold_minutes", 30),
                            agentCode = meta.optString("agent_code", ""),
                            workingDays = meta.optString("working_days", "ALL_7_DAYS"),
                            createdAt = meta.optLong("created_at", System.currentTimeMillis())
                        )
                        list.add(entity)
                    }
                }
                Log.i(TAG, "Fetched ${list.size} shops from Supabase")
                Result.success(list)
            } else {
                Log.w(TAG, "fetchAllShops error: ${response.code} $resStr")
                Result.failure(Exception("Failed to fetch shops: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllShopsFromSupabase exception", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the status of a shop in Supabase (e.g. APPROVED, SUSPENDED, REJECTED).
     */
    suspend fun updateShopStatusInSupabase(shopId: String, newStatus: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val user = findSupabaseUserByShopId(shopId) ?: return@withContext Result.failure(Exception("User not found in Supabase"))
            val userId = user.getString("id")
            val currentMeta = user.optJSONObject("user_metadata") ?: JSONObject()
            currentMeta.put("status", newStatus)

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
            if (response.isSuccessful) {
                Log.i(TAG, "Updated shop status in Supabase to $newStatus for $shopId")
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to update status: ${response.code}"))
            }
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
            val user = findSupabaseUserByShopId(shopId) ?: return@withContext Result.failure(Exception("User not found in Supabase"))
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
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a shop user from Supabase.
     */
    suspend fun deleteShopFromSupabase(shopId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val user = findSupabaseUserByShopId(shopId) ?: return@withContext Result.failure(Exception("User not found in Supabase"))
            val userId = user.getString("id")
            val url = "$SUPABASE_URL/auth/v1/admin/users/$userId"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .delete()
                .build()

            val response = httpClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Helper to find a user by shop_id or user.id
     */
    private suspend fun findSupabaseUserByShopId(shopId: String): JSONObject? = withContext(Dispatchers.IO) {
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
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(resStr)
            val usersArray = json.optJSONArray("users") ?: return@withContext null
            for (i in 0 until usersArray.length()) {
                val u = usersArray.getJSONObject(i)
                val meta = u.optJSONObject("user_metadata") ?: JSONObject()
                if (u.optString("id") == shopId || meta.optString("shop_id") == shopId) {
                    return@withContext u
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateShopMetadataByEmail(email: String, meta: JSONObject, rawPass: String): Result<String> = withContext(Dispatchers.IO) {
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
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Failed to lookup user"))

            val json = JSONObject(resStr)
            val usersArray = json.optJSONArray("users") ?: return@withContext Result.failure(Exception("No users array"))
            var targetId: String? = null
            for (i in 0 until usersArray.length()) {
                val u = usersArray.getJSONObject(i)
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
            if (putRes.isSuccessful) {
                Result.success(targetId)
            } else {
                Result.failure(Exception("Failed to update user: ${putRes.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs up or creates a verified user in Supabase Authentication using the Admin API.
     */
    suspend fun createOrUpdateSupabaseUser(
        email: String,
        pass: String,
        role: UserRole,
        name: String,
        businessId: String? = null,
        employeeId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/auth/v1/admin/users"
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", pass.trim())
                put("email_confirm", true)
                put("user_metadata", JSONObject().apply {
                    put("role", role.name)
                    put("name", name)
                    businessId?.let { put("business_id", it) }
                    employeeId?.let { put("employee_id", it) }
                })
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
                Result.success(json.optString("id", ""))
            } else {
                Log.w(TAG, "Admin create user response: ${response.code} $resStr")
                Result.failure(Exception("Supabase user creation returned ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createOrUpdateSupabaseUser error", e)
            Result.failure(e)
        }
    }

    /**
     * Sync attendance event to remote backend.
     */
    suspend fun syncAttendanceToRemote(
        attendanceJson: JSONObject
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/rest/v1/attendance_events"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SECRET_KEY)
                .addHeader("Authorization", "Bearer $SECRET_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(attendanceJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Log.w(TAG, "Remote attendance sync returned ${response.code}")
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
