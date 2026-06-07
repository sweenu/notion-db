package com.notiondb.widgets

import android.app.Application
import com.notiondb.widgets.di.AppContainer
import com.notiondb.widgets.work.WidgetRefreshScheduler

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Keep placed widgets fresh even if the app is never reopened.
        WidgetRefreshScheduler.ensurePeriodic(this)
    }
}
