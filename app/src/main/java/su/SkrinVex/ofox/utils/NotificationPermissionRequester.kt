package su.SkrinVex.ofox.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val PREFS = "notif_prefs"
private const val KEY_LAUNCHES = "launches_since_deny"
private const val KEY_PERMANENTLY_DENIED = "permanently_denied"
private const val ASK_EVERY_N_LAUNCHES = 3

@Composable
fun NotificationPermissionRequester() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    val alreadyGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    if (alreadyGranted) return

    val permanentlyDenied = prefs.getBoolean(KEY_PERMANENTLY_DENIED, false)
    val launchesSinceDeny = prefs.getInt(KEY_LAUNCHES, 0)

    // Увеличиваем счётчик запусков
    LaunchedEffect(Unit) {
        prefs.edit().putInt(KEY_LAUNCHES, launchesSinceDeny + 1).apply()
    }

    val shouldAsk = !permanentlyDenied && (launchesSinceDeny == 0 || launchesSinceDeny % ASK_EVERY_N_LAUNCHES == 0)
    val shouldShowSettingsDialog = permanentlyDenied && launchesSinceDeny % ASK_EVERY_N_LAUNCHES == 0

    var showSettingsDialog by remember { mutableStateOf(shouldShowSettingsDialog) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            // Если Android больше не показывает диалог — помечаем как permanently denied
            val canAskAgain = (context as? androidx.activity.ComponentActivity)
                ?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
            if (canAskAgain && launchesSinceDeny > 0) {
                prefs.edit().putBoolean(KEY_PERMANENTLY_DENIED, true).apply()
            }
        } else {
            prefs.edit().putInt(KEY_LAUNCHES, 0).putBoolean(KEY_PERMANENTLY_DENIED, false).apply()
        }
    }

    LaunchedEffect(shouldAsk) {
        if (shouldAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Уведомления отключены") },
            text = { Text("Разрешите уведомления в настройках, чтобы получать сообщения от друзей.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Не сейчас") }
            }
        )
    }
}
