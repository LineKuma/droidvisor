package com.droidvisor.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.File

class DataStoreFactoryTest {

    private lateinit var tempFile: File
    private lateinit var testDataStore: DataStore<Preferences>

    @Before
    fun setup() {
        tempFile = File.createTempFile("test_datastore_factory", ".preferences_pb")
        testDataStore = PreferenceDataStoreFactory.createWithPath(
            producePath = { tempFile.absolutePath.toPath() }
        )
    }

    @After
    fun tearDown() {
        if (::tempFile.isInitialized) {
            tempFile.delete()
        }
    }

    @Test
    fun dataStore_canBeCreated() {
        assertNotNull(testDataStore)
    }

    @Test
    fun dataStore_returnsSameInstance() {
        val dataStore1 = testDataStore
        val dataStore2 = testDataStore

        assertNotNull(dataStore1)
        assertNotNull(dataStore2)
        assertSame(dataStore1, dataStore2)
    }

    @Test
    fun dataStore_canBeUsedForPreferences() {
        val dataStore: DataStore<Preferences> = testDataStore
        assertNotNull(dataStore)
    }
}
