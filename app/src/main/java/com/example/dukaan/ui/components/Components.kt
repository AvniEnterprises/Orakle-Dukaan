package com.example.dukaan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaan.data.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun OrakleLogoBrand(
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red Stadium Pill Logo
        Box(
            modifier = Modifier
                .size(36.dp, 22.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(4.dp, OrakleRedPrimary, RoundedCornerShape(12.dp))
                .background(Color.Transparent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "ORAKLE",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = OrakleRedPrimary
                )
            )
            Text(
                text = "DUKAAN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = if (isDark) Color.White else OrakleSlate700
                )
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = OrakleSlate500,
                maxLines = 1,
                fontSize = 10.sp,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GeofenceStatusBadge(
    isInside: Boolean,
    distanceMeters: Double,
    radiusMeters: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isInside) OrakleGreenContainer else OrakleRedContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isInside) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isInside) OrakleGreen else OrakleRedDark,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isInside) {
                    "Location Verified (${distanceMeters.toInt()}m / ${radiusMeters}m radius)"
                } else {
                    "Outside Shop (${distanceMeters.toInt()}m from shop, max ${radiusMeters}m)"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isInside) Color(0xFF166534) else OrakleOnRedContainer
            )
        }
    }
}

@Composable
fun OfflineSyncBanner(
    pendingCount: Int,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingCount > 0) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("offline_sync_banner"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = OrakleAmberContainer)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline Punches",
                        tint = OrakleAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$pendingCount offline punch pending sync",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF92400E)
                    )
                }
                Button(
                    onClick = onSyncClick,
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleAmber),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sync Now", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

