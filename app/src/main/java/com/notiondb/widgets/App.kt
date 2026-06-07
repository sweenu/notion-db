package com.notiondb.widgets

import android.app.Application
import com.notiondb.widgets.di.AppContainer

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
