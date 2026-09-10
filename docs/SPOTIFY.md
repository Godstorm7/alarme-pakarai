# Spotify como som do alarme (passo a passo)

Aqui você adiciona o App Remote SDK da Spotify e a acopla ao `AlarmService`.
**Este código fica fora do build** — é guia de implementação; o app já funciona
cem por cento com os sons locais sintetizados.

---

## 1. Cadastro no Spotify

1. Entre em https://developer.spotify.com/dashboard → **Create app**.
2. Copie o **Client ID** e registre um redirect URI, ex.: `pakarai-alarme://callback`.
3. Se quiser usar o catálogo no modo **Development**, solicite *Extended Quota* em
   *Settings → User management* e adicione sua conta em *Users & access*.

## 2. Dependências (`app/build.gradle.kts`)

```kotlin
dependencies {
    implementation("com.spotify.android:appremote:0.6.0")
    implementation("com.spotify.android:auth:2.0.2")
}
```

> Spotify App Remote **exige** um `redirectUri` registrado. Se não registrar,
> `ConnectionParams` falha na primeira autorização.

## 3. Inicializar a conexão (no `AlarmeApplication`)

```kotlin
private val spotifyPlayer = SpotifyPlayer()

class SpotifyPlayer {
    private var appRemote: AppRemote? = null

    fun connect(context: Context) {
        val params = ConnectionParams.Builder(
            SPOTIFY_CLIENT_ID,
            AuthMethod.KITT
        )
            .setRedirectUri("pakarai-alarme://callback")
            .build()

        val remote = AppRemote.builder()
            .setConnectionParams(params)
            .setConnectionListener(connListener)
            .setTransportType(TransportType.BIDI)
            .connect(context.applicationContext, CORRELATION_ID)

        appRemote = remote
    }

    private val connListener = object : ConnectionListener {
        override fun onConnected() {
            // pode chamar play() agora
        }
        override fun onConnectionFailed(error: Throwable) {}
        override fun onDisconnected() {}
    }

    /** Play numa URI com StreamType.ALARM; retorna false se sem sessão. */
    fun play(uri: String): Boolean {
        val api = appRemote?.playerApi ?: return false
        api.play(uri, StreamType.ALARM)
        return true
    }

    fun stop() {
        appRemote?.playerApi?.pause()
    }
}
```

Repare: as constantes (`SPOTIFY_CLIENT_ID`, `CORRELATION_ID`) vêm de
https://developer.spotify.com/dashboard; o `CORRELATION_ID` é um UUID fixo seu.

## 4. Criar o `SpotifySink` e plugar na fábrica

Em `service/SoundSink.kt` existe uma interface `SoundSink` e uma função de
fábrica `createSoundSink(...)`. Adicione um sink de passagem que tenta o
Spotify e cai na sirene local:

```kotlin
class SpotifySink(
    private val fallback: SoundSink,
    private val spotify: SpotifyPlayer,
) : SoundSink {

    override fun start() {
        // tenta playlist; se falhar, usa a sirene local
        if (!spotify.play("spotify:playlist:YOUR_PLAYLIST_ID")) {
            fallback.start()
        }
    }

    override fun stop() {
        spotify.stop()
        fallback.stop()
    }
}
```

E em `createSoundSink(...)`:

```kotlin
if (alarm.useSpotify) {
    val app = AppScope.appContext.applicationContext as? AlarmeApplication
    app?.shipSpotify?.let { player ->
        return SpotifySink(fallback = ..., spotify = player)
    }
}
```

## 5. Limitações sérias que justificam o fallback

| Limitação | Como o PakaRai lida |
|---|---|
| Requer app do Spotify instalado + login + **Premium** | caí na sirene local |
| Permissões de background do Spotify (OneUI mata de madrugada) | caí na sirene local |
| Volume sozinho (SDK `StreamType.ALARM` às vezes sobrescreve volume) | policiamento re-aplica a cada tick; pode não vencer em algumas versões |
| 1ª autorização abre tela de login | pré-conceda no desenvolvimento |
| Playlist precisa ser pública ou o usuário precisa de sessão válida | caí na sirene local |

Por isso o app **não depende** do Spotify pra acordar você — o fallback garante
um horror sonoro sempre.