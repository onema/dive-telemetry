package io.onema.divetelemetry.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Colors
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Popup
import io.onema.divetelemetry.plugins.BooleanParameter
import io.onema.divetelemetry.plugins.DiveLogPlugin
import io.onema.divetelemetry.plugins.IntParameter
import io.onema.divetelemetry.plugins.InterpolationPlugin
import io.onema.divetelemetry.plugins.OutputPlugin
import io.onema.divetelemetry.plugins.Plugin
import io.onema.divetelemetry.plugins.PluginParameter
import io.onema.divetelemetry.plugins.SafetyStopPlugin
import io.onema.divetelemetry.plugins.StringParameter
import io.onema.divetelemetry.plugins.TechnicalCCRPlugin
import io.onema.divetelemetry.plugins.TechnicalOCPlugin
import io.onema.divetelemetry.service.DiveComputerFormat
import io.onema.divetelemetry.service.defaultComputerFormats
import io.onema.divetelemetry.service.transformDiveLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import java.awt.FileDialog
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File

private val DarkBackground = Color(0xFF1A1D23)
private val CardBackground = Color(0xFF252830)
private val CardHeaderBackground = Color(0xFF2C3038)
private val AccentTeal = Color(0xFF00BCD4)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFB0B8C4)
private val SuccessGreen = Color(0xFF4CAF50)
private val ErrorRed = Color(0xFFEF5350)
private val StatusBarBackground = Color(0xFF15171C)

private val DarkColorPalette =
    Colors(
        primary = AccentTeal,
        primaryVariant = AccentTeal,
        secondary = AccentTeal,
        secondaryVariant = AccentTeal,
        background = DarkBackground,
        surface = CardBackground,
        error = ErrorRed,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onError = Color.White,
        isLight = false,
    )

private val availableFormats: List<DiveComputerFormat> = defaultComputerFormats

private val availablePlugins: List<DiveLogPlugin> =
    listOf(
        InterpolationPlugin,
        // EnforcePressureUnitPlugin, // Disable as this is a toy plugin.
    )

private val availableOutputPlugins: List<OutputPlugin> =
    listOf(
        TechnicalOCPlugin,
        TechnicalCCRPlugin,
        SafetyStopPlugin,
    )

