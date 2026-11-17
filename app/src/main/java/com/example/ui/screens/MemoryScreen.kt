package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.UltronViewModel
import com.example.memory.DecryptedMemory
import com.example.ui.components.TacticalStatusChip
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonDarkContainer
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmberAmber
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPurple

@Composable
fun MemoryScreen(
    viewModel: UltronViewModel,
    modifier: Modifier = Modifier
) {
    val allMemories by viewModel.memories.collectAsStateWithLifecycle()
    val semanticResults by viewModel.semanticMemoryResults.collectAsStateWithLifecycle()
    val syncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var editingMemory by remember { mutableStateOf<DecryptedMemory?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackupToUri(it) }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreBackupFromUri(it) }
    }

    val displayedMemories = remember(allMemories, semanticResults, selectedCategory) {
        val base = semanticResults ?: allMemories
        if (selectedCategory == "ALL") base else base.filter { it.category == selectedCategory }
    }

    Scaffold(
        containerColor = ObsidianVoid,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = CrimsonCore,
                contentColor = TextPrimary,
                modifier = Modifier.testTag("add_memory_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Encrypted Memory")
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ENCRYPTED MEMORY VAULT", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text(
                        text = "AES-256-GCM KEY_V${prefs.activeKeyVersion} • $syncStatus",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { exportBackupLauncher.launch("ultron_omega_vault_backup.json") },
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("export_backup_button")
                ) {
                    Icon(Icons.Default.Backup, contentDescription = "Backup", tint = CyberCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup", color = TextPrimary)
                }
                OutlinedButton(
                    onClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("restore_backup_button")
                ) {
                    Icon(Icons.Default.Restore, contentDescription = "Restore", tint = EmberAmber)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore", color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.runSemanticSearch(it)
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Semantic Search", tint = CrimsonLaser) },
                placeholder = { Text("Semantic search across decrypted memories...", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonCore,
                    unfocusedBorderColor = HairlineBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth().testTag("memory_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ALL", "FACT", "PREFERENCE", "EPISODIC", "INSTRUCTION").forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonDarkContainer,
                            selectedLabelColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("memory_cat_${cat.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (displayedMemories.isEmpty()) {
                    item {
                        Surface(
                            color = CharcoalElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No encrypted memories stored yet. Tap + to store a fact, preference, or standing instruction.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                items(displayedMemories, key = { it.id }) { mem ->
                    Surface(
                        color = CharcoalElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (mem.pinned) CrimsonCore.copy(alpha = 0.6f) else HairlineBorder),
                        modifier = Modifier.fillMaxWidth().testTag("memory_card_${mem.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TacticalStatusChip(label = mem.category, color = CyberCyan)
                                    TacticalStatusChip(
                                        label = if (mem.syncedToCloud) "CLOUD" else "LOCAL",
                                        color = if (mem.syncedToCloud) EmeraldSignal else EmberAmber
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            viewModel.saveMemory(
                                                mem.category,
                                                mem.title,
                                                mem.content,
                                                mem.importance,
                                                !mem.pinned,
                                                mem.id
                                            )
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Default.PushPin,
                                            contentDescription = "Pin Memory",
                                            tint = if (mem.pinned) CrimsonLaser else TextMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { editingMemory = mem },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Memory", tint = TextSecondary)
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteMemory(mem.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = CrimsonLaser)
                                    }
                                }
                            }
                            Text(
                                text = mem.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mem.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog || editingMemory != null) {
        MemoryEditorDialog(
            initial = editingMemory,
            onDismiss = {
                showCreateDialog = false
                editingMemory = null
            },
            onSave = { cat, title, content, importance, pinned ->
                viewModel.saveMemory(
                    category = cat,
                    title = title,
                    content = content,
                    importance = importance,
                    pinned = pinned,
                    existingId = editingMemory?.id
                )
                showCreateDialog = false
                editingMemory = null
            }
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    initial: DecryptedMemory?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Boolean) -> Unit
) {
    var category by remember { mutableStateOf(initial?.category ?: "FACT") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var importance by remember { mutableFloatStateOf((initial?.importance ?: 6).toFloat()) }
    var pinned by remember { mutableStateOf(initial?.pinned ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalDeep,
        title = {
            Text(
                text = if (initial == null) "Store Encrypted Memory" else "Edit Memory",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("FACT", "PREFERENCE", "EPISODIC", "INSTRUCTION").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("memory_title_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Content") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("memory_content_input")
                )
                Text("Importance: ${importance.toInt()}/10", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Slider(
                    value = importance,
                    onValueChange = { importance = it },
                    valueRange = 1f..10f,
                    colors = SliderDefaults.colors(thumbColor = CrimsonCore, activeTrackColor = CrimsonCore)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Pin to Core Context", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(category, title, content, importance.toInt(), pinned)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                modifier = Modifier.testTag("save_memory_confirm_button")
            ) {
                Text("Encrypt & Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
