package com.example.quartz.tracing.demo.quartz

import org.quartz.JobBuilder
import org.quartz.JobDetail

/**
 * Утилита для создания Quartz job'ов с автоматическим захватом trace контекста.
 *
 * Вместо [JobBuilder.newJob] используйте [TracedJobBuilder.newJob] —
 * текущий traceId/spanId захватываются и сохраняются в JobDataMap автоматически.
 */
object TracedJobBuilder {

    /**
     * Создаёт JobBuilder с автоматически захваченным trace контекстом.
     *
     * @param jobClass класс Quartz job'а, должен наследоваться от [TracedQuartzJobBean]
     */
    fun newJob(jobClass: Class<out TracedQuartzJobBean>): JobBuilder =
        JobBuilder.newJob(jobClass).usingJobData(QuartzTraceContextUtils.capture())

    /**
     * Добавляет trace контекст в существующий JobDetail.
     *
     * Полезно, когда JobDetail создаётся без TracedJobBuilder и нужно
     * захватить контекст перед [org.quartz.Scheduler.scheduleJob].
     */
    fun enrichWithTraceContext(jobDetail: JobDetail): JobDetail {
        val traceData = QuartzTraceContextUtils.capture()
        jobDetail.jobDataMap.putAll(traceData)
        return jobDetail
    }
}
