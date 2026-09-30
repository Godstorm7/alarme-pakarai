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
| 9 | `START_REDELIVER_INTENT` | O SO matou o processo no meio do toque → o mesmo intent é reentregue e o ciclo continua (janela sã) |

> **Cuidado na camada 9.** O `START_REDELIVER_INTENT` só é devolvido no caminho principal do toque e só enquanto `isRingingFor(alarmId)` for verdadeiro. Modo "AINDA ACORDADO?" e alarmes inválidos continuam `START_NOT_STICKY`: reentregar o check depois de resolvido reabriria um popup fantasma.

### Fallbacks específicos

- **Som (Spotify):** `SpotifySink` espera ~15s o play; se falhar → sirene local assume. Nunca fica mudo.
- **Permissão exata (`SCHEDULE_EXACT_ALARM`):** sem ela, agenda via `setAlarmClock()` (continua exato — `canScheduleExactAlarms()` é só para a janela de "in-exact"); o app sinaliza "PAUSADOS" e abre settings 1x/dia.
- **Phone locked e alarme único não-recorrente:** `DirectBootPolicy` descarta o toque bloqueado de alarmes únicos (evita tocar no dia seguinte por engano); recorrentes são reagendados pelo mirror.

## Aviso prévio (pré-alerta)

Aviso **antes** do alarme, em 0 (off) / 2 / 5 / 15 min — canal próprio, heads-up silencioso (`IMPORTANCE_LOW`, sem som e sem vibração), então ele **nunca acorda ninguém sozinho**.

- `computePreAlertAt()` é função pura (testada): agenda enquanto `alarmAt - preAlertMinutes > now`. Horário **igual** a `now` ainda vale — era bug perder o aviso quando o alarme estava pra menos de um minuto.
- `AlarmReceiver.handleActionFire()` cancela o aviso antes de reagendar os recorrentes. Sem isso, um alarme de segunda às 7 e outro de terça às 7:30 deixavam o aviso de terça na tela enquanto o de segunda tocava.
- **Direct boot:** o espelho bloqueado (`BootMirror`) não carrega `preAlertMinutes` — o aviso prévio de madrugada é reconstruído por `AlarmFlowCoordinator` depois do desbloqueio. Não tente "consertar" isso no `BootReceiver`: o mirror tem que continuar cabendo nos 512 bytes de prefs de storage protegido.
- Cancelar/desligar o alarme cancela o pré-alerta junto (`AlarmScheduler.cancelPreAlert`).

## Furar o Não perturbe (DND)

O alarme é a exceção do "Não perturbe" — mas só durante o toque, e só com a permissão `ACCESS_NOTIFICATION_POLICY` que é **da pessoa**.

- Sem permissão: nada acontece (nem o filtro é lido pra decidir).
- Só age se o DND estiver ligado (total ou "só prioridade"). Com o DND desligado, quem manda é o volume.
- **O filtro anterior é gravado em disco** (`pakarai_dnd`), não só na memória. Sem isso, se o processo morresse no meio do toque (exatamente o caso que a camada 9 cobre), o aparelho ficaria preso em "só alarme" e o DND da pessoa nunca voltaria.
- Token velho (`> 1 h`) é descartado **sem mexer no filtro**: passado esse prazo já não dá pra saber se o "só alarme" é resíduo do app ou escolha da pessoa — e nenhum alarme pode sobrescrever o DND que ela acabou de escolher.
- Se a pessoa mudar o DND na mão durante o toque, a devolução é cancelada pelo mesmo motivo.
- O toggle `SettingsManager.dndBypass` (no guia) desliga o bypass. Toggle OFF também faz o status virar `SKIPPED` — o app para de pedir acesso a algo que ela acabou de dizer que não quer.

## Janelas (constantes)

| Janela | Valor | Onde | Efeito |
|---|---|---|---|
| `RING_WINDOW_MS` | 30 min | `AlarmStateManager` | O que ainda está "circulando" quando a UI reabre |
| `MISSED_WINDOW_MS` | 12 h | `DirectBootPolicy` / `AlarmScheduler` | Soneca/"AINDA ACORDADO?" vencidos viram lixo; alarme passado vira "pulou a vez" |
| `WATCHDOG_PERIOD_MS` | 9 min | `AlarmService` | Período de varredura contra estado Ringing órfão |
| `PU_AUTO_OPEN_MS` | 24 h | `SettingsManager.canNudgeExactPermission` | Rate-limit de abrir settings da permissão exata |
| `PAUSED_NOTIF_RATE_MS` | 6 h | `SettingsManager.canShowPausedNotification` | Rate-limit da notif "ALARMES PAUSADOS" |
| `TOKEN_MAX_AGE_MS` | 1 h | `DndBypass` | Depois disso o filtro DND pendente é considerado lixo e descartado sem mexer no filtro |