// ==========================================
// REUSABLE EDIT DIALOGS ACROSS ALL PANELS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBusinessDialog(
    business: Business,
    isSuperAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Business) -> Unit,
    onDelete: (() -> Unit)? = null,
    onExportPdf: (() -> Unit)? = null,
    onUploadLogo: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(business.name) }
    var ownerName by remember { mutableStateOf(business.ownerName) }
    var phone by remember { mutableStateOf(business.phone) }
    var email by remember { mutableStateOf(business.email) }
    var businessType by remember { mutableStateOf(business.businessType) }
    var address by remember { mutableStateOf(business.address) }
    var city by remember { mutableStateOf(business.city) }
    var state by remember { mutableStateOf(business.state) }
    var pincode by remember { mutableStateOf(business.pincode) }
    var latitude by remember { mutableStateOf(business.latitude.toString()) }
    var longitude by remember { mutableStateOf(business.longitude.toString()) }
    var geofenceRadius by remember { mutableStateOf(business.geofenceRadiusMeters.toString()) }
    var shiftStart by remember { mutableStateOf(business.shiftStart) }
    var shiftEnd by remember { mutableStateOf(business.shiftEnd) }
    var graceMinutes by remember { mutableStateOf(business.graceMinutes.toString()) }
    var overtimeMinutes by remember { mutableStateOf(business.overtimeThresholdMinutes.toString()) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDetectingLocation by remember { mutableStateOf(false) }

    // SuperAdmin editable fields
    var plan by remember { mutableStateOf(business.plan) }
    var monthlyPrice by remember { mutableStateOf(business.monthlyPrice.toInt().toString()) }
    var empLimit by remember { mutableStateOf(business.employeeLimit.toString()) }
    var eventLimit by remember { mutableStateOf(business.dailyEventLimitPerEmployee.toString()) }
    var status by remember { mutableStateOf(business.status) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isSuperAdmin) "Edit Business & Owner (${business.businessCode})" else "Edit Shop Details & Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Update profile, shop address, shift and policy settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrakleSlate500
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("BASIC STORE PROFILE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate600)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Store / Business Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_biz_name")
                )
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Owner Full Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_biz_owner")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile *") },
                        modifier = Modifier.weight(1f).testTag("edit_biz_phone")
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.weight(1f).testTag("edit_biz_email")
                    )
                }
                OutlinedTextField(
                    value = businessType,
                    onValueChange = { businessType = it },
                    label = { Text("Business Type (Kirana, Clothing, Restaurant, etc.)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("LOCATION & GEOFENCE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate600)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Shop Address") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Shop Latitude") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Shop Longitude") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            isDetectingLocation = true
                            coroutineScope.launch {
                                try {
                                    val fused = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                                    fused.lastLocation.addOnSuccessListener { loc ->
                                        isDetectingLocation = false
                                        if (loc != null) {
                                            latitude = String.format(java.util.Locale.ENGLISH, "%.6f", loc.latitude)
                                            longitude = String.format(java.util.Locale.ENGLISH, "%.6f", loc.longitude)
                                            android.widget.Toast.makeText(context, "Location pinned: $latitude, $longitude", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Please turn on device GPS", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }.addOnFailureListener {
                                        isDetectingLocation = false
                                        android.widget.Toast.makeText(context, "Failed to get GPS location", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    isDetectingLocation = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isDetectingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = OrakleRedPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pin Live GPS", fontSize = 11.sp, color = OrakleRedPrimary, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val uri = android.net.Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($name)")
                                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                context.startActivity(mapIntent)
                            } catch (_: Exception) {
                                val webUri = android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, webUri))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp), tint = OrakleSlate700)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Google Maps", fontSize = 11.sp, color = OrakleSlate800, maxLines = 1)
                    }
                }

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = OrakleSlate100),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShareLocation, contentDescription = null, tint = OrakleRedPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Geofence Perimeter: ${geofenceRadius}m", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate800)
                            }
                        }
                        Text("Real GPS coordinates: $latitude, $longitude", fontSize = 11.sp, color = OrakleSlate600)
                        Text("Staff can only punch IN/OUT within this ${geofenceRadius}m boundary.", fontSize = 10.sp, color = OrakleSlate500)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Geofence Radius (meters):", fontSize = 11.sp, color = OrakleSlate600)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("20", "50", "100", "200", "500").forEach { r ->
                            FilterChip(
                                selected = geofenceRadius == r,
                                onClick = { geofenceRadius = r },
                                label = { Text("${r}m", fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = geofenceRadius,
                        onValueChange = { geofenceRadius = it },
                        label = { Text("Custom Radius in Meters") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("TIMINGS & SHIFT POLICY", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate600)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shiftStart,
                        onValueChange = { shiftStart = it },
                        label = { Text("Shift Start") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = shiftEnd,
                        onValueChange = { shiftEnd = it },
                        label = { Text("Shift End") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = graceMinutes,
                        onValueChange = { graceMinutes = it },
                        label = { Text("Grace Period (mins)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = overtimeMinutes,
                        onValueChange = { overtimeMinutes = it },
                        label = { Text("OT Threshold (mins)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isSuperAdmin) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("PLATFORM SUPERADMIN CONTROLS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleRedPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = plan,
                            onValueChange = { plan = it },
                            label = { Text("Plan (BASIC / PLUS / BUSINESS)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = monthlyPrice,
                            onValueChange = { monthlyPrice = it },
                            label = { Text("Price (₹/mo)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = empLimit,
                            onValueChange = { empLimit = it },
                            label = { Text("Staff Limit") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = eventLimit,
                            onValueChange = { eventLimit = it },
                            label = { Text("Daily Punches/Staff") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Status Action:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { status = BusinessStatus.APPROVED },
                            colors = ButtonDefaults.buttonColors(containerColor = if (status == BusinessStatus.APPROVED || status == BusinessStatus.ACTIVE) OrakleGreen else OrakleSlate300),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(2.dp)
                        ) {
                            Text("Active", fontSize = 11.sp, color = if (status == BusinessStatus.APPROVED || status == BusinessStatus.ACTIVE) Color.White else OrakleSlate900)
                        }
                        Button(
                            onClick = { status = BusinessStatus.SUSPENDED },
                            colors = ButtonDefaults.buttonColors(containerColor = if (status == BusinessStatus.SUSPENDED) OrakleAmber else OrakleSlate300),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(2.dp)
                        ) {
                            Text("Suspend", fontSize = 11.sp, color = if (status == BusinessStatus.SUSPENDED) Color.White else OrakleSlate900)
                        }
                        Button(
                            onClick = { status = BusinessStatus.REJECTED },
                            colors = ButtonDefaults.buttonColors(containerColor = if (status == BusinessStatus.REJECTED) OrakleRedPrimary else OrakleSlate300),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(2.dp)
                        ) {
                            Text("Reject", fontSize = 11.sp, color = if (status == BusinessStatus.REJECTED) Color.White else OrakleSlate900)
                        }
                    }
                }

                if (onUploadLogo != null || onExportPdf != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("SHOP ACTIONS & PROFILE PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleSlate600)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onUploadLogo != null) {
                            OutlinedButton(
                                onClick = onUploadLogo,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Logo", fontSize = 11.sp)
                            }
                        }
                        if (onExportPdf != null) {
                            Button(
                                onClick = onExportPdf,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export PDF", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrakleRedPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete / Archive Business", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && ownerName.isNotBlank() && phone.isNotBlank()) {
                        val updated = business.copy(
                            name = name.trim(),
                            ownerName = ownerName.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            businessType = businessType.trim(),
                            address = address.trim(),
                            city = city.trim(),
                            state = state.trim(),
                            pincode = pincode.trim(),
                            latitude = latitude.toDoubleOrNull() ?: business.latitude,
                            longitude = longitude.toDoubleOrNull() ?: business.longitude,
                            geofenceRadiusMeters = geofenceRadius.toIntOrNull() ?: business.geofenceRadiusMeters,
                            shiftStart = shiftStart.trim(),
                            shiftEnd = shiftEnd.trim(),
                            graceMinutes = graceMinutes.toIntOrNull() ?: business.graceMinutes,
                            overtimeThresholdMinutes = overtimeMinutes.toIntOrNull() ?: business.overtimeThresholdMinutes,
                            plan = if (isSuperAdmin) plan.trim() else business.plan,
                            monthlyPrice = if (isSuperAdmin) (monthlyPrice.toDoubleOrNull() ?: business.monthlyPrice) else business.monthlyPrice,
                            employeeLimit = if (isSuperAdmin) (empLimit.toIntOrNull() ?: business.employeeLimit) else business.employeeLimit,
                            dailyEventLimitPerEmployee = if (isSuperAdmin) (eventLimit.toIntOrNull() ?: business.dailyEventLimitPerEmployee) else business.dailyEventLimitPerEmployee,
                            status = if (isSuperAdmin) status else business.status
                        )
                        onSave(updated)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text("Save Changes")
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
            title = { Text("Confirm Deletion", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${business.name}? This will remove all associated employees and attendance records permanently.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEmployeeDialog(
    businessId: String,
    employeeToEdit: Employee?,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(employeeToEdit?.fullName ?: "") }
    var phone by remember { mutableStateOf(employeeToEdit?.phone ?: "") }
    var email by remember { mutableStateOf(employeeToEdit?.email ?: "") }
    var address by remember { mutableStateOf(employeeToEdit?.address ?: "") }
    var designation by remember { mutableStateOf(employeeToEdit?.designation ?: "Staff") }
    var password by remember { mutableStateOf(employeeToEdit?.password ?: "123456") }
    var salaryType by remember { mutableStateOf(employeeToEdit?.salaryType ?: SalaryType.MONTHLY) }
    var salaryAmount by remember { mutableStateOf(employeeToEdit?.monthlySalary?.toInt()?.toString() ?: "15000") }
    var dailyWage by remember { mutableStateOf(employeeToEdit?.dailyWage?.toInt()?.toString() ?: "600") }
    var bankAccount by remember { mutableStateOf(employeeToEdit?.bankAccount ?: "") }
    var bankIfsc by remember { mutableStateOf(employeeToEdit?.bankIfsc ?: "") }
    var emergencyContact by remember { mutableStateOf(employeeToEdit?.emergencyContact ?: "") }
    var status by remember { mutableStateOf(employeeToEdit?.status ?: "ACTIVE") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (employeeToEdit == null) "Add New Staff Member" else "Edit Staff Details", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("employee_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number *") },
                        modifier = Modifier.weight(1f).testTag("employee_phone_input")
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = designation,
                    onValueChange = { designation = it },
                    label = { Text("Role / Designation") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Employee Login Password *") },
                    placeholder = { Text("e.g. 123456") },
                    modifier = Modifier.fillMaxWidth().testTag("employee_password_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Residential Address") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Salary & Wage Structure:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrakleSlate600)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = salaryType == SalaryType.MONTHLY,
                        onClick = { salaryType = SalaryType.MONTHLY },
                        label = { Text("Monthly", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = salaryType == SalaryType.DAILY_WAGE,
                        onClick = { salaryType = SalaryType.DAILY_WAGE },
                        label = { Text("Daily Wage", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = salaryType == SalaryType.CUSTOM_DAILY,
                        onClick = { salaryType = SalaryType.CUSTOM_DAILY },
                        label = { Text("Custom", fontSize = 11.sp) }
                    )
                }

                if (salaryType == SalaryType.MONTHLY) {
                    OutlinedTextField(
                        value = salaryAmount,
                        onValueChange = { salaryAmount = it },
                        label = { Text("Monthly Salary (₹)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = dailyWage,
                        onValueChange = { dailyWage = it },
                        label = { Text("Daily Rate (₹/day)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bankAccount,
                        onValueChange = { bankAccount = it },
                        label = { Text("Bank Account") },
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = bankIfsc,
                        onValueChange = { bankIfsc = it },
                        label = { Text("IFSC") },
                        modifier = Modifier.weight(0.8f)
                    )
                }
                OutlinedTextField(
                    value = emergencyContact,
                    onValueChange = { emergencyContact = it },
                    label = { Text("Emergency Contact (Name & Phone)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Employee Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrakleSlate600)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = status == "ACTIVE",
                        onClick = { status = "ACTIVE" },
                        label = { Text("Active", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = status == "INACTIVE",
                        onClick = { status = "INACTIVE" },
                        label = { Text("Inactive", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = status == "ARCHIVED",
                        onClick = { status = "ARCHIVED" },
                        label = { Text("Archived", fontSize = 11.sp) }
                    )
                }

                if (employeeToEdit != null && onDelete != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrakleRedPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Staff Member", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        val mSalary = salaryAmount.toDoubleOrNull() ?: 15000.0
                        val dWage = dailyWage.toDoubleOrNull() ?: (mSalary / 26)
                        val emp = Employee(
                            id = employeeToEdit?.id ?: UUID.randomUUID().toString(),
                            businessId = businessId,
                            employeeCode = employeeToEdit?.employeeCode ?: "EMP-${(100..999).random()}",
                            fullName = name.trim(),
                            photoUrl = employeeToEdit?.photoUrl.orEmpty(),
                            phone = phone.trim(),
                            email = email.trim(),
                            password = password.ifBlank { "123456" }.trim(),
                            address = address.trim(),
                            designation = designation.trim(),
                            joiningDate = employeeToEdit?.joiningDate ?: "2026-09-28",
                            salaryType = salaryType,
                            monthlySalary = mSalary,
                            dailyWage = dWage,
                            customDailyRate = dWage,
                            bankAccount = bankAccount.trim(),
                            bankIfsc = bankIfsc.trim(),
                            emergencyContact = emergencyContact.trim(),
                            status = status
                        )
                        onSave(emp)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text(if (employeeToEdit == null) "Add Staff" else "Save Details")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Employee?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${employeeToEdit?.fullName}? Their record and attendance logs will be removed.") },
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
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEmployeeProfileDialog(
    employee: Employee,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit
) {
    var phone by remember { mutableStateOf(employee.phone) }
    var email by remember { mutableStateOf(employee.email) }
    var address by remember { mutableStateOf(employee.address) }
    var emergencyContact by remember { mutableStateOf(employee.emergencyContact) }
    var bankAccount by remember { mutableStateOf(employee.bankAccount) }
    var bankIfsc by remember { mutableStateOf(employee.bankIfsc) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit My Profile Details", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Name: ${employee.fullName} (${employee.employeeCode})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrakleSlate700)
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Phone Number *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Residential Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emergencyContact,
                    onValueChange = { emergencyContact = it },
                    label = { Text("Emergency Contact Person & Phone") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bankAccount,
                        onValueChange = { bankAccount = it },
                        label = { Text("Bank Account No") },
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = bankIfsc,
                        onValueChange = { bankIfsc = it },
                        label = { Text("IFSC") },
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phone.isNotBlank()) {
                        val updated = employee.copy(
                            phone = phone.trim(),
                            email = email.trim(),
                            address = address.trim(),
                            emergencyContact = emergencyContact.trim(),
                            bankAccount = bankAccount.trim(),
                            bankIfsc = bankIfsc.trim()
                        )
                        onSave(updated)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAdvanceDialog(
    advance: AdvanceUdhaar,
    onDismiss: () -> Unit,
    onSave: (AdvanceUdhaar) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var totalAmount by remember { mutableStateOf(advance.totalAmount.toInt().toString()) }
    var remainingAmount by remember { mutableStateOf(advance.remainingAmount.toInt().toString()) }
    var dailyDeduction by remember { mutableStateOf(advance.dailyDeductionAmount.toInt().toString()) }
    var status by remember { mutableStateOf(advance.status) }
    var reason by remember { mutableStateOf(advance.reason) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Udhaar / Advance Record", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalAmount,
                        onValueChange = { totalAmount = it },
                        label = { Text("Total Issued (₹)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = remainingAmount,
                        onValueChange = { remainingAmount = it },
                        label = { Text("Remaining (₹)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = dailyDeduction,
                    onValueChange = { dailyDeduction = it },
                    label = { Text("Daily Deduction on Clock-In (₹)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / Purpose") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = status == "ACTIVE",
                        onClick = { status = "ACTIVE" },
                        label = { Text("Active", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = status == "PAUSED",
                        onClick = { status = "PAUSED" },
                        label = { Text("Paused", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = status == "CLEARED",
                        onClick = {
                            status = "CLEARED"
                            remainingAmount = "0"
                        },
                        label = { Text("Cleared / Paid", fontSize = 11.sp) }
                    )
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrakleRedPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Advance Record", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tot = totalAmount.toDoubleOrNull() ?: advance.totalAmount
                    val rem = remainingAmount.toDoubleOrNull() ?: advance.remainingAmount
                    val ded = dailyDeduction.toDoubleOrNull() ?: advance.dailyDeductionAmount
                    onSave(
                        advance.copy(
                            totalAmount = tot,
                            remainingAmount = rem,
                            dailyDeductionAmount = ded,
                            status = if (rem <= 0) "CLEARED" else status,
                            reason = reason.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: ExpenseRecord,
    isAdmin: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (ExpenseRecord) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var amount by remember { mutableStateOf(expense.amount.toInt().toString()) }
    var category by remember { mutableStateOf(expense.category) }
    var description by remember { mutableStateOf(expense.description) }
    var date by remember { mutableStateOf(expense.date) }
    var status by remember { mutableStateOf(expense.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAdmin) "Edit Expense Claim" else "Edit My Expense Claim", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹) *") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isAdmin) {
                    Text("Approval Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = status == ExpenseStatus.APPROVED,
                            onClick = { status = ExpenseStatus.APPROVED },
                            label = { Text("Approved", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = status == ExpenseStatus.PENDING,
                            onClick = { status = ExpenseStatus.PENDING },
                            label = { Text("Pending", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = status == ExpenseStatus.REJECTED,
                            onClick = { status = ExpenseStatus.REJECTED },
                            label = { Text("Rejected", fontSize = 11.sp) }
                        )
                    }
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrakleRedPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAdmin) "Delete Expense" else "Withdraw Expense Claim", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: expense.amount
                    onSave(
                        expense.copy(
                            amount = amt,
                            category = category.trim(),
                            description = description.trim(),
                            date = date.trim(),
                            status = if (isAdmin) status else expense.status
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLeaveDialog(
    leave: LeaveRequest,
    isAdmin: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (LeaveRequest) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var leaveType by remember { mutableStateOf(leave.leaveType) }
    var startDate by remember { mutableStateOf(leave.startDate) }
    var endDate by remember { mutableStateOf(leave.endDate) }
    var days by remember { mutableStateOf(leave.days.toString()) }
    var reason by remember { mutableStateOf(leave.reason) }
    var adminComment by remember { mutableStateOf(leave.adminComment) }
    var status by remember { mutableStateOf(leave.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAdmin) "Edit / Review Leave Request" else "Edit Leave Application", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = leaveType,
                    onValueChange = { leaveType = it },
                    label = { Text("Leave Type (Sick / Casual / Emergency)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start Date") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End Date") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("Total Days") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Leave") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isAdmin) {
                    OutlinedTextField(
                        value = adminComment,
                        onValueChange = { adminComment = it },
                        label = { Text("Admin Remarks") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Approval Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = status == LeaveStatus.APPROVED,
                            onClick = { status = LeaveStatus.APPROVED },
                            label = { Text("Approved", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = status == LeaveStatus.PENDING,
                            onClick = { status = LeaveStatus.PENDING },
                            label = { Text("Pending", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = status == LeaveStatus.REJECTED,
                            onClick = { status = LeaveStatus.REJECTED },
                            label = { Text("Rejected", fontSize = 11.sp) }
                        )
                    }
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrakleRedPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAdmin) "Delete Leave Record" else "Cancel Application", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        leave.copy(
                            leaveType = leaveType.trim(),
                            startDate = startDate.trim(),
                            endDate = endDate.trim(),
                            days = days.toIntOrNull() ?: leave.days,
                            reason = reason.trim(),
                            adminComment = if (isAdmin) adminComment.trim() else leave.adminComment,
                            status = if (isAdmin) status else leave.status
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDocumentDialog(
    doc: EmployeeDocument,
    onDismiss: () -> Unit,
    onSave: (EmployeeDocument) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var docName by remember { mutableStateOf(doc.docName) }
    var docType by remember { mutableStateOf(doc.docType) }
    var driveFileId by remember { mutableStateOf(doc.driveFileId) }
    var verificationStatus by remember { mutableStateOf(doc.verificationStatus) }
    var expiryDate by remember { mutableStateOf(doc.expiryDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Document Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = docName,
                    onValueChange = { docName = it },
                    label = { Text("Document Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = docType,
                        onValueChange = { docType = it },
                        label = { Text("Type (Aadhaar/PAN/Bank)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expiryDate,
                        onValueChange = { expiryDate = it },
                        label = { Text("Expiry Date") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = driveFileId,
                    onValueChange = { driveFileId = it },
                    label = { Text("Google Drive ID / File Ref") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Verification Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = verificationStatus == "VERIFIED",
                        onClick = { verificationStatus = "VERIFIED" },
                        label = { Text("Verified", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = verificationStatus == "PENDING",
                        onClick = { verificationStatus = "PENDING" },
                        label = { Text("Pending", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = verificationStatus == "REJECTED",
                        onClick = { verificationStatus = "REJECTED" },
                        label = { Text("Rejected", fontSize = 11.sp) }
                    )
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrakleRedPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Document", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        doc.copy(
                            docName = docName.trim(),
                            docType = docType.trim(),
                            driveFileId = driveFileId.trim(),
                            verificationStatus = verificationStatus,
                            expiryDate = expiryDate.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentDialog(
    businessId: String,
    employeeId: String,
    onDismiss: () -> Unit,
    onSave: (EmployeeDocument) -> Unit
) {
    var docName by remember { mutableStateOf("") }
    var docType by remember { mutableStateOf("Aadhaar") }
    var driveLink by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("2030-12-31") }
    val context = LocalContext.current

    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val docsDir = java.io.File(context.filesDir, "employee_docs").apply { mkdirs() }
                val localFile = java.io.File(docsDir, "doc_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }
                driveLink = localFile.absolutePath
                if (docName.isBlank()) docName = "$docType Document"
            } catch (e: Exception) {
                driveLink = uri.toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload / Link Document", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Store verification documents securely on local storage and cloud database.",
                    fontSize = 12.sp,
                    color = OrakleSlate600
                )
                OutlinedTextField(
                    value = docName,
                    onValueChange = { docName = it },
                    label = { Text("Document Title *") },
                    placeholder = { Text("e.g. Rahul Aadhaar Card") },
                    modifier = Modifier.fillMaxWidth().testTag("doc_name_input")
                )
                Text("Document Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Aadhaar", "PAN Card", "Bank Passbook").forEach { type ->
                        FilterChip(
                            selected = docType == type,
                            onClick = { docType = type },
                            label = { Text(type, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { photoPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Photo / File from Device", fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = driveLink,
                    onValueChange = { driveLink = it },
                    label = { Text("File Path / Google Drive Link *") },
                    placeholder = { Text("Selected device path or Drive link") },
                    modifier = Modifier.fillMaxWidth().testTag("doc_drive_link_input")
                )
                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = { expiryDate = it },
                    label = { Text("Expiry Date (optional)") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (docName.isNotBlank() && driveLink.isNotBlank()) {
                        val doc = EmployeeDocument(
                            id = UUID.randomUUID().toString(),
                            businessId = businessId,
                            employeeId = employeeId,
                            docType = docType,
                            docName = docName.trim(),
                            driveFileId = driveLink.trim(),
                            verificationStatus = "PENDING",
                            expiryDate = expiryDate.trim(),
                            uploadDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date())
                        )
                        onSave(doc)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary)
            ) {
                Text("Save Document")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

