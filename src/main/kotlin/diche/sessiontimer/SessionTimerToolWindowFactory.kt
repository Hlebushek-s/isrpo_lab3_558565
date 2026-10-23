package diche.sessiontimer

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Строит окно Session Timer: таймер текущей сессии сверху и список прошлых сессий под ним.
 *
 * Регистрируется в plugin.xml как `toolWindow`. Отмечен [DumbAware], поэтому окно
 * работает и пока IDE индексирует проект.
 */
class SessionTimerToolWindowFactory : ToolWindowFactory, DumbAware {

    /**
     * Создаёт содержимое окна и запускает обновление надписей раз в секунду.
     *
     * @param project проект, для которого открыто окно
     * @param toolWindow окно, в которое добавляется содержимое
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val timerService = project.service<SessionTimerService>()

        val activeLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD, 22f) }
        val totalLabel = JBLabel()
        val statusLabel = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }

        val currentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
            add(JBLabel("Время работы в сессии"))
            add(activeLabel)
            add(totalLabel)
            add(statusLabel)
        }

        // История прошлых сессий: от новых к старым.
        val listModel = DefaultListModel<String>()
        timerService.history.asReversed().forEach { listModel.addElement(formatRecord(it)) }
        if (listModel.isEmpty) listModel.addElement("Пока нет завершённых сессий")

        val historyPanel = JBScrollPane(JBList(listModel)).apply {
            border = IdeBorderFactory.createTitledBorder("Прошлые сессии")
        }

        val root = JPanel(BorderLayout()).apply {
            add(currentPanel, BorderLayout.NORTH)
            add(historyPanel, BorderLayout.CENTER)
        }
        // обновить отображение времени
        fun refresh() {
            activeLabel.text = formatDuration(timerService.activeMillis)
            totalLabel.text = "С момента открытия проекта: ${formatDuration(timerService.totalMillis)}"
            statusLabel.text = if (timerService.isIdle) "АФК" else "Идёт отсчёт"
        }

        refresh()
        val swingTimer = Timer(1000) { refresh() }
        swingTimer.start()

        val content = ContentFactory.getInstance().createContent(root, "", false)
        content.setDisposer { swingTimer.stop() }
        toolWindow.contentManager.addContent(content)
    }
    // Приведение даты к нормальному виду
    private fun formatRecord(record: SessionTimerService.SessionRecord): String {
        val start = DATE_FORMAT.format(Instant.ofEpochMilli(record.startMillis))
        return "$start — активно ${formatDuration(record.activeMillis)} (всего ${formatDuration(record.totalMillis)})"
    }
    // Приведение милисекунд к часам, минутам и секундам
    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = totalSeconds % 3600 / 60
        val s = totalSeconds % 60
        return "%d:%02d:%02d".format(h, m, s)
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    }
}
