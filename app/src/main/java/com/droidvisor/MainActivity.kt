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
import androidx.compose.material.icons.filled.Container
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Vm
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.droidvisor.datastore.dataStore
import com.droidvisor.docker.DockerProxyService
import com.droidvisor.ui.screen.DockerScreen
import com.droidvisor.ui.screen.SettingsScreen
import com.droidvisor.ui.screen.TerminalScreen
import com.droidvisor.ui.screen.VmScreen
import com.droidvisor.ui.viewmodel.DockerViewModel
import com.droidvisor.ui.viewmodel.SettingsViewModel
import com.droidvisor.ui.viewmodel.VmStatusViewModel
import com.droidvisor.vm.ConsoleOutputService
import com.droidvisor.vm.VirtualMachineManagerService
import com.droidvisor.vm.vsock.VsockService

class MainActivity : ComponentActivity() {

    private var vmService: VirtualMachineManagerService? = null
    private var consoleService: ConsoleOutputService? = null
    private var vsockService: VsockService? = null
    private var dockerProxyService: DockerProxyService? = null

    private var vmServiceBound = false
    private var consoleServiceBound = false
    private var vsockServiceBound = false
    private var dockerProxyServiceBound = false

    private val vmServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VirtualMachineManagerService.LocalBinder
            vmService = binder.getService()
            vmServiceBound = true
            consoleService?.let { vmService?.attachConsoleOutputService(it) }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            vmServiceBound = false
            vmService = null
        }
    }

    private val consoleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ConsoleOutputService.LocalBinder
            consoleService = binder.getService()
            consoleServiceBound = true
            vmService?.let { it.attachConsoleOutputService(consoleService!!) }
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
            dockerProxyService?.let { it.attachVsockService(vsockService!!) }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            vsockServiceBound = false
            vsockService = null
        }
    }

    private val dockerProxyServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DockerProxyService.LocalBinder
            dockerProxyService = binder.getService()
            dockerProxyServiceBound = true
            vsockService?.let { dockerProxyService?.attachVsockService(it) }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            dockerProxyServiceBound = false
            dockerProxyService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Intent(this, VirtualMachineManagerService::class.java).also { intent ->
            bindService(intent, vmServiceConnection, Context.BIND_AUTO_CREATE)
        }

        Intent(this, ConsoleOutputService::class.java).also { intent ->
            bindService(intent, consoleServiceConnection, Context.BIND_AUTO_CREATE)
        }

        Intent(this, VsockService::class.java).also { intent ->
            bindService(intent, vsockServiceConnection, Context.BIND_AUTO_CREATE)
        }

        Intent(this, DockerProxyService::class.java).also { intent ->
            bindService(intent, dockerProxyServiceConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            DroidvisorApp(
                vmService = vmService,
                consoleOutputService = consoleService,
                dockerProxyService = dockerProxyService
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (vmServiceBound) {
            unbindService(vmServiceConnection)
            vmServiceBound = false
        }
        if (consoleServiceBound) {
            unbindService(consoleServiceConnection)
            consoleServiceBound = false
        }
        if (vsockServiceBound) {
            unbindService(vsockServiceConnection)
            vsockServiceBound = false
        }
        if (dockerProxyServiceBound) {
            unbindService(dockerProxyServiceConnection)
            dockerProxyServiceBound = false
        }
    }
}

@Composable
fun DroidvisorApp(
    vmService: VirtualMachineManagerService?,
    consoleOutputService: ConsoleOutputService?,
    dockerProxyService: DockerProxyService?
) {
    val navController = rememberNavController()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val vmStatusViewModel: VmStatusViewModel = viewModel()
    val dockerViewModel: DockerViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(androidx.compose.ui.platform.LocalContext.current.dataStore)
    }

    vmService?.let { vmStatusViewModel.setVmService(it) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = "vm") {
                composable("vm") {
                    VmScreen(viewModel = vmStatusViewModel)
                }
                composable("terminal") {
                    TerminalScreen(consoleOutputService = consoleOutputService)
                }
                composable("docker") {
                    DockerScreen(dockerProxyService = dockerProxyService)
                }
                composable("settings") {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }

        NavigationBar {
            val navItems = listOf(
                Triple("vm", "VM", Icons.Default.Vm),
                Triple("terminal", "终端", Icons.Default.Terminal),
                Triple("docker", "Docker", Icons.Default.Container),
                Triple("settings", "设置", Icons.Default.Settings)
            )

            navItems.forEachIndexed { index, item ->
                val (route, label, icon) = item
                NavigationBarItem(
                    icon = { androidx.compose.material3.Icon(icon, contentDescription = label) },
                    label = { Text(label) },
                    selected = selectedTabIndex == index,
                    onClick = {
                        selectedTabIndex = index
                        navController.navigate(route)
                    }
                )
            }
        }
    }
}