@Composable
fun FrameWindowScope.App() {
    var selectedFormat by remember { mutableStateOf(availableFormats.first()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("Select a dive log file to convert.") }
    var isConverting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pluginState =
        remember {
            val initial = mutableStateMapOf<String, MutableMap<String, Any>>()
            for (plugin in availablePlugins + availableOutputPlugins) {
                val defaults = mutableMapOf<String, Any>()
                for (param in plugin.parameters) {
                    defaults[param.key] = param.defaultValue as Any
                }
                initial[plugin.id] = defaults
            }
            initial
        }

    MaterialTheme(colors = DarkColorPalette) {
        Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader()
                val onChooseFile = {
                    val dialog = FileDialog(window, "Select ${selectedFormat.name}", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name ->
                        selectedFormat.extensions.any { ext -> name.endsWith(ext) }
                    }
                    dialog.isVisible = true
                    if (dialog.file != null) {
                        selectedFile = File(dialog.directory, dialog.file)
                        outputFile = null
                        status = "Selected: ${dialog.file}"
                    }
                }
                val onFileDrop: (File) -> Unit = { file ->
                    if (selectedFormat.extensions.any { file.name.endsWith(it, ignoreCase = true) }) {
                        selectedFile = file
                        outputFile = null
                        status = "Selected: ${file.name}"
                    } else {
                        status = "Error: expected a ${selectedFormat.extensions.joinToString()} file for ${selectedFormat.name}"
                    }
                }
                MainContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    selectedFile = selectedFile,
                    selectedFormat = selectedFormat,
                    dropdownExpanded = dropdownExpanded,
                    onDropdownExpandedChange = { dropdownExpanded = it },
                    onFormatSelected = { format ->
                        if (selectedFormat != format) {
                            selectedFormat = format
                            selectedFile = null
                            status = "Select a dive log file to convert."
                        }
                        dropdownExpanded = false
                    },
                    onChooseFile = onChooseFile,
                    onFileDrop = onFileDrop,
                    plugins = availablePlugins,
                    outputPlugins = availableOutputPlugins,
                    pluginState = pluginState,
                    onPluginParamChange = { pluginId, key, value ->
                        val current = pluginState[pluginId].orEmpty()
                        pluginState[pluginId] = (current + (key to value)).toMutableMap()
                    },
                )
                ActionButtons(
                    selectedFile = selectedFile,
                    isConverting = isConverting,
                    onChooseFile = onChooseFile,
                    onConvert = {
                        val input = selectedFile ?: return@ActionButtons
                        val outputName = input.nameWithoutExtension + "-telemetry.csv"
                        val output = File(input.parentFile, outputName)
                        status = "Converting..."
                        isConverting = true

                        val configuredPlugins = buildDiveLogPluginList(pluginState)
                        val configuredOutputPlugins = buildOutputPluginList(pluginState)

                        scope.launch {
                            val result =
                                withContext(Dispatchers.IO) {
                                    val source = input.source().buffer()
                                    val sink = output.sink().buffer()
                                    try {
                                        transformDiveLog(
                                            source = source,
                                            sink = sink,
                                            format = selectedFormat,
                                            plugins = configuredPlugins,
                                            outputPlugins = configuredOutputPlugins,
                                        )
                                    } finally {
                                        source.close()
                                        sink.close()
                                    }
                                }

                            isConverting = false
                            result.fold(
                                ifLeft = { error -> status = "Error: ${error.message}" },
                                ifRight = {
                                    outputFile = output
                                    status = "Done! Saved to $outputName"
                                },
                            )
                        }
                    },
                )
                StatusBar(status = status, isConverting = isConverting, outputFile = outputFile)
            }
        }
    }
}

private fun buildDiveLogPluginList(pluginState: Map<String, Map<String, Any>>): List<DiveLogPlugin> {
    return availablePlugins.mapNotNull { plugin ->
        plugin.configure(pluginState[plugin.id] ?: emptyMap())
    }
}

private fun buildOutputPluginList(pluginState: Map<String, Map<String, Any>>): List<OutputPlugin> =
    availableOutputPlugins.mapNotNull { it.configure(pluginState[it.id] ?: emptyMap()) }

