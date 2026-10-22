package diche.sessiontimer

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.project.ProjectManagerListener

/**
 * При открытии проекта запускает сессию и секундомер.
 */
class SessionTimerStartupActivity : ProjectActivity {

    /**
     * Обращается к сервису для инициализации секундомера.
     *
     * @param project проект, который только что открылся
     */
    override suspend fun execute(project: Project) {
        project.service<SessionTimerService>()
    }
}


/**
 * При закрытии проекта сохраняет текущую сессию в историю.
 */
class SessionTimerProjectListener : ProjectManagerListener {

    /**
     * Вызывается до сохранения настроек проекта, поэтому запись попадёт в workspace.xml.
     * Если сервис в этой сессии не создавался, ничего не делает.
     *
     * @param project проект, который закрывается
     */
    override fun projectClosingBeforeSave(project: Project) {
        project.getServiceIfCreated(SessionTimerService::class.java)?.finishSession()
    }
}