package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.NetworkMode
import com.example.data.model.VirtualInstance
import com.example.data.model.VirtualOsArchitectureMode
import com.example.engine.ContainerEngineConfig
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceSettingsScreen(
    instance: VirtualInstance,
    rootfsDir: File,
    onGenerateImei: () -> String,
    onSave: (VirtualInstance) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var name by remember(instance.id) { mutableStateOf(instance.name) }
    var engineMode by remember(instance.id) { mutableStateOf(instance.engineMode) }
    var widthText by remember(instance.id) { mutableStateOf(instance.widthPx.toString()) }
    var heightText by remember(instance.id) { mutableStateOf(instance.heightPx.toString()) }
    var dpi by remember(instance.id) { mutableFloatStateOf(instance.dpi.toFloat()) }
    var ramMb by remember(instance.id) { mutableFloatStateOf(instance.ramLimitMb.toFloat()) }
    var cpuCores by remember(instance.id) { mutableFloatStateOf(instance.cpuCores.toFloat()) }
    var rootEnabled by remember(instance.id) { mutableStateOf(instance.rootEnabled) }

    var spoofBrand by remember(instance.id) { mutableStateOf(instance.spoofBrand) }
    var spoofModel by remember(instance.id) { mutableStateOf(instance.spoofModel) }
    var spoofManufacturer by remember(instance.id) { mutableStateOf(instance.spoofManufacturer) }
    var spoofImei by remember(instance.id) { mutableStateOf(instance.spoofImei) }
    var spoofAndroidId by remember(instance.id) { mutableStateOf(instance.spoofAndroidId) }

    var mockLocationEnabled by remember(instance.id) { mutableStateOf(instance.mockLocationEnabled) }
    var mockLocationLabel by remember(instance.id) { mutableStateOf(instance.mockLocationLabel) }
    var mockLat by remember(instance.id) { mutableStateOf(instance.mockLatitude.toString()) }
    var mockLng by remember(instance.id) { mutableStateOf(instance.mockLongitude.toString()) }

    var networkMode by remember(instance.id) { mutableStateOf(instance.networkMode) }
    var sharedClipboard by remember(instance.id) { mutableStateOf(instance.sharedClipboardEnabled) }

    val draftInstance = instance.copy(
        name = name.ifBlank { instance.name },
        engineMode = engineMode,
        widthPx = widthText.toIntOrNull()?.coerceIn(360, 2160) ?: instance.widthPx,
        heightPx = heightText.toIntOrNull()?.coerceIn(640, 3840) ?: instance.heightPx,
        dpi = dpi.toInt(),
        ramLimitMb = ramMb.toInt(),
        cpuCores = cpuCores.toInt(),
        rootEnabled = rootEnabled,
        spoofBrand = spoofBrand,
        spoofModel = spoofModel,
        spoofManufacturer = spoofManufacturer,
        spoofImei = spoofImei,
        spoofAndroidId = spoofAndroidId,
        mockLocationEnabled = mockLocationEnabled,
        mockLocationLabel = mockLocationLabel,
        mockLatitude = mockLat.toDoubleOrNull() ?: instance.mockLatitude,
        mockLongitude = mockLng.toDoubleOrNull() ?: instance.mockLongitude,
        networkMode = networkMode,
        sharedClipboardEnabled = sharedClipboard
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan: ${instance.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onSave(draftInstance)
                            onBack()
                        },
                        modifier = Modifier.testTag("save_instance_settings_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Simpan Pengaturan")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsSectionCard(title = "Identitas & Engine VM") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Instance") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Arsitektur Engine", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VirtualOsArchitectureMode.entries.forEach { mode ->
                            FilterChip(
                                selected = engineMode == mode,
                                onClick = { engineMode = mode },
                                label = { Text(mode.shortLabel) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Akses Root Virtual (su)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Sediakan biner su & ro.debuggable=1 di dalam Android 10",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = rootEnabled, onCheckedChange = { rootEnabled = it })
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Layar, DPI & Komputasi") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("720x1280" to (720 to 1280), "1080x1920" to (1080 to 1920), "1080x2400" to (1080 to 2400)).forEach { (label, pair) ->
                            FilterChip(
                                selected = widthText == pair.first.toString() && heightText == pair.second.toString(),
                                onClick = {
                                    widthText = pair.first.toString()
                                    heightText = pair.second.toString()
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { widthText = it.filter(Char::isDigit) },
                            label = { Text("Lebar (px)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it.filter(Char::isDigit) },
                            label = { Text("Tinggi (px)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Kepadatan Layar (DPI): ${dpi.toInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(value = dpi, onValueChange = { dpi = it }, valueRange = 240f..480f, steps = 11)

                    Text("Limit RAM: ${ramMb.toInt()} MB", style = MaterialTheme.typography.labelLarge)
                    Slider(value = ramMb, onValueChange = { ramMb = it }, valueRange = 1024f..6144f, steps = 9)

                    Text("Alokasi CPU: ${cpuCores.toInt()} Core", style = MaterialTheme.typography.labelLarge)
                    Slider(value = cpuCores, onValueChange = { cpuCores = it }, valueRange = 1f..8f, steps = 6)
                }
            }

            item {
                SettingsSectionCard(title = "Model Perangkat Palsu (Spoofing)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Google" to "Pixel 4", "Samsung" to "SM-G975F", "Xiaomi" to "Mi 10").forEach { (b, m) ->
                            FilterChip(
                                selected = spoofBrand == b && spoofModel == m,
                                onClick = {
                                    spoofBrand = b
                                    spoofManufacturer = b
                                    spoofModel = m
                                },
                                label = { Text("$b $m") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = spoofBrand,
                            onValueChange = { spoofBrand = it; spoofManufacturer = it },
                            label = { Text("Merek") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = spoofModel,
                            onValueChange = { spoofModel = it },
                            label = { Text("Model") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = spoofImei,
                            onValueChange = { spoofImei = it },
                            label = { Text("IMEI Virtual (Luhn)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(onClick = { spoofImei = onGenerateImei() }) {
                            Icon(Icons.Default.Casino, contentDescription = "Acak IMEI")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = spoofAndroidId,
                        onValueChange = { spoofAndroidId = it },
                        label = { Text("Android ID Virtual") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SettingsSectionCard(title = "Lokasi Palsu, Jaringan & Clipboard") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lokasi GPS Palsu", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = mockLocationEnabled, onCheckedChange = { mockLocationEnabled = it })
                    }
                    if (mockLocationEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("Jakarta", -6.2088, 106.8456),
                                Triple("Surabaya", -7.2575, 112.7521),
                                Triple("Tokyo", 35.6762, 139.6503)
                            ).forEach { (city, lat, lng) ->
                                FilterChip(
                                    selected = mockLocationLabel == city,
                                    onClick = {
                                        mockLocationLabel = city
                                        mockLat = lat.toString()
                                        mockLng = lng.toString()
                                    },
                                    label = { Text(city) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = mockLat,
                                onValueChange = { mockLat = it },
                                label = { Text("Latitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = mockLng,
                                onValueChange = { mockLng = it },
                                label = { Text("Longitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Mode Jaringan", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkMode.entries.forEach { mode ->
                            FilterChip(
                                selected = networkMode == mode,
                                onClick = { networkMode = mode },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Clipboard Bersama Host <-> Virtual", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = sharedClipboard, onCheckedChange = { sharedClipboard = it })
                    }
                }
            }

            item {
                val cmdPreview = remember(draftInstance) {
                    ContainerEngineConfig.buildCommandPreview(draftInstance, rootfsDir).joinToString(" ")
                }
                SettingsSectionCard(title = "Pratinjau Perintah Container / QEMU") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cmdPreview,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onSave(draftInstance)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simpan & Sinkronkan build.prop")
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
