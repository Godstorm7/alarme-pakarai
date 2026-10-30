# Análise do Alarmy (referência) com o REA

Ferramenta: **REA** (`npx rea-agents@6.8.0`) + provider **jadx-headless-mcp 0.7.1** (`REA_JADX_MCP_JAR`).
Alvo: APK `droom.sleepIfUCan` **26.41.1**, 180 MB, **89.918 classes**, `resource_count=3846`, `targetSdk 36`
(baixado do APKPure — código original, assinatura trocada pelo espelho: cert `CN=2`, sem o cert do Google Play).

Comandos REA usados:
`inspect-android-package`, `search-android-classes`, `inspect-android-class`, `inspect-android-method`.
Apoio: `aapt2 dump permissions|xmltree|resources|strings`, `apksigner verify --print-certs`.

---

## 1. Permissões que importam (43 no total)

| Permissão | Papel |
|---|---|
| `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` | agenda o alarme exato (setAlarmClock) |
| `USE_FULL_SCREEN_INTENT` | abre a tela do alarme sobre lockscreen |
| `SYSTEM_ALERT_WINDOW` | overlay (tela de dispensa / “Dismiss screen”) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | pede isenção de bateria |
| `FOREGROUND_SERVICE` + `_MEDIA_PLAYBACK` + `_SPECIAL_USE` + `_SYSTEM_EXEMPTED` + `_MICROPHONE` | toca o som como serviço de mídia |
| `DISABLE_KEYGUARD`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS` | acordar/religar |
| `KILL_BACKGROUND_PROCESSES` | fecha processos ao redor |

**Ausente:** `PACKAGE_USAGE_STATS`, `BIND_ACCESSIBILITY_SERVICE` (declarado só no `android:permission` do serviço), `QUERY_ALL_PACKAGES`.

## 2. Acessibilidade — UM único serviço

Manifesto (`res/xml/accessibility_service_config.xml`):

```
com.alarmy.shutdownblocker.feature.main.ShutdownBlockerService
  android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
  android:exported="true"
accessibilityEventTypes = 0x20   (TYPE_WINDOW_STATE_CHANGED)
feedbackType            = 0x10   (FEEDBACK_GENERIC)
notificationTimeout     = 50 ms
accessibilityFlags      = 0x51   (DEFAULT | REPORT_VIEW_IDS | REQUEST_ACCESSIBILITY_BUTTON)
canRetrieveWindowContent = true
packageNames            = (nenhum → vale para todos os apps)
```

Métodos do serviço (REA `inspect-android-class`): `onCreate`, `onAccessibilityEvent`, `onInterrupt`, `a(...)` + 2 classes internas.
Corpo de `onAccessibilityEvent` nesta build: **vazio** (`{}`), sem chamadas. `onCreate` só dispara uma coroutine
que grava estado num DataStore. Ou seja: **a lógica anti-desligar do Alarmy não está no handler de eventos desta versão**;
o serviço existe, é declarado e pede acesso total à janela, mas não reage a eventos.

O que o próprio app promete (string #5645, traduzida):
> “‘Prevent turn-off’ é um recurso que impede você de desligar o telefone para ignorar o alarme enquanto ele toca.
> Para ativar, o Alarmy precisa da permissão de Acessibilidade […]. Quando você concede, o Alarmy só verifica qual app
> está na frente da tela quando outra tela aparece enquanto o alarme toca. Se a tela da frente não for o Alarmy —
> como o menu de desligar — ele fecha essa tela e te traz de volta ao alarme.”

No DEX existe **1 referência** a `performGlobalAction` e **1** a `getRootInActiveWindow` — o caminho “fecha a tela e volta”
existe no código, mas não dentro do `onAccessibilityEvent`.

## 3. “Prevent app uninstall” = Device Admin, não acessibilidade

`droom.sleepIfUCan.feature.alarm.receiver.AdminReceiver`
`android:permission="BIND_DEVICE_ADMIN"`, `exported=false`, meta-data `android.app.device_admin` → `res/xml/device_admin.xml`
com `<uses-policies/>` **vazio** (nenhuma política declarada: só serve para bloquear a desinstalação).

Strings: “While this is on, Alarmy can't be uninstalled” (#6004), “Prevent app uninstall enabled” (#6021), “You can't uninstall Alarmy” (#6024).

## 4. Como o alarme realmente sobrevive

- `setAlarmClock()` (1 referência) — o único agendamento que o sistema não mata no Doze e que mostra o ícone de alarme.
- Notificação com **full-screen intent** (`USE_FULL_SCREEN_INTENT`).
- `AlarmService` em foreground com tipo **MEDIA_PLAYBACK (0x2)** — o toque é tratado como mídia, não como “alarme”.
- `AlarmActivity` com `setShowWhenLocked` (5 referências) / `showWhenLocked` no manifesto.
- Isenção de otimização de bateria pedida explicitamente.
- Receivers de `BOOT_COMPLETED` e `LOCKED_BOOT_COMPLETED` (priority 999).
- `AdminReceiver` ativo = desinstalação bloqueada.

Não há `lockTaskMode`, `goToSleep`, nem `TYPE_APPLICATION_OVERLAY` no código (o overlay usa `SYSTEM_ALERT_WINDOW` via outro caminho).

---

## 5. O que isso diz sobre o Pakarai

1. **A arquitetura antiga do Pakarai era a do Alarmy**: um `AccessibilityService` que só olha qual app está na frente
   enquanto o alarme toca. O Alarmy é Play-signed e gigante — mas isso não prova que o Nubank aceita o padrão; prova só
   que a referência de mercado usa o mesmo truque.
2. **O Alarmy não usa UsageStats** (`PACKAGE_USAGE_STATS` ausente). O guard novo do Pakarai usa — e essa é a via mais
   barata e sem acessibilidade para a mesma função (`ForegroundApp` + `GuardPolicy`).
3. **Coisas do Alarmy que valem copiar** (todas sem acessibilidade):
   - `AlarmManager.setAlarmClock()` em vez de `setExactAndAllowWhileIdle` (sobrevive ao Doze e aparece no relógio);
   - full-screen intent + foreground service tipo `mediaPlayback`;
   - `setShowWhenLocked` + `turnScreenOn` na activity do desafio;
   - pedir isenção de otimização de bateria explicitamente;
   - (opcional) `DeviceAdminReceiver` com `uses-policies` vazio **só** para “impedir desinstalar” — esse é o vetor
     mais invasivo do Alarmy e bancos reclamam dele também; no Pakarai, deixar de fora.
4. **Não copiar**: `SYSTEM_ALERT_WINDOW` (overlay) e acessibilidade.

### Hipótese sobre o Nubank (não verificada)
O Alarmy tem acessibilidade + device admin e é instalado pela Play Store. O Pakarai era **sideload** (APK assinado em debug)
com acessibilidade declarada. Um alerta de “app nocivo” costuma casar *capacidade sensível* + *origem não oficial* +
*assinatura desconhecida*. Não há como confirmar no S23 sem teste A/B; o caminho sem acessibilidade já elimina o vetor.