@Composable
private fun AppHeader() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Text(
            text = "DIVE TELEMETRY ",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            text = "v${AppVersion.VERSION}",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    selectedFile: File?,
    selectedFormat: DiveComputerFormat,
    dropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onFormatSelected: (DiveComputerFormat) -> Unit,
    onChooseFile: () -> Unit,
    onFileDrop: (File) -> Unit,
    plugins: List<DiveLogPlugin>,
    outputPlugins: List<OutputPlugin>,
    pluginState: Map<String, Map<String, Any>>,
    onPluginParamChange: (pluginId: String, key: String, value: Any) -> Unit,
) {
    Row(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        InputFileCard(
            modifier = Modifier.weight(1f),
            selectedFile = selectedFile,
            selectedFormat = selectedFormat,
            onChooseFile = onChooseFile,
            onFileDrop = onFileDrop,
        )
        InputFileOptionsCard(
            modifier = Modifier.weight(1f),
            selectedFormat = selectedFormat,
            dropdownExpanded = dropdownExpanded,
            onDropdownExpandedChange = onDropdownExpandedChange,
            onFormatSelected = onFormatSelected,
            plugins = plugins,
            outputPlugins = outputPlugins,
            pluginState = pluginState,
            onPluginParamChange = onPluginParamChange,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputFileCard(
    modifier: Modifier = Modifier,
    selectedFile: File?,
    selectedFormat: DiveComputerFormat,
    onChooseFile: () -> Unit,
    onFileDrop: (File) -> Unit,
) {
    var isDragOver by remember { mutableStateOf(false) }
    val dropTarget =
        remember {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) {
                    isDragOver = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    isDragOver = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    isDragOver = false
                }

                @Suppress("UNCHECKED_CAST")
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    isDragOver = false
                    val dropEvent = event.nativeEvent as? DropTargetDropEvent ?: return false
                    dropEvent.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                    val files = dropEvent.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                    val file = files?.firstOrNull() ?: return false
                    onFileDrop(file)
                    return true
                }
            }
        }
    Card(
        modifier =
            modifier
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        val dragEvent = event.nativeEvent as? DropTargetDragEvent
                        dragEvent?.isDataFlavorSupported(DataFlavor.javaFileListFlavor) == true
                    },
                    target = dropTarget,
                )
                .then(if (isDragOver) Modifier.border(2.dp, AccentTeal, RoundedCornerShape(8.dp)) else Modifier),
        backgroundColor = CardBackground,
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp,
    ) {
        Column {
            CardHeader("INPUT FILE")
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onChooseFile() }.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = "File",
                    modifier = Modifier.size(64.dp),
                    tint = if (selectedFile != null) AccentTeal else TextSecondary,
                )
                Text(
                    text = selectedFile?.name ?: "No file selected",
                    color = if (selectedFile != null) TextPrimary else TextSecondary,
                    fontSize = 14.sp,
                )
                if (selectedFile != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Detected",
                            modifier = Modifier.size(16.dp),
                            tint = SuccessGreen,
                        )
                        Text(
                            text = "AUTO-DETECTED (${selectedFormat.name})",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    Text(
                        text = "Click or drop a file here",
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputFileOptionsCard(
    modifier: Modifier = Modifier,
    selectedFormat: DiveComputerFormat,
    dropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onFormatSelected: (DiveComputerFormat) -> Unit,
    plugins: List<DiveLogPlugin>,
    outputPlugins: List<OutputPlugin>,
    pluginState: Map<String, Map<String, Any>>,
    onPluginParamChange: (pluginId: String, key: String, value: Any) -> Unit,
) {
    Card(
        modifier = modifier,
        backgroundColor = CardBackground,
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp,
    ) {
        Column {
            CardHeader("INPUT FILE OPTIONS")
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Source/Computer Type",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Box {
                    OutlinedButton(
                        onClick = { onDropdownExpandedChange(true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                backgroundColor = Color.Transparent,
                                contentColor = TextPrimary,
                            ),
                        border =
                            ButtonDefaults.outlinedBorder.copy(
                                brush = SolidColor(AccentTeal),
                            ),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(selectedFormat.name)
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Expand",
                                tint = AccentTeal,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { onDropdownExpandedChange(false) },
                    ) {
                        availableFormats.forEach { format ->
                            DropdownMenuItem(onClick = { onFormatSelected(format) }) {
                                Text(format.name)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                PluginSection(
                    label = "PROCESSING",
                    plugins = plugins,
                    pluginState = pluginState,
                    onPluginParamChange = onPluginParamChange,
                )
                Spacer(modifier = Modifier.height(4.dp))
                PluginSection(
                    label = "OUTPUT COLUMNS",
                    plugins = outputPlugins,
                    pluginState = pluginState,
                    onPluginParamChange = onPluginParamChange,
                )
            }
        }
    }
}

@Composable
private fun PluginSection(
    label: String,
    plugins: List<Plugin>,
    pluginState: Map<String, Map<String, Any>>,
    onPluginParamChange: (pluginId: String, key: String, value: Any) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                color = AccentTeal,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AccentTeal.copy(alpha = 0.3f)),
            )
        }
        for (plugin in plugins) {
            PluginControls(
                plugin = plugin,
                config = pluginState[plugin.id] ?: emptyMap(),
                onParamChange = { key, value -> onPluginParamChange(plugin.id, key, value) },
            )
        }
    }
}

@Composable
private fun PluginControls(
    plugin: Plugin,
    config: Map<String, Any>,
    onParamChange: (key: String, value: Any) -> Unit,
) {
    for (param in plugin.parameters) {
        PluginParameterControl(param, plugin.description, config, onParamChange)
    }
}

@Composable
private fun PluginParameterControl(
    param: PluginParameter<*>,
    pluginDescription: String,
    config: Map<String, Any>,
    onParamChange: (key: String, value: Any) -> Unit,
) {
    when (param) {
        is BooleanParameter -> {
            val checked = config[param.key] as? Boolean ?: param.defaultValue
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onParamChange(param.key, it) },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = AccentTeal,
                            uncheckedColor = TextSecondary,
                            checkmarkColor = Color.White,
                        ),
                )
                PluginParamLabel(name = param.name, description = pluginDescription)
            }
        }

        is IntParameter -> {
            // Placeholder for future int parameter controls
        }

        is StringParameter -> {
            val selected = config[param.key] as? String ?: param.defaultValue
            var expanded by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                backgroundColor = Color.Transparent,
                                contentColor = TextPrimary,
                            ),
                        border =
                            ButtonDefaults.outlinedBorder.copy(
                                brush = SolidColor(AccentTeal),
                            ),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(selected.replaceFirstChar { it.uppercaseChar() }, fontSize = 13.sp)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Expand", tint = AccentTeal, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        param.options.forEach { option ->
                            DropdownMenuItem(onClick = {
                                onParamChange(param.key, option)
                                expanded = false
                            }) {
                                Text(option.replaceFirstChar { it.uppercaseChar() })
                            }
                        }
                    }
                }
                PluginParamLabel(name = param.name, description = pluginDescription)
            }
        }
    }
}

