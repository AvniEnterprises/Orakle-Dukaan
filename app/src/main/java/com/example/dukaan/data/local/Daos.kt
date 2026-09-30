package com.example.dukaan.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DukaanDao {
    // Businesses
    @Query("SELECT * FROM businesses ORDER BY createdAt DESC")
    fun getAllBusinesses(): Flow<List<BusinessEntity>>

    @Query("SELECT * FROM businesses WHERE id = :id")
    suspend fun getBusinessById(id: String): BusinessEntity?

    @Query("SELECT * FROM businesses WHERE phone = :identifier OR email = :identifier LIMIT 1")
    suspend fun getBusinessByContact(identifier: String): BusinessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusiness(business: BusinessEntity)

    @Update
    suspend fun updateBusiness(business: BusinessEntity)

    @Query("DELETE FROM businesses WHERE id = :id")
    suspend fun deleteBusinessById(id: String)

    // Employees
    @Query("SELECT * FROM employees WHERE businessId = :businessId ORDER BY fullName ASC")
    fun getEmployeesForBusiness(businessId: String): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: String): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE phone = :phone LIMIT 1")
    suspend fun getEmployeeByPhone(phone: String): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE phone = :identifier OR email = :identifier LIMIT 1")
    suspend fun getEmployeeByContact(identifier: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: EmployeeEntity)

    @Update
    suspend fun updateEmployee(employee: EmployeeEntity)

    @Query("DELETE FROM employees WHERE id = :id")
    suspend fun deleteEmployeeById(id: String)

    // Attendance
    @Query("SELECT * FROM attendance_events WHERE businessId = :businessId ORDER BY timestamp DESC")
    fun getAttendanceForBusiness(businessId: String): Flow<List<AttendanceEventEntity>>

    @Query("SELECT * FROM attendance_events WHERE employeeId = :employeeId ORDER BY timestamp DESC")
    fun getAttendanceForEmployee(employeeId: String): Flow<List<AttendanceEventEntity>>

    @Query("SELECT * FROM attendance_events WHERE employeeId = :employeeId AND dateStr = :dateStr ORDER BY timestamp ASC")
    suspend fun getAttendanceForEmployeeToday(employeeId: String, dateStr: String): List<AttendanceEventEntity>

    @Query("SELECT * FROM attendance_events WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncAttendance(): List<AttendanceEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceEvent(event: AttendanceEventEntity)

    @Update
    suspend fun updateAttendanceEvent(event: AttendanceEventEntity)

    // Leave
    @Query("SELECT * FROM leave_requests WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getLeavesForBusiness(businessId: String): Flow<List<LeaveRequestEntity>>

    @Query("SELECT * FROM leave_requests WHERE employeeId = :employeeId ORDER BY createdAt DESC")
    fun getLeavesForEmployee(employeeId: String): Flow<List<LeaveRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeave(leave: LeaveRequestEntity)

    @Update
    suspend fun updateLeave(leave: LeaveRequestEntity)

    @Query("DELETE FROM leave_requests WHERE id = :id")
    suspend fun deleteLeaveById(id: String)

    // Advances / Udhaar
    @Query("SELECT * FROM advances_udhaar WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getAdvancesForBusiness(businessId: String): Flow<List<AdvanceUdhaarEntity>>

    @Query("SELECT * FROM advances_udhaar WHERE employeeId = :employeeId ORDER BY createdAt DESC")
    fun getAdvancesForEmployee(employeeId: String): Flow<List<AdvanceUdhaarEntity>>

    @Query("SELECT * FROM advances_udhaar WHERE employeeId = :employeeId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveAdvanceForEmployee(employeeId: String): AdvanceUdhaarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdvance(advance: AdvanceUdhaarEntity)

    @Update
    suspend fun updateAdvance(advance: AdvanceUdhaarEntity)

    @Query("DELETE FROM advances_udhaar WHERE id = :id")
    suspend fun deleteAdvanceById(id: String)

    // Expenses
    @Query("SELECT * FROM expenses WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getExpensesForBusiness(businessId: String): Flow<List<ExpenseRecordEntity>>

    @Query("SELECT * FROM expenses WHERE employeeId = :employeeId ORDER BY createdAt DESC")
    fun getExpensesForEmployee(employeeId: String): Flow<List<ExpenseRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseRecordEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseRecordEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: String)

    // Documents
    @Query("SELECT * FROM documents WHERE businessId = :businessId ORDER BY uploadDate DESC")
    fun getDocumentsForBusiness(businessId: String): Flow<List<EmployeeDocumentEntity>>

    @Query("SELECT * FROM documents WHERE employeeId = :employeeId ORDER BY uploadDate DESC")
    fun getDocumentsForEmployee(employeeId: String): Flow<List<EmployeeDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: EmployeeDocumentEntity)

    @Update
    suspend fun updateDocument(doc: EmployeeDocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    // Support Messages
    @Query("SELECT * FROM support_messages WHERE businessId = :businessId ORDER BY timestamp ASC")
    fun getSupportMessages(businessId: String): Flow<List<SupportMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupportMessage(msg: SupportMessageEntity)

    // Audit logs
    @Query("SELECT * FROM audit_logs WHERE businessId = :businessId ORDER BY timestamp DESC LIMIT 100")
    fun getAuditLogs(businessId: String): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    // Agents (Requirement 24-28)
    @Query("SELECT * FROM agents ORDER BY createdAt DESC")
    fun getAllAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getAgentById(id: String): AgentEntity?

    @Query("SELECT * FROM agents WHERE agentCode = :code LIMIT 1")
    suspend fun getAgentByCode(code: String): AgentEntity?

    @Query("SELECT * FROM agents WHERE phone = :contact OR email = :contact LIMIT 1")
    suspend fun getAgentByContact(contact: String): AgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentEntity)

    @Update
    suspend fun updateAgent(agent: AgentEntity)

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun deleteAgentById(id: String)

    // Commissions (Requirement 25-26)
    @Query("SELECT * FROM commissions ORDER BY createdAt DESC")
    fun getAllCommissions(): Flow<List<CommissionEntity>>

    @Query("SELECT * FROM commissions WHERE agentId = :agentId ORDER BY createdAt DESC")
    fun getCommissionsForAgent(agentId: String): Flow<List<CommissionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommission(comm: CommissionEntity)

    @Update
    suspend fun updateCommission(comm: CommissionEntity)

    // Punches (Requirement 11-12)
    @Query("SELECT * FROM punches WHERE businessId = :businessId ORDER BY timestamp DESC")
    fun getPunchesForBusiness(businessId: String): Flow<List<PunchRecordEntity>>

    @Query("SELECT * FROM punches WHERE employeeId = :employeeId ORDER BY timestamp DESC")
    fun getPunchesForEmployee(employeeId: String): Flow<List<PunchRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPunch(punch: PunchRecordEntity)
}

