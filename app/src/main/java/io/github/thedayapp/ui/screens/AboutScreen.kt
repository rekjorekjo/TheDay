package io.github.thedayapp.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thedayapp.BuildConfig
import io.github.thedayapp.R
import io.github.thedayapp.ui.documents.AppDocument
import io.github.thedayapp.update.AppUpdateManager
import io.github.thedayapp.update.UpdateCheckResult
import io.github.thedayapp.update.UpdateDownloadState
import io.github.thedayapp.update.UpdatePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.thedayapp.update.UpdateDownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenDocument: (AppDocument) -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(82.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    UpdateSection()

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    AboutItem(
                        title = stringResource(R.string.update_notes),
                        onClick = { onOpenDocument(AppDocument.UPDATE_NOTES) },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    AboutItem(
                        title = stringResource(R.string.usage_guide),
                        onClick = { onOpenDocument(AppDocument.USAGE_GUIDE) },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    AboutItem(
                        title = stringResource(R.string.privacy_policy),
                        onClick = { onOpenDocument(AppDocument.PRIVACY_POLICY) },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    AboutItem(
                        title = stringResource(R.string.open_source_notices),
                        onClick = { onOpenDocument(AppDocument.OPEN_SOURCE_NOTICES) },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    AboutItem(
                        title = stringResource(R.string.github_repository),
                        onClick = {
                            uriHandler.openUri("https://github.com/rekjorekjo/TheDay")
                        },
                        isExternal = true,
                    )
                }
            }

        }
    }
}

