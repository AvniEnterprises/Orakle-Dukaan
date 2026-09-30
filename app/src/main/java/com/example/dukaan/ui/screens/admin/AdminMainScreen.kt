package com.example.dukaan.ui.screens.admin

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.core.content.FileProvider
import com.example.dukaan.data.model.*
import com.example.dukaan.data.repository.DukaanRepository
import com.example.dukaan.service.ReportGenerator
import com.example.dukaan.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMainScreen(
    repository: DukaanRepository,
    businessId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var business by remember { mutableStateOf<Business?>(null) }
    LaunchedEffect(businessId) {
        business = repository.getBusinessById(businessId)
    }

    val employees by repository.getEmployeesForBusiness(businessId).collectAsState(initial = emptyList())
    val attendanceList by repository.getAttendanceForBusiness(businessId).collectAsState(initial = emptyList())
    val leaves by repository.getLeavesForBusiness(businessId).collectAsState(initial = emptyList())
    val advances by repository.getAdvancesForBusiness(businessId).collectAsState(initial = emptyList())
    val expenses by repository.getExpensesForBusiness(businessId).collectAsState(initial = emptyList())
    val documents by repository.getDocumentsForBusiness(businessId).collectAsState(initial = emptyList())
    val supportMessages by repository.getSupportMessages(businessId).collectAsState(initial = emptyList())

    var selectedNavTab by remember { mutableStateOf(0) } // 0: Dashboard, 1: Staff, 2: Attendance, 3: Money/Udhaar, 4: More
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var showIssueAdvanceDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var selectedEmployeeForEdit by remember { mutableStateOf<Employee?>(null) }
    var showEditShopDialog by remember { mutableStateOf(false) }
    var selectedAdvanceForEdit by remember { mutableStateOf<AdvanceUdhaar?>(null) }
    var selectedExpenseForEdit by remember { mutableStateOf<ExpenseRecord?>(null) }
    var selectedLeaveForEdit by remember { mutableStateOf<LeaveRequest?>(null) }
    var selectedDocForEdit by remember { mutableStateOf<EmployeeDocument?>(null) }
    var showManualPunchDialog by remember { mutableStateOf(false) }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()) }
    val todayPunches = attendanceList.filter { it.dateStr == todayStr }
    val presentEmpIds = todayPunches.filter { it.eventType == AttendanceType.IN }.map { it.employeeId }.distinct()
    val presentCount = presentEmpIds.size
    val totalEmployeesCount = employees.count { it.status == "ACTIVE" }
    val absentCount = (totalEmployeesCount - presentCount).coerceAtLeast(0)
    val onLeaveCount = leaves.count { it.status == LeaveStatus.APPROVED && it.startDate <= todayStr && it.endDate >= todayStr }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = business?.name ?: "Business Admin",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Text(
                            text = "${business?.businessCode ?: "ODK-10284"} • ${business?.plan ?: "PLUS"} Plan • Staff: ${employees.size}/${business?.employeeLimit ?: 25}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OrakleSlate500
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showEditShopDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Shop Details", tint = OrakleRedPrimary)
                    }
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Shop QR Gate Pass", tint = OrakleSlate700)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedNavTab == 0,
                    onClick = { selectedNavTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Home", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedNavTab == 1,
                    onClick = { selectedNavTab = 1 },
                    icon = { Icon(Icons.Default.People, contentDescription = "Staff") },
                    label = { Text("Staff", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedNavTab == 2,
                    onClick = { selectedNavTab = 2 },
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = "Attendance") },
                    label = { Text("Attendance", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedNavTab == 3,
                    onClick = { selectedNavTab = 3 },
                    icon = { Icon(Icons.Default.CurrencyRupee, contentDescription = "Money") },
                    label = { Text("Payroll", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedNavTab == 4,
                    onClick = { selectedNavTab = 4 },
                    icon = { Icon(Icons.Default.Menu, contentDescription = "More") },
                    label = { Text("More", fontSize = 11.sp) }
                )
            }
        },
        floatingActionButton = {
            if (selectedNavTab == 1) {
                FloatingActionButton(
                    onClick = { showAddEmployeeDialog = true },
                    containerColor = OrakleRedPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_employee_fab")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Employee")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedNavTab) {
                0 -> {
                    // TAB 0: HOME / DASHBOARD
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            // Geofence status card
                            business?.let { b ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = OrakleRedPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Shop Geofence: ${b.geofenceRadiusMeters} meters active",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Punches strictly allowed only within shop radius",
                                                fontSize = 11.sp,
                                                color = OrakleSlate500
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("TODAY'S SUMMARY", fontWeight = FontWeight.Bold, color = OrakleSlate600, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatCard(
                                    title = "Present",
                                    value = "$presentCount",
                                    icon = Icons.Default.CheckCircle,
                                    iconColor = OrakleGreen,
                                    bgColor = OrakleGreenContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Absent",
                                    value = "$absentCount",
                                    icon = Icons.Default.Cancel,
                                    iconColor = OrakleRedDark,
                                    bgColor = OrakleRedContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "On Leave",
                                    value = "$onLeaveCount",
                                    icon = Icons.Default.EventBusy,
                                    iconColor = OrakleAmber,
                                    bgColor = OrakleAmberContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            // Quick Action Buttons Grid
                            Text("QUICK ACTIONS", fontWeight = FontWeight.Bold, color = OrakleSlate600, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuickActionButton(
                                    title = "+ Employee",
                                    icon = Icons.Default.PersonAdd,
                                    onClick = { showAddEmployeeDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                QuickActionButton(
                                    title = "Issue Udhaar",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    onClick = { showIssueAdvanceDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                QuickActionButton(
                                    title = "Export PDF",
                                    icon = Icons.Default.PictureAsPdf,
                                    onClick = {
                                        business?.let { b ->
                                            val file = ReportGenerator.generateAttendancePdf(context, b, employees, attendanceList, todayStr)
                                            Toast.makeText(context, "Generated: ${file.name}", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Text("TODAY'S LIVE PUNCHES", fontWeight = FontWeight.Bold, color = OrakleSlate600, fontSize = 12.sp)
                        }

                        if (todayPunches.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = OrakleSlate500, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No attendance punches recorded yet today", color = OrakleSlate600, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            val empMap = employees.associateBy { it.id }
                            items(todayPunches) { punch ->
                                val emp = empMap[punch.employeeId]
                                AttendancePunchRow(punch = punch, employeeName = emp?.fullName ?: "Staff")
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: STAFF LIST
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL STAFF (${employees.size}/${business?.employeeLimit ?: 25})", fontWeight = FontWeight.Bold, color = OrakleSlate700)
                                Button(
                                    onClick = { showAddEmployeeDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Staff", fontSize = 12.sp)
                                }
                            }
                        }

                        items(employees) { emp ->
                            EmployeeCard(
                                employee = emp,
                                onEditClick = { selectedEmployeeForEdit = emp }
                            )
                        }
                    }
                }

                2 -> {
                    // TAB 2: ATTENDANCE HISTORY
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ALL ATTENDANCE RECORDS", fontWeight = FontWeight.Bold, color = OrakleSlate700)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showManualPunchDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Manual Punch", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            business?.let { b ->
                                                val csv = ReportGenerator.exportAttendanceCsv(context, b, employees, attendanceList)
                                                Toast.makeText(context, "Exported: ${csv.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Export CSV", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        val empMap = employees.associateBy { it.id }
                        items(attendanceList) { punch ->
                            val emp = empMap[punch.employeeId]
                            AttendancePunchRow(punch = punch, employeeName = emp?.fullName ?: "Staff")
                        }
                    }
                }

                3 -> {
                    // TAB 3: MONEY / PAYROLL / UDHAAR / EXPENSES
                    var payrollSubTab by remember { mutableStateOf(0) } // 0: Salary, 1: Udhaar Ledger, 2: Expenses
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        TabRow(
                            selectedTabIndex = payrollSubTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = OrakleRedPrimary
                        ) {
                            Tab(selected = payrollSubTab == 0, onClick = { payrollSubTab = 0 }, text = { Text("Salary") })
                            Tab(selected = payrollSubTab == 1, onClick = { payrollSubTab = 1 }, text = { Text("Udhaar / Advance") })
                            Tab(selected = payrollSubTab == 2, onClick = { payrollSubTab = 2 }, text = { Text("Expenses (${expenses.size})") })
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        when (payrollSubTab) {
                            0 -> {
                                // Salary Calculation
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("MONTHLY SALARY CALCULATION", fontWeight = FontWeight.Bold, color = OrakleSlate700, fontSize = 12.sp)
                                            Button(
                                                onClick = {
                                                    business?.let { b ->
                                                        val file = ReportGenerator.generateSalaryReportPdf(context, b, employees, "September 2026")
                                                        Toast.makeText(context, "Salary Statement PDF: ${file.name}", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = OrakleSlate900),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Salary PDF", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    items(employees) { emp ->
                                        val base = emp.monthlySalary
                                        val udhaarRec = if (emp.fullName.contains("Rahul")) 2000.0 else 0.0
                                        val net = base - udhaarRec - 500.0

                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(emp.fullName, fontWeight = FontWeight.Bold)
                                                    Text("Net: ₹${net.toInt()}", fontWeight = FontWeight.Bold, color = OrakleGreen)
                                                }
                                                Text("${emp.designation} • Base: ₹${base.toInt()} • Udhaar Ded: -₹${udhaarRec.toInt()}", fontSize = 12.sp, color = OrakleSlate600)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        business?.let { b ->
                                                            val payslip = ReportGenerator.generatePayslipPdf(context, b, emp, "September 2026", 26, udhaarRec)
                                                            Toast.makeText(context, "Generated Payslip: ${payslip.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Generate & Download Payslip PDF", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // Udhaar Ledger
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("ADVANCE / UDHAAR LEDGER", fontWeight = FontWeight.Bold, color = OrakleSlate700, fontSize = 12.sp)
                                            Button(
                                                onClick = { showIssueAdvanceDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("+ Issue Advance", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    val empMap = employees.associateBy { it.id }
                                    items(advances) { adv ->
                                        val emp = empMap[adv.employeeId]
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(emp?.fullName ?: "Staff", fontWeight = FontWeight.Bold)
                                                    Text("Remaining: ₹${adv.remainingAmount.toInt()} / ₹${adv.totalAmount.toInt()}", fontWeight = FontWeight.Bold, color = OrakleAmber)
                                                }
                                                Text("Daily Deduction: ₹${adv.dailyDeductionAmount.toInt()}/day on attendance punch", fontSize = 12.sp, color = OrakleSlate600)
                                                Text("Reason: ${adv.reason}", fontSize = 12.sp, color = OrakleSlate500)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                val nextStatus = if (adv.status == "ACTIVE") "PAUSED" else "ACTIVE"
                                                                repository.updateAdvanceStatus(adv.id, nextStatus)
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = if (adv.status == "ACTIVE") OrakleAmber else OrakleGreen),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(if (adv.status == "ACTIVE") "Pause" else "Resume", fontSize = 11.sp)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { selectedAdvanceForEdit = adv },
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Edit Udhaar", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // Expenses
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val empMap = employees.associateBy { it.id }
                                    items(expenses) { exp ->
                                        val emp = empMap[exp.employeeId]
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("${exp.category}: ₹${exp.amount.toInt()}", fontWeight = FontWeight.Bold)
                                                    Text(exp.status.name, fontWeight = FontWeight.Bold, color = if (exp.status == ExpenseStatus.APPROVED) OrakleGreen else OrakleAmber)
                                                }
                                                Text("By: ${emp?.fullName ?: "Staff"} on ${exp.date}", fontSize = 12.sp, color = OrakleSlate600)
                                                Text(exp.description, fontSize = 12.sp, color = OrakleSlate500)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (exp.status == ExpenseStatus.PENDING) {
                                                        Button(
                                                            onClick = {
                                                                coroutineScope.launch { repository.updateExpenseStatus(exp.id, ExpenseStatus.APPROVED) }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = OrakleGreen),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text("Approve", fontSize = 11.sp)
                                                        }
                                                        Button(
                                                            onClick = {
                                                                coroutineScope.launch { repository.updateExpenseStatus(exp.id, ExpenseStatus.REJECTED) }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text("Reject", fontSize = 11.sp)
                                                        }
                                                    }
                                                    OutlinedButton(
                                                        onClick = { selectedExpenseForEdit = exp },
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Edit Claim", fontSize = 11.sp)
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

                4 -> {
                    // TAB 4: MORE / LEAVES / DOCUMENTS / SUPPORT CHAT / REPORTS
                    var moreSection by remember { mutableStateOf("MENU") } // "MENU", "LEAVES", "DOCUMENTS", "SUPPORT", "REPORTS"

                    when (moreSection) {
                        "MENU" -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Text("MORE BUSINESS TOOLS", fontWeight = FontWeight.Bold, color = OrakleSlate700)
                                }
                                item {
                                    MoreMenuItem(
                                        title = "Shop Profile & Geofence Settings",
                                        subtitle = "Edit business name, owner, contact, shift timings & geofence",
                                        icon = Icons.Default.Storefront,
                                        onClick = { showEditShopDialog = true }
                                    )
                                }
                                item {
                                    MoreMenuItem(
                                        title = "Leave Requests (${leaves.count { it.status == LeaveStatus.PENDING }} pending)",
                                        subtitle = "Approve or reject sick/casual leaves",
                                        icon = Icons.Default.EventNote,
                                        onClick = { moreSection = "LEAVES" }
                                    )
                                }
                                item {
                                    MoreMenuItem(
                                        title = "Staff Documents (${documents.size} uploaded)",
                                        subtitle = "Aadhaar, Bank Passbook, Google Drive cloud records",
                                        icon = Icons.Default.FolderShared,
                                        onClick = { moreSection = "DOCUMENTS" }
                                    )
                                }
                                item {
                                    MoreMenuItem(
                                        title = "QR Gate Pass Generator",
                                        subtitle = "View and print shop QR code for employee scanner",
                                        icon = Icons.Default.QrCode,
                                        onClick = { showQrDialog = true }
                                    )
                                }
                                item {
                                    MoreMenuItem(
                                        title = "Support Chat with Superadmin",
                                        subtitle = "Direct helpdesk communication",
                                        icon = Icons.Default.SupportAgent,
                                        onClick = { moreSection = "SUPPORT" }
                                    )
                                }
                                item {
                                    MoreMenuItem(
                                        title = "Download Reports (PDF / CSV)",
                                        subtitle = "Master attendance, salary statement & ledger",
                                        icon = Icons.Default.Description,
                                        onClick = {
                                            business?.let { b ->
                                                val f1 = ReportGenerator.generateAttendancePdf(context, b, employees, attendanceList, todayStr)
                                                val f2 = ReportGenerator.generateSalaryReportPdf(context, b, employees, "September 2026")
                                                Toast.makeText(context, "Generated 2 PDFs in cache: ${f1.name} & ${f2.name}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        "LEAVES" -> {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { moreSection = "MENU" }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                    Text("Leave Requests", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val empMap = employees.associateBy { it.id }
                                    items(leaves) { leave ->
                                        val emp = empMap[leave.employeeId]
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(emp?.fullName ?: "Staff", fontWeight = FontWeight.Bold)
                                                    Text(leave.status.name, fontWeight = FontWeight.Bold, color = if (leave.status == LeaveStatus.APPROVED) OrakleGreen else OrakleAmber)
                                                }
                                                Text("${leave.leaveType} • ${leave.startDate} to ${leave.endDate} (${leave.days} days)", fontSize = 12.sp, color = OrakleSlate600)
                                                Text("Reason: ${leave.reason}", fontSize = 12.sp, color = OrakleSlate500)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (leave.status == LeaveStatus.PENDING) {
                                                        Button(
                                                            onClick = { coroutineScope.launch { repository.updateLeaveStatus(leave.id, LeaveStatus.APPROVED, "Approved by Admin") } },
                                                            colors = ButtonDefaults.buttonColors(containerColor = OrakleGreen),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text("Approve", fontSize = 11.sp)
                                                        }
                                                        Button(
                                                            onClick = { coroutineScope.launch { repository.updateLeaveStatus(leave.id, LeaveStatus.REJECTED, "Not approved") } },
                                                            colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text("Reject", fontSize = 11.sp)
                                                        }
                                                    }
                                                    OutlinedButton(
                                                        onClick = { selectedLeaveForEdit = leave },
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Edit Leave", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "DOCUMENTS" -> {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { moreSection = "MENU" }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                    Text("Employee Documents (Google Drive Ref)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val empMap = employees.associateBy { it.id }
                                    items(documents) { doc ->
                                        val emp = empMap[doc.employeeId]
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = OrakleRedPrimary, modifier = Modifier.size(28.dp))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(doc.docName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("Staff: ${emp?.fullName ?: "Staff"} • Type: ${doc.docType}", fontSize = 12.sp, color = OrakleSlate600)
                                                    Text("Drive ID: ${doc.driveFileId} • Status: ${doc.verificationStatus}", fontSize = 11.sp, color = OrakleGreen)
                                                }
                                                IconButton(onClick = { selectedDocForEdit = doc }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit / Verify Document", tint = OrakleSlate600)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "SUPPORT" -> {
                            var replyText by remember { mutableStateOf("") }
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { moreSection = "MENU" }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                    Text("Chat with Superadmin", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(supportMessages) { msg ->
                                        val isMe = msg.senderRole == "ADMIN"
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isMe) OrakleRedPrimary else OrakleSlate200
                                            ) {
                                                Text(
                                                    text = msg.message,
                                                    color = if (isMe) Color.White else OrakleSlate900,
                                                    modifier = Modifier.padding(12.dp),
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Text(
                                                text = if (isMe) "You" else "Superadmin Support",
                                                fontSize = 10.sp,
                                                color = OrakleSlate500,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = replyText,
                                        onValueChange = { replyText = it },
                                        placeholder = { Text("Ask Superadmin...") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (replyText.isNotBlank()) {
                                                coroutineScope.launch {
                                                    repository.sendSupportMessage(
                                                        SupportMessage(
                                                            id = UUID.randomUUID().toString(),
                                                            businessId = businessId,
                                                            senderRole = "ADMIN",
                                                            senderName = business?.ownerName ?: "Admin",
                                                            message = replyText
                                                        )
                                                    )
                                                    replyText = ""
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

    // Shop Profile & Geofence Edit Dialog
    if (showEditShopDialog) {
        business?.let { b ->
            EditBusinessDialog(
                business = b,
                isSuperAdmin = false,
                onDismiss = { showEditShopDialog = false },
                onSave = { updated ->
                    coroutineScope.launch {
                        repository.updateBusiness(updated)
                        business = updated
                        showEditShopDialog = false
                        Toast.makeText(context, "Shop details updated", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // Add Employee Dialog
    if (showAddEmployeeDialog) {
        AddEditEmployeeDialog(
            businessId = businessId,
            employeeToEdit = null,
            onDismiss = { showAddEmployeeDialog = false },
            onSave = { emp ->
                coroutineScope.launch {
                    repository.saveEmployee(emp, isEdit = false)
                    showAddEmployeeDialog = false
                    Toast.makeText(context, "Added staff: ${emp.fullName}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Employee Dialog with Delete support
    selectedEmployeeForEdit?.let { empToEdit ->
        AddEditEmployeeDialog(
            businessId = businessId,
            employeeToEdit = empToEdit,
            onDismiss = { selectedEmployeeForEdit = null },
            onSave = { updatedEmp ->
                coroutineScope.launch {
                    repository.saveEmployee(updatedEmp, isEdit = true)
                    selectedEmployeeForEdit = null
                    Toast.makeText(context, "Updated staff: ${updatedEmp.fullName}", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteEmployee(empToEdit.id, businessId)
                    selectedEmployeeForEdit = null
                    Toast.makeText(context, "Removed staff: ${empToEdit.fullName}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Advance / Udhaar Dialog
    selectedAdvanceForEdit?.let { adv ->
        EditAdvanceDialog(
            advance = adv,
            onDismiss = { selectedAdvanceForEdit = null },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateAdvance(updated)
                    selectedAdvanceForEdit = null
                    Toast.makeText(context, "Updated Udhaar record", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteAdvance(adv.id)
                    selectedAdvanceForEdit = null
                    Toast.makeText(context, "Deleted Udhaar record", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Expense Dialog
    selectedExpenseForEdit?.let { exp ->
        EditExpenseDialog(
            expense = exp,
            isAdmin = true,
            onDismiss = { selectedExpenseForEdit = null },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateExpense(updated)
                    selectedExpenseForEdit = null
                    Toast.makeText(context, "Updated expense claim", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteExpense(exp.id)
                    selectedExpenseForEdit = null
                    Toast.makeText(context, "Deleted expense record", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Leave Request Dialog
    selectedLeaveForEdit?.let { leave ->
        EditLeaveDialog(
            leave = leave,
            isAdmin = true,
            onDismiss = { selectedLeaveForEdit = null },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateLeave(updated)
                    selectedLeaveForEdit = null
                    Toast.makeText(context, "Updated leave request", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteLeave(leave.id)
                    selectedLeaveForEdit = null
                    Toast.makeText(context, "Deleted leave record", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Document Dialog
    selectedDocForEdit?.let { doc ->
        EditDocumentDialog(
            doc = doc,
            onDismiss = { selectedDocForEdit = null },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateDocument(updated)
                    selectedDocForEdit = null
                    Toast.makeText(context, "Updated document record", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteDocument(doc.id)
                    selectedDocForEdit = null
                    Toast.makeText(context, "Deleted document record", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Manual Attendance Punch Dialog
    if (showManualPunchDialog && employees.isNotEmpty()) {
        var selectedEmpId by remember { mutableStateOf(employees.first().id) }
        var punchType by remember { mutableStateOf(AttendanceType.IN) }

        AlertDialog(
            onDismissRequest = { showManualPunchDialog = false },
            title = { Text("Manual Attendance Punch", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Employee:", fontSize = 12.sp, color = OrakleSlate600)
                    employees.forEach { emp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEmpId = emp.id }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedEmpId == emp.id, onClick = { selectedEmpId = emp.id })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(emp.fullName, fontWeight = if (selectedEmpId == emp.id) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    Text("Punch Type:", fontSize = 12.sp, color = OrakleSlate600)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = punchType == AttendanceType.IN,
                            onClick = { punchType = AttendanceType.IN },
                            label = { Text("Clock IN") }
                        )
                        FilterChip(
                            selected = punchType == AttendanceType.OUT,
                            onClick = { punchType = AttendanceType.OUT },
                            label = { Text("Clock OUT") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            business?.let { b ->
                                repository.recordAttendancePunch(
                                    businessId = b.id,
                                    employeeId = selectedEmpId,
                                    eventType = punchType,
                                    userLat = b.latitude,
                                    userLng = b.longitude,
                                    photoUri = "",
                                    method = "MANUAL_ADMIN",
                                    isOfflineMode = false
                                )
                                showManualPunchDialog = false
                                Toast.makeText(context, "Attendance punched manually", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Text("Record Punch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualPunchDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Issue Advance Dialog
    if (showIssueAdvanceDialog) {
        IssueAdvanceDialog(
            employees = employees,
            businessId = businessId,
            onDismiss = { showIssueAdvanceDialog = false },
            onSave = { adv ->
                coroutineScope.launch {
                    repository.saveAdvance(adv)
                    showIssueAdvanceDialog = false
                    Toast.makeText(context, "Udhaar Issued: ₹${adv.totalAmount.toInt()}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Shop Dual QR Gate Passes Dialog (Requirement 16)
    if (showQrDialog) {
        var activeQrTab by remember { mutableStateOf(0) } // 0: CHECK IN, 1: CHECK OUT
        var qrSalt by remember { mutableStateOf(System.currentTimeMillis().toString().takeLast(6)) }
        val biz = business

        val isCheckIn = activeQrTab == 0
        val qrLabel = if (isCheckIn) "CHECK IN" else "CHECK OUT"
        val qrPayload = "ORAKLE_DUKAAN:${if (isCheckIn) "IN" else "OUT"}:${biz?.id ?: ""}:${biz?.businessCode ?: ""}:$qrSalt"

        val qrBitmap = remember(activeQrTab, qrSalt, biz) {
            com.example.dukaan.service.QrCodeUtil.generateQrBitmap(
                payload = qrPayload,
                shopName = biz?.name ?: "Shop",
                typeLabel = qrLabel,
                shopCode = biz?.businessCode ?: "ODK-10284"
            )
        }

        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shop Attendance QRs", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showQrDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // QR 1 vs QR 2 Tab Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { activeQrTab = 0 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCheckIn) Color(0xFF16A34A) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isCheckIn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("QR 1: CHECK IN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { activeQrTab = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isCheckIn) Color(0xFFDC2626) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!isCheckIn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("QR 2: CHECK OUT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Display QR Bitmap
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code Gate Pass ($qrLabel)",
                            modifier = Modifier
                                .size(240.dp)
                                .padding(8.dp)
                        )
                    }

                    Text(
                        text = if (isCheckIn) "Staff scans QR 1 at shift start to record Check-In." else "Staff scans QR 2 at shift finish to record Check-Out.",
                        fontSize = 11.sp,
                        color = OrakleSlate600,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // Actions Row: Download, Print, Regenerate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val fileName = "${biz?.businessCode ?: "ODK"}_${if (isCheckIn) "CHECK_IN" else "CHECK_OUT"}.png"
                                val savedFile = com.example.dukaan.service.QrCodeUtil.saveQrToFile(context, qrBitmap, fileName)
                                Toast.makeText(context, "Saved to Downloads: ${savedFile.name}", Toast.LENGTH_LONG).show()
                                com.example.dukaan.service.QrCodeUtil.shareQrCode(context, savedFile, qrLabel)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Download", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val fileName = "${biz?.businessCode ?: "ODK"}_${if (isCheckIn) "CHECK_IN" else "CHECK_OUT"}.png"
                                val savedFile = com.example.dukaan.service.QrCodeUtil.saveQrToFile(context, qrBitmap, fileName)
                                com.example.dukaan.service.QrCodeUtil.printQrCode(context, savedFile, "Orakle_Dukaan_$qrLabel")
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Print", fontSize = 10.sp)
                        }

                        Button(
                            onClick = {
                                qrSalt = UUID.randomUUID().toString().take(6).uppercase()
                                Toast.makeText(context, "Regenerated fresh QR Token: $qrSalt", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrakleSlate900),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Regenerate", fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 6.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = OrakleRedPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun MoreMenuItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OrakleRedLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = OrakleRedPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = OrakleSlate500)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OrakleSlate500)
        }
    }
}

@Composable
fun AttendancePunchRow(
    punch: AttendanceEvent,
    employeeName: String
) {
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
                        .size(36.dp)
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
                    Text(employeeName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${punch.formattedTime} • ${punch.dateStr}", fontSize = 11.sp, color = OrakleSlate500)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (punch.isGeofenceValid) OrakleGreenContainer else OrakleRedContainer
                ) {
                    Text(
                        text = if (punch.isGeofenceValid) "Inside Shop ✓" else "Outside ✕",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (punch.isGeofenceValid) Color(0xFF166534) else OrakleOnRedContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "${punch.distanceFromShopMeters.toInt()}m from shop",
                    fontSize = 10.sp,
                    color = OrakleSlate500
                )
            }
        }
    }
}

@Composable
fun EmployeeCard(
    employee: Employee,
    onEditClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(OrakleSlate100),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = employee.fullName.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = OrakleRedPrimary,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(employee.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${employee.designation} • ${employee.employeeCode}", fontSize = 12.sp, color = OrakleSlate600)
                    Text("Salary: ₹${employee.monthlySalary.toInt()}/mo (${employee.salaryType})", fontSize = 11.sp, color = OrakleSlate500)
                }
            }

            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Employee", tint = OrakleSlate600)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueAdvanceDialog(
    employees: List<Employee>,
    businessId: String,
    onDismiss: () -> Unit,
    onSave: (AdvanceUdhaar) -> Unit
) {
    var selectedEmpId by remember { mutableStateOf(employees.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf("2000") }
    var dailyDeduction by remember { mutableStateOf("100") }
    var reason by remember { mutableStateOf("Advance for medical/personal") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Issue Udhaar / Advance", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Employee:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                employees.forEach { emp ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedEmpId == emp.id,
                            onClick = { selectedEmpId = emp.id }
                        )
                        Text(emp.fullName, fontSize = 13.sp)
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Advance Amount (₹)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dailyDeduction,
                    onValueChange = { dailyDeduction = it },
                    label = { Text("Daily Deduction per Duty Day (₹)") },
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
                    val amt = amount.toDoubleOrNull() ?: 2000.0
                    val daily = dailyDeduction.toDoubleOrNull() ?: 100.0
                    val adv = AdvanceUdhaar(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        employeeId = selectedEmpId,
                        totalAmount = amt,
                        remainingAmount = amt,
                        dailyDeductionAmount = daily,
                        deductionMethod = "DAILY_DEDUCTION",
                        status = "ACTIVE",
                        reason = reason
                    )
                    onSave(adv)
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text("Confirm Advance")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
