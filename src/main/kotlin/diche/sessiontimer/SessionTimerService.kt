package diche.sessiontimer

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Считает время сессии проекта: с момента открытия проекта (полное)
 * и только время активной работы (без простоя).
 * Завершённые сессии хранятся в обычном списке (динамическом массиве) в workspace.xml проекта.
 */
@Service(Service.Level.PROJECT)
@State(name = "SessionTimerHistory", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SessionTimerService : PersistentStateComponent<SessionTimerService.HistoryState>, Disposable {

    /** Одна завершённая сессия. */
    class SessionRecord {
        /** Момент начала сессии (миллисекунды с 1 января 1970). */
        var startMillis: Long = 0

        /** Время активной работы за сессию в миллисекундах. */
        var activeMillis: Long = 0

        /** Полное время сессии в миллисекундах. */
        var totalMillis: Long = 0
    }

    /** Состояние, которое сохраняется между запусками: массив прошлых сессий. */
    class HistoryState {
        /** Завершённые сессии, от старых к новым. */
        var sessions: MutableList<SessionRecord> = ArrayList()
    }

    private var historyState = HistoryState()

    private val startMillis = System.currentTimeMillis()

    @Volatile
    private var lastActivityMillis = startMillis

    @Volatile
    private var lastTickMillis = startMillis

    /** Время активной работы в миллисекундах, без простоя. Меняется только внутри сервиса. */
    @Volatile
    var activeMillis: Long = 0
        private set

    @Volatile
    private var finished = false

    private val ticker: ScheduledFuture<*>

    init {
        // Считаем любое действие пользователя активностью
        IdeEventQueue.getInstance().addActivityListener(
            { lastActivityMillis = System.currentTimeMillis() },
            this
        )
        ticker = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(::tick, 1, 1, TimeUnit.SECONDS)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val delta = now - lastTickMillis
        lastTickMillis = now
        val isActive = now - lastActivityMillis <= IDLE_THRESHOLD_MS
        if (isActive && delta in 0..MAX_TICK_GAP_MS) {
            activeMillis += delta
        }
    }

    /** Полное время сессии в миллисекундах. */
    val totalMillis: Long
        get() = System.currentTimeMillis() - startMillis

    /** `true`, если пользователь не делал действий дольше [IDLE_THRESHOLD_MS]. */
    val isIdle: Boolean
        get() = System.currentTimeMillis() - lastActivityMillis > IDLE_THRESHOLD_MS

    /** Прошлые сессии, от старых к новым. */
    val history: List<SessionRecord>
        get() = historyState.sessions

    /** Добавляет текущую сессию в конец списка. Вызывается при закрытии проекта. */
    fun finishSession() {
        if (finished) return
        finished = true
        ticker.cancel(false)

        if (activeMillis < MIN_RECORD_MS) return

        val record = SessionRecord().also {
            it.startMillis = startMillis
            it.activeMillis = activeMillis
            it.totalMillis = totalMillis
        }
        val sessions = historyState.sessions
        sessions.add(record)
        while (sessions.size > MAX_HISTORY) {
            sessions.removeAt(0)
        }
    }

    /** Отдаёт IDE состояние, которое нужно сохранить в workspace.xml. */
    override fun getState(): HistoryState = historyState

    /** IDE вызывает при открытии проекта, чтобы передать ранее сохранённую историю. */
    override fun loadState(state: HistoryState) {
        historyState = state
    }

    override fun dispose() {
        ticker.cancel(false)
    }

    companion object {
        /** Сколько времени без действий считается простоем "AFK": 5 минут. */
        const val IDLE_THRESHOLD_MS = 5 * 60 * 1000L
        /** Максимальная разница по времени между тиками (проверка на сон) */
        private const val MAX_TICK_GAP_MS = 5_000L
        /** Минимальная продолжительность сессии для её сохранения */
        private const val MIN_RECORD_MS = 60_000L
        /** Количество хранимых записей */
        private const val MAX_HISTORY = 10
    }
}
