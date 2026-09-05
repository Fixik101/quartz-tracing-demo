package com.example.quartz.tracing.demo.quartz

import org.quartz.JobDetail
import org.quartz.Scheduler
import org.quartz.Trigger
import java.util.Date

/**
 * Обёртка над [Scheduler], которая захватывает trace-контекст в момент планирования —
 * ДО того, как job попадёт в job store.
 *
 * Зачем обёртка, а не [org.quartz.listeners.SchedulerListenerSupport]? Quartz уведомляет
 * `SchedulerListener.jobAdded` ПОСЛЕ сохранения job'а в job store. Для RAMJobStore это
 * незаметно (JobDataMap разделяется с сохранённым job'ом), но для JDBCJobStore job вместе
 * с JobDataMap уже сериализован в БД — мутация из listener'а теряется. Перехват
 * `scheduleJob`/`addJob` на границе планировщика кладёт trace-контекст в JobDataMap до
 * persist, поэтому вариант одинаково работает для RAM и JDBC хранилищ.
 *
 * Захват делает [QuartzTraceContextUtils.capture]: если активного span'а нет, job data
 * не меняется и job выполнится со своим собственным trace'ом.
 * Все остальные методы делегируются реальному планировщику (делегирование Kotlin).
 *
 * Зарегистрировать обёртку нужно как @Primary-бин типа [Scheduler] (см.
 * [QuartzTraceSchedulerConfiguration]), чтобы планирование из приложения шло через неё.
 */
class TraceContextCapturingScheduler(
    private val delegate: Scheduler,
) : Scheduler by delegate {

    override fun addJob(jobDetail: JobDetail, replace: Boolean) {
        delegate.addJob(jobDetail.attachTraceContext(), replace)
    }

    override fun addJob(jobDetail: JobDetail, replace: Boolean, storeNonDurableWhileAwaitingScheduling: Boolean) {
        delegate.addJob(jobDetail.attachTraceContext(), replace, storeNonDurableWhileAwaitingScheduling)
    }

    override fun scheduleJob(jobDetail: JobDetail, trigger: Trigger): Date =
        delegate.scheduleJob(jobDetail.attachTraceContext(), trigger)

    override fun scheduleJob(jobDetail: JobDetail, triggers: Set<out Trigger>, replace: Boolean) {
        delegate.scheduleJob(jobDetail.attachTraceContext(), triggers, replace)
    }

    override fun scheduleJobs(triggersAndJobs: Map<JobDetail, Set<out Trigger>>, replace: Boolean) {
        triggersAndJobs.keys.forEach { it.attachTraceContext() }
        delegate.scheduleJobs(triggersAndJobs, replace)
    }

    private fun JobDetail.attachTraceContext(): JobDetail {
        jobDataMap.putAll(QuartzTraceContextUtils.capture())
        return this
    }
}
