package com.example.dukaan.data.model

enum class UserRole {
    SUPERADMIN,
    BUSINESS_ADMIN,
    EMPLOYEE,
    AGENT
}

enum class BusinessStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED,
    ACTIVE,
    EXPIRED
}

enum class SalaryType {
    MONTHLY,
    DAILY_WAGE,
    CUSTOM_DAILY
}

enum class AttendanceType {
    IN,
    OUT
}

enum class LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class ExpenseStatus {
    PENDING,
    APPROVED,
    REJECTED
}

data class CurrentUser(
    val id: String,
    val role: UserRole,
    val email: String,
    val name: String,
    val businessId: String? = null,
    val employeeId: String? = null
)

data class Business(
    val id: String,
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
    val geofenceRadiusMeters: Int = 100,
    val plan: String = "BASIC",
    val monthlyPrice: Double = 149.0,
    val employeeLimit: Int = 10,
    val dailyEventLimitPerEmployee: Int = 6,
    val status: BusinessStatus = BusinessStatus.APPROVED,
    val shiftStart: String = "09:00 AM",
    val shiftEnd: String = "07:00 PM",
    val graceMinutes: Int = 15,
    val overtimeThresholdMinutes: Int = 30,
    val agentCode: String = "",
    val workingDays: String = "MON,TUE,WED,THU,FRI,SAT",
    val createdAt: Long = System.currentTimeMillis()
)

data class Employee(
    val id: String,
    val businessId: String,
    val employeeCode: String,
    val fullName: String,
    val photoUrl: String = "",
    val phone: String,
    val email: String = "",
    val password: String = "123456",
    val address: String = "",
    val designation: String = "Staff",
    val joiningDate: String = "2026-01-01",
    val salaryType: SalaryType = SalaryType.MONTHLY,
    val monthlySalary: Double = 15000.0,
    val dailyWage: Double = 600.0,
    val customDailyRate: Double = 600.0,
    val bankAccount: String = "",
    val bankIfsc: String = "",
    val emergencyContact: String = "",
    val status: String = "ACTIVE"
)

data class AttendanceEvent(
    val id: String,
    val businessId: String,
    val employeeId: String,
    val eventType: AttendanceType,
    val timestamp: Long,
    val formattedTime: String,
    val dateStr: String,
    val latitude: Double,
    val longitude: Double,
    val distanceFromShopMeters: Double,
    val isGeofenceValid: Boolean,
    val photoUri: String = "",
    val verificationMethod: String = "LIVE_GPS_PHOTO",
    val status: String = "VERIFIED",
    val isOffline: Boolean = false,
    val syncStatus: String = "SYNCED"
)

data class LeaveRequest(
    val id: String,
    val businessId: String,
    val employeeId: String,
    val leaveType: String,
    val startDate: String,
    val endDate: String,
    val days: Int,
    val reason: String,
    val status: LeaveStatus = LeaveStatus.PENDING,
    val adminComment: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class AdvanceUdhaar(
    val id: String,
    val businessId: String,
    val employeeId: String,
    val totalAmount: Double,
    val remainingAmount: Double,
    val dailyDeductionAmount: Double = 100.0,
    val deductionMethod: String = "DAILY_DEDUCTION",
    val status: String = "ACTIVE",
    val reason: String = "Festival advance",
    val createdAt: Long = System.currentTimeMillis()
)

data class AdvanceDeductionLog(
    val id: String,
    val advanceId: String,
    val employeeId: String,
    val deductionDate: String,
    val amountDeducted: Double,
    val remainingAfter: Double,
    val notes: String = "Daily attendance deduction"
)

data class ExpenseRecord(
    val id: String,
    val businessId: String,
    val employeeId: String,
    val category: String,
    val amount: Double,
    val description: String,
    val date: String,
    val receiptUri: String = "",
    val status: ExpenseStatus = ExpenseStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

data class EmployeeDocument(
    val id: String,
    val businessId: String,
    val employeeId: String,
    val docType: String,
    val docName: String,
    val driveFileId: String,
    val verificationStatus: String = "VERIFIED",
    val expiryDate: String = "2028-12-31",
    val uploadDate: String = "2026-09-28"
)

data class IssueReport(
    val id: String,
    val businessId: String,
    val employeeId: String,
    val category: String,
    val description: String,
    val status: String = "PENDING",
    val adminResponse: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class SupportMessage(
    val id: String,
    val businessId: String,
    val senderRole: String,
    val senderName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = true
)

data class AuditLog(
    val id: String,
    val businessId: String,
    val action: String,
    val performedBy: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val price: Double,
    val maxEmployees: Int,
    val maxEventsPerEmployeePerDay: Int,
    val features: List<String>
)

data class Agent(
    val id: String,
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

data class CommissionRecord(
    val id: String,
    val agentId: String,
    val businessId: String,
    val shopName: String,
    val subscriptionAmount: Double,
    val commissionPercent: Double = 20.0,
    val commissionAmount: Double,
    val status: String = "PENDING", // PENDING, APPROVED, PAID
    val paymentDate: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PunchRecord(
    val id: String,
    val businessId: String,
    val employeeId: String,
    val punchType: AttendanceType,
    val timestamp: Long = System.currentTimeMillis(),
    val timeStr: String,
    val dateStr: String,
    val photoUri: String = "",
    val location: String = "",
    val status: String = "VALID"
)

