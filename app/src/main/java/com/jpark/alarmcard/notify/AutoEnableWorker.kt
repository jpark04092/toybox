package com.jpark.alarmcard.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.jpark.alarmcard.data.CardRepository
import com.jpark.alarmcard.data.local.CardEntity
import com.jpark.alarmcard.domain.model.AutoEnableSchedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoEnableWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CardRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cardId = inputData.getString(EXTRA_CARD_ID) ?: return Result.failure()

        return try {
            handleAutoEnable(cardId)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun handleAutoEnable(cardId: String) {
        val entity = repository.getCardById(cardId) ?: return
        if (!entity.autoEnabled) return

        val now = Calendar.getInstance()
        if (AutoEnableSchedule.isSelected(now.get(Calendar.DAY_OF_WEEK), entity.autoEnableDays)) {
            if (entity.type == CardEntity.TYPE_BUS) {
                repository.setBusAlarm(cardId, true, entity.alarmMinutesBefore)
                BusAlarmWorker.scheduleNext(applicationContext, 5L)
            } else if (entity.type == CardEntity.TYPE_STOCK) {
                repository.setStockAlarm(cardId, true, entity.alarmPriceThreshold, entity.alarmRateThreshold)
                StockAlarmWorker.scheduleNext(applicationContext, 5L)
            }
        }

        // 다음 스케줄 예약
        scheduleNext(applicationContext, entity)
    }

    companion object {
        private const val EXTRA_CARD_ID = "card_id"
        private const val WORK_TAG_PREFIX = "auto_enable_"

        fun scheduleNext(context: Context, entity: CardEntity) {
            val time = entity.autoEnableTime ?: return
            val parts = time.split(":")
            if (parts.size != 2) return

            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return

            val nextRunAt = AutoEnableSchedule.nextRunTimeMillis(
                nowMillis = System.currentTimeMillis(),
                hour = hour,
                minute = minute,
                daysMask = entity.autoEnableDays
            ) ?: return

            val delayMs = (nextRunAt - System.currentTimeMillis()).coerceAtLeast(0L)
            
            val data = workDataOf(EXTRA_CARD_ID to entity.id)
            val request = OneTimeWorkRequestBuilder<AutoEnableWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG_PREFIX + entity.id)
                .setInputData(data)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_TAG_PREFIX + entity.id,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, cardId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG_PREFIX + cardId)
        }
    }
}
