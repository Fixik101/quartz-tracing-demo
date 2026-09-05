package com.example.quartz.tracing.demo.quartz

import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.JobListener
import org.springframework.stereotype.Component

/**
 * Восстанавливает trace-контекст из JobDataMap на время выполнения job'а.
 *
 * [jobToBeExecuted] ставит span в scope (ключи MDC traceId/spanId наполняются автоматически через
 * MDCScopeDecorator), [jobWasExecuted] закрывает scope и чистит состояние.
 * Quartz вызывает оба метода на том же worker-потоке, что и [org.quartz.Job.execute],
 * поэтому ThreadLocal-хранилище cleanup-функции безопасно.
 *
 * Если в job data нет trace-контекста (задача запланирована вне trace'а), job выполнится
 * с новым корневым span — поведение идентично [QuartzTraceContextUtils.restore].
 */
@Component
class TraceContextJobListener : JobListener {

    private val executionCleanup = ThreadLocal<() -> Unit>()

    override fun getName(): String = "traceContextJobListener"

    override fun jobToBeExecuted(context: JobExecutionContext) {
        val traceContext = context.jobDetail.jobDataMap
            .getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)
        executionCleanup.set(QuartzTraceContextUtils.restore(traceContext))
    }

    override fun jobExecutionVetoed(context: JobExecutionContext) = Unit

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        executionCleanup.get()?.invoke()
        executionCleanup.remove()
    }
}
