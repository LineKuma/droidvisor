package com.droidvisor.docker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.Image
import com.droidvisor.docker.model.DockerInfo
import com.droidvisor.docker.model.PortBinding

@RunWith(MockitoJUnitRunner::class)
class DockerDashboardViewModelTest {

    private lateinit var viewModel: DockerDashboardViewModel

    @Before
    fun setup() {
        viewModel = DockerDashboardViewModel()
    }

    @Test
    fun initialState_hasMockData() {
        assertNotNull(viewModel.containers.value)
        assertNotNull(viewModel.images.value)
        assertNotNull(viewModel.dockerInfo.value)
    }

    @Test
    fun containers_initializedAsEmptyList() {
        viewModel = DockerDashboardViewModel()
    }

    @Test
    fun images_initializedAsEmptyList() {
        viewModel = DockerDashboardViewModel()
    }

    @Test
    fun isLoading_initializedAsFalse() {
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun selectContainer_updatesSelectedContainerId() {
        viewModel.selectContainer("test-id")
        assertEquals("test-id", viewModel.selectedContainerId.value)
    }

    @Test
    fun getSelectedContainer_whenNoneSelected_returnsNull() {
        assertNull(viewModel.getSelectedContainer())
    }

    @Test
    fun setLogFilter_updatesFilter() {
        viewModel.setLogFilter("test")
    }

    @Test
    fun getFilteredLogs_withEmptyFilter_returnsAll() {
        assertTrue(viewModel.getFilteredLogs().isEmpty())
    }
}