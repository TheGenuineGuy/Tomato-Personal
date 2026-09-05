package org.nsh07.pomodoro.ui.settingsScreen.screens.backupRestore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.nsh07.pomodoro.backup.GoogleDriveBackupManager
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
    
    val scope = rememberCoroutineScope()
    
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
                shapes = segmentedListItemShapes(0, 3),
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
                shapes = segmentedListItemShapes(1, 3),
                onClick = {
                    if (!isBackingUp) {
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
                shapes = segmentedListItemShapes(2, 3),
                onClick = { backupManager.signOut() },
                content = { Text("Disconnect") },
                colors = ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.error)
            )
        }
    }
}
