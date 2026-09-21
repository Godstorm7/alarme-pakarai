# Alarme Pakarai

Alarme Android **agressivo** para Samsung (S23+, OneUI), feito sob medida pra te **acordar de verdade**:

- **Volume progressivo configurável** por alarme: volume inicial, teto, tempo até o pico, curva (linear/explosiva/escada).
- **Bloqueio de volume**: se você tentar abaixar a sirene no shade, ela sobe de volta (configurável).
- **Sons locais 100% sintetizados** (sirene, buzina, bip) — sem depender de internet ou de app externo.
- **Desafio matemático na LOCKSCREEN**: `showWhenLocked` → a tela do desafio desenha por cima do keyguard e é respondível **sem desbloquear**. Errou, gera outra. Só para quando acerta.
- **Fila de missões por alarme**: encadeie quantos modos quiser na ordem que quiser (o próximo só começa após o anterior), com editor dedicado de reordenação (↑/↓).
- **9 modos de desafio**: matemática, memória, digitar o texto, QR Code (imprima e escaneie em outro cômodo — botão "Compartilhar QR" gera a imagem), **objeto** (foto: reconhecimento offline por embedding MobileNetV2, com matching por centróide de multi-visões), shake, passos, girar e botão.
- **"AINDA ACORDADO?" pós-desligamento**: minutos depois de resolver, pergunta com SIM/NÃO aleatórios (+soneca) até você confirmar de novo.
- **Soneca configurável e por-alarme**: limite (0 = modo radical), duração, aviso "ÚLTIMA SONECA" na lockscreen.
- **Anti-fuga em 4 camadas**: Foreground Service + Full-Screen Intent (abre por cima de tudo, tipo chamada) + **serviço de acessibilidade que reabre o desafio em ~1s se você fugir** + Screen Pinning (prende a tela).
- **Otimização Samsung**: wizard com deep links diretos pra desbloquear bateria / Smart Manager / alarmes exatos / acessibilidade / tela cheia.
- **Widget "PRÓXIMO ALARME"**: mostra o horário do próximo disparo na home, atualizado a cada 30min, no boot e ao salvar/apagar/responder um alarme.
- **Confiabilidade**: `setAlarmClock()` (mesmo mecanismo do Clock nativo, imune a Doze/deep sleep da OneUI), `BOOT_COMPLETED` pra reagendar após reboot, **direct boot** (`LOCKED_BOOT_COMPLETED` + espelho dos alarmes em storage protegida de credenciais → reagenda até antes do 1º desbloqueio pós-reboot), **watchdog periódico** que zera toques órfãos, recuperação de **alarme perdido** (se o app morrer no meio do toque, retoma ao abrir na hora; alarme único que já perdeu a vez é reagendado pra próxima hora igual — **nunca toca atrasado**; soneca/"AINDA ACORDADO?" vencidos há mais de 12h são descartados), notificação **"ALARMES PAUSADOS"** quando a permissão de alarme exato cai (reboot/force-stop) e **aquecimento gradual padrão de 5 min** (configurável 0/2/5/15).
- **Debounce de receiver**: a OneUI entrega intents antigos em rajada ao desbloquear — sem isso o alarme dupla.

---

## Limites honestos (o que NENHUM app de alarme consegue)

1. **Force-stop** pelo usuário em Configurações → **nada** re-toca até você abrir o app. É trava de sistema.
2. **Segurar o botão de energia até desligar** → impossível bloquear por software.
3. Volumes da OneUI/adaptive battery → por isso existe o wizard.

O app mitiga 1 e 2: ao reabrir durante um toque em andamento, ele **retoma o ciclo de imediato** (sirene/soneca na janela sã). Um alarme único que simplesmente perdeu a vez é reagendado pra próxima hora igual — sem tocar atrasado.

---

## Stack

Kotlin 2.0 · Jetpack Compose (Material3) · Room · AlarmManager · Coroutines · minSdk 26 · targetSdk 35 · **sem Hilt/Koin** (DI manual via `AppScope`).