@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }
    val preferences = remember { UpdatePreferences(context) }

    var isChecking by remember { mutableStateOf(false) }
    var isCheckingGlass by remember { mutableStateOf(false) }
    var checkMessage by remember { mutableStateOf<String?>(null) }
    var glassUpgradeMessage by remember { mutableStateOf<String?>(null) }
    var wifiOnly by remember { mutableStateOf(preferences.wifiOnly) }
    var downloadStatus by remember { mutableStateOf(updateManager.currentStatus()) }
    var isVerifyingLocally by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            downloadStatus = updateManager.currentStatus()

            delay(
                when (downloadStatus.state) {
                    UpdateDownloadState.WAITING,
                    UpdateDownloadState.DOWNLOADING,
                    UpdateDownloadState.VERIFYING -> 1000L

                    else -> 3000L
                },
            )
        }
    }

    LaunchedEffect(downloadStatus.state) {
        if (downloadStatus.state == UpdateDownloadState.VERIFYING && !isVerifyingLocally) {
            isVerifyingLocally = true

            try {
                updateManager.verifyPendingDownloadIfNeeded()
            } finally {
                isVerifyingLocally = false
                downloadStatus = updateManager.currentStatus()
            }
        }
    }

    val downloadBusy = downloadStatus.state == UpdateDownloadState.WAITING ||
        downloadStatus.state == UpdateDownloadState.DOWNLOADING ||
        downloadStatus.state == UpdateDownloadState.VERIFYING

    // 检查更新行
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(enabled = !isChecking && !downloadBusy) {
                when (downloadStatus.state) {
                    UpdateDownloadState.NONE, UpdateDownloadState.FAILED -> {
                        coroutineScope.launch {
                            updateManager.resetFailedDownloadForManualCheck()
                            downloadStatus = updateManager.currentStatus()
                            isChecking = true
                            checkMessage = null

                            try {
                                when (val result = updateManager.checkForUpdate()) {
                                    is UpdateCheckResult.UpdateAvailable -> {
                                        val started = updateManager.startDownload(result.release)
                                        if (started) {
                                            checkMessage = context.getString(
                                                R.string.update_downloading,
                                                result.release.versionName,
                                            )
                                        } else {
                                            checkMessage = context.getString(R.string.update_download_failed)
                                        }
                                    }

                                    UpdateCheckResult.UpToDate -> {
                                        checkMessage = context.getString(R.string.update_upto_date)
                                    }

                                    UpdateCheckResult.CheckFailed -> {
                                        checkMessage = context.getString(R.string.update_failed)
                                    }
                                }

                                downloadStatus = updateManager.currentStatus()
                            } finally {
                                isChecking = false
                            }
                        }
                    }
                    UpdateDownloadState.READY -> {
                        val activity = context.findActivity()
                        if (activity != null) {
                            updateManager.requestInstall(activity)
                        }
                    }
                    else -> {
                        // WAITING, DOWNLOADING, VERIFYING 状态不执行操作
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.update_check_button),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = getStatusText(
                isChecking = isChecking,
                downloadStatus = downloadStatus,
                checkMessage = checkMessage,
                context = context,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))

    if (BuildConfig.EDITION == "classic") {
        val glassDownloadPending = preferences.pendingAssetName?.startsWith("TheDay-Glass-") == true
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(enabled = !isCheckingGlass && !downloadBusy) {
                    if (downloadStatus.state == UpdateDownloadState.READY && glassDownloadPending) {
                        context.findActivity()?.let(updateManager::requestInstall)
                    } else {
                        coroutineScope.launch {
                            isCheckingGlass = true
                            glassUpgradeMessage = null
                            try {
                                when (val result = updateManager.checkForGlassUpgrade()) {
                                    is UpdateCheckResult.UpdateAvailable -> {
                                        val started = updateManager.startDownload(result.release)
                                        glassUpgradeMessage = if (started) {
                                            context.getString(R.string.glass_upgrade_downloading)
                                        } else {
                                            context.getString(R.string.update_download_failed)
                                        }
                                    }
                                    UpdateCheckResult.UpToDate -> {
                                        glassUpgradeMessage = context.getString(R.string.glass_upgrade_unavailable)
                                    }
                                    UpdateCheckResult.CheckFailed -> {
                                        glassUpgradeMessage = context.getString(R.string.update_failed)
                                    }
                                }
                                downloadStatus = updateManager.currentStatus()
                            } finally {
                                isCheckingGlass = false
                            }
                        }
                    }
                }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.glass_upgrade_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.glass_upgrade_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = when {
                    isCheckingGlass -> stringResource(R.string.update_checking)
                    glassDownloadPending -> getStatusText(
                        isChecking = false,
                        downloadStatus = downloadStatus,
                        checkMessage = glassUpgradeMessage,
                        context = context,
                    )
                    glassUpgradeMessage != null -> glassUpgradeMessage!!
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
    }

    // Wi-Fi 行
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.update_wifi_only_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.update_wifi_only_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = wifiOnly,
            onCheckedChange = { newValue ->
                wifiOnly = newValue
                preferences.wifiOnly = newValue
            },
        )
    }
}

@Composable
private fun getStatusText(
    isChecking: Boolean,
    downloadStatus: UpdateDownloadStatus,
    checkMessage: String?,
    context: Context,
): String {
    return when {
        isChecking -> context.getString(R.string.update_checking)
        checkMessage == context.getString(R.string.update_failed) -> context.getString(R.string.update_failed)
        checkMessage == context.getString(R.string.update_upto_date) -> context.getString(R.string.update_upto_date)
        downloadStatus.state == UpdateDownloadState.WAITING -> context.getString(R.string.update_waiting_network)
        downloadStatus.state == UpdateDownloadState.DOWNLOADING -> {
            val progressPercent = downloadStatus.progressPercent
            if (progressPercent != null) {
                context.getString(R.string.update_downloading_progress, progressPercent)
            } else {
                context.getString(R.string.update_downloading_without_progress)
            }
        }
        downloadStatus.state == UpdateDownloadState.VERIFYING -> context.getString(R.string.update_verifying)
        downloadStatus.state == UpdateDownloadState.READY -> context.getString(R.string.update_install_button)
        downloadStatus.state == UpdateDownloadState.FAILED -> context.getString(R.string.update_download_failed)
        else -> context.getString(R.string.update_upto_date)
    }
}

@Composable
private fun AboutItem(
    title: String,
    onClick: () -> Unit,
    isExternal: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (isExternal) Icons.Rounded.OpenInNew else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}