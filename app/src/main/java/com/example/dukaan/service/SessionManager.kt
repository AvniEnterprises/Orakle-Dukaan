package com.example.dukaan.service

import android.content.Context
import android.content.SharedPreferences
import com.example.dukaan.data.model.CurrentUser
import com.example.dukaan.data.model.UserRole

object SessionManager {
    private const val PREF_NAME = "dukaan_user_session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ROLE = "user_role"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_NAME = "user_name"
    private const val KEY_BUSINESS_ID = "user_business_id"
    private const val KEY_EMPLOYEE_ID = "user_employee_id"
    private const val KEY_AGENT_ID = "user_agent_id"
    private const val KEY_LOGIN_TIME = "login_time"

    // 12 Hours in milliseconds
    const val SESSION_DURATION_MS = 12 * 60 * 60 * 1000L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveUserSession(context: Context, user: CurrentUser) {
        val editor = getPrefs(context).edit()
        editor.putString(KEY_USER_ID, user.id)
        editor.putString(KEY_ROLE, user.role.name)
        editor.putString(KEY_EMAIL, user.email)
        editor.putString(KEY_NAME, user.name)
        editor.putString(KEY_BUSINESS_ID, user.businessId)
        editor.putString(KEY_EMPLOYEE_ID, user.employeeId)
        editor.putString(KEY_AGENT_ID, user.agentId)
        editor.putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
        editor.apply()

        NotificationHelper.setLoggedInRole(user.role)
    }

    fun getUserSession(context: Context): CurrentUser? {
        val prefs = getPrefs(context)
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val roleStr = prefs.getString(KEY_ROLE, null) ?: return null
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0L)

        // Check if 12 hours have passed
        val elapsed = System.currentTimeMillis() - loginTime
        if (elapsed > SESSION_DURATION_MS || elapsed < 0) {
            clearUserSession(context)
            return null
        }

        return try {
            val role = UserRole.valueOf(roleStr)
            val user = CurrentUser(
                id = userId,
                role = role,
                email = prefs.getString(KEY_EMAIL, "") ?: "",
                name = prefs.getString(KEY_NAME, "") ?: "",
                businessId = prefs.getString(KEY_BUSINESS_ID, null),
                employeeId = prefs.getString(KEY_EMPLOYEE_ID, null),
                agentId = prefs.getString(KEY_AGENT_ID, null)
            )
            NotificationHelper.setLoggedInRole(role)
            user
        } catch (e: Exception) {
            clearUserSession(context)
            null
        }
    }

    fun clearUserSession(context: Context) {
        getPrefs(context).edit().clear().apply()
        NotificationHelper.setLoggedInRole(null)
    }
}
