package com.jpark.alarmcard

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jpark.alarmcard.data.CardRepository
import com.jpark.alarmcard.notify.AutoDisableWorker
import com.jpark.alarmcard.notify.AutoEnableWorker
import com.jpark.alarmcard.notify.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AlarmCardApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var repository: CardRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        NotificationHelper.ensureChannel(this)
        AutoDisableWorker.scheduleNext(this)
        rescheduleAutoEnableWorkers()
    }

    private fun rescheduleAutoEnableWorkers() {
        applicationScope.launch {
            try {
                repository.getAutoEnabledCards().forEach { entity ->
                    AutoEnableWorker.scheduleNext(this@AlarmCardApp, entity)
                }
            } catch (t: Throwable) {
                Timber.w(t, "Failed to restore auto-enable workers on app start")
            }
        }
    }
}
