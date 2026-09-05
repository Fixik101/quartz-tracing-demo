package com.example.quartz.tracing.demo.quartz

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.quartz.impl.JobDetailImpl
import org.slf4j.MDC

class TraceContextJobListenerTest : TracingTestBase() {

    private val jobListener = TraceContextJobListener()

    @Test
    fun `restores trace around execution and clears MDC after`() {
        val parentSpan = tracing.tracer().nextSpan().name("schedule").start()
        val traceParent: String
        try {
            traceParent = tracing.tracer().withSpanInScope(parentSpan).use {
                QuartzTraceContextUtils.capture()
                    .getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)!!
            }
        } finally {
            parentSpan.finish()
        }
        val parentContext = parentSpan.context()
        val context = executionContext(
            JobDataMap(mapOf(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA to traceParent))
        )

        jobListener.jobToBeExecuted(context)
        try {
            assertEquals(parentContext.traceIdString(), MDC.get("traceId"))
            assertNotNull(MDC.get("spanId"))

            val jobSpanContext = tracing.tracer().currentSpan()!!.context()
            assertEquals(parentContext.traceIdString(), jobSpanContext.traceIdString())
            assertNotEquals(parentContext.spanIdString(), jobSpanContext.spanIdString())
            assertEquals(parentContext.spanIdString(), jobSpanContext.parentIdString())
        } finally {
            jobListener.jobWasExecuted(context, null)
        }

        assertNull(MDC.get("traceId"))
        assertNull(MDC.get("spanId"))
    }

    @Test
    fun `runs job as fresh root span when job data has no trace context`() {
        val context = executionContext(JobDataMap())

        jobListener.jobToBeExecuted(context)
        try {
            assertNotNull(MDC.get("traceId"))
            assertNotNull(MDC.get("spanId"))
            assertNull(tracing.tracer().currentSpan()?.context()?.parentIdString())
        } finally {
            jobListener.jobWasExecuted(context, null)
        }

        assertNull(MDC.get("traceId"))
        assertNull(MDC.get("spanId"))
    }

    private fun executionContext(dataMap: JobDataMap): JobExecutionContext {
        val context = mockk<JobExecutionContext>()
        every { context.jobDetail } returns jobDetail(dataMap)
        return context
    }

    private fun jobDetail(dataMap: JobDataMap = JobDataMap()): JobDetail {
        val jobDetail = JobDetailImpl()
        jobDetail.key = JobKey.jobKey("demo_job")
        jobDetail.jobClass = DemoJob::class.java
        jobDetail.jobDataMap.putAll(dataMap)
        return jobDetail
    }
}
