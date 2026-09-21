# Confiabilidade — Alarme Pakarai

Este documento registra *como* o app garante que um alarme **toque na hora** e **não toque atrasado/fantasma**, e o que fazer ao mexer nessas partes. Rascunho de referência para quem for mudar o `AlarmService`, `AlarmScheduler` ou os receivers.

## Princípios

1. **Nunca tocar atrasado.** Se o app perdeu a janela de toque, ele não toca às 8:37 quando o despertador era 8:00 — ou toca imediatamente (alarme ainda "circulando") ou **não toca** e reagenda a próxima ocorrência.
2. **Sempre há fallback.** Toda dependência externa (Spotify, permissão de alarme exato, unlock do usuário, app morto) tem um caminho de contingência local.
3. **Estado sobrevive a morte de processo e reboot.** O que tocar é decidido por um espelho simples em `SharedPreferences` (storage protegida) OU pela re-avaliação pura de `AlarmEntity`.
4. **Zero confiança no estado em memória.** Custódia única do estado em `AlarmStateStore` (vm-safe); receivers são stateless.

## Camadas de garantia (em ordem)

| Camada | Mecanismo | Cobre |
|---|---|---|
| 1 | `AlarmManager.setAlarmClock()` | Doze/deep sleep da OneUI — mesmo caminho do Clock nativo, imune |
| 2 | `BOOT_COMPLETED` | Reboot normal: Room reavaliada, reagenda tudo |
| 3 | `LOCKED_BOOT_COMPLETED` + mirror (`BootMirror`) | Reboot **antes do 1º desbloqueio**: `PrefsBootMirror` em `createDeviceProtectedStorageContext()` (lectura sem credenciais) |
| 4 | `QUICKBOOT_POWERON` | "Reinício rápido" da Samsung (diferente do reboot completo) |
| 5 | Watchdog periódico (~9 min) | Toque órfão: estado `Ringing` vencido é zerado no `AlarmService` sozinho |
| 6 | `ACTION_USER_UNLOCKED` + `recoverMissedDue()` | Alarme que disparou exato mas a UI não pegou — toca imediatamente ao desbloquear |
| 7 | `DirectBootPolicy` | Decide *se* o toque bloqueado deve tocar/agendar/ignorar (decisões puras, testadas) |
| 8 | Notif "ALARMES PAUSADOS" | Permissão de alarme exato caiu (reboot/force-stop) → avisa + botão re-solicita (1x/dia) |

### Fallbacks específicos

- **Som (Spotify):** `SpotifySink` espera ~15s o play; se falhar → sirene local assume. Nunca fica mudo.
- **Permissão exata (`SCHEDULE_EXACT_ALARM`):** sem ela, agenda via `setAlarmClock()` (continua exato — `canScheduleExactAlarms()` é só para a janela de "in-exact"); o app sinaliza "PAUSADOS" e abre settings 1x/dia.
- **Phone locked e alarme único não-recorrente:** `DirectBootPolicy` descarta o toque bloqueado de alarmes únicos (evita tocar no dia seguinte por engano); recorrentes são reagendados pelo mirror.

## Janelas (constantes)

| Janela | Valor | Onde | Efeito |
|---|---|---|---|
| `RING_WINDOW_MS` | 30 min | `AlarmStateManager` | O que ainda está "circulando" quando a UI reabre |
| `MISSED_WINDOW_MS` | 12 h | `DirectBootPolicy` / `AlarmScheduler` | Soneca/"AINDA ACORDADO?" vencidos viram lixo; alarme passado vira "pulou a vez" |
| `WATCHDOG_PERIOD_MS` | 9 min | `AlarmService` | Período de varredura contra estado Ringing órfão |
| `PU_AUTO_OPEN_MS` | 24 h | `SettingsManager.canNudgeExactPermission` | Rate-limit de abrir settings da permissão exata |
| `PAUSED_NOTIF_RATE_MS` | 6 h | `SettingsManager.canShowPausedNotification` | Rate-limit da notif "ALARMES PAUSADOS" |

## Onde vive cada pedaço

- **Agendamento**: `scheduler/AlarmScheduler.kt` — `setAlarmClock`, request codes (`id*31 + action.hashCode()`), espelho, refresh do widget, rate-limit da permissão.
- **Espelho**: `core/BootMirror.kt` — `PrefsBootMirror` lê/escreve `id|hour|minute|repeatMask|enabled` em prefs DE (sem Room — Room não funciona antes do unlock).
- **Decisões de direct boot**: `scheduler/DirectBootPolicy.kt` — funções puras; **qualquer alteração aqui exige teste em `DirectBootTest`**.
- **Recuperação no startup**: `core/AlarmFlowCoordinator.kt` (orquestra) + `scheduler/AlarmScheduler.kt` (`restoreSnoozeAction`/`restoreCheckAction`/`RestoreAction` — janela sã, limpeza de lixo, reagendamento).
- **CUJ**: `AlarmeApplication` (init adiado até `isUserUnlocked`), `receiver/BootReceiver.kt`, `receiver/AlarmReceiver.kt` (`handleLockedFire`).

## Testes que protegem isso

- `scheduler/DirectBootTest` (11) — políticas puro + mirror.
- `scheduler/ComputeNextTriggerTest` (7) — não retorna horário passado, respeita dias.
- `scheduler/AlarmRestoreTest` (10) — restauração de ciclos pendentes.
- `core/AlarmFlowCoordinatorTest` (13) — fluxo de startup com portas injetadas.
- `service/SpotifySinkTest` (6) — fallback de som.

Rode sempre: `./gradlew testDebugUnitTest` e `./gradlew lintDebug`.

## Nota de build em `G:\` (Google Drive)

Gradle num Google Drive é lento: testes/lint podem ficar 30-90s sem emitir saída. Não é travamento — aguarde o timeout explícito (máx. 10 min) com watchdog de 30s de silêncio antes de abortar.

## Bugs conhecidos e decisões

- **Force-stop em Settings → nada toca** até reabrir o app (trava do Android). Ao reabrir, `recoverMissedDue` cobre se ainda estiver na janela; senão "pula a vez".
- **Rabada de intents no unlock**: debounce no `AlarmReceiver` (a OneUI re-entrega intents antigos em rajada).
- **O volume Spotify é best-effort** (o app do Spotify sobrescreve); o policiamento re-aplica a cada tick.