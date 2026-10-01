package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dukaan.data.model.CurrentUser
import com.example.dukaan.data.model.UserRole
import com.example.dukaan.data.repository.DukaanRepository
import com.example.dukaan.service.SessionManager
import com.example.dukaan.ui.components.OrakleLogoBrand
import com.example.dukaan.ui.screens.admin.AdminMainScreen
import com.example.dukaan.ui.screens.agent.AgentMainScreen
import com.example.dukaan.ui.screens.auth.BusinessRegisterScreen
import com.example.dukaan.ui.screens.auth.LoginScreen
import com.example.dukaan.ui.screens.employee.EmployeeMainScreen
import com.example.dukaan.ui.screens.superadmin.SuperAdminScreen
import com.example.ui.theme.DukaanTheme
import com.example.ui.theme.OrakleRedPrimary
import com.example.ui.theme.OrakleSlate500
import com.example.ui.theme.OrakleSlate900
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DukaanTheme {
                val repository = remember { DukaanRepository(applicationContext) }
                val coroutineScope = rememberCoroutineScope()

                // Clean any leftover demo data and sync live cloud businesses on startup
                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        repository.cleanDemoDataIfPresent()
                        repository.syncBusinessesFromSupabase()
                    }
                }

                // Request Camera, Location & Notification permissions at startup
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { /* Permissions result handled gracefully */ }

                LaunchedEffect(Unit) {
                    val perms = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CAMERA
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val permissionsNeeded = perms.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (permissionsNeeded.isNotEmpty()) {
                        permissionLauncher.launch(permissionsNeeded.toTypedArray())
                    }
                }

                OrakleDukaanApp(repository = repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrakleDukaanApp(
    repository: DukaanRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Current logged-in user with 12-hour session persistence
    var currentUser by remember { mutableStateOf(SessionManager.getUserSession(context)) }

    var isRegisteringShop by remember { mutableStateOf(false) }
    var prefilledAgentCode by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Global App Header Bar when logged in
            currentUser?.let { user ->
                TopAppRoleBar(
                    user = user,
                    onLogout = {
                        SessionManager.clearUserSession(context)
                        currentUser = null
                    }
                )
            }

            // Main Content Area based on Role or Auth state
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isRegisteringShop -> {
                        BackHandler {
                            isRegisteringShop = false
                            prefilledAgentCode = ""
                        }
                        BusinessRegisterScreen(
                            repository = repository,
                            onBackToLogin = {
                                isRegisteringShop = false
                                prefilledAgentCode = ""
                            },
                            onRegistered = { _ ->
                                isRegisteringShop = false
                                prefilledAgentCode = ""
                            },
                            initialAgentCode = prefilledAgentCode
                        )
                    }

                    currentUser == null -> {
                        LoginScreen(
                            repository = repository,
                            onLoginSuccess = { user ->
                                SessionManager.saveUserSession(context, user)
                                currentUser = user
                            },
                            onOpenRegister = {
                                prefilledAgentCode = ""
                                isRegisteringShop = true
                            }
                        )
                    }

                    currentUser?.role == UserRole.SUPERADMIN -> {
                        BackHandler {
                            SessionManager.clearUserSession(context)
                            currentUser = null
                        }
                        SuperAdminScreen(repository = repository)
                    }

                    currentUser?.role == UserRole.BUSINESS_ADMIN -> {
                        BackHandler {
                            SessionManager.clearUserSession(context)
                            currentUser = null
                        }
                        AdminMainScreen(
                            repository = repository,
                            businessId = currentUser?.businessId.orEmpty()
                        )
                    }

                    currentUser?.role == UserRole.EMPLOYEE -> {
                        BackHandler {
                            SessionManager.clearUserSession(context)
                            currentUser = null
                        }
                        EmployeeMainScreen(
                            repository = repository,
                            employeeId = currentUser?.employeeId.orEmpty(),
                            businessId = currentUser?.businessId.orEmpty()
                        )
                    }

                    currentUser?.role == UserRole.AGENT -> {
                        BackHandler {
                            SessionManager.clearUserSession(context)
                            currentUser = null
                        }
                        AgentMainScreen(
                            repository = repository,
                            agentId = currentUser?.agentId ?: currentUser?.id.orEmpty(),
                            onRegisterShop = { code ->
                                prefilledAgentCode = code
                                isRegisteringShop = true
                            },
                            onLogout = {
                                SessionManager.clearUserSession(context)
                                currentUser = null
                            }
                        )
                    }

                    else -> {
                        BackHandler {
                            SessionManager.clearUserSession(context)
                            currentUser = null
                        }
                        SuperAdminScreen(repository = repository)
                    }
                }
            }
        }
    }
}

@Composable
fun TopAppRoleBar(
    user: CurrentUser,
    onLogout: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OrakleLogoBrand()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (user.role) {
                        UserRole.SUPERADMIN -> OrakleSlate900
                        UserRole.BUSINESS_ADMIN -> OrakleRedPrimary
                        UserRole.EMPLOYEE -> Color(0xFF16A34A)
                        UserRole.AGENT -> Color(0xFF0284C7)
                    }
                ) {
                    Text(
                        text = when (user.role) {
                            UserRole.SUPERADMIN -> "Superadmin"
                            UserRole.BUSINESS_ADMIN -> "Shop Admin"
                            UserRole.EMPLOYEE -> "Staff"
                            UserRole.AGENT -> "Agent"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = user.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.size(32.dp).testTag("app_logout_button")
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = OrakleRedPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