---

## Como rodar

1. Abra a pasta `alarme-pakarai/` no **Android Studio**.
2. Deixe o Gradle sincronizar (distribuição 8.10.2, baixada automaticamente).
3. (Opcional) Spotify como som: exporte `SPOTIFY_CLIENT_ID` (ou ponha em `~/.gradle/gradle.properties` como `SPOTIFY_CLIENT_ID=...`) antes de buildar — sem isso o app compila igual, só usa sons locais.
4. `Run ▶` num dispositivo/emulador (ideal: seu S23 físico).
5. Na 1ª abertura, o banner laranja leva ao **Wizard Samsung** — faça os 4 passos.
6. Crie um alarme de teste pra daqui a 1-2 min e solte o telefone na lock screen.

**Testes de confiabilidade (adb):**
```bash
# forçar Doze (cuidado: desfaz com reboot ou com):
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce
# simular bateria fraca
adb shell settings put global low_power 1
adb shell settings put global low_power 0
# listar alarmes agendados (procure pelo Pakarai)
adb shell dumpsys alarm | findstr -i pakarai
```

---

## Integração com Spotify (implementada)

O app nasce com sons locais (nada quebra sem internet). O Spotify é um **som opcional** por alarme, com fallback automático pra sirene local:

1. Cadastre um app em https://developer.spotify.com/dashboard → pegue o **Client ID** (PKCE, sem secret). Habilitar redirect URI não é necessário pro fluxo web.
2. Exporte o Client ID antes de buildar: `SPOTIFY_CLIENT_ID` (env var) ou `SPOTIFY_CLIENT_ID=` em `gradle.properties`.
3. No editor de som, toque em **Spotify** → **Conectar** (abre login na Web API) → **Buscar / selecionar track ou playlist**. O login e o alarme usam o deep link `pakarai://spotify-callback`.

Como funciona por dentro (`service/SpotifySink.kt`):
- Conexão via `AppRemote` SDK spotify (`AppScope.spotifySession`/`spotifyClient`); playback por `playerApi.play(uri, StreamType.ALARM, 0)`.
- **Fallback em 15s**: se o Spotify não der play (app morto pela Samsung, sem login, sem Internet), a sirene local assume sozinha — o alarme **nunca** fica mudo por culpa do Spotify.
- Configurações de desenvolvimento em `docs/SPOTIFY.md`.

> Limitações (conhecidas/oficiais):
- Precisa do **app do Spotify instalado**, login do usuário e **Premium** pra tocar track/playlist específica.
- O **volume progressivo fica "best-effort"**: o app do Spotify costuma sobrescrever o volume do canal (issue oficial do SDK). O policiamento re-aplica a cada tick, mas algumas versões do Spotify resistem.
- **Sempre terá fallback pra sirene local** se o Spotify falhar/estar sem internet/tiver o app morto pela Samsung de madrugada.

---

## Fluxo do alarme

```
setAlarmClock() dispara (imune a Doze/deep sleep)
   → AlarmReceiver (debounce anti-OneUI)
      → AlarmService (Foreground Service mediaPlayback + wake lock)
         → abre ChallengeActivity POR CIMA da lockscreen
         → RampController: volume sobe (início→teto) + policia
         → GuardService (acessibilidade) vigia: fugiu → volta em <1s
         → resolveu a missão → para + agenda o próximo (se recorrente)

Reboot (modo normal):   BOOT_COMPLETED → reagenda tudo via espelho Room.
Reboot (bloqueado):     LOCKED_BOOT_COMPLETED → lê espelho em storage protegida (DirectBootPolicy)
                        → reagenda via AlarmManager enquanto isso (sem UI/Room).
Madrugada, toque órfão: watchdog periódico (AlarmService) zera estado "Ringing" vencido + limpa.
```

## Estrutura

