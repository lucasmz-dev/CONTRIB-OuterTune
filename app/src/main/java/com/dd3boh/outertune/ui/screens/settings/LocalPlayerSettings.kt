package com.dd3boh.outertune.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AutomaticScannerKey
import com.dd3boh.outertune.constants.DevSettingsKey
import com.dd3boh.outertune.constants.DialogCornerRadius
import com.dd3boh.outertune.constants.LookupYtmArtistsKey
import com.dd3boh.outertune.constants.ScanPathsKey
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.constants.ScannerSensitivityKey
import com.dd3boh.outertune.constants.ScannerStrictExtKey
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerTypeKey
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.utils.DEFAULT_SCAN_PATH

import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.purgeCache
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.getScanner
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.unloadAdvancedScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    context: Context,
    database: MusicDatabase,
) {
   val mediaPermissionLevel =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    val coroutineScope = rememberCoroutineScope()

    // scanner vars
    var isScannerActive by remember { mutableStateOf(false) }
    var isScanFinished by remember { mutableStateOf(false) }
    var mediaPermission by remember { mutableStateOf(true) }
    var showFilePickerDialog by remember {
        mutableStateOf(false)
    }

    // scanner prefs
    val (scannerType, onScannerTypeChange) = rememberEnumPreference(
        key = ScannerTypeKey,
        defaultValue = ScannerImpl.MEDIASTORE_FFPROBE
    )
    val (scannerSensitivity, onScannerSensitivityChange) = rememberEnumPreference(
        key = ScannerSensitivityKey,
        defaultValue = ScannerMatchCriteria.LEVEL_2
    )
    val (strictExtensions, onStrictExtensionsChange) = rememberPreference(ScannerStrictExtKey, defaultValue = false)
    val (autoScan, onAutoScanChange) = rememberPreference(AutomaticScannerKey, defaultValue = true)
    val (scanPaths, onScanPathsChange) = rememberPreference(ScanPathsKey, defaultValue = DEFAULT_SCAN_PATH)

    var fullRescan by remember { mutableStateOf(false) }
    val (lookupYtmArtists, onlookupYtmArtistsChange) = rememberPreference(LookupYtmArtistsKey, defaultValue = true)

    // misc
    val (devSettings) = rememberPreference(DevSettingsKey, defaultValue = false)

    // other vars
    var tempScanPaths by remember { mutableStateOf("") }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        // automatic scanner
        SwitchPreference(
            title = { Text(stringResource(R.string.auto_scanner_title)) },
            description = stringResource(R.string.auto_scanner_description),
            icon = { Icon(Icons.Rounded.Autorenew, null) },
            checked = autoScan,
            onCheckedChange = onAutoScanChange
        )

        // file path selector
        PreferenceEntry(
            title = { Text(stringResource(R.string.scan_paths_title)) },
            onClick = {
                showFilePickerDialog = true
            },
        )

        if (showFilePickerDialog) {
            if (tempScanPaths.isEmpty()) {
                tempScanPaths = scanPaths
            }

            BasicAlertDialog(
                onDismissRequest = {
                    showFilePickerDialog = false
                    tempScanPaths = ""
                },
                content = {
                    Column(modifier = Modifier
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(DialogCornerRadius))
                        .padding(16.dp)
                    ) {
                        val dirPickerLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenDocumentTree()
                        ) { uri ->
                            if (uri?.path != null && !tempScanPaths!!.contains(uri.path!!)) {
                                tempScanPaths += "${uri.path}\n"
                            }
                        }

                        // main content
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Scan paths",
                                style = MaterialTheme.typography.titleLarge,
                            )

                            // folders list
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        RoundedCornerShape(ThumbnailCornerRadius)
                                    )
                            ) {
                                tempScanPaths!!.split('\n').forEach {
                                    if (it.isNotBlank())
                                        Row(modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .clickable { }) {
                                            Text(
                                                // I hate this but I'll do it properly... eventually
                                                text = if (it.substringAfter("tree/").substringBefore(':') == "primary") {
                                                    "Internal Storage/${it.substringAfter(':')}"
                                                } else {
                                                    "External (${it.substringAfter("tree/").substringBefore(':')})/${it.substringAfter(':')}"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .align(Alignment.CenterVertically)
                                            )
                                            IconButton(
                                                onClick = { tempScanPaths = tempScanPaths!!.replace("$it\n", "") },
                                                onLongClick = {}
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                }
                            }

                            // add folder button
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(onClick = { dirPickerLauncher.launch(null) }) {
                                    Text(stringResource(R.string.scan_paths_add_folder))
                                }

                                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(4.dp)
                                    )

                                    Text(
                                        stringResource(R.string.scan_paths_tooltip),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }

                        // bottom options
                        Row() {
                            Row(modifier = Modifier.weight(1f)) {
                                TextButton(
                                    onClick = {
                                        tempScanPaths = DEFAULT_SCAN_PATH
                                    },
                                ) {
                                    Text(stringResource(R.string.reset))
                                }
                            }

                            TextButton(
                                onClick = {
                                    showFilePickerDialog = false
                                    onScanPathsChange(tempScanPaths!!)
                                    tempScanPaths = ""
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }

                            TextButton(
                                onClick = {
                                    showFilePickerDialog = false
                                    tempScanPaths = ""
                                }
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    }
                }
            )
        }



        PreferenceGroupTitle(
            title = stringResource(R.string.manual_scanner_title)
        )

        // scanner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, // WHY WON'T YOU CENTER

        ) {
            Button(
                enabled = !isScannerActive,
                onClick = {
                    if (isScannerActive) {
                        return@Button
                    }

                    // check permission
                    if (context.checkSelfPermission(mediaPermissionLevel)
                        != PackageManager.PERMISSION_GRANTED
                    ) {

                        Toast.makeText(
                            context,
                            "The scanner requires storage permissions",
                            Toast.LENGTH_SHORT
                        ).show()

                        requestPermissions(
                            context as Activity,
                            arrayOf(mediaPermissionLevel), PackageManager.PERMISSION_GRANTED
                        )

                        mediaPermission = false
                        return@Button
                    } else if (context.checkSelfPermission(mediaPermissionLevel)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        mediaPermission = true
                    }

                    isScanFinished = false
                    isScannerActive = true

                    Toast.makeText(
                        context,
                        "Starting full library scan this may take a while...",
                        Toast.LENGTH_SHORT
                    ).show()
                    coroutineScope.launch(Dispatchers.IO) {
                        val scanner = getScanner()
                        // full rescan
                        if (fullRescan) {
                            val directoryStructure = scanner.scanLocal(context, database, scanPaths.split('\n'), scannerType).value
                            scanner.syncDB(database, directoryStructure.toList(), scannerSensitivity, strictExtensions, true)
                            unloadAdvancedScanner()

                            // start artist linking job
                            if (lookupYtmArtists) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    scanner.localToRemoteArtist(database)
                                }
                            }
                        } else {
                            // quick scan
                            val directoryStructure =  scanner.scanLocal(context, database, scanPaths.split('\n'), ScannerImpl.MEDIASTORE).value
                            scanner.quickSync(
                                database, directoryStructure.toList(), scannerSensitivity,
                                strictExtensions, scannerType
                            )
                            unloadAdvancedScanner()

                            // start artist linking job
                            if (lookupYtmArtists) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    scanner.localToRemoteArtist(database)
                                }
                            }
                        }

                        purgeCache()

                        isScannerActive = false
                        isScanFinished = true
                    }
                }
            ) {
                Text(
                    text = if (isScannerActive) {
                        "Scanning..."
                    } else if (isScanFinished) {
                        "Scan complete"
                    } else if (!mediaPermission) {
                        "No Permission"
                    } else {
                        "Scan"
                    }
                )
            }


            // progress indicator
            if (!isScannerActive) {
                return@Row
            }

            // padding hax
            VerticalDivider(
                modifier = Modifier.padding(5.dp)
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        // scanner checkboxes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = fullRescan,
                    onCheckedChange = { fullRescan = it }
                )
                Text(
                    stringResource(R.string.scanner_variant_rescan), color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = lookupYtmArtists,
                    onCheckedChange = onlookupYtmArtistsChange,
                )
                Text(
                    stringResource(R.string.scanner_online_artist_linking), color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )

            Text(
                stringResource(R.string.scanner_warning),
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }


        PreferenceGroupTitle(
            title = stringResource(R.string.scanner_settings_title)
        )

        // scanner type
        EnumListPreference(
            title = { Text(stringResource(R.string.scanner_type_title)) },
            icon = { Icon(Icons.Rounded.Speed, null) },
            selectedValue = scannerType,
            onValueSelected = onScannerTypeChange,
            valueText = {
                when (it) {
                    ScannerImpl.MEDIASTORE -> stringResource(R.string.scanner_type_mediastore)
                    ScannerImpl.MEDIASTORE_FFPROBE -> stringResource(R.string.scanner_type_mediastore_ffprobe)
                    ScannerImpl.FFPROBE -> stringResource(R.string.scanner_type_ffprobe)
                }
            }
        )

        // scanner sensitivity
        EnumListPreference(
            title = { Text(stringResource(R.string.scanner_sensitivity_title)) },
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            selectedValue = scannerSensitivity,
            onValueSelected = onScannerSensitivityChange,
            valueText = {
                when (it) {
                    ScannerMatchCriteria.LEVEL_1 -> stringResource(R.string.scanner_sensitivity_L1)
                    ScannerMatchCriteria.LEVEL_2 -> stringResource(R.string.scanner_sensitivity_L2)
                    ScannerMatchCriteria.LEVEL_3 -> stringResource(R.string.scanner_sensitivity_L3)
                }
            }
        )


        // strict file ext
        SwitchPreference(
            title = { Text(stringResource(R.string.scanner_strict_file_name_title)) },
            description = stringResource(R.string.scanner_strict_file_name_description),
            icon = { Icon(Icons.Rounded.TextFields, null) },
            checked = strictExtensions,
            onCheckedChange = onStrictExtensionsChange
        )


        if (devSettings) {
            PreferenceGroupTitle(
                title = stringResource(R.string.settings_debug)
            )

            PreferenceEntry(
                title = { Text("DEBUG: Nuke local lib") },
                icon = { Icon(Icons.Rounded.Backup, null) },
                onClick = {
                    Toast.makeText(
                        context,
                        "Nuking local files from database...",
                        Toast.LENGTH_SHORT
                    ).show()
                    coroutineScope.launch(Dispatchers.IO) {
                        Timber.tag("Settings").d("Nuke database status:  ${database.nukeLocalData()}")
                    }
                }
            )

            PreferenceEntry(
                title = { Text("DEBUG: Force local to remote artist migration NOW") },
                icon = { Icon(Icons.Rounded.Backup, null) },
                onClick = {
                    Toast.makeText(
                        context,
                        "Starting migration...",
                        Toast.LENGTH_SHORT
                    ).show()
                    coroutineScope.launch(Dispatchers.IO) {
                        Timber.tag("Settings").d("Nuke database (MANUAL TRIGGERED) status:  ${database.nukeLocalData()}")
                    }
                }
            )
        }

    }




    TopAppBar(
        title = { Text(stringResource(R.string.local_player_settings_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}