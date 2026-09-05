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

class GoogleDriveBackupManager(private val context: Context) : KoinComponent {

    private val prefs: SharedPreferences = context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
    
    private val systemDao: SystemDao by inject()

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _accountEmail = MutableStateFlow<String?>(null)
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    private val _lastBackupTimestamp = MutableStateFlow<Long?>(null)
    val lastBackupTimestamp: StateFlow<Long?> = _lastBackupTimestamp.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

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

    suspend fun performBackupNow(): Boolean = withContext(Dispatchers.IO) {
        _isBackingUp.value = true
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext false
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
                .apply { selectedAccount = account.account }
                
            val driveService = Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
                .setApplicationName("Potato")
                .build()

            // Checkpoint WAL
            systemDao.checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
            val dbFile = context.getDatabasePath("app_database")
            if (!dbFile.exists()) return@withContext false
            
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
        } catch (e: Exception) {
            Log.e("DriveBackup", "Backup failed", e)
            false
        } finally {
            _isBackingUp.value = false
        }
    }
}
