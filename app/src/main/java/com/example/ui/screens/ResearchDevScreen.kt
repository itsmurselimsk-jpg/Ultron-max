package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.AiUiState
import com.example.UltronViewModel
import com.example.developer.DevTaskMode
import com.example.ui.components.TacticalStatusChip
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonDarkContainer
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ResearchDevScreen(
    viewModel: UltronViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .padding(16.dp)
    ) {
        Text(
            text = "RESEARCH & DEVELOPER LAB",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ObsidianVoid,
            contentColor = CrimsonLaser,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CrimsonCore
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Web Research") },
                modifier = Modifier.testTag("tab_web_research")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Developer & GitHub") },
                modifier = Modifier.testTag("tab_developer")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            ResearchTabContent(viewModel)
        } else {
            DeveloperTabContent(viewModel)
        }
    }
}

@Composable
private fun ResearchTabContent(viewModel: UltronViewModel) {
    val researchState by viewModel.researchState.collectAsStateWithLifecycle()
    val report by viewModel.researchResult.collectAsStateWithLifecycle()
    val citations by viewModel.citations.collectAsStateWithLifecycle()

    var queryInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                color = CharcoalDeep,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        label = { Text("Google Search Grounded Query", color = TextSecondary) },
                        placeholder = { Text("e.g., Android 16 Credential Manager updates", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonCore,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("research_query_input")
                    )
                    Button(
                        onClick = { viewModel.runWebResearch(queryInput) },
                        enabled = researchState !is AiUiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("run_research_button")
                    ) {
                        Icon(Icons.Default.Public, contentDescription = "Search Grounded Web")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search & Extract Citations")
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Direct URL Analyzer", color = TextSecondary) },
                        placeholder = { Text("https://developer.android.com/...", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("research_url_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.analyzeUrl(urlInput, queryInput) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("analyze_url_button")
                        ) {
                            Text("Analyze URL", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { if (urlInput.isNotBlank()) viewModel.openUrlInBrowser(urlInput) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("open_browser_button")
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open Browser", tint = CyberCyan)
                        }
                    }
                }
            }
        }

        if (researchState is AiUiState.Loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = CrimsonCore, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Grounding with Google Search & extracting sources...", color = TextSecondary)
                }
            }
        } else if (researchState is AiUiState.Error) {
            item {
                Surface(color = CrimsonDarkContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        (researchState as AiUiState.Error).message,
                        color = TextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        report?.let { rep ->
            item {
                Surface(
                    color = CharcoalElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("EXECUTIVE RESEARCH SUMMARY", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(rep.summary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        if (rep.sources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("GROUNDED SOURCES (${rep.sources.size})", style = MaterialTheme.typography.labelSmall, color = EmeraldSignal)
                            rep.sources.forEach { src ->
                                Text(
                                    text = "• ${src.title} (${src.url})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberCyan,
                                    modifier = Modifier
                                        .padding(vertical = 3.dp)
                                        .clickable { viewModel.openUrlInBrowser(src.url) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("SAVED CITATIONS (${citations.size})", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        }

        items(citations, key = { it.id }) { cit ->
            Surface(
                color = CharcoalElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).clickable { viewModel.openUrlInBrowser(cit.url) }) {
                        Text(cit.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(cit.url, style = MaterialTheme.typography.labelSmall, color = CyberCyan, maxLines = 1)
                        Text("Query: ${cit.query}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconButton(onClick = { viewModel.deleteCitation(cit.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Citation", tint = CrimsonLaser)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperTabContent(viewModel: UltronViewModel) {
    val devState by viewModel.devState.collectAsStateWithLifecycle()
    val repos by viewModel.githubRepos.collectAsStateWithLifecycle()
    val issues by viewModel.githubIssues.collectAsStateWithLifecycle()
    val filePreview by viewModel.githubFilePreview.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(DevTaskMode.CODE_GEN) }
    var codeInput by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DevTaskMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonDarkContainer,
                            selectedLabelColor = TextPrimary
                        )
                    )
                }
            }
        }

        item {
            Surface(
                color = CharcoalDeep,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        label = { Text("${mode.label} Prompt / Stacktrace / Code", color = TextSecondary) },
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonCore,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dev_code_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.runDeveloperTask(mode, codeInput) },
                            enabled = devState !is AiUiState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("run_dev_task_button")
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "Execute Dev Task")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Execute Argon")
                        }
                        OutlinedButton(
                            onClick = { viewModel.loadGitHubRepos() },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("load_github_repos_button")
                        ) {
                            Text("GitHub Repos", color = CyberCyan)
                        }
                    }
                }
            }
        }

        when (val st = devState) {
            is AiUiState.Loading -> {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = CrimsonCore, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Running Gemini 4 Argon analysis...", color = TextSecondary)
                    }
                }
            }
            is AiUiState.Success -> {
                item {
                    Surface(
                        color = CharcoalElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = st.text,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            is AiUiState.Error -> {
                item {
                    Surface(color = CrimsonDarkContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(st.message, color = TextPrimary, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            else -> Unit
        }

        if (repos.isNotEmpty()) {
            item {
                Text("GITHUB REPOSITORIES (${repos.size})", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
            }
            items(repos, key = { it.id }) { repo ->
                Surface(
                    color = CharcoalElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.inspectGitHubRepo(repo.fullName) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(repo.fullName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            TacticalStatusChip(label = repo.language ?: "Code", color = EmeraldSignal)
                        }
                        if (!repo.description.isNullOrBlank()) {
                            Text(repo.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Text("★ ${repo.stars} • Open Issues: ${repo.openIssues} (Tap to inspect)", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    }
                }
            }
        }

        if (issues.isNotEmpty()) {
            item {
                Text("OPEN ISSUES", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }
            items(issues, key = { it.id }) { issue ->
                Surface(color = CharcoalDeep, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "#${issue.number} ${issue.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        if (!filePreview.isNullOrBlank()) {
            item {
                Surface(
                    color = CharcoalDeep,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("README.md PREVIEW", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(filePreview!!, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}
