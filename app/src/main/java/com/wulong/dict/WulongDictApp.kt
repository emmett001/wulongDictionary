package com.wulong.dict

import android.app.Application

class WulongDictApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContainer = AppContainer(this)

        // Pre-warm WebView pool to avoid cold-start on first dictionary lookup
        appContainer.webViewPool.preWarm(2)
    }

    companion object {
        lateinit var instance: WulongDictApp
            private set
    }
}
