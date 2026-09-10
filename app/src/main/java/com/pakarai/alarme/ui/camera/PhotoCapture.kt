package com.pakarai.alarme.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executor

/**
 * Preview de câmera + botão. Tira UMA foto e devolve o arquivo.
 * O chamador decide onde salvar (editor registra em filesDir/objects;
 * o desafio usa cacheDir e descarta depois).
 */
@Composable
fun PhotoCaptureCard(
    targetDir: File,
    onCaptured: (File) -> Unit,
    buttonText: String = "FOTOGRAFAR",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failMsg by remember { mutableStateOf("") }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Column(modifier = modifier) {
        if (!granted) {
            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("PERMITIR CÂMERA", color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            try {
                                val provider = future.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val ic = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                imageCapture = ic
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    ic
                                )
                            } catch (_: Exception) {
                                failMsg = "Não deu pra abrir a câmera."
                            }
                        }, ContextCompat.getMainExecutor(context))
                        previewView
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (failMsg.isNotEmpty()) {
                    Text(
                        text = failMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .padding(10.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (busy) return@Button
                    val ic = imageCapture ?: return@Button
                    targetDir.mkdirs()
                    val file = File(
                        targetDir,
                        "obj_${System.currentTimeMillis()}.jpg"
                    )
                    busy = true
                    failMsg = ""
                    val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                    ic.takePicture(
                        opts,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                busy = false
                                onCaptured(file)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                busy = false
                                failMsg = "Não consegui salvar a foto."
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = !busy
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (busy) "FOCALIZANDO..." else buttonText.uppercase(),
                    fontWeight = FontWeight.Black
                )
            }
            if (busy) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Processando a foto, segura firme...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}