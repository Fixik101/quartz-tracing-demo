package com.example.quartz.tracing.demo.quartz

import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.springframework.scheduling.quartz.QuartzJobBean
import com.example.quartz.tracing.demo.quartz.QuartzTraceContextUtils.TRACE_ID_JOB_DATA
import com.example.quartz.tracing.demo.quartz.QuartzTraceContextUtils.SPAN_ID_JOB_DATA

/**
 * Базовый класс для Quartz задач, который автоматически восстанавливает trace контекст в MDC.
 * Наследники должны реализовать [executeWithTraceContext] вместо [executeInternal].
 */
abstract class TracedQuartzJobBean : QuartzJobBean() {

    protected abstract fun executeWithTraceContext(dataMap: JobDataMap)

    override fun executeInternal(context: JobExecutionContext) {
        val dataMap = context.jobDetail.jobDataMap
        val traceId = dataMap.getString(TRACE_ID_JOB_DATA)
        val spanId = dataMap.getString(SPAN_ID_JOB_DATA)

        val cleanup = QuartzTraceContextUtils.restore(traceId)

        try {
            executeWithTraceContext(dataMap)
        } finally {
            cleanup()
        }
    }
}
