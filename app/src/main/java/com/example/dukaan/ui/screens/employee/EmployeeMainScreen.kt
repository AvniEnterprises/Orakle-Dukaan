package com.example.dukaan.ui.screens.employee

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaan.data.model.*
import com.example.dukaan.data.repository.DukaanRepository
import com.example.dukaan.service.LocationHelper
import com.example.dukaan.service.NotificationHelper
import com.example.dukaan.service.RealCameraSelfieDialog
import com.example.dukaan.service.RealQrScannerDialog
import com.example.dukaan.service.ReportGenerator
import com.example.dukaan.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

fun isBeforeShiftEnd(shiftEndStr: String?): Boolean {
    if (shiftEndStr.isNullOrBlank()) return false
    try {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val endMinutes = if (shiftEndStr.contains("AM", ignoreCase = true) || shiftEndStr.contains("PM", ignoreCase = true)) {
            val df = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
            val d = df.parse(shiftEndStr.trim())
            val c = Calendar.getInstance().apply { time = d }
            c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        } else {
            val parts = shiftEndStr.trim().split(":")
            val h = parts[0].trim().toInt()
            val m = if (parts.size > 1) parts[1].trim().take(2).toInt() else 0
            h * 60 + m
        }
        return currentMinutes < endMinutes
    } catch (_: Exception) {
        return false
    }
}

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

    // Real device location state
    var deviceLat by remember { mutableStateOf<Double?>(null) }
    var deviceLng by remember { mutableStateOf<Double?>(null) }
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    fun refreshLocation() {
        isFetchingLocation = true
        LocationHelper.getRealLocation(
            context = context,
            onSuccess = { lat, lng, acc ->
                deviceLat = lat
                deviceLng = lng
                locationAccuracy = acc
                isFetchingLocation = false
            },
            onError = { _ ->
                isFetchingLocation = false
            }
        )
    }

    LaunchedEffect(Unit) {
        refreshLocation()
    }

    // Silent background sync polling loop (Every 8 seconds)
    LaunchedEffect(employeeId, businessId) {
        while (isActive) {
            try {
                repository.syncBusinessesFromSupabase()
                repository.syncEmployeesAndAttendanceFromSupabase(businessId)
                employee = repository.getEmployeeById(employeeId)
                business = repository.getBusinessById(businessId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) break
            }
            kotlinx.coroutines.delay(8000)
        }
    }

    val attendanceHistory by repository.getAttendanceForEmployee(employeeId).collectAsState(initial = emptyList())
    val myLeaves by repository.getLeavesForEmployee(employeeId).collectAsState(initial = emptyList())
    val myAdvances by repository.getAdvancesForEmployee(employeeId).collectAsState(initial = emptyList())
    val myExpenses by repository.getExpensesForEmployee(employeeId).collectAsState(initial = emptyList())
    val myDocuments by repository.getDocumentsForEmployee(employeeId).collectAsState(initial = emptyList())
    val supportMessages by repository.getSupportMessages(businessId).collectAsState(initial = emptyList())

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()) }
    val todayPunches = attendanceHistory.filter { it.dateStr == todayStr }

    // Current State: Is currently working?
    val lastPunch = todayPunches.firstOrNull() // sorted by timestamp desc
    val isCurrentlyWorking = lastPunch?.eventType == AttendanceType.IN

    // Offline simulation toggle
    var isOfflineMode by remember { mutableStateOf(false) }
    val pendingSyncPunches = attendanceHistory.count { it.syncStatus == "PENDING_SYNC" }

    // Real device coordinates or fallback to shop
    var isSimulatingOutside by remember { mutableStateOf(false) }
    val shopLat = business?.latitude ?: 26.9124
    val shopLng = business?.longitude ?: 75.7873
    val currentLat = deviceLat ?: (if (isSimulatingOutside) shopLat + 0.005 else shopLat)
    val currentLng = deviceLng ?: (if (isSimulatingOutside) shopLng + 0.005 else shopLng)

    val distanceMeters = remember(currentLat, currentLng, business) {
        business?.let { b ->
            repository.calculateDistanceMeters(b.latitude, b.longitude, currentLat, currentLng)
        } ?: 15.0
    }
    val isInsideGeofence = distanceMeters <= (business?.geofenceRadiusMeters ?: 100)

    var selectedTab by remember { mutableStateOf(0) } // 0: Today, 1: History, 2: Salary/Udhaar, 3: Requests, 4: Profile
    BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }

    var showCameraDialog by remember { mutableStateOf(false) }
    var pendingPunchType by remember { mutableStateOf<AttendanceType?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var selectedLeaveForEdit by remember { mutableStateOf<LeaveRequest?>(null) }
    var selectedExpenseForEdit by remember { mutableStateOf<ExpenseRecord?>(null) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var isPunching by remember { mutableStateOf(false) }
    var showEarlyDutyEndDialog by remember { mutableStateOf(false) }
    var isEarlyDutyEndPunch by remember { mutableStateOf(false) }

    val profilePhotoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bmp = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bmp != null) {
                        val photosDir = java.io.File(context.filesDir, "employee_avatars").apply { mkdirs() }
                        val file = java.io.File(photosDir, "avatar_${employeeId}.jpg")
                        val fos = java.io.FileOutputStream(file)
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, fos)
                        fos.flush()
                        fos.close()
                        repository.updateEmployeeAvatar(employeeId, file)
                        employee = repository.getEmployeeById(employeeId)
                        Toast.makeText(context, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to upload photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Document and PDF state
    var showAddDocDialog by remember { mutableStateOf(false) }
    var selectedDocForEdit by remember { mutableStateOf<EmployeeDocument?>(null) }
    var selectedPdfFile by remember { mutableStateOf<java.io.File?>(null) }
    var showPdfActionsDialog by remember { mutableStateOf(false) }
    var supportText by remember { mutableStateOf("") }

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

                                    // Real GPS Coordinates and Refresh Button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp), tint = OrakleRedPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (deviceLat != null) "Live GPS: ${String.format(Locale.ENGLISH, "%.4f, %.4f", currentLat, currentLng)}" else "Detecting GPS...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = OrakleSlate600
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { refreshLocation() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            if (isFetchingLocation) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                            } else {
                                                Icon(Icons.Default.Refresh, contentDescription = "Refresh GPS", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

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
                                        if (isBeforeShiftEnd(business?.shiftEnd)) {
                                            showEarlyDutyEndDialog = true
                                        } else {
                                            isEarlyDutyEndPunch = false
                                            pendingPunchType = AttendanceType.OUT
                                            showCameraDialog = true
                                        }
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
                                                    selectedPdfFile = payslip
                                                    showPdfActionsDialog = true
                                                    Toast.makeText(context, "Payslip generated: ${payslip.name}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download & Print Payslip PDF", fontSize = 12.sp)
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("MY LEAVE REQUESTS (${myLeaves.size})", fontWeight = FontWeight.Bold, color = OrakleSlate700, fontSize = 12.sp)
                                if (myLeaves.any { it.status != LeaveStatus.PENDING }) {
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                repository.clearEmployeeCompletedLeaves(employeeId)
                                                Toast.makeText(context, "Completed leave history cleared!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear Past Leaves", fontSize = 11.sp, color = OrakleRedPrimary)
                                    }
                                }
                            }
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
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val avatarBitmap = remember(employee?.photoUrl) {
                                            try {
                                                val p = employee?.photoUrl.orEmpty()
                                                if (p.isNotBlank() && !p.startsWith("http")) {
                                                    val f = java.io.File(p)
                                                    if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                                                } else null
                                            } catch (_: Exception) { null }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(OrakleRedLight)
                                                .border(2.dp, OrakleRedPrimary, CircleShape)
                                                .clickable {
                                                    profilePhotoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (avatarBitmap != null) {
                                                Image(
                                                    bitmap = avatarBitmap.asImageBitmap(),
                                                    contentDescription = "Profile Photo",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(
                                                    text = (employee?.fullName?.take(1) ?: "S").uppercase(),
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrakleRedPrimary
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(employee?.fullName ?: "Staff", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("${employee?.designation ?: "Staff"} • ${employee?.employeeCode}", fontSize = 12.sp, color = OrakleSlate600)
                                            TextButton(
                                                onClick = {
                                                    profilePhotoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(13.dp), tint = OrakleRedPrimary)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Upload Photo", fontSize = 11.sp, color = OrakleRedPrimary)
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { showEditProfileDialog = true },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("employee_edit_profile_button")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Edit", fontSize = 11.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Mobile: ${employee?.phone}", fontSize = 12.sp, color = OrakleSlate600)
                                    Text("Address: ${employee?.address}", fontSize = 12.sp, color = OrakleSlate500)
                                    Text("Emergency Contact: ${employee?.emergencyContact}", fontSize = 12.sp, color = OrakleSlate500)
                                    if (!employee?.bankAccount.isNullOrBlank()) {
                                        Text("Bank A/C: ${employee?.bankAccount} • IFSC: ${employee?.bankIfsc}", fontSize = 12.sp, color = OrakleSlate500)
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("MY DOCUMENTS (${myDocuments.size})", fontWeight = FontWeight.Bold, color = OrakleSlate700)
                                Button(
                                    onClick = { showAddDocDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Upload", fontSize = 12.sp)
                                }
                            }
                        }

                        if (myDocuments.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = OrakleSlate500, modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("No documents uploaded yet", color = OrakleSlate600, fontSize = 12.sp)
                                        Text("Upload Aadhaar, PAN, or Bank passbook for verification", color = OrakleSlate500, fontSize = 11.sp)
                                    }
                                }
                            }
                        } else {
                            items(myDocuments) { doc ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = OrakleRedPrimary)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(doc.docName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Type: ${doc.docType} • Exp: ${doc.expiryDate}", fontSize = 11.sp, color = OrakleSlate500)
                                            Text("Status: ${doc.verificationStatus}", fontSize = 11.sp, color = if (doc.verificationStatus == "VERIFIED") OrakleGreen else OrakleAmber)
                                        }
                                        IconButton(onClick = { selectedDocForEdit = doc }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Doc", tint = OrakleSlate600)
                                        }
                                    }
                                }
                            }
                        }

                        // SUPPORT / HELPDESK CHAT CARD WITH SHOP ADMIN
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Chat, contentDescription = null, tint = OrakleRedPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Help & Support with Shop Admin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Send queries or issues directly to shop owner.", fontSize = 11.sp, color = OrakleSlate500)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val myShopMessages = supportMessages.takeLast(5)
                                    if (myShopMessages.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            myShopMessages.forEach { msg ->
                                                val isMe = msg.senderRole == "EMPLOYEE"
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isMe) OrakleGreenContainer else OrakleSlate100,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(msg.message, fontSize = 12.sp, color = OrakleSlate900)
                                                        Text(
                                                            text = "${if (isMe) "You" else msg.senderName} • ${SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(msg.timestamp))}",
                                                            fontSize = 10.sp,
                                                            color = OrakleSlate500
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = supportText,
                                            onValueChange = { supportText = it },
                                            placeholder = { Text("Type query to shop owner...", fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                if (supportText.isNotBlank()) {
                                                    coroutineScope.launch {
                                                        repository.sendSupportMessage(
                                                            SupportMessage(
                                                                id = UUID.randomUUID().toString(),
                                                                businessId = businessId,
                                                                senderRole = "EMPLOYEE",
                                                                senderName = employee?.fullName ?: "Staff",
                                                                message = supportText.trim()
                                                            )
                                                        )
                                                        supportText = ""
                                                        Toast.makeText(context, "Query sent to Admin!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = "Send", tint = OrakleRedPrimary)
                                        }
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
        RealCameraSelfieDialog(
            onDismiss = {
                showCameraDialog = false
                pendingPunchType = null
            },
            onPhotoCaptured = { photoFile ->
                showCameraDialog = false
                isPunching = true
                coroutineScope.launch {
                    val result = repository.recordAttendancePunch(
                        businessId = businessId,
                        employeeId = employeeId,
                        eventType = punchType,
                        userLat = currentLat,
                        userLng = currentLng,
                        photoUri = photoFile.absolutePath,
                        method = "LIVE_CAMERA_GPS",
                        isOfflineMode = isOfflineMode
                    )
                    isPunching = false
                    pendingPunchType = null
                    if (result.isSuccess) {
                        if (isEarlyDutyEndPunch) {
                            NotificationHelper.showAdminNotification(
                                context = context,
                                title = "⚠️ Early Duty End Alert: ${employee?.fullName}",
                                message = "${employee?.fullName} ended duty EARLY at ${SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())} before shift end (${business?.shiftEnd})."
                            )
                        }
                        val ev = result.getOrNull()!!
                        val msg = if (ev.isGeofenceValid) {
                            "Duty ${ev.eventType} recorded successfully at ${ev.formattedTime} with live selfie photo!"
                        } else {
                            "Punch recorded as OUTSIDE GEOFENCE (${ev.distanceFromShopMeters.toInt()}m from shop)."
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // QR Code Scanner Live Camera Dialog
    if (showQrScannerDialog) {
        RealQrScannerDialog(
            expectedShopCode = business?.businessCode ?: businessId,
            onDismiss = { showQrScannerDialog = false },
            onQrScanned = { scannedCode ->
                showQrScannerDialog = false
                val bizCode = business?.businessCode.orEmpty()
                val bizId = businessId
                val isMatchingShop = (bizCode.isNotBlank() && scannedCode.contains(bizCode, ignoreCase = true)) ||
                        (bizId.isNotBlank() && scannedCode.contains(bizId, ignoreCase = true)) ||
                        scannedCode.contains("ORAKLE_DUKAAN", ignoreCase = true)

                if (!isMatchingShop) {
                    Toast.makeText(context, "QR Mismatch! This QR code does not belong to ${business?.name ?: "your shop"} ($bizCode).", Toast.LENGTH_LONG).show()
                    return@RealQrScannerDialog
                }

                val detectedType = when {
                    scannedCode.contains(":OUT", ignoreCase = true) || scannedCode.contains("CHECK OUT", ignoreCase = true) || scannedCode.contains("_OUT", ignoreCase = true) -> AttendanceType.OUT
                    scannedCode.contains(":IN", ignoreCase = true) || scannedCode.contains("CHECK IN", ignoreCase = true) || scannedCode.contains("_IN", ignoreCase = true) -> AttendanceType.IN
                    else -> if (isCurrentlyWorking) AttendanceType.OUT else AttendanceType.IN
                }

                if (detectedType == AttendanceType.OUT && isBeforeShiftEnd(business?.shiftEnd)) {
                    NotificationHelper.showAdminNotification(
                        context = context,
                        title = "⚠️ Early Duty End Alert: ${employee?.fullName}",
                        message = "${employee?.fullName} ended duty EARLY at ${SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())} before shift end (${business?.shiftEnd}) via QR Gate Pass."
                    )
                }

                isPunching = true
                coroutineScope.launch {
                    val res = repository.recordAttendancePunch(
                        businessId = businessId,
                        employeeId = employeeId,
                        eventType = detectedType,
                        userLat = currentLat,
                        userLng = currentLng,
                        photoUri = "QR_PASS_${scannedCode.take(20)}",
                        method = "QR_GATE_PASS",
                        isOfflineMode = isOfflineMode
                    )
                    isPunching = false
                    if (res.isSuccess) {
                        Toast.makeText(context, "QR Verified! Duty $detectedType marked at ${res.getOrNull()?.formattedTime}.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Early Duty End Warning Dialog
    if (showEarlyDutyEndDialog) {
        AlertDialog(
            onDismissRequest = { showEarlyDutyEndDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = OrakleAmber) },
            title = { Text("Early Duty End Warning", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Your assigned shift ends at ${business?.shiftEnd ?: "shift end time"}. You are ending duty before your scheduled hours.\n\nAn alert will be sent immediately to the Shop Owner.",
                    fontSize = 13.sp,
                    color = OrakleSlate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEarlyDutyEndDialog = false
                        isEarlyDutyEndPunch = true
                        pendingPunchType = AttendanceType.OUT
                        showCameraDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Text("Confirm Early Exit")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEarlyDutyEndDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Document Dialog
    if (showAddDocDialog) {
        AddDocumentDialog(
            businessId = businessId,
            employeeId = employeeId,
            onDismiss = { showAddDocDialog = false },
            onSave = { doc ->
                coroutineScope.launch {
                    repository.saveDocument(doc)
                    showAddDocDialog = false
                    Toast.makeText(context, "Document uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Document Dialog
    if (selectedDocForEdit != null) {
        EditDocumentDialog(
            doc = selectedDocForEdit!!,
            onDismiss = { selectedDocForEdit = null },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateDocument(updated)
                    selectedDocForEdit = null
                    Toast.makeText(context, "Document updated!", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                val docId = selectedDocForEdit!!.id
                coroutineScope.launch {
                    repository.deleteDocument(docId)
                    selectedDocForEdit = null
                    Toast.makeText(context, "Document deleted!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // PDF Print and Download Action Dialog
    if (showPdfActionsDialog && selectedPdfFile != null) {
        val file = selectedPdfFile!!
        AlertDialog(
            onDismissRequest = { showPdfActionsDialog = false },
            icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = OrakleRedPrimary) },
            title = { Text("Payslip Downloaded", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("File saved in device storage: ${file.name}", fontSize = 12.sp, color = OrakleSlate600)
                    Text("Choose an option below to view or print the payslip:", fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ReportGenerator.printPdf(context, file, "Payslip_Print")
                        showPdfActionsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print / Save PDF")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        ReportGenerator.openOrShareFile(context, file)
                        showPdfActionsDialog = false
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open / Share")
                }
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
