package com.example.dukaan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey val id: String,
    val businessCode: String,
    val name: String,
    val ownerName: String,
    val phone: String,
    val email: String,
    val password: String = "123456",
    val businessType: String,
    val address: String,
    val city: String,
    val state: String,
    val pincode: String,
    val latitude: Double,
    val longitude: Double,
    val geofenceRadiusMeters: Int,
    val plan: String,
    val monthlyPrice: Double,
    val employeeLimit: Int,
    val dailyEventLimitPerEmployee: Int,
    val status: String,
    val shiftStart: String,
    val shiftEnd: String,
    val graceMinutes: Int,
    val overtimeThresholdMinutes: Int,
    val agentCode: String = "",
    val workingDays: String = "MON,TUE,WED,THU,FRI,SAT",
    val createdAt: Long
)

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeCode: String,
    val fullName: String,
    val photoUrl: String,
    val phone: String,
    val email: String,
    val password: String = "123456",
    val address: String,
    val designation: String,
    val joiningDate: String,
    val salaryType: String,
    val monthlySalary: Double,
    val dailyWage: Double,
    val customDailyRate: Double,
    val bankAccount: String,
    val bankIfsc: String,
    val emergencyContact: String,
    val status: String
)

@Entity(tableName = "attendance_events")
data class AttendanceEventEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeId: String,
    val eventType: String,
    val timestamp: Long,
    val formattedTime: String,
    val dateStr: String,
    val latitude: Double,
    val longitude: Double,
    val distanceFromShopMeters: Double,
    val isGeofenceValid: Boolean,
    val photoUri: String,
    val verificationMethod: String,
    val status: String,
    val isOffline: Boolean,
    val syncStatus: String
)

@Entity(tableName = "leave_requests")
data class LeaveRequestEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeId: String,
    val leaveType: String,
    val startDate: String,
    val endDate: String,
    val days: Int,
    val reason: String,
    val status: String,
    val adminComment: String,
    val createdAt: Long
)

@Entity(tableName = "advances_udhaar")
data class AdvanceUdhaarEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeId: String,
    val totalAmount: Double,
    val remainingAmount: Double,
    val dailyDeductionAmount: Double,
    val deductionMethod: String,
    val status: String,
    val reason: String,
    val createdAt: Long
)

@Entity(tableName = "expenses")
data class ExpenseRecordEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeId: String,
    val category: String,
    val amount: Double,
    val description: String,
    val date: String,
    val receiptUri: String,
    val status: String,
    val createdAt: Long
)

@Entity(tableName = "documents")
data class EmployeeDocumentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeId: String,
    val docType: String,
    val docName: String,
    val driveFileId: String,
    val verificationStatus: String,
    val expiryDate: String,
    val uploadDate: String
)

@Entity(tableName = "support_messages")
data class SupportMessageEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val senderRole: String,
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val action: String,
    val performedBy: String,
    val details: String,
    val timestamp: Long
)

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val password: String = "123456",
    val agentCode: String,
    val commissionPercent: Double = 20.0,
    val status: String = "ACTIVE",
    val earnings: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "commissions")
data class CommissionEntity(
    @PrimaryKey val id: String,
    val agentId: String,
    val businessId: String,
    val shopName: String,
    val subscriptionAmount: Double,
    val commissionPercent: Double = 20.0,
    val commissionAmount: Double,
    val status: String = "PENDING",
    val paymentDate: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "punches")
data class PunchRecordEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeId: String,
    val punchType: String,
    val timestamp: Long,
    val timeStr: String,
    val dateStr: String,
    val photoUri: String = "",
    val location: String = "",
    val status: String = "VALID"
)

