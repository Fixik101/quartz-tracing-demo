package com.example.quartz.tracing.starter

import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.JobListener

/**
 * Восстанавливает trace-контекст из `JobDataMap` на время выполнения job'а.
 *
 * Quartz вызывает [jobToBeExecuted] и [jobWasExecuted] на том же worker-потоке,
 * что и [org.quartz.Job.execute], поэтому `ThreadLocal` с текущим [TraceScope] безопасен:
 * один запуск job'а = один поток.
 *
 * Если в job data контекста нет (задача запланирована вне trace'а или сохранена в БД
 * до внедрения стартера), span всё равно создаётся — корневой, со своим traceId.
 * Это мягкий fallback: `NoTrace` в логах не появляется.
 */
class TraceContextJobListener(
    private val traceContext: QuartzTraceContext,
    private val properties: QuartzTracingProperties,
) : JobListener {

    private val executionScope = ThreadLocal<TraceScope?>()

    override fun getName(): String = "quartzTracingJobListener"

    override fun jobToBeExecuted(context: JobExecutionContext) {
        closeScope(null)

        val traceParent = context.jobDetail.jobDataMap.getString(properties.jobDataKey)
        executionScope.set(
            traceContext.restore(traceParent, context.jobDetail.key, context.trigger)
        )
    }

    override fun jobExecutionVetoed(context: JobExecutionContext) {
        closeScope(null)
    }

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        closeScope(jobException)
    }

    private fun closeScope(error: Throwable?) {
        executionScope.get()?.close(error)
        executionScope.remove()
    }
}
