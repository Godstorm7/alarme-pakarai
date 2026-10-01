package com.pakarai.alarme.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Análise de frames ENQUANTO a pessoa mira, pro desafio de foto mostrar a
 * similaridade ao vivo.
 *
 * O callback já recebe um bitmap em pé e reduzido — a câmera faz a orientação e o
 * downscale, porque isso é encanamento de câmera e o chamador só quer pixels.
 */
class FrameAnalyzer(
    /** Espaçamento mínimo entre frames analisados. Baixo demais esquenta e atrasa o preview. */
    val intervalMs: Long = 700L,
    val onFrame: (Bitmap) -> Unit,
)

/** Lado maior do bitmap entregue ao [FrameAnalyzer]. Acima disso não ganha nada e custa CPU. */
private const val MAX_ANALYSIS_SIDE = 320

/**
 * Preview de câmera + botão. Tira UMA foto e devolve o arquivo.
 * O chamador decide onde salvar (editor registra em filesDir/objects;
 * o desafio usa cacheDir e descarta depois).
 *
 * [frameAnalyzer] e [overlay] são opcionais e não mudam o comportamento do cadastro
 * no editor: o overlay é desenhado por cima do preview (fantasma da referência) e o
 * analyzer entrega frames pra quem quiser calcular similaridade durante a mira.
 */
@Composable
fun PhotoCaptureCard(
    targetDir: File,
    onCaptured: (File) -> Unit,
    buttonText: String = "FOTOGRAFAR",
    frameAnalyzer: FrameAnalyzer? = null,
    overlay: @Composable (BoxScope.() -> Unit)? = null,
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

    // O factory do AndroidView roda UMA vez, mas o chamador recompõe: segurar o
    // FrameAnalyzer num State evita que o analisador continue chamando um callback
    // velho (com um score obsoleto, ou pior, de uma tela que já saiu).
    val analyzerState = rememberUpdatedState(frameAnalyzer)
    val lastAnalyzedAt = remember { AtomicLong(0L) }

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
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
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
                                val analyzer = analyzerState.value
                                if (analyzer == null) {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        ic
                                    )
                                } else {
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    analysis.setAnalyzer(executor) { image ->
                                        try {
                                            val fa = analyzerState.value ?: return@setAnalyzer
                                            val now = SystemClock.elapsedRealtime()
                                            if (now - lastAnalyzedAt.get() < fa.intervalMs) {
                                                return@setAnalyzer
                                            }
                                            lastAnalyzedAt.set(now)
                                            // toBitmap() do CameraX NÃO gira o frame
                                            // (a rotação fica em ImageUtil.rotateBitmap,
                                            // pra quem quiser): sem girar aqui, o
                                            // preview deitado viraria um score sem
                                            // sentido comparado com a referência em pé.
                                            fa.onFrame(
                                                downscale(
                                                    rotate(image.toBitmap(), image.imageInfo.rotationDegrees),
                                                    MAX_ANALYSIS_SIDE
                                                )
                                            )
                                        } catch (_: Exception) {
                                        } finally {
                                            image.close()
                                        }
                                    }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        ic,
                                        analysis
                                    )
                                }
                            } catch (_: Exception) {
                                failMsg = "Não deu pra abrir a câmera."
                            }
                        }, ContextCompat.getMainExecutor(context))
                        previewView
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // Fantasma da referência + placar da similaridade, por cima do preview.
                // Chamado direto no escopo do Box: o chamador recebe o BoxScope e
                // posiciona com matchParentSize()/align().
                overlay?.let { it() }
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

/** Gira o bitmap no eixo do sensor. 0/360 devolve o mesmo objeto (sem cópia). */
private fun rotate(src: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return src
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Reduz pelo lado maior. Já pequeno o bastante não copia. */
private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
    val side = minOf(src.width, src.height)
    if (side <= maxSide) return src
    val scale = maxSide.toFloat() / side
    return Bitmap.createScaledBitmap(
        src,
        (src.width * scale).toInt().coerceAtLeast(1),
        (src.height * scale).toInt().coerceAtLeast(1),
        true
    )
}