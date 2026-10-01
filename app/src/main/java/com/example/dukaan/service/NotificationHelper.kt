package com.example.dukaan.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dukaan.data.model.UserRole

object NotificationHelper {
    private const val CHANNEL_ID = "dukaan_live_updates"
    private const val CHANNEL_NAME = "Dukaan Live Alerts"

    @Volatile
    var currentLoggedInRole: UserRole? = null

    fun setLoggedInRole(role: UserRole?) {
        currentLoggedInRole = role
    }

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts for punches, attendance, leaves, and salary"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        targetRole: UserRole? = null,
        notificationId: Int = (System.currentTimeMillis() % 10000).toInt()
    ) {
        try {
            // Strict role separation: If a specific targetRole is specified, only show if the current logged in user has that exact role!
            var activeRole = currentLoggedInRole
            if (activeRole == null) {
                activeRole = SessionManager.getUserSession(context)?.role
                currentLoggedInRole = activeRole
            }
            if (targetRole != null) {
                if (activeRole == null || targetRole != activeRole) {
                    return
                }
            }

            init(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val manager = NotificationManagerCompat.from(context)
            manager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Permission POST_NOTIFICATIONS might not be granted
        } catch (e: Exception) {
            android.util.Log.w("NotificationHelper", "Failed to show notification: ${e.message}")
        }
    }

    fun showAdminNotification(context: Context, title: String, message: String) {
        showNotification(context, title, message, targetRole = UserRole.BUSINESS_ADMIN)
    }

    fun showEmployeeNotification(context: Context, title: String, message: String) {
        showNotification(context, title, message, targetRole = UserRole.EMPLOYEE)
    }

    fun showSuperAdminNotification(context: Context, title: String, message: String) {
        showNotification(context, title, message, targetRole = UserRole.SUPERADMIN)
    }

    fun showAgentNotification(context: Context, title: String, message: String) {
        showNotification(context, title, message, targetRole = UserRole.AGENT)
    }
}
