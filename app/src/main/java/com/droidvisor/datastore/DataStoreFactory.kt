package com.droidvisor.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "droidvisor_preferences")

/** 初始化流程是否已通过 */
val SETUP_PASSED_KEY = booleanPreferencesKey("setup_passed")