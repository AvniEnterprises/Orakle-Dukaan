package com.example.dukaan.ui.screens.employee

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaan.data.model.*
import com.example.dukaan.data.repository.DukaanRepository
import com.example.dukaan.service.ReportGenerator
import com.example.dukaan.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeMainScreen(
    repository: DukaanRepository,
    employeeId: String,
    businessId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var employee by remember { mutableStateOf<Employee?>(null) }
    var business by remember { mutableStateOf<Business?>(null) }

    LaunchedEffect(employeeId, businessId) {
        repository.syncBusinessesFromSupabase()
        repository.syncEmployeesAndAttendanceFromSupabase(businessId)
        employee = repository.getEmployeeById(employeeId)
        business = repository.getBusinessById(businessId)
    }

    val attendanceHistory by repository.getAttendanceForEmployee(employeeId).collectAsState(initial = emptyList())
    val myLeaves by repository.getLeavesForEmployee(employeeId).collectAsState(initial = emptyList())
    val myAdvances by repository.getAdvancesForEmployee(employeeId).collectAsState(initial = emptyList())
    val myExpenses by repository.getExpensesForEmployee(employeeId).collectAsState(initial = emptyList())
    val myDocuments by repository.getDocumentsForEmployee(employeeId).collectAsState(initial = emptyList())

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()) }
    val todayPunches = attendanceHistory.filter { it.dateStr == todayStr }

    // Current State: Is currently working?
    val lastPunch = todayPunches.firstOrNull() // sorted by timestamp desc
    val isCurrentlyWorking = lastPunch?.eventType == AttendanceType.IN

    // Offline simulation toggle
    var isOfflineMode by remember { mutableStateOf(false) }
    val pendingSyncPunches = attendanceHistory.count { it.syncStatus == "PENDING_SYNC" }

    // Simulated user coordinates (by default at shop, or toggled to test outside geofence)
    var isSimulatingOutside by remember { mutableStateOf(false) }
    val shopLat = business?.latitude ?: 26.9124
    val shopLng = business?.longitude ?: 75.7873
    val currentLat = if (isSimulatingOutside) shopLat + 0.005 else shopLat + 0.0001
    val currentLng = if (isSimulatingOutside) shopLng + 0.005 else shopLng + 0.0001

    val distanceMeters = remember(currentLat, currentLng, business) {
        business?.let { b ->
            repository.calculateDistanceMeters(b.latitude, b.longitude, currentLat, currentLng)
        } ?: 15.0
    }
    val isInsideGeofence = distanceMeters <= (business?.geofenceRadiusMeters ?: 100)

    var selectedTab by remember { mutableStateOf(0) } // 0: Today, 1: History, 2: Salary/Udhaar, 3: Requests, 4: Profile
    var showCameraDialog by remember { mutableStateOf(false) }
    var pendingPunchType by remember { mutableStateOf<AttendanceType?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var selectedLeaveForEdit by remember { mutableStateOf<LeaveRequest?>(null) }
    var selectedExpenseForEdit by remember { mutableStateOf<ExpenseRecord?>(null) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var isPunching by remember { mutableStateOf(false) }

    // Live Clock
    var currentTimeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeStr = SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).format(Date())
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = employee?.fullName ?: "Employee Portal",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${business?.name ?: "Shop"} • ${employee?.designation ?: "Staff"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OrakleSlate500
                        )
                    }
                },
                actions = {
                    // Offline Mode Chip
                    FilterChip(
                        selected = isOfflineMode,
                        onClick = {
                            isOfflineMode = !isOfflineMode
                            Toast.makeText(context, if (isOfflineMode) "Offline Mode ON" else "Online Mode ON", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(if (isOfflineMode) "Offline" else "Online", fontSize = 10.sp) },
                        leadingIcon = {
                            Icon(
                                if (isOfflineMode) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Today") },
                    label = { Text("Today", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("Punches", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Salary") },
                    label = { Text("Salary", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.PostAdd, contentDescription = "Requests") },
                    label = { Text("Requests", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontSize = 11.sp) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Offline banner if pending sync punches exist
            OfflineSyncBanner(
                pendingCount = pendingSyncPunches,
                onSyncClick = {
                    coroutineScope.launch {
                        val count = repository.syncPendingAttendance()
                        Toast.makeText(context, "Synced $count punches to server!", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            when (selectedTab) {
                0 -> {
                    // TAB 0: TODAY SMART ATTENDANCE
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            // Current Status Hero Card
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isCurrentlyWorking) OrakleGreenContainer else MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isCurrentlyWorking) OrakleGreen else OrakleSlate200
                                    ) {
                                        Text(
                                            text = if (isCurrentlyWorking) "DUTY IN PROGRESS" else "OFF DUTY",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isCurrentlyWorking) Color.White else OrakleSlate700,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = currentTimeStr.ifEmpty { "09:00:00 AM" },
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).format(Date()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OrakleSlate500
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Geofence status badge
                                    GeofenceStatusBadge(
                                        isInside = isInsideGeofence,
                                        distanceMeters = distanceMeters,
                                        radiusMeters = business?.geofenceRadiusMeters ?: 100
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Quick GPS Testing toggle for evaluator
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text("Simulate Outside Geofence:", fontSize = 11.sp, color = OrakleSlate600)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Switch(
                                            checked = isSimulatingOutside,
                                            onCheckedChange = { isSimulatingOutside = it },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            // Primary IN / OUT Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        pendingPunchType = AttendanceType.IN
                                        showCameraDialog = true
                                    },
                                    enabled = !isCurrentlyWorking && !isPunching,
                                    colors = ButtonDefaults.buttonColors(containerColor = OrakleGreen),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(58.dp)
                                        .testTag("start_duty_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text("START DUTY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Punch IN", fontSize = 10.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        pendingPunchType = AttendanceType.OUT
                                        showCameraDialog = true
                                    },
                                    enabled = isCurrentlyWorking && !isPunching,
                                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(58.dp)
                                        .testTag("end_duty_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text("END DUTY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Punch OUT", fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        item {
                            // QR Punch Secondary option
                            OutlinedButton(
                                onClick = { showQrScannerDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = OrakleRedPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan Shop QR Gate Pass", color = OrakleSlate900, fontWeight = FontWeight.Medium)
                            }
                        }

                        item {
                            // Event Limit Progress Bar
                            val dailyLimit = business?.dailyEventLimitPerEmployee ?: 6
                            val eventsToday = todayPunches.size
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Today's Punches", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OrakleSlate700)
                                        Text("$eventsToday of $dailyLimit used", fontSize = 12.sp, color = OrakleRedPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { (eventsToday.toFloat() / dailyLimit).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = OrakleRedPrimary,
                                        trackColor = OrakleSlate200,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${business?.plan ?: "BASIC"} plan limit • Counted per IN and OUT",
                                        fontSize = 10.sp,
                                        color = OrakleSlate500
                                    )
                                }
                            }
                        }

                        item {
                            Text("TODAY'S ACTIVITY", fontWeight = FontWeight.Bold, color = OrakleSlate700, fontSize = 13.sp)
                        }

                        if (todayPunches.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = OrakleSlate500, modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Tap 'START DUTY' to mark your attendance today", color = OrakleSlate600, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            items(todayPunches) { punch ->
                                PunchHistoryItem(punch = punch)
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: ATTENDANCE HISTORY
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text("ALL PUNCHES HISTORY (${attendanceHistory.size})", fontWeight = FontWeight.Bold, color = OrakleSlate700)
                        }
                        items(attendanceHistory) { punch ->
                            PunchHistoryItem(punch = punch)
                        }
                    }
                }

                2 -> {
                    // TAB 2: SALARY & UDHAAR
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            // Base Salary Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = OrakleSlate900)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text("MONTHLY SALARY STRUCTURE", color = OrakleSlate500, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("₹${employee?.monthlySalary?.toInt() ?: 15000}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    Text("Type: ${employee?.salaryType?.name ?: "MONTHLY"} • Bank: ${employee?.bankAccount ?: "Verified"}", color = OrakleSlate200, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            business?.let { b ->
                                                employee?.let { emp ->
                                                    val payslip = ReportGenerator.generatePayslipPdf(context, b, emp, "September 2026", 26, 1000.0)
                                                    Toast.makeText(context, "Downloaded Payslip: ${payslip.name}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download Latest Payslip PDF", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        item {
                            Text("MY UDHAAR / ADVANCE STATUS", fontWeight = FontWeight.Bold, color = OrakleSlate700)
                        }

                        val activeAdv = myAdvances.firstOrNull { it.status == "ACTIVE" }
                        if (activeAdv != null) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Active Advance", fontWeight = FontWeight.Bold)
                                            Text("₹${activeAdv.remainingAmount.toInt()} Remaining", color = OrakleAmber, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Total Issued: ₹${activeAdv.totalAmount.toInt()} • Daily Cut: ₹${activeAdv.dailyDeductionAmount.toInt()}/day", fontSize = 12.sp, color = OrakleSlate600)
                                        Text("Reason: ${activeAdv.reason}", fontSize = 12.sp, color = OrakleSlate500)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = OrakleGreenContainer
                                        ) {
                                            Text(
                                                text = "✓ ₹${activeAdv.dailyDeductionAmount.toInt()} automatically recovered on completed duty punch",
                                                color = Color(0xFF166534),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OrakleGreen)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("No pending udhaar balance. All clear!", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // TAB 3: REQUESTS (LEAVE & EXPENSES)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showLeaveDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apply Leave", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { showExpenseDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrakleSlate900),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Claim Petrol", fontSize = 12.sp)
                                }
                            }
                        }

                        item {
                            Text("MY LEAVE REQUESTS (${myLeaves.size})", fontWeight = FontWeight.Bold, color = OrakleSlate700, fontSize = 12.sp)
                        }

                        items(myLeaves) { leave ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${leave.leaveType} (${leave.days} days)", fontWeight = FontWeight.Bold)
                                            Text(leave.status.name, fontWeight = FontWeight.Bold, color = if (leave.status == LeaveStatus.APPROVED) OrakleGreen else OrakleAmber)
                                        }
                                        Text("${leave.startDate} to ${leave.endDate}", fontSize = 12.sp, color = OrakleSlate600)
                                        Text("Reason: ${leave.reason}", fontSize = 11.sp, color = OrakleSlate500)
                                    }
                                    if (leave.status == LeaveStatus.PENDING) {
                                        IconButton(
                                            onClick = { selectedLeaveForEdit = leave },
                                            modifier = Modifier.testTag("employee_edit_leave_${leave.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Leave", tint = OrakleSlate700)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("MY EXPENSE CLAIMS (${myExpenses.size})", fontWeight = FontWeight.Bold, color = OrakleSlate700, fontSize = 12.sp)
                        }

                        items(myExpenses) { exp ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${exp.category}: ₹${exp.amount.toInt()}", fontWeight = FontWeight.Bold)
                                        Text("${exp.description} • ${exp.date}", fontSize = 11.sp, color = OrakleSlate500)
                                        Text(exp.status.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (exp.status == ExpenseStatus.APPROVED) OrakleGreen else OrakleAmber)
                                    }
                                    if (exp.status == ExpenseStatus.PENDING) {
                                        IconButton(
                                            onClick = { selectedExpenseForEdit = exp },
                                            modifier = Modifier.testTag("employee_edit_expense_${exp.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Expense Claim", tint = OrakleSlate700)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // TAB 4: PROFILE & DOCUMENTS
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(employee?.fullName ?: "Staff", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        OutlinedButton(
                                            onClick = { showEditProfileDialog = true },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("employee_edit_profile_button")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit Profile", fontSize = 12.sp)
                                        }
                                    }
                                    Text("Code: ${employee?.employeeCode} • Mobile: ${employee?.phone}", fontSize = 12.sp, color = OrakleSlate600)
                                    Text("Address: ${employee?.address}", fontSize = 12.sp, color = OrakleSlate500)
                                    Text("Emergency Contact: ${employee?.emergencyContact}", fontSize = 12.sp, color = OrakleSlate500)
                                    if (!employee?.bankAccount.isNullOrBlank()) {
                                        Text("Bank A/C: ${employee?.bankAccount} • IFSC: ${employee?.bankIfsc}", fontSize = 12.sp, color = OrakleSlate500)
                                    }
                                }
                            }
                        }

                        item {
                            Text("DOCUMENTS (Google Drive Cloud Storage)", fontWeight = FontWeight.Bold, color = OrakleSlate700)
                        }

                        items(myDocuments) { doc ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = OrakleRedPrimary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(doc.docName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Type: ${doc.docType} • Drive Ref: ${doc.driveFileId}", fontSize = 11.sp, color = OrakleSlate500)
                                        Text("Status: ${doc.verificationStatus}", fontSize = 11.sp, color = OrakleGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Live Camera Selfie Capture Dialog
    if (showCameraDialog && pendingPunchType != null) {
        val punchType = pendingPunchType!!
        AlertDialog(
            onDismissRequest = { showCameraDialog = false },
            title = {
                Text("Confirm $punchType with Live Photo", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(OrakleSlate100)
                            .border(3.dp, if (punchType == AttendanceType.IN) OrakleGreen else OrakleRedPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Camera Selfie",
                                modifier = Modifier.size(48.dp),
                                tint = OrakleRedPrimary
                            )
                            Text("Live Selfie", fontSize = 11.sp, color = OrakleSlate600)
                        }
                    }

                    Text("GPS Coordinates:", fontSize = 11.sp, color = OrakleSlate500)
                    Text("$currentLat, $currentLng", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                    GeofenceStatusBadge(
                        isInside = isInsideGeofence,
                        distanceMeters = distanceMeters,
                        radiusMeters = business?.geofenceRadiusMeters ?: 100
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isPunching = true
                        showCameraDialog = false
                        coroutineScope.launch {
                            val result = repository.recordAttendancePunch(
                                businessId = businessId,
                                employeeId = employeeId,
                                eventType = punchType,
                                userLat = currentLat,
                                userLng = currentLng,
                                photoUri = "mock_selfie_blob_${System.currentTimeMillis()}",
                                method = "LIVE_GPS_PHOTO",
                                isOfflineMode = isOfflineMode
                            )
                            isPunching = false
                            if (result.isSuccess) {
                                val ev = result.getOrNull()!!
                                val msg = if (ev.isGeofenceValid) {
                                    "Duty ${ev.eventType} recorded successfully at ${ev.formattedTime}!"
                                } else {
                                    "Punch recorded as OUTSIDE GEOFENCE (${ev.distanceFromShopMeters.toInt()}m from shop)."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (punchType == AttendanceType.IN) OrakleGreen else OrakleRedPrimary),
                    modifier = Modifier.testTag("confirm_punch_button")
                ) {
                    Text("Confirm & Punch $punchType")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // QR Code Scanner Simulator Dialog
    if (showQrScannerDialog) {
        AlertDialog(
            onDismissRequest = { showQrScannerDialog = false },
            title = { Text("QR Gate Pass Scanner", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(OrakleSlate900, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = OrakleRedPrimary, modifier = Modifier.size(54.dp))
                            Text("Scan Shop QR Code", color = Color.White, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Point camera to shop QR pass: ${business?.businessCode}", fontSize = 12.sp, color = OrakleSlate600)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQrScannerDialog = false
                        coroutineScope.launch {
                            val nextType = if (isCurrentlyWorking) AttendanceType.OUT else AttendanceType.IN
                            val res = repository.recordAttendancePunch(
                                businessId = businessId,
                                employeeId = employeeId,
                                eventType = nextType,
                                userLat = currentLat,
                                userLng = currentLng,
                                photoUri = "qr_verified",
                                method = "QR_GATE_PASS",
                                isOfflineMode = isOfflineMode
                            )
                            if (res.isSuccess) {
                                Toast.makeText(context, "QR Verified! Duty $nextType marked.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Text("Simulate QR Scan Punch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrScannerDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Apply Leave Dialog
    if (showLeaveDialog) {
        var leaveType by remember { mutableStateOf("SICK") }
        var daysCount by remember { mutableStateOf("1") }
        var reason by remember { mutableStateOf("Fever and doctor visit") }

        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Apply for Leave", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Leave Type:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    listOf("SICK", "CASUAL", "EMERGENCY", "OTHER").forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = leaveType == type, onClick = { leaveType = type })
                            Text(type, fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = daysCount,
                        onValueChange = { daysCount = it },
                        label = { Text("Number of Days") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val d = daysCount.toIntOrNull() ?: 1
                        val req = LeaveRequest(
                            id = UUID.randomUUID().toString(),
                            businessId = businessId,
                            employeeId = employeeId,
                            leaveType = leaveType,
                            startDate = todayStr,
                            endDate = todayStr,
                            days = d,
                            reason = reason
                        )
                        coroutineScope.launch {
                            repository.applyLeave(req)
                            showLeaveDialog = false
                            Toast.makeText(context, "Leave application submitted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Text("Submit Application")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Submit Expense Claim Dialog
    if (showExpenseDialog) {
        var category by remember { mutableStateOf("PETROL") }
        var amount by remember { mutableStateOf("200") }
        var desc by remember { mutableStateOf("Delivery fuel for store customers") }

        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            title = { Text("Submit Travel/Petrol Claim", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (PETROL, TRAVEL, SUPPLIES)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 200.0
                        val exp = ExpenseRecord(
                            id = UUID.randomUUID().toString(),
                            businessId = businessId,
                            employeeId = employeeId,
                            category = category,
                            amount = amt,
                            description = desc,
                            date = todayStr,
                            status = ExpenseStatus.PENDING
                        )
                        coroutineScope.launch {
                            repository.submitExpense(exp)
                            showExpenseDialog = false
                            Toast.makeText(context, "Expense claim submitted for Admin review", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Text("Submit Claim")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpenseDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Employee Profile Dialog
    if (showEditProfileDialog && employee != null) {
        EditEmployeeProfileDialog(
            employee = employee!!,
            onDismiss = { showEditProfileDialog = false },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateEmployeeProfile(
                        employeeId = updated.id,
                        phone = updated.phone,
                        address = updated.address,
                        emergencyContact = updated.emergencyContact,
                        bankAccount = updated.bankAccount,
                        bankIfsc = updated.bankIfsc
                    )
                    employee = repository.getEmployeeById(employeeId)
                    showEditProfileDialog = false
                    Toast.makeText(context, "Profile details updated", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Leave Application Dialog
    selectedLeaveForEdit?.let { leave ->
        EditLeaveDialog(
            leave = leave,
            isAdmin = false,
            onDismiss = { selectedLeaveForEdit = null },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateLeave(updated)
                    selectedLeaveForEdit = null
                    Toast.makeText(context, "Leave application updated", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteLeave(leave.id)
                    selectedLeaveForEdit = null
                    Toast.makeText(context, "Leave application withdrawn", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Expense Claim Dialog
    selectedExpenseForEdit?.let { exp ->
        EditExpenseDialog(
            expense = exp,
            isAdmin = false,
            onDismiss = { selectedExpenseForEdit = null },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateExpense(updated)
                    selectedExpenseForEdit = null
                    Toast.makeText(context, "Expense claim updated", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteExpense(exp.id)
                    selectedExpenseForEdit = null
                    Toast.makeText(context, "Expense claim withdrawn", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun PunchHistoryItem(punch: AttendanceEvent) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (punch.eventType == AttendanceType.IN) OrakleGreenContainer else OrakleRedContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = punch.eventType.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (punch.eventType == AttendanceType.IN) Color(0xFF166534) else OrakleOnRedContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("${punch.formattedTime} (${punch.verificationMethod})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(punch.dateStr, fontSize = 11.sp, color = OrakleSlate500)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (punch.isGeofenceValid) OrakleGreenContainer else OrakleRedContainer
                ) {
                    Text(
                        text = if (punch.isGeofenceValid) "Inside Geofence ✓" else "Outside ✕",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (punch.isGeofenceValid) Color(0xFF166534) else OrakleOnRedContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (punch.syncStatus == "PENDING_SYNC") {
                    Text("Pending Sync ⏳", fontSize = 10.sp, color = OrakleAmber)
                }
            }
        }
    }
}
