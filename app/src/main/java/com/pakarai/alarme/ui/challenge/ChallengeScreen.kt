package com.pakarai.alarme.ui.challenge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.ImageEmbedder
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.ui.camera.PhotoCaptureCard
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

private const val TWO_PI = (2 * Math.PI).toFloat()

/**
 * Tela de bloqueio do alarme com os modos de desafio.
 * Cadeia de rodadas: math/memory/type/object respeitam challengeRounds;
 * shake/steps/spin/qr resolvem num desafio sÃ³ (a repetiÃ§Ã£o jÃ¡ Ã© a dificuldade).
 */
@Composable
fun ChallengeScreen(
    alarmId: Long,
    pinWarning: Boolean,
    onRequestPin: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    var alarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var loading by remember { mutableStateOf(true) }
    var round by remember { mutableIntStateOf(1) }
    var snoozeCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(alarmId) {
        delay(200)
        val loaded = AppScope.repository.getById(alarmId)
        alarm = loaded
        snoozeCount = AppScope.stateManager.getSnoozeUsed()
        loading = false
    }

    val current = alarm
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when {
            current == null -> Text(
                text = if (loading) "Carregando..." else "Alarme nÃ£o encontrado",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            !current.mathEnabled -> Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ChallengeHeader(current.label, round = null, rounds = null)
                Spacer(Modifier.height(16.dp))
                BigActionButton(
                    text = "DESLIGAR",
                    onClick = { activity?.let { ChallengeActivity.resolve(it, current) } }
                )
            }

            else -> {
                val mode = ChallengeMode.fromKey(current.challengeMode)
                val rounds = ChallengeMode.supportsRounds(mode)

                fun nextRound() {
                    val a = current ?: return
                    val m = ChallengeMode.fromKey(a.challengeMode)
                    if (ChallengeMode.supportsRounds(m) && round < a.challengeRounds) {
                        round += 1
                    } else {
                        activity?.let { ChallengeActivity.resolve(it, a) }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ChallengeHeader(
                        current.label,
                        round = if (rounds) round else null,
                        rounds = if (rounds) current.challengeRounds else null
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = mode.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    key(round) {
                        when (mode) {
                            ChallengeMode.MATH -> MathRound(current.mathDifficulty) { nextRound() }
                            ChallengeMode.TYPE -> TypeRound { nextRound() }
                            ChallengeMode.MEMORY -> MemoryRound(round) { nextRound() }
                            ChallengeMode.OBJECT -> ObjectRound(
                            refPath = current.objectRefPath,
                            refLabel = current.objectRefLabel
                        ) { nextRound() }
                            ChallengeMode.SHAKE -> ShakeRound { nextRound() }
                            ChallengeMode.STEPS -> StepsRound { nextRound() }
                            ChallengeMode.SPIN -> SpinRound { nextRound() }
                            ChallengeMode.QR -> QrRound(current.challengeQrSecret) { nextRound() }
                        }
                    }

                    if (current.snoozeLimit > snoozeCount) {
                        Spacer(Modifier.height(18.dp))
                        TextButton(onClick = {
                            activity?.let {
                                ChallengeActivity.snooze(it, current)
                                it.finish()
                            }
                        }) {
                            Text(
                                if (snoozeCount == current.snoozeLimit - 1)
                                    "SONECA (ÃšLTIMA!)"
                                else
                                    "SONECA (${current.snoozeMinutes}min)",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (pinWarning) {
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "TRAVA DE TELA DESATIVADA",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Pra impedir o Home de sair, ligue: ConfiguraÃ§Ãµes â†’ SeguranÃ§a â†’ FixaÃ§Ã£o de tela.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = onRequestPin) {
                                    Text(
                                        "TENTAR TRAVAR DE NOVO",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun ChallengeHeader(label: String, round: Int?, rounds: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "ALARME ATIVO",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label.uppercase().ifEmpty { "DESPERTAR" },
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        if (round != null && rounds != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "RODADA $round de $rounds",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

//â”€â”€ MATEMÃTICA â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun MathRound(difficulty: Int, onDone: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val q = generateQuestion(difficulty)
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
            onValueChange = { input = it.filter { c -> c.isDigit() || c == '-' } },
            label = { Text("Resposta") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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
                text = "NÃƒO. Ã‰ OUTRA. ACORDA.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(14.dp))
        BigActionButton(
            text = "RESOLVER",
            onClick = {
                if (input.toIntOrNull() == answer) {
                    onDone()
                } else {
                    wrong = true
                    val q = generateQuestion(difficulty)
                    question = q.first
                    answer = q.second
                    input = ""
                }
            }
        )
    }
}

/** Gera questÃ£o conforme dificuldade. Retorna (texto, resposta). */
private fun generateQuestion(difficulty: Int): Pair<String, Int> {
    val rnd = Random.Default
    return when (difficulty) {
        0 -> {
            val a = rnd.nextInt(5, 25)
            val b = rnd.nextInt(1, 15)
            if (rnd.nextBoolean()) "$a + $b" to a + b else "$a - $b" to a - b
        }
        1 -> {
            val a = rnd.nextInt(12, 95)
            val b = rnd.nextInt(2, 9)
            "$a Ã— $b" to a * b
        }
        else -> {
            val a = rnd.nextInt(10, 60)
            val b = rnd.nextInt(4, 9)
            val c = rnd.nextInt(4, 9)
            "$a + $b Ã— $c" to a + b * c
        }
    }
}

//â”€â”€ DIGITAR â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private val TYPE_WORDS = listOf(
    "MADRUGADA", "ACORDA", "DESPERTAR", "PIJAMA", "CAFEINA",
    "SONOLENTO", "RELÃ“GIO", "VOLUME", "ENERGIA", "MOTIVAÃ‡ÃƒO"
)

@Composable
private fun TypeRound(onDone: () -> Unit) {
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
            onValueChange = { input = it.uppercase() },
            label = { Text("Digite a palavra") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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
                text = "NÃƒO Ã‰ ISSO. ACORDA.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(14.dp))
        BigActionButton(
            text = "CONFIRMAR",
            onClick = {
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

//â”€â”€ MEMÃ“RIA â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private val MEMORY_ICONS = listOf(
    Icons.Filled.Star, Icons.Filled.Favorite, Icons.Filled.Home, Icons.Filled.Lock,
    Icons.Filled.Phone, Icons.Filled.Settings, Icons.Filled.Face, Icons.Filled.Email,
)

@Composable
private fun MemoryRound(round: Int, onDone: () -> Unit) {
    val seqLen = (round + 2).coerceAtMost(6)
    var seq by remember { mutableStateOf(List(seqLen) { Random.nextInt(MEMORY_ICONS.size) }) }
    val shuffledPositions by remember { mutableStateOf(MEMORY_ICONS.indices.shuffled()) }
    var showing by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<List<Int>>(emptyList()) }
    var wrong by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showing = true
        delay(1_700)
        showing = false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (showing) "MEMORIZE a sequÃªncia" else "Repita na ordem: ${picked.size}/${seqLen}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(seqLen) { i ->
                if (showing || i < picked.size) {
                    IconBox(icon = MEMORY_ICONS[seq[i]])
                } else {
                    IconBox(icon = null)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Grid(
            items = shuffledPositions,
            columnCount = 4,
            itemContent = { iconIndex ->
                IconButton(
                    icon = MEMORY_ICONS[iconIndex],
                    enabled = !showing && !wrong,
                    onClick = {
                        val next = picked + iconIndex
                        if (iconIndex != seq[picked.size]) {
                            wrong = true
                            picked = emptyList()
                        } else {
                            picked = next
                            if (next.size == seqLen) onDone()
                        }
                    }
                )
            }
        )
        if (wrong) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "ERROU. TENTA DE NOVO.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

//â”€â”€ OBJETO (foto do objeto cadastrado, reconhecimento offline) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ObjectRound(
    refPath: String,
    refLabel: String,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val targetDir = remember { File(context.cacheDir, "challenge_obj").apply { mkdirs() } }
    var status by remember { mutableStateOf("") }
    var matching by remember { mutableStateOf(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    fun verify(file: File) {
        matching = true
        status = ""
        Thread {
            val loaded = ImageEmbedder.ensureLoaded(context)
            val ref = if (loaded && refPath.isNotBlank()) ImageEmbedder.embed(File(refPath)) else null
            val query = if (loaded) ImageEmbedder.embed(file) else null
            file.delete()
            val ok = ref != null && query != null && ImageEmbedder.matches(ref, query)
            handler.post {
                matching = false
                if (ok) {
                    onDone()
                } else {
                    status = if (ref == null)
                        "Cadastra a foto do objeto no editor antes de salvar o alarme."
                    else
                        "NÃƒO Ã‰ O OBJETO CADASTRADO. ACORDA E TENTA DE NOVO."
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

//â”€â”€ AGITAR â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ShakeRound(onDone: () -> Unit) {
    val context = LocalContext.current
    val target = 15
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
                text = "SEM ACELERÃ”METRO NESTE APARELHO",
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

//â”€â”€ ANDAR (passos) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun StepsRound(onDone: () -> Unit) {
    val context = LocalContext.current
    val target = 20
    var steps by remember { mutableIntStateOf(0) }
    var noSensor by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        var listener: SensorEventListener? = null
        if (sensor == null) {
            noSensor = true
        } else {
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val n = steps + 1
                    steps = n
                    if (n >= target) onDone()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { listener?.let { sm.unregisterListener(it) } }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (noSensor) {
            Text(
                text = "SEM SENSOR DE PASSOS NESTE APARELHO",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        } else {
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

//â”€â”€ GIRAR (alinhar alvo) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun SpinRound(onDone: () -> Unit) {
    val context = LocalContext.current
    val target by remember { mutableIntStateOf(Random.nextInt(6, 19) * 15) }
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
                text = "SEM SENSOR DE ROTAÃ‡ÃƒO NESTE APARELHO",
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
                text = "atÃ© virar ${target}Â°",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${abs(totalDeg).toInt()}Â° / ${target}Â°",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(18.dp))
            ProgressBar(fraction = (abs(totalDeg).coerceAtMost(target.toFloat()) / target))
        }
    }
}

//â”€â”€ QR CODE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun QrRound(secret: String, onDone: () -> Unit) {
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
        val match = code == expected
        if (match) {
            onDone()
        } else {
            status = "QR INCORRETO. Ã‰ o que tem o segredo certo."
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
                "Segredo padrÃ£o: PAKARAI. Defina no editor e imprima o QR."
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
                            status = "NÃ£o deu pra abrir a cÃ¢mera."
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
                text = "PERMITIR CÃ‚MERA",
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

//â”€â”€ UI helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
            }
        }
    }
}

@Composable
private fun IconBox(icon: ImageVector?) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(
                color = if (icon != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun IconButton(icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (enabled) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(62.dp)
            .background(
                color = bg,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
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
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
    }
}
