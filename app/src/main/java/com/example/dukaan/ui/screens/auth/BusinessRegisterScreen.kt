package com.example.dukaan.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaan.data.model.Business
import com.example.dukaan.data.repository.DukaanRepository
import com.example.dukaan.ui.components.OrakleLogoBrand
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessRegisterScreen(
    repository: DukaanRepository,
    onBackToLogin: () -> Unit,
    onRegistered: (Business) -> Unit,
    initialAgentCode: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("Kirana") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Jaipur") }
    var state by remember { mutableStateOf("Rajasthan") }
    var pincode by remember { mutableStateOf("302001") }
    var geofenceRadius by remember { mutableStateOf("100") }
    var selectedPlan by remember { mutableStateOf("BASIC") }
    var agentCode by remember { mutableStateOf(initialAgentCode) }
    var workingDays by remember { mutableStateOf("ALL_7_DAYS") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showPendingApprovalDialog by remember { mutableStateOf<Business?>(null) }

    val businessTypes = listOf(
        "Kirana", "Clothing", "Restaurant", "Salon", "Medical",
        "Tailor", "Bakery", "Mobile Shop", "Hardware", "Workshop", "Small Office", "Other"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Your Shop", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToLogin) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OrakleLogoBrand()
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Start managing your staff attendance in under 5 minutes.",
                    fontSize = 13.sp,
                    color = OrakleSlate500
                )
            }

            item {
                Text("BUSINESS & OWNER DETAILS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate700)
            }

            item {
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Shop / Business Name *") },
                    placeholder = { Text("e.g. Verma Medical Store") },
                    modifier = Modifier.fillMaxWidth().testTag("reg_biz_name")
                )
            }

            item {
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Owner Full Name *") },
                    placeholder = { Text("e.g. Vijay Verma") },
                    modifier = Modifier.fillMaxWidth().testTag("reg_owner_name")
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number *") },
                        placeholder = { Text("10 digit mobile") },
                        modifier = Modifier.weight(1f).testTag("reg_phone")
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email *") },
                        placeholder = { Text("owner@gmail.com") },
                        modifier = Modifier.weight(1f).testTag("reg_email")
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Create Login Password *") },
                    placeholder = { Text("Minimum 6 characters") },
                    modifier = Modifier.fillMaxWidth().testTag("reg_password")
                )
            }

            item {
                OutlinedTextField(
                    value = agentCode,
                    onValueChange = { agentCode = it.uppercase() },
                    label = { Text("Agent / Referral Code (Optional)") },
                    placeholder = { Text("e.g. AGT-1024") },
                    trailingIcon = {
                        if (agentCode.isNotBlank()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Referral code applied", tint = OrakleGreen)
                        }
                    },
                    supportingText = {
                        if (agentCode.isNotBlank()) {
                            Text("Referral code active • Onboarded via Field Partner", color = OrakleGreen, fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("reg_agent_code")
                )
            }

            item {
                Text("BUSINESS CATEGORY & SCHEDULE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                OptInDropdown(
                    options = businessTypes,
                    selected = businessType,
                    onSelected = { businessType = it }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Weekly Working Schedule:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = workingDays == "ALL_7_DAYS",
                        onClick = { workingDays = "ALL_7_DAYS" },
                        label = { Text("All 7 Days (Retail/Kirana)") }
                    )
                    FilterChip(
                        selected = workingDays == "MON_TO_SAT",
                        onClick = { workingDays = "MON_TO_SAT" },
                        label = { Text("Mon-Sat (Office)") }
                    )
                }
            }

            item {
                Text("LOCATION & GEOFENCE RADIUS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate700)
            }

            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Shop Street Address *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pincode,
                        onValueChange = { pincode = it },
                        label = { Text("Pincode") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text("Attendance Radius (Default: 100m):", fontSize = 12.sp, color = OrakleSlate600)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("50", "100", "200").forEach { r ->
                        FilterChip(
                            selected = geofenceRadius == r,
                            onClick = { geofenceRadius = r },
                            label = { Text("${r}m") }
                        )
                    }
                }
            }

            item {
                Text("SELECT SUBSCRIPTION PLAN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlanSelectRow(
                        name = "BASIC (₹149/mo)",
                        details = "Up to 2 staff • 4 punches/day • 2 fixed daily attendances",
                        isSelected = selectedPlan == "BASIC",
                        onSelect = { selectedPlan = "BASIC" }
                    )
                    PlanSelectRow(
                        name = "PLUS (₹249/mo) ★ Recommended",
                        details = "Up to 5 staff • 6 punches/day • 2 fixed daily attendances • Udhaar Ledger",
                        isSelected = selectedPlan == "PLUS",
                        onSelect = { selectedPlan = "PLUS" }
                    )
                    PlanSelectRow(
                        name = "BUSINESS (₹499/mo)",
                        details = "Up to 10 staff • 8 punches/day • 2 fixed daily attendances • Priority",
                        isSelected = selectedPlan == "BUSINESS",
                        onSelect = { selectedPlan = "BUSINESS" }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (businessName.isBlank() || ownerName.isBlank() || phone.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please fill required fields (Shop Name, Owner Name, Phone, Password)", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        coroutineScope.launch {
                            val biz = repository.registerBusiness(
                                name = businessName.trim(),
                                ownerName = ownerName.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                password = password.trim(),
                                businessType = businessType,
                                address = address.ifEmpty { "Main Market" },
                                city = city,
                                state = state,
                                pincode = pincode,
                                lat = 26.9124,
                                lng = 75.7873,
                                geofenceRadius = geofenceRadius.toIntOrNull() ?: 100,
                                plan = selectedPlan,
                                agentCode = agentCode.trim(),
                                workingDays = workingDays
                            )
                            isSubmitting = false
                            showPendingApprovalDialog = biz
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_registration_button")
                ) {
                    Text("Register Shop & Submit for Approval", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }

    showPendingApprovalDialog?.let { biz ->
        AlertDialog(
            onDismissRequest = {
                showPendingApprovalDialog = null
                onRegistered(biz)
            },
            title = {
                Text("Shop Registration Submitted!", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Shop: ${biz.name} (${biz.businessCode})",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Your account is currently PENDING approval from Superadmin.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Once the Superadmin reviews and activates your shop, you will be able to log in with your email/phone and create employees.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPendingApprovalDialog = null
                        onRegistered(biz)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Text("OK, Go to Login")
                }
            }
        )
    }
}

@Composable
fun PlanSelectRow(
    name: String,
    details: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(details, fontSize = 11.sp, color = OrakleSlate500)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptInDropdown(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selected,
            onValueChange = {},
            label = { Text("Select Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
