package com.droidvisor.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DataStoreFactoryTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = Robolectric.setupActivity(android.app.Activity::class.java)
    }

    @Test
    fun dataStore_extension_isPresent() {
        val dataStore = context.dataStore
        assertNotNull(dataStore)
    }

    @Test
    fun dataStore_returnsSameInstance() {
        val dataStore1 = context.dataStore
        val dataStore2 = context.dataStore

        assertNotNull(dataStore1)
        assertNotNull(dataStore2)
        assertSame(dataStore1, dataStore2)
    }

    @Test
    fun dataStore_canBeUsedForPreferences() {
        val dataStore: DataStore<Preferences> = context.dataStore
        assertNotNull(dataStore)
    }
}