## Onde vive cada pedaço

- **Agendamento**: `scheduler/AlarmScheduler.kt` — `setAlarmClock`, request codes (`id*31 + action.hashCode()`), espelho, refresh do widget, rate-limit da permissão.
- **Espelho**: `core/BootMirror.kt` — `PrefsBootMirror` lê/escreve `id|hour|minute|repeatMask|enabled` em prefs DE (sem Room — Room não funciona antes do unlock).
- **Decisões de direct boot**: `scheduler/DirectBootPolicy.kt` — funções puras; **qualquer alteração aqui exige teste em `DirectBootTest`**.
- **Pré-alerta**: `scheduler/AlarmScheduler.kt` (`computePreAlertAt`, `schedulePreAlert`, `cancelPreAlert`) + `receiver/AlarmReceiver.kt` (`handleActionPreAlert`).
- **DND**: `core/DndBypass.kt` (entrada/saída + token em disco) e o `restoreDnd()` do `AlarmService`, chamado no `cleanup()` e no `onDestroy`.
- **Diagnóstico honesto do sistema**: `core/SetupStatus.kt` (estados), `core/SetupLinks.kt` (deep link ou caminho escrito) e `core/SetupWatch.kt` (dispensas do banner).
- **Recuperação no startup**: `core/AlarmFlowCoordinator.kt` (orquestra) + `scheduler/AlarmScheduler.kt` (`restoreSnoozeAction`/`restoreCheckAction`/`RestoreAction` — janela sã, limpeza de lixo, reagendamento).
- **CUJ**: `AlarmeApplication` (init adiado até `isUserUnlocked`), `receiver/BootReceiver.kt`, `receiver/AlarmReceiver.kt` (`handleLockedFire`).

## Testes que protegem isso

- `scheduler/DirectBootTest` (11) — políticas puro + mirror.
- `scheduler/ComputeNextTriggerTest` (7) — não retorna horário passado, respeita dias.
- `scheduler/AlarmRestoreTest` (10) — restauração de ciclos pendentes.
- `core/AlarmFlowCoordinatorTest` (13) — fluxo de startup com portas injetadas.
- `service/SpotifySinkTest` (6) — fallback de som.
- `scheduler/PreAlertTest` (7) — janela do aviso prévio, incluindo o limite `>= now`.
- `ui/challenge/TapRoundTest` (7) — progressão do modo taptap.
- `core/DndTokenTest` (4) — validade do token de devolução do DND (o resto do
  `DndBypass` depende do `NotificationManager` e só roda no aparelho).

Teste instrumentado (precisa de dispositivo/emulador, roda com
`./gradlew connectedDebugAndroidTest`):

- `data/Migration14To15Test` (3) — migração v14→v15: alarme antigo sobrevive
  inteiro, `challengeRounds` some, `tapCount`/`preAlertMinutes` nascem com
  100/0. Ele cria o banco v14 na mão (SQLiteOpenHelper do framework) e abre
  pelo Room, então pega também erro de coluna faltando no `INSERT`.

Rode sempre: `./gradlew testDebugUnitTest` e `./gradlew lintDebug`.

## Nota de build em `G:\` (Google Drive)

Gradle num Google Drive é lento: testes/lint podem ficar 30-90s sem emitir saída. Não é travamento — aguarde o timeout explícito (máx. 10 min) com watchdog de 30s de silêncio antes de abortar.

## Bugs conhecidos e decisões

- **Force-stop em Settings → nada toca** até reabrir o app (trava do Android). Ao reabrir, `recoverMissedDue` cobre se ainda estiver na janela; senão "pula a vez".
- **Rabada de intents no unlock**: debounce no `AlarmReceiver` (a OneUI re-entrega intents antigos em rajada).
- **O volume Spotify é best-effort** (o app do Spotify sobrescreve); o policiamento re-aplica a cada tick.
- **Samsung mata o app e esconde ajustes.** Bloqueador Automático, Restrições Máximas e "Auto start" não têm API pública. O app **não inventa estado**: eles ficam `MANUAL`/`CONFIRME` com o caminho escrito e a palavra "possível causa". Nunca "ATIVE"/"PENDENTE" inventado — foi exatamente um `MODE_DEFAULT` lido como "ligado" que fez o app afirmar que a Fixação de janelas estava ativa na OneUI.
- **Fixar janelas (`MODE_DEFAULT` = herdado, não ligado).** Só `AppOpsManager.OP_PIN_SCREEN` respondendo `MODE_ALLOWED` conta como ativo; herdado/desconhecido vira `CONFIRME`. E a tela do desafio só chama `startLockTask()` se o appop **não** estiver explicitamente negado — no herdado, ela tenta e o sistema decide.
