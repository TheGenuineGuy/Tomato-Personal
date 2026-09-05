package org.nsh07.pomodoro.backup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.nsh07.pomodoro.data.AppDatabase
import java.util.concurrent.TimeUnit
import androidx.sqlite.db.SimpleSQLiteQuery
import org.nsh07.pomodoro.data.SystemDao
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream

data class DriveBackupItem(
    val id: String,
    val name: String,
    val timestampMs: Long,
    val sizeBytes: Long
)

class GoogleDriveBackupManager(private val context: Context) : KoinComponent {

    private val prefs: SharedPreferences = context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
    
    private val systemDao: SystemDao by inject()
    private val database: AppDatabase by inject()

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _accountEmail = MutableStateFlow<String?>(null)
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    private val _lastBackupTimestamp = MutableStateFlow<Long?>(null)
    val lastBackupTimestamp: StateFlow<Long?> = _lastBackupTimestamp.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    init {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            _isSignedIn.value = true
            _accountEmail.value = account.email
        }
        val lastBackup = prefs.getLong("last_backup", 0L)
        if (lastBackup > 0L) {
            _lastBackupTimestamp.value = lastBackup
        }
    }

    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(Exception::class.java)
            if (account != null) {
                _isSignedIn.value = true
                _accountEmail.value = account.email
                schedulePeriodicBackup()
            }
        } catch (e: Exception) {
            Log.e("DriveBackup", "Sign in failed", e)
        }
    }

    fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
            _isSignedIn.value = false
            _accountEmail.value = null
            cancelPeriodicBackup()
        }
    }

    private fun schedulePeriodicBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "drive_backup_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun cancelPeriodicBackup() {
        WorkManager.getInstance(context).cancelUniqueWork("drive_backup_work")
    }
    
    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
            .apply { selectedAccountName = account.email }
            
        return Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("Potato")
            .build()
    }

    suspend fun fetchAvailableBackups(): List<DriveBackupItem> = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: return@withContext emptyList()
            
            val result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .execute()
                
            val files = result.files ?: emptyList()
            files.mapNotNull { file ->
                val id = file.id ?: return@mapNotNull null
                val name = file.name ?: return@mapNotNull null
                val time = file.createdTime?.value ?: 0L
                val size = file.getSize() ?: 0L
                DriveBackupItem(id, name, time, size)
            }
        } catch (t: Throwable) {
            Log.e("DriveBackup", "Fetch backups failed", t)
            emptyList()
        }
    }

    suspend fun performRestoreFromDrive(fileId: String): Boolean = withContext(Dispatchers.IO) {
        _isRestoring.value = true
        try {
            val driveService = getDriveService() ?: return@withContext false
            
            // Close database first
            database.close()
            
            val dbName = "app_database"
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.parentFile!!.exists()) dbFile.parentFile!!.mkdirs()
            
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
            
            val outputStream = FileOutputStream(dbFile)
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.close()
            
            // Restart the app cleanly
            restartApp()
            true
        } catch (t: Throwable) {
            Log.e("DriveBackup", "Restore failed", t)
            false
        } finally {
            _isRestoring.value = false
        }
    }
    
    private fun restartApp() {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component

        val mainIntent = Intent.makeRestartActivityTask(componentName)
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    suspend fun performBackupNow(): Boolean = withContext(Dispatchers.IO) {
        _isBackingUp.value = true
        try {
            val driveService = getDriveService() ?: return@withContext false

            // Check if local database is completely empty to prevent overwriting cloud backups
            val lastStatDate = database.statDao().getLastDate()
            val dbFile = context.getDatabasePath("app_database")
            
            // Checkpoint WAL
            systemDao.checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
            if (!dbFile.exists()) return@withContext false
            
            if (lastStatDate == null || dbFile.length() < 25000L) { // ~25KB is typically an empty sqlite db
                // Check if cloud has backups
                val backups = fetchAvailableBackups()
                if (backups.isNotEmpty()) {
                    Log.w("DriveBackup", "Local DB is empty but cloud has backups. Aborting backup to prevent overwrite.")
                    return@withContext false
                }
            }
            
            // Create backup file in Drive
            val timestamp = System.currentTimeMillis()
            val fileMeta = com.google.api.services.drive.model.File().apply {
                name = "potato_backup_v2_$timestamp.db"
                parents = listOf("appDataFolder")
            }
            
            val fileContent = FileContent("application/octet-stream", dbFile)
            driveService.files().create(fileMeta, fileContent).execute()
            
            // Cleanup old backups
            val result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name, createdTime)")
                .setOrderBy("createdTime desc")
                .execute()
                
            val files = result.files ?: emptyList()
            if (files.size > 5) {
                for (i in 5 until files.size) {
                    driveService.files().delete(files[i].id).execute()
                }
            }
            
            prefs.edit().putLong("last_backup", timestamp).apply()
            _lastBackupTimestamp.value = timestamp
            
            true
        } catch (t: Throwable) {
            Log.e("DriveBackup", "Backup failed", t)
            false
        } finally {
            _isBackingUp.value = false
        }
    }
}
