# Alarme PakaRai

Alarme Android **agressivo** para Samsung (S23+, OneUI), feito sob medida pra te **acordar de verdade**:

- **Volume progressivo configurável** por alarme: volume inicial, teto, tempo até o pico, curva (linear/explosiva/escada).
- **Bloqueio de volume**: se você tentar abaixar a sirene no shade, ela sobe de volta (configurável).
- **Sons locais 100% sintetizados** (sirene, buzina, bip) — sem depender de internet ou de app externo.
- **Desafio matemático na LOCKSCREEN**: `showWhenLocked` → a tela do desafio desenha por cima do keyguard e é respondível **sem desbloquear**. Errou, gera outra. Só para quando acerta.
- **Soneca configurável**: limite (0 = modo radical), duração, aviso "ÚLTIMA SONECA" na lockscreen.
- **Anti-fuga em 4 camadas**: Foreground Service + Full-Screen Intent (abre por cima de tudo, tipo chamada) + **serviço de acessibilidade que reabre o desafio em ~1s se você fugir** + Screen Pinning (prende a tela).
- **Otimização Samsung**: wizard com deep links diretos pra desbloquear bateria / Smart Manager / alarmes exatos / acessibilidade.
- **Confiabilidade**: `setAlarmClock()` (mesmo mecanismo do Clock nativo, imune a Doze/deep sleep da OneUI), `BOOT_COMPLETED` pra reagendar após reboot, recuperação de **alarme perdido** (se o app morrer no meio do toque, retoma ao abrir; se o alarme devia ter tocado mas o app não rodou, **toca atrasado mesmo assim**).
- **Debounce de receiver**: a OneUI entrega intents antigos em rajada ao desbloquear — sem isso o alarme dupla.

---

## Limites honestos (o que NENHUM app de alarme consegue)

1. **Force-stop** pelo usuário em Configurações → **nada** re-toca até você abrir o app. É trava de sistema.
2. **Segurar o botão de energia até desligar** → impossível bloquear por software.
3. Volumes da OneUI/adaptive battery → por isso existe o wizard.

O app mitiga 1 e 2: ao reabrir, ele **detecta o alarme pendente e toca na hora**.

---

## Stack

Kotlin 2.0 · Jetpack Compose (Material3) · Room · AlarmManager · Coroutines · minSdk 26 · targetSdk 35 · **sem Hilt/Koin** (DI manual via `AppScope`).

---

## Como rodar

1. Abra a pasta `alarme-pakarai/` no **Android Studio**.
2. Deixe o Gradle sincronizar (distribuição 8.10.2, baixada automaticamente).
3. `Run ▶` num dispositivo/emulador (ideal: seu S23 físico).
4. Na 1ª abertura, o banner laranja leva ao **Wizard Samsung** — faça os 4 passos.
5. Crie um alarme de teste pra daqui a 1-2 min e solte o telefone na lock screen.

**Testes de confiabilidade (adb):**
```bash
# forçar Doze (cuidado: desfaz com reboot ou com):
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce
# simular bateria fraca
adb shell settings put global low_power 1
adb shell settings put global low_power 0
# listar alarmes agendados (procure pelo PakaRai)
adb shell dumpsys alarm | findstr -i pakarai
```

---

## Integração com Spotify (opcional)

O app nasce com sons locais (nada quebra sem internet). Pra usar sua playlist do Spotify como som:

1. Cadastre um app em https://developer.spotify.com/dashboard → pegue o **Client ID** e habilite o redirect URI.
2. Adicione as dependências no `app/build.gradle.kts`:

```kotlin
dependencies {
    // App Remote SDK (oficial)
    implementation("com.spotify.android:appremote:0.6.0")
    implementation("com.spotify.android:auth:2.0.2")
}
```

3. Siga o passo a passo e o código de exemplo em `docs/SPOTIFY.md` para criar um `SpotifySink` que faz `playerApi.play(uri, StreamType.ALARM)` e adicioná-lo na fábrica `createSoundSink`.

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
         → resolveu a matemática → para + agenda o próximo (se recorrente)
```

## Estrutura

```
app/src/main/java/com/pakarai/alarme/
├── accessibility/GuardService.kt      ← vigilância anti-fuga
├── core/                              ← estado do alarme + prefs (sobrevive a morte de processo)
├── data/                              ← Room: AlarmEntity + DAO + repo
├── receiver/                          ← AlarmReceiver + BootReceiver
├── scheduler/AlarmScheduler.kt        ← setAlarmClock + cálculo de próxima ocorrência
├── service/                           ← AlarmService + RampController + sons sintetizados
└── ui/                                ← Home, Editor, Challenge (lockscreen), Wizard Samsung
```

## Roadmap

- [ ] Desafio QR/barcode (exige sair da cama pra escanear)
- [ ] Desafio "shake"
- [ ] Poka de confirmação ("tô acordado?") pós-desligamento
- [ ] Integração Spotify (ver `docs/SPOTIFY.md`)
- [ ] Widget de alarme próximo