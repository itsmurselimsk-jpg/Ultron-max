package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.AiUiState
import com.example.UltronContainer
import com.example.UltronViewModel
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonDarkContainer
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vision.VisionTaskMode

@Composable
fun VisionScreen(
    viewModel: UltronViewModel,
    container: UltronContainer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val capturedBitmap by viewModel.visionBitmap.collectAsStateWithLifecycle()
    val docName by viewModel.visionDocName.collectAsStateWithLifecycle()
    val docText by viewModel.visionDocText.collectAsStateWithLifecycle()
    val visionResult by viewModel.visionResult.collectAsStateWithLifecycle()

    var selectedMode by remember { mutableStateOf(VisionTaskMode.REALTIME_SCENE) }
    var questionInput by remember { mutableStateOf("") }
    var cameraEnabled by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraActive by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraEnabled = granted
        cameraActive = granted
        viewModel.refreshSystemState()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.loadImageFromPicker(it) }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadDocumentFromSaf(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "OMEGA VISION STUDIO",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = "CameraX Live Feed • Photo Picker OCR & Screenshot Intel • SAF Document Analysis",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // CameraX Preview / Captured Frame Card
        Surface(
            color = CharcoalDeep,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth().height(230.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when {
                    cameraActive && cameraEnabled -> {
                        val previewView = remember { PreviewView(context) }
                        DisposableEffect(lifecycleOwner) {
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            val executor = ContextCompat.getMainExecutor(context)
                            cameraProviderFuture.addListener({
                                runCatching {
                                    val provider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().apply {
                                        surfaceProvider = previewView.surfaceProvider
                                    }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture
                                    )
                                }.onFailure { err ->
                                    cameraError = err.localizedMessage ?: "Camera hardware unavailable."
                                    cameraActive = false
                                }
                            }, executor)
                            onDispose {
                                runCatching { cameraProviderFuture.get().unbindAll() }
                            }
                        }
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    capturedBitmap != null -> {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Captured visual input",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    !docName.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = CyberCyan)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(docName!!, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                text = docText?.take(160).orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cameraError ?: "Start CameraX Feed, Pick a Screenshot/Photo, or Load a Document",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Input Source Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (!cameraEnabled) {
                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    } else if (!cameraActive) {
                        cameraError = null
                        cameraActive = true
                    } else {
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bmp = container.visionEngine.imageProxyToBitmap(image)
                                    image.close()
                                    viewModel.setCapturedBitmap(bmp)
                                    cameraActive = false
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    cameraError = exception.localizedMessage
                                }
                            }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("vision_camera_button")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Camera Capture")
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (cameraActive) "Capture" else "CameraX")
            }

            OutlinedButton(
                onClick = {
                    cameraActive = false
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("vision_photo_picker_button")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Photo Picker", tint = CyberCyan)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Photo", color = TextPrimary)
            }

            OutlinedButton(
                onClick = {
                    cameraActive = false
                    documentPickerLauncher.launch(arrayOf("application/pdf", "text/*"))
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("vision_doc_picker_button")
            ) {
                Icon(Icons.Default.Description, contentDescription = "Document Picker", tint = CyberCyan)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Doc/PDF", color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mode Selection Chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VisionTaskMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrimsonDarkContainer,
                        selectedLabelColor = TextPrimary
                    ),
                    modifier = Modifier.testTag("vision_mode_${mode.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = questionInput,
            onValueChange = { questionInput = it },
            label = { Text("Visual / Document Question (optional)", color = TextSecondary) },
            placeholder = { Text("e.g., Extract all error codes or summarize this PDF", color = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CrimsonCore,
                unfocusedBorderColor = HairlineBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().testTag("vision_question_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { viewModel.analyzeVision(selectedMode, questionInput) },
            enabled = (capturedBitmap != null || !docText.isNullOrBlank()) && visionResult !is AiUiState.Loading,
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("vision_analyze_button")
        ) {
            Icon(Icons.Default.Psychology, contentDescription = "Analyze")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Analyze with Gemini Vision")
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (val res = visionResult) {
            is AiUiState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = CrimsonCore, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Running multimodal analysis...", color = TextSecondary)
                }
            }
            is AiUiState.Success -> {
                Surface(
                    color = CharcoalElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth().testTag("vision_result_card")
                ) {
                    Text(
                        text = res.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            is AiUiState.Error -> {
                Surface(
                    color = CrimsonDarkContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CrimsonLaser),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = res.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            else -> Unit
        }
    }
}
