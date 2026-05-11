package com.wulong.dict.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wulong.dict.domain.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class LanguageSettings(private val context: Context) {

    val languageCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: Language.EN.code
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = code
        }
    }

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("language_code")
    }
}
