package com.example.dukaan.data.remote

import android.util.Log
import com.example.dukaan.data.model.CurrentUser
import com.example.dukaan.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SupabaseClient {
    private const val TAG = "SupabaseClient"
    const val SUPABASE_URL = "https://uzylcwkxlonqyjlhqpre.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_p-NWTFqbWVnnTjvFt7Q2Vg_vq5nAO62"
    const val SECRET_KEY = "sb_secret_isGiGh9Fmi-itTa_wBwl8w_cDcaltrw"

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
                val name = meta.optString("name", if (role == UserRole.SUPERADMIN) "Platform Superadmin" else if (role == UserRole.EMPLOYEE) "Rahul Verma" else "Ramesh Sharma")
                val bizId = meta.optString("business_id", "biz-sharma-10284")
                val empId = meta.optString("employee_id", if (role == UserRole.EMPLOYEE) "emp-rahul-01" else null)

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
