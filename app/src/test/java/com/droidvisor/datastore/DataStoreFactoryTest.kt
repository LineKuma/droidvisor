package com.droidvisor.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class DataStoreFactoryTest {

    private val mockContext = mock(Context::class.java)

    @Test
    fun dataStore_extension_isPresent() {
        val context = mockContext
        `when`(context.dataStore).thenReturn(mock(DataStore::class.java))

        val dataStore: DataStore<*> = context.dataStore
        assertNotNull(dataStore)
    }

    @Test
    fun dataStore_returnsSameInstance() {
        val context = mockContext
        val dataStore1 = context.dataStore
        val dataStore2 = context.dataStore

        assertNotNull(dataStore1)
        assertNotNull(dataStore2)
    }

    @Test
    fun dataStore_canBeUsedForPreferences() {
        val context = mockContext
        val dataStore = context.dataStore

        assertNotNull(dataStore)
    }
}