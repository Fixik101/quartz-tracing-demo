package com.example.quartz.tracing.demo.quartz

import com.example.quartz.tracing.demo.quartz.QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.springframework.scheduling.quartz.QuartzJobBean

/**
 * Базовый класс для Quartz задач, который автоматически восстанавливает trace-контекст
 * (W3C traceparent из JobDataMap) на время выполнения.
 * Наследники должны реализовать [executeWithTraceContext] вместо [executeInternal].
 */
abstract class TracedQuartzJobBean : QuartzJobBean() {

    protected abstract fun executeWithTraceContext(dataMap: JobDataMap)

    override fun executeInternal(context: JobExecutionContext) {
        val dataMap = context.jobDetail.jobDataMap
        val traceContext = dataMap.getString(TRACE_CONTEXT_JOB_DATA)

        val cleanup = QuartzTraceContextUtils.restore(traceContext)

        try {
            executeWithTraceContext(dataMap)
        } finally {
            cleanup()
        }
    }
}
