package com.wulong.dict.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.orderDataStore: DataStore<Preferences> by preferencesDataStore(name = "dict_order")

class DictionaryOrderSettings(private val context: Context) {

    val dictOrder: Flow<List<String>> = context.orderDataStore.data.map { prefs ->
        val raw = prefs[ORDER_KEY] ?: ""
        if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    suspend fun setOrder(order: List<String>) {
        context.orderDataStore.edit { prefs ->
            prefs[ORDER_KEY] = order.joinToString(",")
        }
    }

    companion object {
        private val ORDER_KEY = stringPreferencesKey("dict_order_list")
    }
}
