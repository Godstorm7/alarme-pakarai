package com.pakarai.alarme.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.ui.theme.AlarmePakaraiTheme
import java.util.concurrent.Executors
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView

/**
 * Leitor de QR em tela cheia. Devolve o conteúdo lido via setResult.
 * Usado no editor pra gravar o segredo escaneando qualquer QR.
 */
class QrScanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val accent by AppScope.settings.accentId.collectAsStateWithLifecycle()
            AlarmePakaraiTheme(accentId = accent) {
                QrScanScreen(
                    onClose = { finish() },
                    onScanned = { content ->
                        val intent = Intent().putExtra(RESULT_EXTRA, content)
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val RESULT_EXTRA = "qr_content"

        fun read(context: Context): Intent = Intent(context, QrScanActivity::class.java)
    }
}

@Composable
private fun QrScanScreen(
    onClose: () -> Unit,
    onScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    var status by remember { mutableStateOf("") }

    fun scannerScored(value: String?) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            if (value.isNullOrBlank()) return@post
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            v?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            onScanned(value)
        }
    }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (granted) {
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
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                val scanner = BarcodeScanning.getClient(
                                    BarcodeScannerOptions.Builder()
                                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                        .build()
                                )
                                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                                    val media = image.image
                                    if (media == null) {
                                        image.close()
                                        return@setAnalyzer
                                    }
                                    val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
                                    scanner.process(input)
                                        .addOnSuccessListener { barcodes ->
                                            scannerScored(barcodes.firstOrNull()?.rawValue)
                                        }
                                        .addOnCompleteListener { image.close() }
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            } catch (_: Exception) {
                                status = "Não deu pra abrir a câmera."
                            }
                        }, ContextCompat.getMainExecutor(context))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.White,
                        modifier = Modifier
                            .background(Color(0x66000000), RoundedCornerShape(50))
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Aponte pro QR Code",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "O conteúdo dele vira o segredo do alarme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB8C2CF),
                    textAlign = TextAlign.Center
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!granted) {
                    TextButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("PERMITIR CÂMERA", color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}