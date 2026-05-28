package com.droidvisor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.droidvisor.datastore.dataStore
import com.droidvisor.docker.DockerDashboardViewModel
import com.droidvisor.docker.DockerProxyService
import com.droidvisor.ui.screen.DockerDashboardScreen
import com.droidvisor.ui.screen.PermissionScreen
import com.droidvisor.ui.viewmodel.PermissionViewModel
import com.droidvisor.ui.screen.SettingsScreen
import com.droidvisor.ui.screen.TerminalScreen
import com.droidvisor.ui.screen.VmManagementScreen
import com.droidvisor.ui.viewmodel.SettingsViewModel
import com.droidvisor.vm.BackupManagerService
import com.droidvisor.vm.ConsoleOutputService
import com.droidvisor.vm.VmManagerService
import com.droidvisor.vm.vsock.VsockService

class MainActivity : ComponentActivity() {

    private var vmManagerService: VmManagerService? = null
    private var consoleService: ConsoleOutputService? = null
    private var vsockService: VsockService? = null
    private var backupManagerService: BackupManagerService? = null
    private var dockerProxyService: DockerProxyService? = null

    private var vmManagerBound = false
    private var consoleServiceBound = false
    private var vsockServiceBound = false
    private var backupManagerBound = false
    private var dockerProxyBound = false

    private val vmManagerConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VmManagerService.LocalBinder
            vmManagerService = binder.getService()
            vmManagerBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            vmManagerBound = false
            vmManagerService = null
        }
    }

    private val consoleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ConsoleOutputService.LocalBinder
            consoleService = binder.getService()
            consoleServiceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            consoleServiceBound = false
            consoleService = null
        }
    }

    private val vsockServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VsockService.LocalBinder
            vsockService = binder.getService()
            vsockServiceBound = true

            dockerProxyService?.attachVsockService(vsockService!!)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            vsockServiceBound = false
            vsockService = null
        }
    }

    private val backupManagerConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BackupManagerService.LocalBinder
            backupManagerService = binder.getService()
            backupManagerBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            backupManagerBound = false
            backupManagerService = null
        }
    }

    private val dockerProxyConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DockerProxyService.LocalBinder
            dockerProxyService = binder.getService()
            dockerProxyBound = true

            if (vsockService != null) {
                dockerProxyService?.attachVsockService(vsockService!!)
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            dockerProxyBound = false
            dockerProxyService = null
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
                vmManagerService = vmManagerService,
                consoleOutputService = consoleService,
                backupManagerService = backupManagerService,
                vsockService = vsockService,
                dockerProxyService = dockerProxyService
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (vmManagerBound) {
            unbindService(vmManagerConnection)
            vmManagerBound = false
        }
        if (consoleServiceBound) {
            unbindService(consoleServiceConnection)
            consoleServiceBound = false
        }
        if (vsockServiceBound) {
            unbindService(vsockServiceConnection)
            vsockServiceBound = false
        }
        if (backupManagerBound) {
            unbindService(backupManagerConnection)
            backupManagerBound = false
        }
        if (dockerProxyBound) {
            unbindService(dockerProxyConnection)
            dockerProxyBound = false
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
    vmManagerService: VmManagerService?,
    consoleOutputService: ConsoleOutputService?,
    backupManagerService: BackupManagerService?,
    vsockService: VsockService?,
    dockerProxyService: DockerProxyService?
) {
    val navController = rememberNavController()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var hasPassedPermissionCheck by remember { mutableStateOf(false) }

    val permissionViewModel: PermissionViewModel = viewModel()

    val permissionState by permissionViewModel.permissionState.collectAsState()

    if (!hasPassedPermissionCheck) {
        PermissionScreen(
            viewModel = permissionViewModel,
            onAllPermissionsGranted = { hasPassedPermissionCheck = true }
        )
    } else {
        val dockerViewModel: DockerDashboardViewModel = viewModel()

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
            Surface(modifier = Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = "vm") {
                    composable("vm") {
                        VmManagementScreen(
                            vmManagerService = vmManagerService,
                            backupManagerService = backupManagerService
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
        }
    }
}
