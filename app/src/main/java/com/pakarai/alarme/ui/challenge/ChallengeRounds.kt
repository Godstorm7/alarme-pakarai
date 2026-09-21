package com.pakarai.alarme.ui.challenge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pakarai.alarme.core.ImageEmbedder
import com.pakarai.alarme.ui.theme.PakaRaiMotion
import com.pakarai.alarme.ui.theme.rememberAnimationsEnabled
import com.pakarai.alarme.ui.camera.PhotoCaptureCard
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

private const val TWO_PI = (2 * Math.PI).toFloat()

/** Vibra curto no acerto e mais forte no erro. No-op se o aparelho não tiver vibrador. */
internal fun vibrate(context: Context, ms: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
    try {
        val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vib == null || !vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(ms)
        }
    } catch (_: Exception) {
    }
}

//── MATEMÁTICA ─────────────────────────────────────────────────

@Composable
internal fun MathRound(difficulty: Int, onInteract: () -> Unit, onDone: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val q = generateMathQuestion(difficulty)
        question = q.first
        answer = q.second
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Pra desligar, resolva:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "$question = ?",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it.filter { c -> c.isDigit() || c == '-' }
                onInteract()
            },
            label = { Text("Resposta") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            )
        )
        Spacer(Modifier.height(8.dp))
        if (wrong) {
            Text(
                text = "ERROU, TENTE NOVAMENTE!",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(14.dp))
        BigActionButton(
            text = "RESOLVER",
            onClick = {
                onInteract()
                if (input.toIntOrNull() == answer) {
                    onDone()
                } else {
                    wrong = true
                    input = ""
                }
            }
        )
    }
}

//── DIGITAR ───────────────────────────────────────────────────────────────────

private val TYPE_WORDS = listOf(
    "MADRUGADA", "ACORDA", "DESPERTAR", "PIJAMA", "CAFEINA",
    "SONOLENTO", "RELÓGIO", "VOLUME", "ENERGIA", "MOTIVAÇÃO"
)

@Composable
internal fun TypeRound(onInteract: () -> Unit, onDone: () -> Unit) {
    var word by remember { mutableStateOf(TYPE_WORDS.random()) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Digite exatamente:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = word,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it.uppercase()
                onInteract()
            },
            label = { Text("Digite a palavra") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            )
        )
        Spacer(Modifier.height(8.dp))
        if (wrong) {
            Text(
                text = "NÃO É ISSO. ACORDA.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(14.dp))
        BigActionButton(
            text = "CONFIRMAR",
            onClick = {
                onInteract()
                if (input.trim() == word) {
                    onDone()
                } else {
                    wrong = true
                    input = ""
                    word = TYPE_WORDS.random()
                }
            }
        )
    }
}

//── MEMÓRIA ──────────────────────────────────────────────────────────────────

private val MEMORY_COLORS = listOf(
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFDD835),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
    Color(0xFF00ACC1),
    Color(0xFFD81B60),
)

