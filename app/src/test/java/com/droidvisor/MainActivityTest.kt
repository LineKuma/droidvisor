package com.droidvisor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {

    @Test
    fun navItem_creation_succeeds() {
        val icon: ImageVector = Icons.Default.Cloud
        val navItem = NavItem("test", "Test", icon)
        assertNotNull(navItem.route)
        assertEquals("test", navItem.route)
        assertEquals("Test", navItem.title)
        assertEquals(icon, navItem.icon)
    }

    @Test
    fun navItem_route_is_accessible() {
        val icon: ImageVector = Icons.Default.Cloud
        val navItem = NavItem("dashboard", "Dashboard", icon)
        assertTrue(navItem.route == "dashboard")
    }
}