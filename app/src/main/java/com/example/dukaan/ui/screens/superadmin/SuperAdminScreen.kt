package com.example.dukaan.ui.screens.superadmin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaan.data.model.*
import com.example.dukaan.data.repository.DukaanRepository
import com.example.dukaan.service.InAppUpdateDialog
import com.example.dukaan.service.LocalBackupManager
import com.example.dukaan.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminScreen(
    repository: DukaanRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val businesses by repository.getAllBusinesses().collectAsState(initial = emptyList())
    val allEmployees by repository.getAllEmployees().collectAsState(initial = emptyList())
    val agents by repository.getAllAgents().collectAsState(initial = emptyList())
    val commissions by repository.getAllCommissions().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0: Businesses, 1: Subscriptions, 2: Agents, 3: Audit Logs
    var selectedTypeFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember { mutableStateOf("All") }
    var selectedBusinessForControl by remember { mutableStateOf<Business?>(null) }
    var selectedBusinessForStaff by remember { mutableStateOf<Business?>(null) }
    var showOnboardBusinessDialog by remember { mutableStateOf(false) }
    var showAddAgentDialog by remember { mutableStateOf(false) }
    var selectedAgentForEdit by remember { mutableStateOf<Agent?>(null) }
    var showInAppUpdateDialog by remember { mutableStateOf(false) }

    val businessTypes = listOf("All", "Kirana", "Clothing", "Restaurant", "Salon", "Medical", "Tailor", "Workshop", "Office")

    val totalActive = businesses.count { it.status == BusinessStatus.APPROVED || it.status == BusinessStatus.ACTIVE }
    val totalPending = businesses.count { it.status == BusinessStatus.PENDING }
    val totalSuspended = businesses.count { it.status == BusinessStatus.SUSPENDED || it.status == BusinessStatus.REJECTED }
    val mrr = businesses.filter { it.status == BusinessStatus.APPROVED || it.status == BusinessStatus.ACTIVE }
        .sumOf { it.monthlyPrice }

    val filteredBusinesses = businesses.filter {
        val matchesType = selectedTypeFilter == "All" || it.businessType.equals(selectedTypeFilter, ignoreCase = true)
        val matchesStatus = when (selectedStatusFilter) {
            "Pending" -> it.status == BusinessStatus.PENDING
            "Active" -> it.status == BusinessStatus.APPROVED || it.status == BusinessStatus.ACTIVE
            "Disabled" -> it.status == BusinessStatus.SUSPENDED || it.status == BusinessStatus.REJECTED
            else -> true
        }
        matchesType && matchesStatus
    }

    var isSyncing by remember { mutableStateOf(false) }

    // Instant Live Auto-Refresh polling loop (Every 2.5 seconds in foreground)
    LaunchedEffect(Unit) {
        repository.seedDefaultAgentIfEmpty()
        while (true) {
            isSyncing = true
            repository.syncBusinessesFromSupabase()
            repository.syncAllEmployeesFromSupabase()
            repository.syncAgentsFromSupabase()
            isSyncing = false
            kotlinx.coroutines.delay(2500)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showOnboardBusinessDialog = true },
                    containerColor = OrakleRedPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.AddBusiness, contentDescription = "Onboard New Business")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Superadmin Control Center",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "LIVE CLOUD",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Orakle Platform Owner • Supabase Live Sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = OrakleSlate500
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OrakleRedPrimary.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, OrakleRedPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { showInAppUpdateDialog = true }
                            .testTag("superadmin_top_update_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = "Check for App Updates",
                                tint = OrakleRedPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "App Update",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrakleRedPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Navigation Tabs (Single-line Scrollable Tabs to prevent text glitches)
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = OrakleRedPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Shops (${businesses.size})", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Plans & MRR", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Agents (${agents.size})", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Audit Logs", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Business List & Overview
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            // Overview KPIs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatCard(
                                    title = "Active Shops",
                                    value = "$totalActive",
                                    icon = Icons.Default.Storefront,
                                    iconColor = OrakleGreen,
                                    bgColor = OrakleGreenContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Pending Approval",
                                    value = "$totalPending",
                                    icon = Icons.Default.PendingActions,
                                    iconColor = OrakleAmber,
                                    bgColor = OrakleAmberContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            // Status Filter Chips
                            Text(
                                text = "Filter by Account Status:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = OrakleSlate600,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedStatusFilter == "All",
                                        onClick = { selectedStatusFilter = "All" },
                                        label = { Text("All (${businesses.size})", maxLines = 1, softWrap = false) }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedStatusFilter == "Pending",
                                        onClick = { selectedStatusFilter = "Pending" },
                                        label = { Text("Pending (${totalPending})", maxLines = 1, softWrap = false) }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedStatusFilter == "Active",
                                        onClick = { selectedStatusFilter = "Active" },
                                        label = { Text("Active (${totalActive})", maxLines = 1, softWrap = false) }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedStatusFilter == "Disabled",
                                        onClick = { selectedStatusFilter = "Disabled" },
                                        label = { Text("Disabled (${totalSuspended})", maxLines = 1, softWrap = false) }
                                    )
                                }
                            }
                        }

                        item {
                            // Category Filter Chips
                            Text(
                                text = "Filter by Business Type:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = OrakleSlate600,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(businessTypes) { type ->
                                    FilterChip(
                                        selected = selectedTypeFilter == type,
                                        onClick = { selectedTypeFilter = type },
                                        label = { Text(type) }
                                    )
                                }
                            }
                        }

                        items(filteredBusinesses) { biz ->
                            val staffCount = allEmployees.count {
                                it.businessId.equals(biz.id, ignoreCase = true) ||
                                it.businessId.equals(biz.businessCode, ignoreCase = true)
                            }
                            BusinessSuperadminCard(
                                business = biz,
                                staffCount = staffCount,
                                onManageClick = { selectedBusinessForControl = biz },
                                onManageStaffClick = { selectedBusinessForStaff = biz },
                                onToggleStatus = {
                                    coroutineScope.launch {
                                        val newStatus = if (biz.status == BusinessStatus.APPROVED || biz.status == BusinessStatus.ACTIVE) {
                                            BusinessStatus.SUSPENDED
                                        } else {
                                            BusinessStatus.APPROVED
                                        }
                                        repository.updateBusinessStatus(biz.id, newStatus, "Superadmin")
                                        Toast.makeText(context, "${biz.name} is now ${newStatus.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }

                1 -> {
                    // Revenue and Plans
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = OrakleSlate900)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("MONTHLY RECURRING REVENUE (MRR)", color = OrakleSlate500, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("₹${mrr.toInt()}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Column {
                                            Text("Active Subscriptions", color = OrakleSlate500, fontSize = 11.sp)
                                            Text("$totalActive", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column {
                                            Text("Average Revenue/Shop", color = OrakleSlate500, fontSize = 11.sp)
                                            val arpu = if (totalActive > 0) mrr / totalActive else 0.0
                                            Text("₹${arpu.toInt()}", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("STANDARD PRICING PLANS", fontWeight = FontWeight.Bold, color = OrakleSlate700)
                        }

                        item {
                            PlanTierCard(
                                name = "BASIC",
                                price = "₹149 / month",
                                maxEmp = "Max 2 Employees",
                                maxEvents = "4 Punches/day • 2 Fixed Attendances",
                                features = listOf("Live GPS Geofence Attendance", "2 Fixed Daily Attendance Events", "Max 4 Punches/Day", "PDF/CSV Reports")
                            )
                        }
                        item {
                            PlanTierCard(
                                name = "PLUS (Most Popular)",
                                price = "₹249 / month",
                                maxEmp = "Max 5 Employees",
                                maxEvents = "6 Punches/day • 2 Fixed Attendances",
                                features = listOf("All Basic features", "Max 6 Punches/Day", "Udhaar/Advance Daily Deduction Ledger", "Two QR Gate Passes (IN & OUT)")
                            )
                        }
                        item {
                            PlanTierCard(
                                name = "BUSINESS",
                                price = "₹499 / month",
                                maxEmp = "Max 10 Employees",
                                maxEvents = "8 Punches/day • 2 Fixed Attendances",
                                features = listOf("Full Business Features", "Max 8 Punches/Day", "Priority WhatsApp Integration", "Custom Working Schedules")
                            )
                        }
                        item {
                            PlanTierCard(
                                name = "ENTERPRISE",
                                price = "Customizable / month",
                                maxEmp = "Custom Staff Limit",
                                maxEvents = "Custom Punches/day",
                                features = listOf("Superadmin Custom Override", "Custom Employee & Punch Limits", "Dedicated Account Manager", "Multi-Shop Central Billing")
                            )
                        }
                    }
                }

                2 -> {
                    // TAB 2: AGENTS & REFERRAL COMMISSION SYSTEM (Requirement 24-28)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Field Agents",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showAddAgentDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Agent", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        item {
                            val totalCommPaid = commissions.filter { it.status == "PAID" }.sumOf { it.commissionAmount }
                            val totalCommPending = commissions.filter { it.status == "PENDING" }.sumOf { it.commissionAmount }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatCard(
                                    title = "Active Agents",
                                    value = "${agents.size}",
                                    icon = Icons.Default.SupportAgent,
                                    iconColor = OrakleSlate900,
                                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Pending Comm.",
                                    value = "₹${totalCommPending.toInt()}",
                                    icon = Icons.Default.Pending,
                                    iconColor = OrakleAmber,
                                    bgColor = OrakleAmberContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Paid Comm.",
                                    value = "₹${totalCommPaid.toInt()}",
                                    icon = Icons.Default.CheckCircle,
                                    iconColor = OrakleGreen,
                                    bgColor = OrakleGreenContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Text("REGISTERED AGENTS (Default 20% Commission)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate600)
                        }

                        if (agents.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Text("No agents created yet. Tap '+ Add Agent' to generate a referral agent.", color = OrakleSlate500, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            items(agents) { agent ->
                                val referredCount = businesses.count { it.agentCode.equals(agent.agentCode, ignoreCase = true) }
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(agent.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("Code: ${agent.agentCode} • ${agent.phone}", fontSize = 11.sp, color = OrakleSlate500)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (agent.status == "ACTIVE") OrakleGreenContainer else OrakleAmberContainer
                                            ) {
                                                Text(
                                                    text = "${agent.commissionPercent.toInt()}% Comm • ${agent.status}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (agent.status == "ACTIVE") Color(0xFF166534) else Color(0xFF92400E),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Divider(color = OrakleSlate200)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Shops Referred: $referredCount • Total Earned: ₹${agent.earnings.toInt()}", fontSize = 11.sp, color = OrakleSlate600)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { selectedAgentForEdit = agent },
                                                    modifier = Modifier.size(32.dp).testTag("edit_agent_${agent.id}")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit Agent", tint = OrakleSlate700, modifier = Modifier.size(16.dp))
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val newStatus = if (agent.status == "ACTIVE") "SUSPENDED" else "ACTIVE"
                                                            repository.updateAgent(agent.copy(status = newStatus))
                                                            Toast.makeText(context, "${agent.name} is now $newStatus", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(if (agent.status == "ACTIVE") "Suspend" else "Activate", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("COMMISSION LEDGER (Auto-Generated on Payment)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate600)
                        }

                        if (commissions.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                        Text("No commission records yet. Commission is generated when a referred shop's payment is confirmed.", color = OrakleSlate500, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            items(commissions) { comm ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(comm.shopName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Sub: ₹${comm.subscriptionAmount.toInt()} • Comm (${comm.commissionPercent.toInt()}%): ₹${comm.commissionAmount.toInt()}", fontSize = 11.sp, color = OrakleSlate600)
                                            Text("Date: ${comm.paymentDate}", fontSize = 10.sp, color = OrakleSlate500)
                                        }

                                        if (comm.status == "PENDING") {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.markCommissionPaid(comm.id)
                                                        Toast.makeText(context, "Commission marked as PAID", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = OrakleGreen),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Mark Paid", fontSize = 11.sp)
                                            }
                                        } else {
                                            Surface(shape = RoundedCornerShape(6.dp), color = OrakleGreenContainer) {
                                                Text("PAID ✓", color = Color(0xFF166534), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // TAB 3: Audit Logs & Local Backups
                    val auditLogs by repository.getAuditLogs("").collectAsState(initial = emptyList())
                    var lastBackupTime by remember { mutableStateOf(LocalBackupManager.getLastBackupTimeFormatted(context)) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth().testTag("superadmin_backup_card")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Backup, contentDescription = null, tint = OrakleRedPrimary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Local Daily Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFDCFCE7)
                                        ) {
                                            Text(
                                                "AUTO DAILY ON",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF15803D),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Last Backup: $lastBackupTime",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OrakleSlate700
                                    )
                                    Text(
                                        text = "Room database ka snapshot local device me surakshit save hota hai (Daily automatic).",
                                        fontSize = 11.sp,
                                        color = OrakleSlate500
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val res = LocalBackupManager.performManualBackup(context, repository)
                                                    if (res.isSuccess) {
                                                        lastBackupTime = LocalBackupManager.getLastBackupTimeFormatted(context)
                                                        Toast.makeText(context, "Backup saved: ${res.getOrNull()?.name}", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Backup failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Backup Now (Export)", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("SECURITY & SYSTEM AUDIT LOGS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate700)
                        }

                        items(auditLogs) { log ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(OrakleSlate100),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = OrakleRedPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(log.action, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(log.details, fontSize = 12.sp, color = OrakleSlate600)
                                        Text("By ${log.performedBy}", fontSize = 11.sp, color = OrakleSlate500)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Business Edit & Policy Override Dialog
    selectedBusinessForControl?.let { biz ->
        EditBusinessDialog(
            business = biz,
            isSuperAdmin = true,
            onDismiss = { selectedBusinessForControl = null },
            onSave = { updated ->
                coroutineScope.launch {
                    repository.updateBusiness(updated, "Superadmin")
                    selectedBusinessForControl = null
                    Toast.makeText(context, "Business & policy updated", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteBusiness(biz.id, "Superadmin")
                    repository.syncBusinessesFromSupabase()
                    repository.syncAllEmployeesFromSupabase()
                    selectedBusinessForControl = null
                    Toast.makeText(context, "Shop and all staff deleted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Business Staff Management Dialog
    selectedBusinessForStaff?.let { biz: Business ->
        SuperAdminStaffDialog(
            business = biz,
            repository = repository,
            onDismiss = { selectedBusinessForStaff = null }
        )
    }

    // Onboard New Business Dialog
    if (showOnboardBusinessDialog) {
        val blankBusiness = Business(
            id = UUID.randomUUID().toString(),
            businessCode = "ODK-${(1000..9999).random()}",
            name = "",
            ownerName = "",
            phone = "",
            email = "",
            businessType = "Kirana",
            address = "",
            city = "Jaipur",
            state = "Rajasthan",
            pincode = "302001",
            latitude = 26.9124,
            longitude = 75.7873
        )
        EditBusinessDialog(
            business = blankBusiness,
            isSuperAdmin = true,
            onDismiss = { showOnboardBusinessDialog = false },
            onSave = { newBiz ->
                coroutineScope.launch {
                    repository.registerBusiness(newBiz)
                    showOnboardBusinessDialog = false
                    Toast.makeText(context, "Onboarded ${newBiz.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showAddAgentDialog) {
        AddEditAgentDialog(
            agent = null,
            onDismiss = { showAddAgentDialog = false },
            onSave = { name, phone, email, pass, comm, code ->
                coroutineScope.launch {
                    val created = repository.createAgent(
                        name = name,
                        phone = phone,
                        email = email,
                        password = pass,
                        commissionPercent = comm,
                        customCode = code
                    )
                    showAddAgentDialog = false
                    Toast.makeText(context, "Created Agent ${created.name} (${created.agentCode})", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    selectedAgentForEdit?.let { agentToEdit ->
        AddEditAgentDialog(
            agent = agentToEdit,
            onDismiss = { selectedAgentForEdit = null },
            onSave = { name, phone, email, pass, comm, code ->
                coroutineScope.launch {
                    repository.updateAgent(
                        agentToEdit.copy(
                            name = name,
                            phone = phone,
                            email = email,
                            password = pass,
                            commissionPercent = comm,
                            agentCode = code
                        )
                    )
                    selectedAgentForEdit = null
                    Toast.makeText(context, "Updated Agent $name", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteAgent(agentToEdit.id)
                    selectedAgentForEdit = null
                    Toast.makeText(context, "Deleted Agent ${agentToEdit.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showInAppUpdateDialog) {
        InAppUpdateDialog(
            onDismiss = { showInAppUpdateDialog = false }
        )
    }
}

@Composable
fun BusinessSuperadminCard(
    business: Business,
    staffCount: Int = 0,
    onManageClick: () -> Unit,
    onManageStaffClick: () -> Unit,
    onToggleStatus: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = business.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${business.businessCode} • ${business.businessType} • ${business.city}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OrakleSlate500,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                val (badgeBg, badgeTextColor) = when (business.status) {
                    BusinessStatus.APPROVED, BusinessStatus.ACTIVE -> Pair(OrakleGreenContainer, Color(0xFF166534))
                    BusinessStatus.PENDING -> Pair(OrakleAmberContainer, Color(0xFF92400E))
                    else -> Pair(OrakleRedContainer, OrakleOnRedContainer)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = business.status.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = OrakleSlate200)
            Spacer(modifier = Modifier.height(10.dp))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Owner: ${business.ownerName} (${business.phone}) • ${business.email}",
                    fontSize = 12.sp,
                    color = OrakleSlate600,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "Staff: $staffCount / ${business.employeeLimit} registered • Plan: ${business.plan} (₹${business.monthlyPrice.toInt()}/mo) • Shift: ${business.shiftStart} - ${business.shiftEnd}",
                    fontSize = 11.sp,
                    color = OrakleSlate500,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // One-click Activate / Disable toggle button
                if (business.status == BusinessStatus.APPROVED || business.status == BusinessStatus.ACTIVE) {
                    Button(
                        onClick = onToggleStatus,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleAmber),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).testTag("disable_biz_${business.businessCode}")
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Disable", fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                } else {
                    Button(
                        onClick = onToggleStatus,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleGreen),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).testTag("activate_biz_${business.businessCode}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (business.status == BusinessStatus.PENDING) "Approve" else "Activate", fontSize = 10.sp, maxLines = 1, softWrap = false)
                    }
                }

                OutlinedButton(
                    onClick = onManageStaffClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).testTag("staff_biz_${business.businessCode}")
                ) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Staff", fontSize = 11.sp, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = onManageClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleSlate900),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).testTag("manage_biz_${business.businessCode}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Edit", fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
fun PlanTierCard(
    name: String,
    price: String,
    maxEmp: String,
    maxEvents: String,
    features: List<String>
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, fontWeight = FontWeight.Bold, color = OrakleRedPrimary, fontSize = 16.sp)
                Text(price, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text("$maxEmp • $maxEvents", fontSize = 12.sp, color = OrakleSlate600)
            Spacer(modifier = Modifier.height(8.dp))
            features.forEach { feat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = OrakleGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(feat, fontSize = 12.sp, color = OrakleSlate700)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminBusinessControlDialog(
    business: Business,
    onDismiss: () -> Unit,
    onUpdateStatus: (BusinessStatus) -> Unit,
    onUpdatePlan: (String, Double, Int, Int) -> Unit
) {
    var editPlan by remember { mutableStateOf(business.plan) }
    var editPrice by remember { mutableStateOf(business.monthlyPrice.toInt().toString()) }
    var editEmpLimit by remember { mutableStateOf(business.employeeLimit.toString()) }
    var editEventLimit by remember { mutableStateOf(business.dailyEventLimitPerEmployee.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Manage ${business.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Code: ${business.businessCode} • Owner: ${business.ownerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrakleSlate500
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ACCOUNT STATUS ACTION:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate600)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onUpdateStatus(BusinessStatus.APPROVED) },
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Approve", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onUpdateStatus(BusinessStatus.SUSPENDED) },
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleAmber),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Suspend", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onUpdateStatus(BusinessStatus.REJECTED) },
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Divider()

                Text("CUSTOM PRICING & LIMIT OVERRIDE:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate600)

                OutlinedTextField(
                    value = editPlan,
                    onValueChange = { editPlan = it },
                    label = { Text("Plan (BASIC / PLUS / BUSINESS / CUSTOM)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text("Monthly Price (₹)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = editEmpLimit,
                        onValueChange = { editEmpLimit = it },
                        label = { Text("Staff Limit") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = editEventLimit,
                    onValueChange = { editEventLimit = it },
                    label = { Text("Daily Attendance Punches/Staff") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = editPrice.toDoubleOrNull() ?: 149.0
                    val e = editEmpLimit.toIntOrNull() ?: 10
                    val d = editEventLimit.toIntOrNull() ?: 6
                    onUpdatePlan(editPlan, p, e, d)
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleSlate900)
            ) {
                Text("Save Plan & Limits")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminStaffDialog(
    business: Business,
    repository: DukaanRepository,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val employees by repository.getEmployeesForBusiness(business.id).collectAsState(initial = emptyList())
    var selectedEmployeeForEdit by remember { mutableStateOf<Employee?>(null) }
    var showAddEmployeeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(business.id) {
        while (true) {
            repository.syncEmployeesAndAttendanceFromSupabase(business.id)
            kotlinx.coroutines.delay(2000)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Manage Staff", fontWeight = FontWeight.Bold)
                    Text("${business.name} (${business.businessCode})", fontSize = 12.sp, color = OrakleSlate500)
                }
                IconButton(
                    onClick = { showAddEmployeeDialog = true },
                    modifier = Modifier.testTag("superadmin_add_staff_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Staff", tint = OrakleRedPrimary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Text(
                    text = "Staff Count: ${employees.size} / ${business.employeeLimit} allowed",
                    fontSize = 12.sp,
                    color = OrakleSlate600,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (employees.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No staff registered for this shop yet.", color = OrakleSlate500, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(employees) { emp ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = OrakleSlate50),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(emp.fullName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("${emp.designation} • ${emp.phone}", fontSize = 11.sp, color = OrakleSlate600)
                                        Text("₹${emp.monthlySalary.toInt()}/mo • Status: ${emp.status}", fontSize = 11.sp, color = OrakleSlate500)
                                    }
                                    IconButton(
                                        onClick = { selectedEmployeeForEdit = emp }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Staff Details", tint = OrakleSlate700)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = OrakleSlate900)
            ) {
                Text("Close")
            }
        }
    )

    if (showAddEmployeeDialog) {
        AddEditEmployeeDialog(
            businessId = business.id,
            employeeToEdit = null,
            onDismiss = { showAddEmployeeDialog = false },
            onSave = { newEmp ->
                coroutineScope.launch {
                    repository.saveEmployee(newEmp, isEdit = false)
                    showAddEmployeeDialog = false
                    Toast.makeText(context, "Added ${newEmp.fullName}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    selectedEmployeeForEdit?.let { emp ->
        AddEditEmployeeDialog(
            businessId = business.id,
            employeeToEdit = emp,
            onDismiss = { selectedEmployeeForEdit = null },
            onSave = { updatedEmp ->
                coroutineScope.launch {
                    repository.saveEmployee(updatedEmp, isEdit = true)
                    selectedEmployeeForEdit = null
                    Toast.makeText(context, "Updated ${updatedEmp.fullName}", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteEmployee(emp.id, business.id)
                    selectedEmployeeForEdit = null
                    Toast.makeText(context, "Staff member deleted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun AddEditAgentDialog(
    agent: Agent?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, email: String, pass: String, comm: Double, code: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(agent?.name.orEmpty()) }
    var phone by remember { mutableStateOf(agent?.phone.orEmpty()) }
    var email by remember { mutableStateOf(agent?.email.orEmpty()) }
    var password by remember { mutableStateOf(agent?.password ?: "123456") }
    var commissionPercent by remember { mutableStateOf((agent?.commissionPercent ?: 20.0).toInt().toString()) }
    var agentCode by remember { mutableStateOf(agent?.agentCode.orEmpty()) }
    var errorText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (agent == null) "Add Field Agent / Partner" else "Edit Agent Details",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorText.isNotBlank()) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Agent Full Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("add_agent_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (10 digits) *") },
                    modifier = Modifier.fillMaxWidth().testTag("add_agent_phone_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address (Optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_agent_email_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Login Password *") },
                    modifier = Modifier.fillMaxWidth().testTag("add_agent_password_input")
                )
                OutlinedTextField(
                    value = commissionPercent,
                    onValueChange = { commissionPercent = it },
                    label = { Text("Commission Percentage (%)") },
                    placeholder = { Text("e.g. 20") },
                    modifier = Modifier.fillMaxWidth().testTag("add_agent_commission_input")
                )
                OutlinedTextField(
                    value = agentCode,
                    onValueChange = { agentCode = it.uppercase() },
                    label = { Text("Referral Code (Auto-generated if empty)") },
                    placeholder = { Text("e.g. AGT-1024") },
                    modifier = Modifier.fillMaxWidth().testTag("add_agent_code_input")
                )

                if (agent != null && onDelete != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrakleRedPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Agent Account", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank()) {
                        errorText = "Please enter Agent Name and Phone number"
                        return@Button
                    }
                    val commVal = commissionPercent.toDoubleOrNull() ?: 20.0
                    val finalCode = agentCode.ifBlank { agent?.agentCode ?: "AGT-${(1000..9999).random()}" }
                    onSave(name.trim(), phone.trim(), email.trim(), password.trim(), commVal, finalCode.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                modifier = Modifier.testTag("save_agent_submit_button")
            ) {
                Text(if (agent == null) "Create Agent" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Agent?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${agent?.name}? Their referred shops will remain in the system.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
