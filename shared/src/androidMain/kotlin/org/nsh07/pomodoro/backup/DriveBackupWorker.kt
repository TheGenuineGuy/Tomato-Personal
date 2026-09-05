package org.nsh07.pomodoro.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val manager: GoogleDriveBackupManager by inject()

    override suspend fun doWork(): Result {
        return try {
            manager.performBackupNow()
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
