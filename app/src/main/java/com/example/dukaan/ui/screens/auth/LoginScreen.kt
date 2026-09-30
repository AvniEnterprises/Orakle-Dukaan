package com.example.dukaan.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaan.data.model.CurrentUser
import com.example.dukaan.data.model.UserRole
import com.example.dukaan.data.remote.SupabaseClient
import com.example.dukaan.data.repository.DukaanRepository
import com.example.dukaan.ui.components.OrakleLogoBrand
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repository: DukaanRepository,
    onLoginSuccess: (CurrentUser) -> Unit,
    onOpenRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OrakleLogoBrand()
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Staff attendance aur daily employee management ka tension khatam.",
            style = MaterialTheme.typography.bodyMedium,
            color = OrakleSlate600,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Card with Login Form
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Sign In to Your Account",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = emailOrPhone,
                    onValueChange = { emailOrPhone = it },
                    label = { Text("Email or Mobile") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = OrakleRedPrimary) },
                    modifier = Modifier.fillMaxWidth().testTag("login_email_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = OrakleRedPrimary) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("login_password_input")
                )

                Button(
                    onClick = {
                        val input = emailOrPhone.trim()
                        val pass = password.trim()

                        if (input.isEmpty() || pass.isEmpty()) {
                            Toast.makeText(context, "Please enter both Email/Mobile and Password", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        coroutineScope.launch {
                            // 1. MASTER SUPERADMIN AUTHENTICATION (Strict: only hypersmile100@gmail.com / ProjectKnight@161718)
                            if (input.equals("hypersmile100@gmail.com", ignoreCase = true)) {
                                if (pass == "ProjectKnight@161718") {
                                    isLoading = false
                                    Toast.makeText(context, "Welcome Master Superadmin", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess(
                                        CurrentUser(
                                            id = "superadmin-master",
                                            role = UserRole.SUPERADMIN,
                                            email = "hypersmile100@gmail.com",
                                            name = "Master Superadmin"
                                        )
                                    )
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Incorrect Superadmin Password!", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }

                            // 2. SHOP ADMIN AUTHENTICATION (Real Database Check & Approval Gate)
                            var biz = repository.getBusinessByContact(input)
                            if (biz == null) {
                                // Sync live from Supabase in case the shop was registered from another device
                                repository.syncBusinessesFromSupabase()
                                biz = repository.getBusinessByContact(input)
                            }
                            if (biz != null) {
                                if (biz.password.isNotBlank() && biz.password != pass) {
                                    isLoading = false
                                    Toast.makeText(context, "Incorrect password for shop ${biz.name}", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                when (biz.status) {
                                    com.example.dukaan.data.model.BusinessStatus.PENDING -> {
                                        isLoading = false
                                        Toast.makeText(
                                            context,
                                            "Shop '${biz.name}' is PENDING approval from Superadmin. Please wait for activation.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }
                                    com.example.dukaan.data.model.BusinessStatus.SUSPENDED,
                                    com.example.dukaan.data.model.BusinessStatus.REJECTED,
                                    com.example.dukaan.data.model.BusinessStatus.EXPIRED -> {
                                        isLoading = false
                                        Toast.makeText(
                                            context,
                                            "Shop '${biz.name}' is currently ${biz.status.name}. Please contact Superadmin.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }
                                    com.example.dukaan.data.model.BusinessStatus.APPROVED,
                                    com.example.dukaan.data.model.BusinessStatus.ACTIVE -> {
                                        isLoading = false
                                        Toast.makeText(context, "Welcome, ${biz.ownerName} (${biz.name})", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(
                                            CurrentUser(
                                                id = "admin-${biz.id}",
                                                role = UserRole.BUSINESS_ADMIN,
                                                email = biz.email,
                                                name = biz.ownerName,
                                                businessId = biz.id
                                            )
                                        )
                                        return@launch
                                    }
                                }
                            }

                            // 3. EMPLOYEE AUTHENTICATION (Real Database Check & Status Gate)
                            var emp = repository.getEmployeeByContact(input)
                            if (emp == null) {
                                // Sync live from Supabase in case employee was registered from Admin's phone
                                repository.syncAllEmployeesFromSupabase()
                                emp = repository.getEmployeeByContact(input)
                            }
                            if (emp != null) {
                                if (emp.password.isNotBlank() && emp.password != pass) {
                                    isLoading = false
                                    Toast.makeText(context, "Incorrect password for employee ${emp.fullName}", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                val shop = repository.getBusinessById(emp.businessId)
                                if (shop != null && (shop.status == com.example.dukaan.data.model.BusinessStatus.SUSPENDED || shop.status == com.example.dukaan.data.model.BusinessStatus.REJECTED)) {
                                    isLoading = false
                                    Toast.makeText(context, "Shop is suspended. Contact owner.", Toast.LENGTH_LONG).show()
                                    return@launch
                                }

                                if (emp.status != "ACTIVE") {
                                    isLoading = false
                                    Toast.makeText(context, "Employee account is ${emp.status}.", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                isLoading = false
                                Toast.makeText(context, "Welcome, ${emp.fullName}", Toast.LENGTH_SHORT).show()
                                onLoginSuccess(
                                    CurrentUser(
                                        id = emp.id,
                                        role = UserRole.EMPLOYEE,
                                        email = emp.email,
                                        name = emp.fullName,
                                        businessId = emp.businessId,
                                        employeeId = emp.id
                                    )
                                )
                                return@launch
                            }

                            // 4. REMOTE CLOUD AUTH VIA SUPABASE
                            if (input.contains("@")) {
                                val supResult = SupabaseClient.signInWithEmail(input, pass)
                                if (supResult.isSuccess) {
                                    val user = supResult.getOrNull()
                                    if (user != null) {
                                        isLoading = false
                                        Toast.makeText(context, "Signed in via Cloud Auth", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(user)
                                        return@launch
                                    }
                                }
                            }

                            // 5. NO MATCH FOUND
                            isLoading = false
                            Toast.makeText(
                                context,
                                "Account not found or invalid credentials. Check details or register shop.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("login_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("New business owner? ", fontSize = 12.sp, color = OrakleSlate600)
                    Text(
                        text = "Register Shop",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrakleRedPrimary,
                        modifier = Modifier.clickable { onOpenRegister() }.testTag("login_register_shop_link")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Professional instructions card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Role Access Information:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = OrakleSlate700)
                Text("• Shop Admin: Register your shop, wait for Superadmin activation, then log in", fontSize = 11.sp, color = OrakleSlate600)
                Text("• Staff/Employee: Log in with mobile/email and password provided by shop admin", fontSize = 11.sp, color = OrakleSlate600)
            }
        }
    }
}

