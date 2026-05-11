package com.wulong.dict

import android.app.Application
import com.wulong.dict.data.local.LanguageSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class WulongDictApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val languageSettings = LanguageSettings(this)
        val initialLanguage = runBlocking { languageSettings.languageCode.first() }

        appContainer = AppContainer(this, languageSettings, initialLanguage)

        // Pre-warm WebView pool to avoid cold-start on first dictionary lookup
        appContainer.webViewPool.preWarm(2)
    }

    companion object {
        lateinit var instance: WulongDictApp
            private set
    }
}
