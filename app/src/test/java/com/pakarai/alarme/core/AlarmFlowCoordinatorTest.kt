package com.pakarai.alarme.core

import com.pakarai.alarme.core.AlarmStateManager.State
import com.pakarai.alarme.data.AlarmDataSource
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.scheduler.MISSED_WINDOW_MS
import com.pakarai.alarme.scheduler.StartupScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O fluxo de startup decide o que acontece quando o app (re)abre depois de
 * reboot / morte de processo. Ele usa sÃ³ portas injetadas (estado, dados,
 * agendador e relÃ³gio) pra rodar 100% em JVM sem Android. Ringing Ã© tratado
 * pela camada Android (precisa de service) â€” aqui cuidamos do resto.
 */
class AlarmFlowCoordinatorTest {

    private val fixedNow = 1_700_000_000_000L
    private val windowMs = MISSED_WINDOW_MS
    private val alarm = AlarmEntity(id = 7, hour = 6, minute = 30)

    //â”€â”€ re-agendamento â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `reagenda todo alarme habilitado em ordem`() = runBlocking {
        val b = alarm.copy(id = 8, hour = 7)
        val h = harness(alarms = listOf(alarm, b))

        h.coordinator.onStartup()

        assertEquals(listOf("schedule(7)", "schedule(8)"), h.scheduler.calls)
    }

    @Test
    fun `pula alarme desabilitado`() = runBlocking {
        val disabled = alarm.copy(id = 9, enabled = false)
        val h = harness(alarms = listOf(alarm, disabled))

        h.coordinator.onStartup()

        assertEquals(listOf("schedule(7)"), h.scheduler.calls)
    }

    @Test
    fun `limpa estados vencidos antes de decidir`() = runBlocking {
        val h = harness()

        h.coordinator.onStartup()

        assertEquals(1, h.store.checkExpiredCalls)
        assertEquals(fixedNow, h.store.lastCheckExpiredNow)
    }

    //â”€â”€ soneca pendente â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `soneca futura reagenda no instante exato`() = runBlocking {
        val at = fixedNow + 5 * 60_000L
        val h = harness(initialState = snooze(7, at), alarms = listOf(alarm))

        h.coordinator.onStartup()

        assertEquals(listOf("schedule(7)", "snoozeAt(7,$at)"), h.scheduler.calls)
        assertEquals(0, h.store.clearCalls)
    }

    @Test
    fun `soneca vencida dentro da janela retoma imediatamente`() = runBlocking {
        val h = harness(
            initialState = snooze(7, fixedNow - 2 * 60_000L),
            alarms = listOf(alarm),
        )

        h.coordinator.onStartup()

        assertEquals(listOf("schedule(7)", "immediate(7)"), h.scheduler.calls)
    }

    @Test
    fun `soneca vencida alÃ©m da janela limpa sem tocar`() = runBlocking {
        val h = harness(
            initialState = snooze(7, fixedNow - windowMs - 1),
            alarms = listOf(alarm),
        )

        h.coordinator.onStartup()

        assertEquals(listOf("schedule(7)"), h.scheduler.calls)
        assertEquals(1, h.store.clearCalls)
    }

    @Test
    fun `soneca de alarme apagado limpa`() = runBlocking {
        val h = harness(initialState = snooze(7, fixedNow + 60_000L))

        h.coordinator.onStartup()

        assertEquals(emptyList<String>(), h.scheduler.calls)
        assertEquals(1, h.store.clearCalls)
    }

    @Test
    fun `soneca de alarme desabilitado e nÃ£o recorrente limpa`() = runBlocking {
        val disabled = alarm.copy(enabled = false)
        val h = harness(initialState = snooze(7, fixedNow + 60_000L), alarms = listOf(disabled))

        h.coordinator.onStartup()

        assertTrue(h.scheduler.calls.none { it.startsWith("snooze") || it.startsWith("immediate") })
        assertEquals(1, h.store.clearCalls)
    }

    //â”€â”€ "AINDA ACORDADO?" pendente â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `check futuro reagenda com a diferenÃ§a`() = runBlocking {
        val h = harness(initialState = check(7, fixedNow + 60_000L), alarms = listOf(alarm))

        h.coordinator.onStartup()

        assertTrue(h.scheduler.calls.contains("check(7,60000)"))
    }

    @Test
    fun `check vencido dentro da janela reabre imediatamente`() = runBlocking {
        val h = harness(initialState = check(7, fixedNow - 30_000L), alarms = listOf(alarm))

        h.coordinator.onStartup()

        assertTrue(h.scheduler.calls.contains("immediateCheck(7)"))
    }

    @Test
    fun `check vencido alÃ©m da janela limpa`() = runBlocking {
        val h = harness(initialState = check(7, fixedNow - windowMs - 1), alarms = listOf(alarm))

        h.coordinator.onStartup()

        assertTrue(h.scheduler.calls.none { it.startsWith("check") })
        assertEquals(1, h.store.clearCalls)
    }

    @Test
    fun `check de alarme apagado limpa mesmo futuro`() = runBlocking {
        val h = harness(initialState = check(7, fixedNow + 60_000L))

        h.coordinator.onStartup()

        assertTrue(h.scheduler.calls.none { it.startsWith("check") })
        assertEquals(1, h.store.clearCalls)
    }

    //â”€â”€ estado ocioso â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `estado ocioso sÃ³ agenda os alarmes`() = runBlocking {
        val h = harness(alarms = listOf(alarm))

        h.coordinator.onStartup()

        assertEquals(listOf("schedule(7)"), h.scheduler.calls)
        assertEquals(0, h.store.clearCalls)
    }

    //â”€â”€ infra de teste â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun snooze(
        alarmId: Long,
        expiresAtMs: Long,
        remaining: Int = 1,
    ) = State.Snoozing(alarmId, remaining, expiresAtMs, usedSnoozes = 0)

    private fun check(alarmId: Long, nextAtMs: Long) = State.Checking(alarmId, nextAtMs)

    private class FakeStore(initial: State) : AlarmStateStore {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<State> = _state
        var checkExpiredCalls = 0
        var lastCheckExpiredNow: Long? = null
        var clearCalls = 0

        override fun checkExpired(nowMs: Long) {
            checkExpiredCalls++
            lastCheckExpiredNow = nowMs
        }

        override fun clear() {
            clearCalls++
            _state.value = State.Idle
        }
    }

    private class FakeDataSource(private val alarms: List<AlarmEntity>) : AlarmDataSource {
        override suspend fun getAll(): List<AlarmEntity> = alarms
        override suspend fun getById(id: Long): AlarmEntity? = alarms.firstOrNull { it.id == id }
    }

    private class RecordingScheduler : StartupScheduler {
        val calls = mutableListOf<String>()

        override fun schedule(alarm: AlarmEntity) { calls += "schedule(${alarm.id})" }
        override fun scheduleSnoozeAt(alarmId: Long, triggerAtMs: Long) { calls += "snoozeAt($alarmId,$triggerAtMs)" }
        override fun scheduleCheck(alarmId: Long, afterMs: Long) { calls += "check($alarmId,$afterMs)" }
        override fun scheduleImmediate(alarmId: Long) { calls += "immediate($alarmId)" }
        override fun scheduleImmediateCheck(alarmId: Long) { calls += "immediateCheck($alarmId)" }
    }

    private fun harness(
        initialState: State = State.Idle,
        alarms: List<AlarmEntity> = emptyList(),
    ): Harness {
        val store = FakeStore(initialState)
        val dataSource = FakeDataSource(alarms)
        val scheduler = RecordingScheduler()
        val coordinator = AlarmFlowCoordinator(
            store = store,
            dataSource = dataSource,
            scheduler = scheduler,
            now = { fixedNow },
        )
        return Harness(store, dataSource, scheduler, coordinator)
    }

    private class Harness(
        val store: FakeStore,
        val dataSource: FakeDataSource,
        val scheduler: RecordingScheduler,
        val coordinator: AlarmFlowCoordinator,
    )
}