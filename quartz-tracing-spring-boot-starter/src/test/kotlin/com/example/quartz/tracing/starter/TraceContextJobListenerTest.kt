package com.example.quartz.tracing.starter

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.JobKey
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.JobDetailImpl
import org.slf4j.MDC

class TraceContextJobListenerTest : TracingTestBase() {

    private val properties = QuartzTracingProperties()
    private val traceContext by lazy { QuartzTraceContext(tracer, propagator, properties) }
    private val listener by lazy { TraceContextJobListener(traceContext, properties) }

    @Test
    fun `restores trace around execution and clears MDC after`() {
        val (parentContext, traceParent) = withParentSpan("schedule") {
            traceContext.capture().getString(properties.jobDataKey)!!
        }
        val context = executionContext(jobData(traceParent))

        listener.jobToBeExecuted(context)
        try {
            assertEquals(parentContext.traceIdString(), MDC.get("traceId"))
            assertNotNull(MDC.get("spanId"))
        } finally {
            listener.jobWasExecuted(context, null)
        }

        assertNull(MDC.get("traceId"))
        assertNull(MDC.get("spanId"))

        val finished = finishedSpans.spans()
        assertEquals(2, finished.size)
        assertEquals(parentContext.spanIdString(), finished.last().parentId())
    }

    @Test
    fun `runs job as fresh root span when job data has no trace context`() {
        val context = executionContext(JobDataMap())

        listener.jobToBeExecuted(context)
        try {
            assertNotNull(MDC.get("traceId"))
            assertNotNull(MDC.get("spanId"))
        } finally {
            listener.jobWasExecuted(context, null)
        }

        assertNull(finishedSpans.spans().single().parentId())
    }

    @Test
    fun `failed job is reported to the trace backend as an error`() {
        val context = executionContext(JobDataMap())
        val failure = JobExecutionException("boom")

        listener.jobToBeExecuted(context)
        listener.jobWasExecuted(context, failure)

        assertEquals(failure, finishedSpans.spans().single().error())
    }

    @Test
    fun `vetoed execution does not leak scope or span`() {
        val context = executionContext(JobDataMap())

        listener.jobToBeExecuted(context)
        listener.jobExecutionVetoed(context)

        assertNull(MDC.get("traceId"))
        assertEquals(1, finishedSpans.spans().size)
    }

    private fun executionContext(dataMap: JobDataMap): JobExecutionContext {
        val context = mockk<JobExecutionContext>()
        every { context.jobDetail } returns jobDetail(dataMap)
        every { context.trigger } returns mockk<Trigger>().also {
            every { it.key } returns TriggerKey.triggerKey("demo_trigger", "triggers")
        }
        return context
    }

    private fun jobData(traceParent: String): JobDataMap =
        JobDataMap(mapOf(properties.jobDataKey to traceParent))

    private fun jobDetail(dataMap: JobDataMap = JobDataMap()): JobDetail {
        val jobDetail = JobDetailImpl()
        jobDetail.key = JobKey.jobKey("demo_job", "jobs")
        jobDetail.jobClass = NoopJob::class.java
        jobDetail.jobDataMap.putAll(dataMap)
        return jobDetail
    }

    private class NoopJob : org.quartz.Job {
        override fun execute(context: JobExecutionContext) = Unit
    }
}