```
app/src/main/java/com/pakarai/alarme/
├── accessibility/GuardService.kt      ← vigilância anti-fuga
├── core/                              ← estado do alarme + prefs (sobrevive a morte de processo)
│   ├── BootMirror.kt                  ← espelho dos alarmes em storage protegida (direct boot)
│   └── AlarmFlowCoordinator.kt        ← recuperação de ciclos pendentes no startup
├── data/                              ← Room: AlarmEntity + DAO + repo
├── receiver/                          ← AlarmReceiver + BootReceiver (direct boot aware)
├── scheduler/                         ← setAlarmClock + cálculo de próxima ocorrência
│   ├── AlarmScheduler.kt              ← agendamento exato + espelho + refresh do widget
│   └── DirectBootPolicy.kt            ← decisões puras de toque-bloqueado (Ignore/Recur/Forget)
├── spotify/                           ← PKCE + sessão + cliente Web API (login, busca, playlist)
├── service/                           ← AlarmService (watchdog) + RampController + sons sintetizados
│   └── SpotifySink.kt                 ← playback Spotify/ALARM com fallback de 15s pra sirene
├── widget/NextAlarmWidget.kt          ← widget "PRÓXIMO ALARME"
└── ui/                                ← Home, Editor (som + fila de missões), Challenge, Check, Wizard
```

## Testes e CI

- **Unitários (JVM)** — `./gradlew testDebugUnitTest`:
  - `scheduler/ComputeNextTriggerTest` — próxima ocorrência (único, diário, dias da semana).
  - `scheduler/AlarmRestoreTest` — restauração de ciclos pendentes (soneca/"AINDA ACORDADO?") no startup.
  - `scheduler/DirectBootTest` — políticas de toque com telefone bloqueado (esquecer/recorrente/ignorar).
  - `core/AlarmFlowCoordinatorTest` — fluxo de startup inteiro com portas injetadas (reagenda, janela sã, limpeza).
  - `core/AppJobsTest` — jobs de aplicação nomeados.
  - `core/ImageEmbedderMathTest` — matemática do centróide de embeddings.
  - `ui/challenge/ChallengeMathTest` — geração de perguntas e parsing do resultado.
  - `service/RampControllerTest` — curva de volume (linear/explosiva/escada).
  - `service/SynthMathTest` — síntese dos sons locais.
  - `service/SpotifySinkTest` — fallback independente do framework (15s, play falhou, isPlaying).
- **Lint** — `./gradlew lintDebug` (0 erros, warnings conhecidos).
- **Instrumentados (devices)** — `./gradlew connectedAndroidTest`: `ImageEmbedderTest` compara fotos reais do mesmo objeto vs. outro pelo modelo MobileNetV2.
- **CI (GitHub Actions)** — `.github/workflows/build.yml`: `assembleDebug` + `testDebugUnitTest` + `lintDebug`.

## Créditos dos sons

Os sons de alarme reais (`alarm_classic`, `alarm_beep`, `alarm_buzzer`,
`alarm_rooster`, `alarm_helium`, `alarm_oxygen`) são os alarmes do
**AOSP DeskClock / framework `frameworks/base/data/sounds`**, licença
**Apache-2.0** (Google). Fonte: https://android.googlesource.com/platform/frameworks/base

## Confiabilidade

O design de robustez (direct boot, watchdog, espelho, janelas de recuperação, fallbacks) está documentado em **[`docs/RELIABILITY.md`](docs/RELIABILITY.md)**.

## Roadmap

- [x] Desafio QR/barcode (exige sair da cama pra escanear)
- [x] Desafio "shake"
- [x] Poka de confirmação ("tô acordado?") pós-desligamento
- [x] Widget de alarme próximo
- [x] Fila de missões por alarme (ordem livre)
- [x] Integração Spotify como som (Web API + AppRemote, fallback 15s)
- [x] Direct boot: alarmes voltam a tocar logo após reboot, sem desbloquear
- [ ] Importar alarmes do Clock nativo / NFC de desligamento