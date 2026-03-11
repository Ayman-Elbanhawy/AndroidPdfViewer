package com.aymanelbanhawy.enterprisepdf.app

import android.app.Application
import androidx.work.Configuration
import com.aymanelbanhawy.editor.core.EditorCoreContainer

class EnterprisePdfApplication : Application(), Configuration.Provider {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(EditorCoreContainer(this))
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