@Composable
internal fun MemoryRound(pairCount: Int, onInteract: () -> Unit, onDone: () -> Unit) {
    val pairs = pairCount.coerceIn(2, MEMORY_COLORS.size)
    val board by remember(pairs) {
        mutableStateOf(MEMORY_COLORS.take(pairs).flatMap { listOf(it, it) }.shuffled())
    }
    val context = LocalContext.current
    var flipped by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matched by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var wrongFlip by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    LaunchedEffect(flipped) {
        if (flipped.size == 2) {
            val (a, b) = flipped
            if (board[a] == board[b]) {
                matched = matched + a + b
                vibrate(context, 60)
                flipped = emptyList()
            } else {
                // deixa as DUAS cores à mostra e só então vira de volta (sem tile vermelho)
                wrongFlip = true
                vibrate(context, 120)
                delay(900)
                wrongFlip = false
                flipped = emptyList()
            }
        }
    }

    LaunchedEffect(matched) {
        if (matched.size == board.size) {
            cleared = true
            vibrate(context, 60)
            delay(800)
            onDone()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "ACHE OS PARES",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Toque em dois blocos iguais pra formar um par.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Pares: ${matched.size / 2}/$pairs",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Grid(
            items = board.indices.toList(),
            columnCount = 4,
            itemContent = { idx ->
                MemoryTile(
                    color = board[idx],
                    faceUp = idx in matched || idx in flipped,
                    onClick = {
                        onInteract()
                        val canFlip = !wrongFlip &&
                            flipped.size < 2 &&
                            idx !in matched &&
                            idx !in flipped
                        if (canFlip) flipped = flipped + idx
                    }
                )
            }
        )
        if (wrongFlip) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "ERROU. OS BLOCOS NÃO BATERAM.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        }
        AnimatedVisibility(
            visible = cleared,
            enter = fadeIn(tween(PakaRaiMotion.MEDIUM)) +
                scaleIn(tween(PakaRaiMotion.MEDIUM), initialScale = 0.7f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "✓ TUDO CERTO!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

//── TILES (memória estilo Alarmy: memorize os tiles que acendem) ─────────────

private enum class TilePhase { READY, MEMORIZE, PLAY }

/** Quantos tiles acendem: dificuldade 3..7, nunca o tabuleiro inteiro. */
internal fun tilesTarget(difficulty: Int, cells: Int = 16): Int =
    difficulty.coerceIn(3, 7).coerceAtMost(cells - 1)

/** Segundos do countdown "Memorize!" a partir dos ms configurados (mínimo 1). */
internal fun memorizeSeconds(memorizeMs: Int): Int =
    ((memorizeMs + 999) / 1000).coerceAtLeast(1)

@Composable
internal fun TilesRound(
    difficulty: Int,
    memorizeMs: Int,
    onInteract: () -> Unit,
    onDone: () -> Unit,
) {
    val cells = 16
    val target = tilesTarget(difficulty, cells)
    val totalSeconds = memorizeSeconds(memorizeMs)
    val answer = remember { (0 until cells).shuffled().take(target).toSet() }
    val context = LocalContext.current
    val animations = rememberAnimationsEnabled()
    val pulse = remember { Animatable(1f) }
    var phase by remember { mutableStateOf(TilePhase.READY) }
    var countdown by remember { mutableIntStateOf(totalSeconds) }
    var found by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var wrongIndex by remember { mutableIntStateOf(-1) }
    var wrongCount by remember { mutableIntStateOf(0) }
    var cleared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(600)
        phase = TilePhase.MEMORIZE
    }

    LaunchedEffect(phase) {
        if (phase == TilePhase.MEMORIZE) {
            var n = totalSeconds
            while (n > 0) {
                countdown = n
                delay(1000)
                n--
            }
            phase = TilePhase.PLAY
        }
    }

    LaunchedEffect(wrongIndex) {
        if (wrongIndex >= 0) {
            delay(700)                     // pisca vermelho no tile errado
            wrongIndex = -1
            found = emptySet()             // penalidade: perde o progresso
            phase = TilePhase.MEMORIZE     // e re-memoriza (não dá pra roubar clicando em tudo)
        }
    }

    LaunchedEffect(found) {
        if (found.size == answer.size) {
            cleared = true
            vibrate(context, 60)
            delay(800)
            onDone()
        }
    }

    val title = when (phase) {
        TilePhase.READY -> "PREPARE-SE"
        TilePhase.MEMORIZE -> "MEMORIZE! $countdown"
        TilePhase.PLAY -> "ACHE OS TILES ACESOS"
    }

    // pulse a cada segundo do "MEMORIZE! N"
    LaunchedEffect(countdown) {
        if (phase == TilePhase.MEMORIZE && animations) {
            pulse.snapTo(1.18f)
            pulse.animateTo(1f, tween(PakaRaiMotion.SLOW))
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (phase) {
                TilePhase.READY -> "Os tiles vão acender. Decore as posições."
                TilePhase.MEMORIZE -> "Decore os $target tiles acesos."
                TilePhase.PLAY -> "Toque nos $target tiles que acenderam."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (phase == TilePhase.PLAY) "Faltam: ${answer.size - found.size}" else "Tiles: $target",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Grid(
            items = (0 until cells).toList(),
            columnCount = 4,
            itemContent = { idx ->
                val isAnswer = idx in answer
                val revealed = phase == TilePhase.MEMORIZE && isAnswer
                MemoryTile(
                    color = MaterialTheme.colorScheme.primary,
                    faceUp = revealed || idx in found,
                    wrong = idx == wrongIndex,
                    onClick = {
                        if (phase == TilePhase.PLAY && !cleared) {
                            onInteract()
                            if (isAnswer) {
                                found = found + idx
                            } else {
                                wrongCount += 1
                                wrongIndex = idx
                                vibrate(context, 150)
                            }
                        }
                    }
                )
            }
        )
        if (wrongIndex >= 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "ERROU! TENTE DE NOVO.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        }
        AnimatedVisibility(
            visible = cleared,
            enter = fadeIn(tween(PakaRaiMotion.MEDIUM)) +
                scaleIn(tween(PakaRaiMotion.MEDIUM), initialScale = 0.7f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "✓ TUDO CERTO!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
        if (wrongCount > 0 && wrongIndex < 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Erros: $wrongCount",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

//── OBJETO (foto do objeto cadastrado, reconhecimento offline) ─────────────

@Composable
internal fun ObjectRound(
    refPath: String,
    refLabel: String,
    onInteract: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val targetDir = remember { File(context.cacheDir, "challenge_obj").apply { mkdirs() } }
    var status by remember { mutableStateOf("") }
    var matching by remember { mutableStateOf(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    fun verify(file: File) {
        onInteract()
        matching = true
        status = ""
        Thread {
            val loaded = ImageEmbedder.ensureLoaded(context)
            val ref = if (loaded && refPath.isNotBlank()) ImageEmbedder.embedViews(File(refPath)) else emptyList()
            val query = if (loaded) ImageEmbedder.embedViews(file) else emptyList()
            file.delete()
            val ok = ref.isNotEmpty() && query.isNotEmpty() && ImageEmbedder.matchesViews(ref, query)
            handler.post {
                matching = false
                if (ok) {
                    onDone()
                } else {
                    status = if (ref.isEmpty())
                        "Cadastra a foto do objeto no editor antes de salvar o alarme."
                    else
                        "NÃO É O OBJETO CADASTRADO. ACORDA E TENTA DE NOVO."
                }
            }
        }.start()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "TIRA FOTO DO OBJETO",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Aponte pra ${refLabel.ifBlank { "o objeto cadastrado no editor" }} e fotografe.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        PhotoCaptureCard(
            targetDir = targetDir,
            onCaptured = { verify(it) },
            buttonText = "TIRAR FOTO",
            modifier = Modifier.fillMaxWidth()
        )
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = status,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

//── AGITAR ───────────────────────────────────────────────────────────────────

@Composable
internal fun ShakeRound(target: Int, onInteract: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    var lastPeak by remember { mutableLongStateOf(0L) }
    var noSensor by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var listener: SensorEventListener? = null
        if (sensor == null) {
            noSensor = true
        } else {
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val mag = sqrt(
                        event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]
                    )
                    val now = SystemClock.elapsedRealtime()
                    if (mag > 13f && now - lastPeak > 400) {
                        onInteract()
                        val n = count + 1
                        count = n
                        lastPeak = now
                        if (n >= target) onDone()
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { listener?.let { sm.unregisterListener(it) } }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (noSensor) {
            Text(
                text = "SEM ACELERÔMETRO NESTE APARELHO",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        } else {
            Text(
                text = "AGITE O CELULAR!",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "$count / $target",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(18.dp))
            ProgressBar(fraction = count.toFloat() / target)
        }
    }
}

//── ANDAR (passos) ──────────────────────────────────────────────────────────────────

@Composable
internal fun StepsRound(target: Int, onInteract: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var steps by remember { mutableIntStateOf(0) }
    var noSensor by remember { mutableStateOf(false) }
    var registerFailed by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor == null) {
            noSensor = true
            registerFailed = false
            return@DisposableEffect onDispose {}
        }
        noSensor = false
        var listener: SensorEventListener? = null
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onInteract()
                val n = steps + 1
                steps = n
                if (n >= target) onDone()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        // registerListener devolve false se o sensor não puder ser ativado —
        // distinto de "sem sensor no aparelho" (mensagens diferentes)
        registerFailed = !sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            !granted -> {
                Text(
                    text = "Pra contar seus passos, o PakaRai precisa acessar sua atividade física. Toque abaixo pra permitir.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                BigActionButton(
                    text = "PERMITIR ATIVIDADE FÍSICA",
                    onClick = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                )
            }

            noSensor -> {
                Text(
                    text = "SEM SENSOR DE PASSOS NESTE APARELHO",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black
                )
            }

            registerFailed -> {
                Text(
                    text = "NÃO DEU PRA ATIVAR O SENSOR DE PASSOS",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black
                )
            }

            else -> {
                Text(
                    text = "LEVANTA E ANDA!",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "$steps / $target passos",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(18.dp))
                ProgressBar(fraction = steps.toFloat() / target)
            }
        }
    }
}

//── GIRAR (alinhar alvo) ──────────────────────────────────────────────────────────────────

@Composable
internal fun SpinRound(target: Int, onInteract: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var ref by remember { mutableFloatStateOf(Float.NaN) }
    var last by remember { mutableFloatStateOf(Float.NaN) }
    var totalDeg by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    var noSensor by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var listener: SensorEventListener? = null
        if (sensor == null) {
            noSensor = true
        } else {
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val rot = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rot, event.values)
                    val yaw = SensorManager.getOrientation(rot, FloatArray(3))[0]
                    if (ref.isNaN()) {
                        ref = yaw
                        last = yaw
                        return
                    }
                    var d = yaw - last
                    if (d > Math.PI.toFloat()) d -= TWO_PI
                    else if (d < -Math.PI.toFloat()) d += TWO_PI
                    totalDeg += d * 180f / Math.PI.toFloat()
                    last = yaw
                    onInteract()
                    if (!done && abs(totalDeg) >= target) {
                        done = true
                        onDone()
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { listener?.let { sm.unregisterListener(it) } }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (noSensor) {
            Text(
                text = "SEM SENSOR DE ROTAÇÃO NESTE APARELHO",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        } else {
            Text(
                text = "GIRE O CELULAR",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "até virar ${target}°",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${abs(totalDeg).toInt()}° / ${target}°",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(18.dp))
            ProgressBar(fraction = (abs(totalDeg).coerceAtMost(target.toFloat()) / target))
        }
    }
}

//── QR CODE ──────────────────────────────────────────────────────────────────

@SuppressLint("UnsafeOptInUsageError")
@Composable
internal fun QrRound(secret: String, onInteract: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val expected = secret.ifBlank { "PAKARAI" }
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    var status by remember { mutableStateOf("") }

    fun onScan(code: String?) {
        onInteract()
        val match = code == expected
        if (match) {
            onDone()
        } else {
            status = "QR INCORRETO. É o que tem o segredo certo."
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "LEIA O QR CODE",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (secret.isBlank())
                "Segredo padrão: PAKARAI. Defina no editor e imprima o QR."
            else
                "Segredo: $secret",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
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
                                        val value = barcodes.firstOrNull()?.rawValue
                                        val mainHandler = Handler(Looper.getMainLooper())
                                        mainHandler.post { onScan(value) }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            )
        } else {
            BigActionButton(
                text = "PERMITIR CÂMERA",
                onClick = { launcher.launch(Manifest.permission.CAMERA) }
            )
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = status,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

//── UI helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun Grid(
    items: List<Int>,
    columnCount: Int,
    itemContent: @Composable (Int) -> Unit,
) {
    val chunked = items.chunked(columnCount)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chunked.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { idx ->
                    Box(modifier = Modifier.weight(1f)) {
                        itemContent(idx)
                    }
                }
                // completa a última linha com espaços invisíveis pra não esticar os tiles
                repeat(columnCount - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MemoryTile(color: Color, faceUp: Boolean, wrong: Boolean = false, onClick: () -> Unit) {
    val animations = rememberAnimationsEnabled()
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = tween(PakaRaiMotion.MEDIUM),
        label = "tileFlip"
    )
    val showFace = rotation > 90f
    val bg by animateColorAsState(
        targetValue = when {
            wrong -> MaterialTheme.colorScheme.error
            showFace -> color
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(PakaRaiMotion.FAST),
        label = "tileBg"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                if (animations) {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
            }
            .clip(RoundedCornerShape(14.dp))
            .background(color = bg, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!showFace && !wrong) {
            Text(
                text = "?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
internal fun ProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(12.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
        )
    }
}

@Composable
internal fun BigActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiary)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onTertiary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
    }
}