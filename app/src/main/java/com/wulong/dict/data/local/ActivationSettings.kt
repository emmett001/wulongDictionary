package com.wulong.dict.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.activationStore: DataStore<Preferences> by preferencesDataStore(name = "activation")

class ActivationSettings(private val context: Context) {

    val isActivated: Flow<Boolean> = context.activationStore.data.map { prefs ->
        prefs[KEY_ACTIVATED] ?: false
    }

    val inviteNo: Flow<String> = context.activationStore.data.map { prefs ->
        prefs[KEY_INVITE_NO] ?: ""
    }

    suspend fun setActivated(inviteNo: String) {
        context.activationStore.edit { prefs ->
            prefs[KEY_ACTIVATED] = true
            prefs[KEY_INVITE_NO] = inviteNo
        }
    }

    companion object {
        private val KEY_ACTIVATED  = booleanPreferencesKey("activated")
        private val KEY_INVITE_NO  = stringPreferencesKey("invite_no")
    }
}
