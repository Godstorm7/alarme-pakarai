# Spotify como som do alarme

O Pakarai toca uma fonte do seu Spotify (faixa, álbum, artista ou playlist)
pelo **Web API** oficial com **OAuth 2.0 PKCE** — sem senha guardada no app.
O login é feito na tela do próprio Spotify, dentro do editor de som.

O áudio sai pelo **app do Spotify** no canal de *música*. Por isso o fluxo tem
três camadas de defesa: play via Web API → confirmação de que algo tocou →
fallback para a sirene local (ninguém dorme sem ser acordado).

---

## Como funciona por dentro

| Etapa | Caminho no código |
|---|---|
| Login PKCE | `spotify/SpotifySession.kt` → `authorizationUrl()` abre `accounts.spotify.com/authorize` |
| Retorno | deep link `pakarai://spotify-callback?code=…` → `MainActivity.handleSpotifyDeepLink` troca o code por tokens |
| Refresh | `SpotifySession.refreshAccessToken` (token dura 1h, renovado na hora) |
| Busca | `spotify/SpotifyHttpClient.kt` → `GET /v1/search` |
| Reprodução | `service/SpotifySink.kt` → devolve/transfere o device e `PUT /v1/me/player/play` |
| Ramp de volume | `SpotifySink` reaplica `setVolume` pela Web API a cada 200ms na mesma curva das sirenes |
| Fallback | 0s se o play falhar, 15s se nada estiver tocando → sirene local |

Escopos pedidos: `user-modify-playback-state user-read-playback-state`.

---

## 1. Cadastro no Spotify (a única parte manual)

1. Entre em https://developer.spotify.com/dashboard → **Create app**.
2. Em **Redirect URIs**, registre exatamente:
   ```
   pakarai://spotify-callback
   ```
   (não é um https! é o scheme custom que o app escuta).
3. Marque **Web API** (não usamos o SDK Android/App Remote).
4. Copie o **Client ID** (32 hex). Ele entra no app de um destes jeitos:
   - **No app (recomendado para testar)**: abra *Editar alarme → SOM DO ALARME → SPOTIFY*.
     Estando o build sem Client ID, aparece um campo **"Client ID do Spotify"** —
     cole lá e toque **SALVAR E CONECTAR**. Fica salvo no aparelho (SharedPreferences),
     sem precisar rebuildar.
   - **No build**: defina a gradle property `SPOTIFY_CLIENT_ID`.
     Global seu, fora do git:
     ```
     # ~/.gradle/gradle.properties
     SPOTIFY_CLIENT_ID=006391a5960a46fb8636a79cdba37e71
     ```
     (também aceita a variável de ambiente `SPOTIFY_CLIENT_ID`.)

### Quota de desenvolvimento (obrigatório pro alarme tocar)

O `PUT /v1/me/player/play` é um endpoint "de player": em **Development Mode**
só funciona para os usuários que você adicionar. No app do Spotify:
- **Settings do app → Extended Quota Mode** → habilite/solicite;
- **Users & access** → adicione **sua própria conta**.

Sem isso o play responde `403` e o alarme cai na sirene (o fallback).

---

## 2. Recursos do Web API usados

| Endpoint | Uso |
|---|---|
| `PUT /v1/me/player` | forza o device ativo (transfer) |
| `GET /v1/me/player/devices` | lista devices e pega o ativo |
| `PUT /v1/me/player/play` | começa a tocar a URI |
| `PUT /v1/me/player/pause` | pausa |
| `PUT /v1/me/player/volume` | rampa de volume (0–100%) |
| `GET /v1/me/player` | confirma que está tocando (15s) |
| `GET /v1/search` | busca faixa/álbum/artista/playlist no editor |

---

## 3. Rampa de volume no Spotify

O volume do *canal de alarme* não afeta o app do Spotify. Então, quando o alarme
tem volume crescente (`rampMs > 0`), o `SpotifySink` recebe um `SpotifyRamp`
(initial/peak/ms/curve) via `createSoundSink` e reaplica `setVolume` pela Web
API a cada 200ms — a **mesma** matemática de `rampValue`/`curveProgress` das
sirenes locais: Linear, Explosiva (exp) e Escada (step). Quem tem a rampa
configurada ouve a música **crescendo** junto com o alarme.

- Instantâneo (`rampMs = 0`): sobe direto para o volume máximo atual do toque.
- Se o play falhar/confirmar não-tocando, o fallback (sirene no canal de alarme)
  assume a rampa normal do `RampController`, como qualquer som local.
- O spotify em si não é "policiado" (se você abaixar o volume da música durante
  o toque, ele não volta sozinho) — limite conhecido do Web API.

## 4. Falhas tratadas (fallback automático)

| Situação | Resultado |
|---|---|
| Sem app do Spotify / sem login / sem Premium | sirene local |
| Sem device ativo (fone desligado, device restrito) | sirene local |
| `403` de quota (Extended Quota não habilitado/sem sua conta) | sirene local |
| Play aceito mas nada tocando em 15s | sirene local |
| Token expirado | renovado sozinho na hora |
| Usuário negou o login no browser | aviso no editor, fica Desconectado |
| Spotify morto durante o toque | mantém o que já estava tocando (não há re-play) |

Por isso o app **não depende** do Spotify pra acordar você.