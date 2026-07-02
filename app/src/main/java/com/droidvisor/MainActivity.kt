package com.droidvisor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.droidvisor.datastore.dataStore
import com.droidvisor.datastore.dataStore
import com.droidvisor.docker.DockerDashboardViewModel
import com.droidvisor.docker.DockerProxyService
import com.droidvisor.ui.screen.CreateVmScreen
import com.droidvisor.ui.screen.DockerDashboardScreen
import com.droidvisor.ui.screen.PermissionScreen
import com.droidvisor.ui.viewmodel.PermissionViewModel
import com.droidvisor.ui.screen.SettingsScreen
import com.droidvisor.ui.screen.TerminalScreen
import com.droidvisor.ui.screen.VmManagementScreen
import com.droidvisor.ui.viewmodel.SettingsViewModel
import com.droidvisor.ui.viewmodel.VmManagementViewModel
import com.droidvisor.vm.BackupManagerService
import com.droidvisor.vm.ConsoleOutputService
import com.droidvisor.vm.VmManagerService
import com.droidvisor.vm.vsock.VsockService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val _vmManagerService = MutableStateFlow<VmManagerService?>(null)
    private val _consoleService = MutableStateFlow<ConsoleOutputService?>(null)
    private val _vsockService = MutableStateFlow<VsockService?>(null)
    private val _backupManagerService = MutableStateFlow<BackupManagerService?>(null)
    private val _dockerProxyService = MutableStateFlow<DockerProxyService?>(null)

    private var boundCount = 0
    private var expectedBoundCount = 5

    private val _allServicesReady = MutableStateFlow(false)

    private val vmManagerConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VmManagerService.LocalBinder
            _vmManagerService.value = binder.getService()
            checkReady()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            _vmManagerService.value = null
            _allServicesReady.value = false
        }
    }

    private val consoleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ConsoleOutputService.LocalBinder
            _consoleService.value = binder.getService()
            checkReady()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            _consoleService.value = null
            _allServicesReady.value = false
        }
    }

    private val vsockServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VsockService.LocalBinder
            _vsockService.value = binder.getService()
            _dockerProxyService.value?.attachVsockService(binder.getService())
            checkReady()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            _vsockService.value = null
            _allServicesReady.value = false
        }
    }

    private val backupManagerConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BackupManagerService.LocalBinder
            _backupManagerService.value = binder.getService()
            checkReady()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            _backupManagerService.value = null
            _allServicesReady.value = false
        }
    }

    private val dockerProxyConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DockerProxyService.LocalBinder
            _dockerProxyService.value = binder.getService()
            _vsockService.value?.let { vsock -> binder.getService().attachVsockService(vsock) }
            checkReady()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            _dockerProxyService.value = null
            _allServicesReady.value = false
        }
    }

    private fun checkReady() {
        boundCount++
        if (boundCount >= expectedBoundCount) {
            _allServicesReady.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Intent(this, VmManagerService::class.java).also { intent ->
            bindService(intent, vmManagerConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, ConsoleOutputService::class.java).also { intent ->
            bindService(intent, consoleServiceConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, VsockService::class.java).also { intent ->
            bindService(intent, vsockServiceConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, BackupManagerService::class.java).also { intent ->
            bindService(intent, backupManagerConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, DockerProxyService::class.java).also { intent ->
            bindService(intent, dockerProxyConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            DroidvisorApp(
                vmManagerServiceFlow = _vmManagerService.asStateFlow(),
                consoleOutputServiceFlow = _consoleService.asStateFlow(),
                backupManagerServiceFlow = _backupManagerService.asStateFlow(),
                vsockServiceFlow = _vsockService.asStateFlow(),
                dockerProxyServiceFlow = _dockerProxyService.asStateFlow(),
                allServicesReadyFlow = _allServicesReady.asStateFlow()
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listOf(
            vmManagerConnection to "vmManager",
            consoleServiceConnection to "console",
            vsockServiceConnection to "vsock",
            backupManagerConnection to "backup",
            dockerProxyConnection to "dockerProxy"
        ).forEach { (conn, _) ->
            try { unbindService(conn) } catch (_: Exception) {}
        }
    }
}

data class NavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

@Composable
fun DroidvisorApp(
    vmManagerServiceFlow: StateFlow<VmManagerService?>,
    consoleOutputServiceFlow: StateFlow<ConsoleOutputService?>,
    backupManagerServiceFlow: StateFlow<BackupManagerService?>,
    vsockServiceFlow: StateFlow<VsockService?>,
    dockerProxyServiceFlow: StateFlow<DockerProxyService?>,
    allServicesReadyFlow: StateFlow<Boolean>
) {
    val vmManagerService by vmManagerServiceFlow.collectAsState()
    val consoleOutputService by consoleOutputServiceFlow.collectAsState()
    val backupManagerService by backupManagerServiceFlow.collectAsState()
    val vsockService by vsockServiceFlow.collectAsState()
    val dockerProxyService by dockerProxyServiceFlow.collectAsState()
    val allServicesReady by allServicesReadyFlow.collectAsState()

    val navController = rememberNavController()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // ── 权限页面一次性显示 ─────────────────────────────────────────
    // 默认安装后仅展示一次，通过 DataStore 持久化已读状态。
    val context = LocalContext.current
    var hasPassedPermissionCheck by remember { mutableStateOf(false) }
    var dataStoreLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        hasPassedPermissionCheck = prefs[PERMISSION_CHECK_PASSED_KEY] ?: false
        dataStoreLoaded = true
    }

    // 当用户通过权限检测后，持久化已读状态
    LaunchedEffect(hasPassedPermissionCheck) {
        if (hasPassedPermissionCheck) {
            context.dataStore.edit { prefs ->
                prefs[PERMISSION_CHECK_PASSED_KEY] = true
            }
        }
    }

    val permissionViewModel: PermissionViewModel = viewModel()
    val permissionState by permissionViewModel.permissionState.collectAsState()

    if (!dataStoreLoaded) {
        // 等待 DataStore 读取完成
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (!hasPassedPermissionCheck) {
        PermissionScreen(
            viewModel = permissionViewModel,
            onAllPermissionsGranted = { hasPassedPermissionCheck = true }
        )
    } else if (!allServicesReady) {
        // Loading state while services bind
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在启动服务...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        val dockerViewModel: DockerDashboardViewModel = viewModel()
        val vmManagementViewModel: VmManagementViewModel = viewModel()

        LaunchedEffect(vmManagerService) {
            if (vmManagerService != null) {
                vmManagementViewModel.bindService(vmManagerService!!)
            }
        }
        LaunchedEffect(dockerProxyService) {
            if (dockerProxyService != null) {
                dockerViewModel.attachDockerProxyService(dockerProxyService!!)
            }
        }

        val context = LocalContext.current
        val settingsViewModel: SettingsViewModel = viewModel {
            SettingsViewModel(context.dataStore)
        }

        val navItems = listOf(
            NavItem("vm", "虚拟机", Icons.Default.Computer),
            NavItem("docker", "Docker", Icons.Default.Cloud),
            NavItem("terminal", "终端", Icons.Default.Terminal),
            NavItem("settings", "设置", Icons.Default.Settings)
        )

        MaterialTheme {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        navItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title) },
                                selected = selectedTabIndex == index,
                                onClick = {
                                    selectedTabIndex = index
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = "vm",
                    modifier = Modifier.padding(paddingValues)
                ) {
                    composable("vm") {
                        VmManagementScreen(
                            vmManagerService = vmManagerService,
                            backupManagerService = backupManagerService,
                            viewModel = vmManagementViewModel,
                            onNavigateToCreate = {
                                navController.navigate("vm/create")
                            }
                        )
                    }
                    composable("vm/create") {
                        CreateVmScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onCreateVm = { name, template, protectedVm, customMem, customCpu, customDisk ->
                                vmManagementViewModel.createVm(
                                    name, template, protectedVm,
                                    customMem, customCpu, customDisk
                                )
                                navController.popBackStack()
                            },
                            avfCapabilities = vmManagerService?.avfCapabilities?.value
                        )
                    }
                    composable("docker") {
                        DockerDashboardScreen(viewModel = dockerViewModel)
                    }
                    composable("terminal") {
                        TerminalScreen(
                            consoleOutputService = consoleOutputService,
                            vsockService = vsockService
                        )
                    }
                    composable("settings") {
                        SettingsScreen(viewModel = settingsViewModel)
                    }
                }
            }
        }
    }
}

/** 权限检测页面已读标记 — 安装后仅展示一次 */
private val PERMISSION_CHECK_PASSED_KEY = booleanPreferencesKey("permission_check_passed")