@Composable
private fun PluginParamLabel(
    name: String,
    description: String,
) {
    var showTooltip by remember { mutableStateOf(false) }
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = name, color = TextPrimary, fontSize = 13.sp)
    Spacer(modifier = Modifier.width(6.dp))
    Box {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = description,
            modifier =
                Modifier
                    .size(18.dp)
                    .clickable { showTooltip = !showTooltip },
            tint = if (showTooltip) AccentTeal else TextSecondary,
        )
        if (showTooltip) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { showTooltip = false },
            ) {
                Surface(
                    color = Color(0xFF3A3F4B),
                    shape = RoundedCornerShape(4.dp),
                    elevation = 4.dp,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(
                        text = description,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeader(title: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CardHeaderBackground, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = AccentTeal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ActionButtons(
    selectedFile: File?,
    isConverting: Boolean,
    onChooseFile: () -> Unit,
    onConvert: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        OutlinedButton(
            onClick = onChooseFile,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = AccentTeal,
                ),
            border =
                ButtonDefaults.outlinedBorder.copy(
                    brush = SolidColor(AccentTeal),
                ),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text("Choose File...")
        }
        Button(
            onClick = onConvert,
            enabled = selectedFile != null && !isConverting,
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = AccentTeal,
                    contentColor = Color.White,
                    disabledBackgroundColor = AccentTeal.copy(alpha = 0.3f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f),
                ),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text("Convert")
        }
    }
}

@Composable
private fun StatusBar(
    status: String,
    isConverting: Boolean,
    outputFile: File?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(AccentTeal.copy(alpha = 0.3f)),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StatusBarBackground)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = status,
                color =
                    when {
                        status.startsWith("Error") -> ErrorRed
                        status.startsWith("Done") -> SuccessGreen
                        else -> TextSecondary
                    },
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (isConverting) {
                LinearProgressIndicator(
                    modifier = Modifier.width(120.dp).height(4.dp),
                    color = AccentTeal,
                    backgroundColor = CardBackground,
                )
            } else if (outputFile != null) {
                OutlinedButton(
                    onClick = {
                        val desktop = java.awt.Desktop.getDesktop()
                        if (desktop.isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR)) {
                            desktop.browseFileDirectory(outputFile)
                        } else {
                            desktop.open(outputFile.parentFile)
                        }
                    },
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            backgroundColor = Color.Transparent,
                            contentColor = SuccessGreen,
                        ),
                    border = ButtonDefaults.outlinedBorder.copy(brush = SolidColor(SuccessGreen)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp),
                ) {
                    Text("Open", fontSize = 10.sp)
                }
            }
        }
    }
}
