package com.example.dukaan.ui.screens.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dukaan.data.model.Agent
import com.example.dukaan.data.model.Business
import com.example.dukaan.data.model.BusinessStatus
import com.example.dukaan.data.model.CommissionRecord
import com.example.dukaan.data.repository.DukaanRepository
import com.example.dukaan.service.QrCodeUtil
import com.example.dukaan.ui.components.StatCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentMainScreen(
    repository: DukaanRepository,
    agentId: String,
    onRegisterShop: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allAgents by repository.getAllAgents().collectAsState(initial = emptyList())
    val agent = allAgents.find { it.id == agentId }
    val allBusinesses by repository.getAllBusinesses().collectAsState(initial = emptyList())
    val commissions by repository.getCommissionsForAgent(agentId).collectAsState(initial = emptyList())

    val referredShops = remember(allBusinesses, agent) {
        if (agent == null || agent.agentCode.isBlank()) emptyList()
        else allBusinesses.filter { it.agentCode.equals(agent.agentCode, ignoreCase = true) }
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Overview & QR, 1: Referred Shops, 2: Commissions, 3: Profile & Payout
    var showQrDialog by remember { mutableStateOf(false) }
    var shopSearchQuery by remember { mutableStateOf("") }
    var commissionFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, PAID

    BackHandler {
        if (selectedTab != 0) {
            selectedTab = 0
        } else {
            onLogout()
        }
    }

    val totalEarnings = agent?.earnings ?: 0.0
    val totalPendingCommission = commissions.filter { it.status == "PENDING" }.sumOf { it.commissionAmount }
    val totalPaidCommission = commissions.filter { it.status == "PAID" }.sumOf { it.commissionAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = agent?.name ?: "Field Partner",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "PARTNER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Code: ${agent?.agentCode ?: "---"} • ${agent?.commissionPercent?.toInt() ?: 20}% Comm",
                            fontSize = 12.sp,
                            color = OrakleSlate500
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.syncBusinessesFromSupabase()
                                Toast.makeText(context, "Shops refreshed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("agent_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data", tint = OrakleSlate700)
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.testTag("agent_logout_button")
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = OrakleRedPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag("agent_nav_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (referredShops.isNotEmpty()) {
                                    Badge { Text("${referredShops.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Store, contentDescription = "Shops")
                        }
                    },
                    label = { Text("Shops", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag("agent_nav_shops")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Commissions") },
                    label = { Text("Commissions", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag("agent_nav_commissions")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Payouts") },
                    label = { Text("Payouts", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag("agent_nav_payouts")
                )
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> AgentDashboardTab(
                    agent = agent,
                    referredCount = referredShops.size,
                    totalEarnings = totalEarnings,
                    pendingCommission = totalPendingCommission,
                    paidCommission = totalPaidCommission,
                    onOpenQrDialog = { showQrDialog = true },
                    onRegisterShopClick = {
                        onRegisterShop(agent?.agentCode.orEmpty())
                    },
                    onViewAllShops = { selectedTab = 1 },
                    onViewCommissions = { selectedTab = 2 }
                )

                1 -> AgentReferredShopsTab(
                    shops = referredShops,
                    searchQuery = shopSearchQuery,
                    onSearchQueryChange = { shopSearchQuery = it },
                    onOnboardNew = { onRegisterShop(agent?.agentCode.orEmpty()) }
                )

                2 -> AgentCommissionsTab(
                    commissions = commissions,
                    selectedFilter = commissionFilter,
                    onFilterChange = { commissionFilter = it }
                )

                3 -> AgentProfilePayoutTab(
                    agent = agent,
                    totalEarnings = totalEarnings,
                    pendingEarnings = totalPendingCommission,
                    onLogout = onLogout
                )
            }
        }
    }

    if (showQrDialog && agent != null) {
        AgentQrDialog(
            agent = agent,
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
fun AgentDashboardTab(
    agent: Agent?,
    referredCount: Int,
    totalEarnings: Double,
    pendingCommission: Double,
    paidCommission: Double,
    onOpenQrDialog: () -> Unit,
    onRegisterShopClick: () -> Unit,
    onViewAllShops: () -> Unit,
    onViewCommissions: () -> Unit
) {
    val context = LocalContext.current
    val agentCode = agent?.agentCode ?: ""

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Referral Banner
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Your Referral Code",
                                fontSize = 12.sp,
                                color = OrakleSlate500,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = agentCode.ifBlank { "NOT GENERATED" },
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0284C7)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (agentCode.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Agent Code", agentCode))
                                        Toast.makeText(context, "Referral Code '$agentCode' Copied!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("agent_copy_code_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code")
                            }

                            FilledTonalIconButton(
                                onClick = onOpenQrDialog,
                                modifier = Modifier.testTag("agent_show_qr_button")
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = "Show QR Code")
                            }
                        }
                    }

                    Text(
                        text = "Share this code with local retailers. When they register and activate their subscription, you earn ${agent?.commissionPercent?.toInt() ?: 20}% monthly commission.",
                        fontSize = 12.sp,
                        color = OrakleSlate600,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (agentCode.isNotBlank()) {
                                    val shareText = "Namaste! Setup your Dukaan staff attendance & payroll app in 2 minutes. Register your shop on Dukaan App with my referral code: $agentCode to get instant verification & free onboarding assistance!"
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Referral Invite")
                                    context.startActivity(shareIntent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrakleGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("agent_share_invite_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onRegisterShopClick,
                            colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("agent_onboard_shop_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Onboard Shop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Commission & Performance Metrics
        item {
            Text(
                text = "PERFORMANCE OVERVIEW",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = OrakleSlate600
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Total Earned",
                    value = "₹${totalEarnings.toInt()}",
                    icon = Icons.Default.Payments,
                    iconColor = OrakleGreen,
                    bgColor = OrakleGreenContainer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Pending Comm.",
                    value = "₹${pendingCommission.toInt()}",
                    icon = Icons.Default.HourglassEmpty,
                    iconColor = OrakleAmber,
                    bgColor = OrakleAmberContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Referred Shops",
                    value = "$referredCount",
                    icon = Icons.Default.Store,
                    iconColor = Color(0xFF0284C7),
                    bgColor = Color(0xFFE0F2FE),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Paid Out",
                    value = "₹${paidCommission.toInt()}",
                    icon = Icons.Default.CheckCircle,
                    iconColor = Color(0xFF166534),
                    bgColor = OrakleGreenContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Navigation Cards
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewAllShops() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("View Referred Shops", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("$referredCount businesses connected", fontSize = 11.sp, color = OrakleSlate500)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OrakleSlate400)
                    }

                    Divider(color = OrakleSlate200)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewCommissions() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = OrakleGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Commission Ledger", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Track pending & approved payouts", fontSize = 11.sp, color = OrakleSlate500)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OrakleSlate400)
                    }
                }
            }
        }
    }
}

@Composable
fun AgentReferredShopsTab(
    shops: List<Business>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOnboardNew: () -> Unit
) {
    val filtered = remember(shops, searchQuery) {
        if (searchQuery.isBlank()) shops
        else shops.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.ownerName.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true) ||
                    it.businessCode.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("REFERRED SHOPS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${shops.size} Total Retailers", fontSize = 11.sp, color = OrakleSlate500)
            }
            Button(
                onClick = onOnboardNew,
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Shop", fontSize = 12.sp)
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search shop, owner, phone...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OrakleSlate400) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = OrakleSlate400, modifier = Modifier.size(48.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "No shops referred yet." else "No matching shops found.",
                        color = OrakleSlate500,
                        fontSize = 13.sp
                    )
                    if (searchQuery.isBlank()) {
                        Button(
                            onClick = onOnboardNew,
                            colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Onboard Your First Shop", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered) { shop ->
                    ReferredShopCard(shop = shop)
                }
            }
        }
    }
}

@Composable
fun ReferredShopCard(shop: Business) {
    val context = LocalContext.current
    val (statusBg, statusText) = when (shop.status) {
        BusinessStatus.APPROVED, BusinessStatus.ACTIVE -> Pair(OrakleGreenContainer, Color(0xFF166534))
        BusinessStatus.PENDING -> Pair(OrakleAmberContainer, Color(0xFF92400E))
        else -> Pair(OrakleRedContainer, OrakleOnRedContainer)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(shop.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${shop.businessCode} • ${shop.businessType} • ${shop.city}", fontSize = 11.sp, color = OrakleSlate500)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                    Text(
                        text = shop.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusText,
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
                Column {
                    Text("Owner: ${shop.ownerName}", fontSize = 12.sp, color = OrakleSlate700)
                    Text("Plan: ${shop.plan} (₹${shop.monthlyPrice.toInt()}/mo)", fontSize = 11.sp, color = OrakleSlate600)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (shop.phone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${shop.phone}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentCommissionsTab(
    commissions: List<CommissionRecord>,
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    val filtered = remember(commissions, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> commissions.filter { it.status == "PENDING" }
            "PAID" -> commissions.filter { it.status == "PAID" }
            else -> commissions
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("COMMISSION LEDGER", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { onFilterChange("ALL") },
                label = { Text("All (${commissions.size})") }
            )
            FilterChip(
                selected = selectedFilter == "PENDING",
                onClick = { onFilterChange("PENDING") },
                label = { Text("Pending (${commissions.count { it.status == "PENDING" }})") }
            )
            FilterChip(
                selected = selectedFilter == "PAID",
                onClick = { onFilterChange("PAID") },
                label = { Text("Paid (${commissions.count { it.status == "PAID" }})") }
            )
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = OrakleSlate400, modifier = Modifier.size(48.dp))
                    Text(
                        text = "No commission records in this category.",
                        color = OrakleSlate500,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Commissions appear here once your referred shops activate their plans.",
                        color = OrakleSlate400,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered) { comm ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(comm.shopName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = "Plan: ₹${comm.subscriptionAmount.toInt()} • Commission (${comm.commissionPercent.toInt()}%): ₹${comm.commissionAmount.toInt()}",
                                    fontSize = 12.sp,
                                    color = OrakleSlate600
                                )
                                Text("Date: ${comm.paymentDate}", fontSize = 11.sp, color = OrakleSlate400)
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (comm.status == "PAID") OrakleGreenContainer else OrakleAmberContainer
                            ) {
                                Text(
                                    text = if (comm.status == "PAID") "PAID ✓" else "PENDING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (comm.status == "PAID") Color(0xFF166534) else Color(0xFF92400E),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentProfilePayoutTab(
    agent: Agent?,
    totalEarnings: Double,
    pendingEarnings: Double,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("agent_payout_prefs", Context.MODE_PRIVATE) }
    var upiId by remember { mutableStateOf(prefs.getString("agent_upi_${agent?.id}", "") ?: "") }
    var bankAccount by remember { mutableStateOf(prefs.getString("agent_bank_${agent?.id}", "") ?: "") }
    var ifscCode by remember { mutableStateOf(prefs.getString("agent_ifsc_${agent?.id}", "") ?: "") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Agent Profile Details
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color(0xFF0284C7).copy(alpha = 0.2f), modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0284C7))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(agent?.name ?: "Field Partner", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Referral Code: ${agent?.agentCode ?: "---"}", fontSize = 12.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = OrakleSlate200)

                    Text("Phone: ${agent?.phone ?: "---"}", fontSize = 12.sp, color = OrakleSlate700)
                    Text("Email: ${agent?.email?.ifBlank { "Not provided" } ?: "---"}", fontSize = 12.sp, color = OrakleSlate700)
                    Text("Commission Rate: ${agent?.commissionPercent?.toInt() ?: 20}% per shop subscription", fontSize = 12.sp, color = OrakleSlate700)
                    Text("Account Status: ${agent?.status ?: "ACTIVE"}", fontSize = 12.sp, color = if (agent?.status == "ACTIVE") OrakleGreen else OrakleAmber, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Payout Account Settings
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("BANK & UPI PAYOUT DETAILS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OrakleSlate800)
                    Text(
                        text = "Superadmin transfers approved commissions directly to this UPI ID or Bank Account.",
                        fontSize = 11.sp,
                        color = OrakleSlate500
                    )

                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it.trim() },
                        label = { Text("UPI ID (e.g. mobile@upi / agent@okaxis)") },
                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = OrakleGreen) },
                        modifier = Modifier.fillMaxWidth().testTag("agent_payout_upi_input")
                    )

                    OutlinedTextField(
                        value = bankAccount,
                        onValueChange = { bankAccount = it.trim() },
                        label = { Text("Bank Account Number (Optional)") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = OrakleSlate600) },
                        modifier = Modifier.fillMaxWidth().testTag("agent_payout_bank_input")
                    )

                    OutlinedTextField(
                        value = ifscCode,
                        onValueChange = { ifscCode = it.trim().uppercase() },
                        label = { Text("IFSC Code") },
                        modifier = Modifier.fillMaxWidth().testTag("agent_payout_ifsc_input")
                    )

                    Button(
                        onClick = {
                            prefs.edit()
                                .putString("agent_upi_${agent?.id}", upiId)
                                .putString("agent_bank_${agent?.id}", bankAccount)
                                .putString("agent_ifsc_${agent?.id}", ifscCode)
                                .apply()
                            Toast.makeText(context, "Payout details saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleSlate900),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("agent_save_payout_button")
                    ) {
                        Text("Save Payout Details")
                    }
                }
            }
        }

        // Help & Support
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Partner Helpdesk", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate800)
                    Text("For payout issues or higher commission tier requests, email Superadmin at hypersmile100@gmail.com", fontSize = 11.sp, color = OrakleSlate600)
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:hypersmile100@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Dukaan Field Agent Payout Query (${agent?.agentCode})")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Contact: hypersmile100@gmail.com", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Email Superadmin Helpdesk", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("agent_profile_logout_button")
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out from Partner Account", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AgentQrDialog(
    agent: Agent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrPayload = "DUKAAN_AGENT:${agent.agentCode}"
    val qrBitmap = remember(agent.agentCode) {
        QrCodeUtil.generateQrBitmap(
            payload = qrPayload,
            shopName = agent.name,
            typeLabel = "PARTNER REFERRAL",
            shopCode = agent.agentCode,
            width = 500,
            height = 500
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Agent Referral QR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ask retailer to scan this QR or enter code at registration:",
                    fontSize = 12.sp,
                    color = OrakleSlate600,
                    textAlign = TextAlign.Center
                )

                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Agent QR Code",
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.1f)
                ) {
                    Text(
                        text = agent.agentCode,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color(0xFF0284C7),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleSlate900),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}
