package org.nsh07.pomodoro.ui.settingsScreen.screens.backupRestore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.nsh07.pomodoro.backup.GoogleDriveBackupManager
import org.nsh07.pomodoro.backup.DriveBackupItem
import androidx.compose.material3.SegmentedListItem
import org.nsh07.pomodoro.ui.theme.TomatoShapeDefaults.segmentedListItemShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun DriveBackupSection() {
    val backupManager: GoogleDriveBackupManager = koinInject()
    
    val isSignedIn by backupManager.isSignedIn.collectAsState()
    val accountEmail by backupManager.accountEmail.collectAsState()
    val lastBackupTimestamp by backupManager.lastBackupTimestamp.collectAsState()
    val isBackingUp by backupManager.isBackingUp.collectAsState()
    val isRestoring by backupManager.isRestoring.collectAsState()
    
    val scope = rememberCoroutineScope()
    
    var showRestoreDialog by remember { mutableStateOf(false) }
    var availableBackups by remember { mutableStateOf<List<DriveBackupItem>>(emptyList()) }
    var isFetchingBackups by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        backupManager.handleSignInResult(result.data)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Google Drive Backup",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
        )
        
        if (!isSignedIn) {
            SegmentedListItem(
                shapes = segmentedListItemShapes(0, 1),
                onClick = { signInLauncher.launch(backupManager.getSignInIntent()) },
                content = { Text("Connect Google Drive") },
                supportingContent = { Text("Automatic daily backups") }
            )
        } else {
            SegmentedListItem(
                shapes = segmentedListItemShapes(0, 4),
                onClick = {},
                content = { Text(accountEmail ?: "Connected") },
                supportingContent = { 
                    val timeString = lastBackupTimestamp?.let { 
                        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: "Never"
                    Text("Last backup: $timeString") 
                }
            )
            
            SegmentedListItem(
                shapes = segmentedListItemShapes(1, 4),
                onClick = {
                    if (!isBackingUp && !isRestoring) {
                        scope.launch { backupManager.performBackupNow() }
                    }
                },
                content = { Text("Back up now") },
                trailingContent = {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            )
            
            SegmentedListItem(
                shapes = segmentedListItemShapes(2, 4),
                onClick = {
                    if (!isBackingUp && !isRestoring && !isFetchingBackups) {
                        isFetchingBackups = true
                        scope.launch {
                            availableBackups = backupManager.fetchAvailableBackups()
                            isFetchingBackups = false
                            showRestoreDialog = true
                        }
                    }
                },
                content = { Text("Restore from Drive") },
                trailingContent = {
                    if (isFetchingBackups || isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            )

            SegmentedListItem(
                shapes = segmentedListItemShapes(3, 4),
                onClick = { backupManager.signOut() },
                content = { Text("Disconnect") },
                colors = ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.error)
            )
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Backup") },
            text = {
                if (availableBackups.isEmpty()) {
                    Text("No backups found in Google Drive.")
                } else {
                    Column {
                        Text("Select a backup to restore. This will overwrite current data and restart the app.")
                        Spacer(Modifier.height(16.dp))
                        availableBackups.take(5).forEach { backup ->
                            val dateString = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(backup.timestampMs))
                            val sizeKb = backup.sizeBytes / 1024
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showRestoreDialog = false
                                        scope.launch { backupManager.performRestoreFromDrive(backup.id) }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(dateString, style = MaterialTheme.typography.bodyLarge)
                                    Text("$sizeKb KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
