package com.pakarai.alarme.ui.editor

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.openSpotifyApp
import com.pakarai.alarme.service.SOUND_GROUPS
import com.pakarai.alarme.service.SoundPreview
import com.pakarai.alarme.service.fallbackLabel
import com.pakarai.alarme.spotify.SearchOutcome
import com.pakarai.alarme.spotify.SpotifyItem
import com.pakarai.alarme.spotify.SpotifyStatus
import com.pakarai.alarme.ui.theme.PakaRaiSpacing
import kotlinx.coroutines.launch

/**
 * Tela "SOM DO ALARME": escolha de som local + preview + Spotify (conexão PKCE,
 * busca e seleção de faixa/álbum/artista/playlist). Nova tela cheia dentro do
 * editor, compartilhando o mesmo [EditorViewModel] — o draft nunca se perde.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioEditorScreen(
    vm: EditorViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    val spotifyStatus by AppScope.spotifySession.status.collectAsStateWithLifecycle()
    val spotifyError by AppScope.spotifySession.lastError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var previewing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { SoundPreview.stop() }
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SpotifyItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var myPlaylists by remember { mutableStateOf<List<SpotifyItem>>(emptyList()) }

    LaunchedEffect(spotifyStatus) {
        myPlaylists = if (spotifyStatus == SpotifyStatus.Connected) {
            AppScope.spotifyClient.myPlaylists()
        } else {
            emptyList()
        }
    }

    /** Define a faixa/álbum/playlist escolhida como som do alarme (lembra o fallback local). */
    fun choose(item: SpotifyItem) {
        vm.update { a ->
            // 1ª vez que vira Spotify: lembra o som local atual
            // (toca se o Spotify não funcionar na hora do alarme).
            val fallback = if (a.soundKind == "spotify") a
            else a.copy(fallbackKind = a.soundKind, fallbackUri = a.ringtoneUri)
            fallback.copy(
                soundKind = "spotify",
                spotifyUri = item.uri,
                spotifyLabel = item.name,
                ringtoneUri = ""
            )
        }
        // abre o Spotify: a Web API só toca com um device ativo (app rodando)
        if (!openSpotifyApp(context)) {
            searchError = "Spotify não instalado neste aparelho — toque no som de reserva se quiser."
        } else {
            searchError = null
        }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                vm.setRingtone(uri.toString())
                SoundPreview.playRingtone(context, uri.toString())
                previewing = true
            }
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isBlank()) return
        scope.launch {
            searching = true
            searchError = null
            when (val outcome = AppScope.spotifyClient.searchDetailed(q)) {
                is SearchOutcome.Ok -> {
                    results = outcome.items
                    searched = true
                }
                is SearchOutcome.Failure -> {
                    results = emptyList()
                    searched = true
                    searchError = friendlySpotifyError(outcome)
                }
            }
            searching = false
        }
    }

    fun connect() {
        val auth = AppScope.spotifySession.authorizationUrl() ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(auth)))
    }

    var clientIdText by remember { mutableStateOf("") }

    fun configureClient() {
        val id = clientIdText.trim()
        if (id.isBlank()) return
        AppScope.spotifySession.configure(id)
        val auth = AppScope.spotifySession.authorizationUrl() ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(auth)))
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = PakaRaiSpacing.lg, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SOM DO ALARME",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "O que toca na hora de acordar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PakaRaiSpacing.lg)
        ) {
            // SONS LOCAIS
            SectionShell(
                Icons.Filled.GraphicEq,
                "SONS LOCAIS",
                "Sons reais, sintetizados e do sistema. Toque pra ouvir."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SOUND_GROUPS.forEach { (groupTitle, options) ->
                        Text(
                            text = groupTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        options.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { option ->
                                SoundCard(
                                    option = option,
                                    selected = alarm.soundKind == option.id,
                                    fallback = alarm.soundKind == "spotify" && alarm.fallbackKind == option.id,
                                    modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (option.id == "ringtone") {
                                                val previewUri = alarm.ringtoneUri.ifBlank {
                                                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString() ?: ""
                                                }
                                                SoundPreview.playRingtone(context, previewUri)
                                                previewing = true
                                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do alarme")
                                                }
                                                ringtoneLauncher.launch(intent)
                                            } else {
                                                vm.update { it.copy(soundKind = option.id, ringtoneUri = "") }
                                                SoundPreview.playSiren(context, option.id)
                                                previewing = true
                                            }
                                        }
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (alarm.soundKind == "ringtone" && alarm.ringtoneUri.isNotBlank()) {
                                SoundPreview.playRingtone(context, alarm.ringtoneUri)
                            } else {
                                SoundPreview.playSiren(context, alarm.soundKind)
                            }
                            previewing = true
                        }
                    ) {
                        Text("Ouvir a prévia de novo", color = MaterialTheme.colorScheme.primary)
                    }
                    if (previewing) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "TOCANDO…",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = {
                            SoundPreview.stop()
                            previewing = false
                        }) {
                            Text("PARAR", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // SPOTIFY
            SectionShell(
                Icons.Filled.MusicNote,
                "SPOTIFY",
                siblingSubtitle(spotifyStatus, alarm)
            ) {
                when (val err = spotifyError) {
                    null -> {}
                    else -> {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                when (spotifyStatus) {
                    SpotifyStatus.NotConfigured -> {
                        Text(
                            text = "Este build não veio com o SPOTIFY_CLIENT_ID. Cola o Client ID do teu app (developer.spotify.com → app → Settings) pra habilitar o Spotify.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = clientIdText,
                            onValueChange = { clientIdText = it },
                            label = { Text("Client ID do Spotify") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { configureClient() }),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { configureClient() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("SALVAR E CONECTAR", fontWeight = FontWeight.Black)
                        }
                    }

                    SpotifyStatus.Disconnected -> {
                        Text(
                            text = "Use uma música sua como som do alarme. A conexão é oficial do Spotify (PKCE, sem senha guardada no app).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { connect() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("CONECTAR AO SPOTIFY", fontWeight = FontWeight.Black)
                        }
                    }

                    SpotifyStatus.Connected -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CONECTADO",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                AppScope.spotifySession.clear()
                                results = emptyList()
                                myPlaylists = emptyList()
                            }) {
                                Text("Desconectar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (alarm.soundKind == "spotify") {
                            Spacer(Modifier.height(6.dp))
                            SpotifySelectionCard(
                                icon = Icons.Filled.MusicNote,
                                title = alarm.spotifyLabel.ifBlank { "Música do Spotify" },
                                subtitle = "Vai tocar direto no app do Spotify"
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Se o Spotify não tocar: ${fallbackLabel(alarm.fallbackKind, alarm.fallbackUri)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (myPlaylists.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "MINHAS PLAYLISTS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                myPlaylists.take(8).forEach { item ->
                                    SpotifyResultRow(
                                        item = item,
                                        selected = alarm.soundKind == "spotify" && alarm.spotifyUri == item.uri,
                                        onClick = { choose(item) }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Busca: artista, álbum, faixa, playlist") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search() }),
                            trailingIcon = {
                                IconButton(onClick = { search() }) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = "Buscar",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            )
                        )

                        if (searching) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Buscar…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val err = searchError
                        if (err != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = { search() }) {
                                Text("TENTAR DE NOVO", color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (searched && !searching && results.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Nenhum resultado para \"$query\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (results.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                results.forEach { item ->
                                    SpotifyResultRow(
                                        item = item,
                                        selected = alarm.soundKind == "spotify" && alarm.spotifyUri == item.uri,
                                        onClick = { choose(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(PakaRaiSpacing.xl))
        }
    }
}

/** Mensagem amigável pro erro real da busca (mostra o código quando não é óbvio). */
private fun friendlySpotifyError(f: SearchOutcome.Failure): String = when (f.code) {
    401 -> "Sessão do Spotify expirada. Toque em DESCONECTAR e conecte de novo."
    403 -> "O Spotify negou a busca (403). No dashboard do seu app, adicione sua conta em \"Users and Access\" (modo Development)."
    429 -> "Muitas buscas em pouco tempo. Espere alguns segundos e tente de novo."
    null -> "Não consegui falar com o Spotify (${f.message}). Confira a internet e tente de novo."
    else -> "Erro ${f.code}: ${f.message}"
}

@Composable
private fun siblingSubtitle(status: SpotifyStatus, alarm: com.pakarai.alarme.data.AlarmEntity): String =
    when (status) {
        SpotifyStatus.NotConfigured -> "Não configurado neste build."
        SpotifyStatus.Disconnected -> "Escolhe uma música e usa como som."
        SpotifyStatus.Connected -> if (alarm.soundKind == "spotify") alarm.spotifyLabel.ifBlank { "Música do Spotify" } else "Conectado. Busca algo pra tocar."
    }

@Composable
private fun SpotifySelectionCard(icon: ImageVector, title: String, subtitle: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SpotifyResultRow(
    item: SpotifyItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface)
                )
            } else {
                Surface(
                    color = if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.subtitle.isBlank()) item.kind else "${item.subtitle} · ${item.kind}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}