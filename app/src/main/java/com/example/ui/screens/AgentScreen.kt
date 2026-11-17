package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.UltronViewModel
import com.example.ui.components.TacticalStatusChip
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmberAmber
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AgentScreen(
    viewModel: UltronViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.agentTasks.collectAsStateWithLifecycle()
    val steps by viewModel.agentSteps.collectAsStateWithLifecycle()
    val activeTaskId by viewModel.activeAgentTaskId.collectAsStateWithLifecycle()

    var goalInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .padding(16.dp)
    ) {
        Text(
            text = "AUTONOMOUS AGENT COMMAND",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = "Multi-agent planner • Tool registry • Risk gating • Result verification",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = CharcoalDeep,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    label = { Text("Mission Goal", color = TextSecondary) },
                    placeholder = { Text("e.g., Check battery & network, research Kotlin 2.2, and save summary to memory", color = TextMuted) },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonCore,
                        unfocusedBorderColor = HairlineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("agent_goal_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (goalInput.isNotBlank()) {
                                val g = goalInput
                                goalInput = ""
                                viewModel.launchAgentGoal(g)
                            }
                        },
                        enabled = activeTaskId == null,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("launch_agent_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Dispatch Agent")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dispatch Mission")
                    }
                    if (activeTaskId != null) {
                        OutlinedButton(
                            onClick = { viewModel.cancelActiveAgentTask() },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_agent_button")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel Mission", tint = CrimsonLaser)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", color = CrimsonLaser)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "EXECUTION PIPELINE & HISTORY",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (tasks.isEmpty()) {
                item {
                    Surface(
                        color = CharcoalElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No autonomous missions dispatched yet. Enter a multi-step goal above to engage the Omega Planner.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(tasks, key = { it.id }) { task ->
                val taskSteps = steps.filter { it.taskId == task.id }.sortedBy { it.stepOrder }
                val statusColor = when (task.status) {
                    "VERIFIED" -> EmeraldSignal
                    "RUNNING", "PLANNING" -> CyberCyan
                    "AWAITING_CONFIRMATION" -> EmberAmber
                    else -> CrimsonLaser
                }
                Surface(
                    color = CharcoalElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth().testTag("agent_task_card_${task.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.goal,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TacticalStatusChip(label = task.status, color = statusColor)
                        }
                        if (task.planSummary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = task.planSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (taskSteps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            taskSteps.forEach { step ->
                                Surface(
                                    color = CharcoalDeep,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${step.stepOrder}. [${step.agentRole}] ${step.toolName}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = CyberCyan
                                            )
                                            Text(
                                                text = "${step.riskLevel} • ${step.status}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when (step.status) {
                                                    "VERIFIED" -> EmeraldSignal
                                                    "AWAITING_CONFIRMATION" -> EmberAmber
                                                    "FAILED", "CANCELLED" -> CrimsonLaser
                                                    else -> TextSecondary
                                                }
                                            )
                                        }
                                        if (step.output.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = step.output,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
