package com.example.quartz.tracing.starter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.quartz.CronScheduleBuilder
import org.quartz.JobKey
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.slf4j.MDC

class QuartzTraceContextTest : TracingTestBase() {

    private val properties = QuartzTracingProperties()
    private val traceContext by lazy { QuartzTraceContext(tracer, propagator, properties) }

    @Test
    fun `capture stores W3C traceparent`() {
        val (parentContext, jobData) = withParentSpan("schedule") {
            traceContext.capture()
        }

        val traceParent = jobData.getString(properties.jobDataKey)
        assertNotNull(traceParent)
        assertTrue(traceParent!!.startsWith("00-"), "expected W3C traceparent, got: $traceParent")
        assertTrue(traceParent.contains(parentContext.traceIdString()))
    }

    @Test
    fun `capture without active span returns empty job data`() {
        assertNull(traceContext.capture().getString(properties.jobDataKey))
    }

    @Test
    fun `restore continues trace with a finished child span and populates MDC`() {
        val (parentContext, traceParent) = withParentSpan("schedule") {
            traceContext.capture().getString(properties.jobDataKey)!!
        }
        val spansBefore = finishedSpans.spans().size

        val scope = traceContext.restore(traceParent, jobKey(), oneShotTrigger())
        try {
            val jobContext = scope.span.context()

            assertEquals(parentContext.traceIdString(), jobContext.traceId())
            assertNotEquals(parentContext.spanIdString(), jobContext.spanId())
            assertEquals(parentContext.spanIdString(), jobContext.parentId())

            assertEquals(parentContext.traceIdString(), MDC.get("traceId"))
            assertEquals(jobContext.spanId(), MDC.get("spanId"))
        } finally {
            scope.close()
        }

        assertNull(MDC.get("traceId"))
        assertNull(MDC.get("spanId"))

        val finished = finishedSpans.spans()
        assertEquals(spansBefore + 1, finished.size)
        val jobSpan = finished.last()
        assertEquals("jobs.demo_job", jobSpan.name())
        assertEquals(parentContext.spanIdString(), jobSpan.parentId())
        assertEquals("demo_job", jobSpan.tag("quartz.job.key"))
        assertEquals("jobs", jobSpan.tag("quartz.job.group"))
        assertEquals("demo_trigger", jobSpan.tag("quartz.trigger.key"))
    }

    @Test
    fun `recurring trigger starts its own root trace and keeps planning trace id as a tag`() {
        val (parentContext, traceParent) = withParentSpan("schedule") {
            traceContext.capture().getString(properties.jobDataKey)!!
        }

        val scope = traceContext.restore(traceParent, jobKey(), cronTrigger())
        try {
            val jobContext = scope.span.context()

            assertNotEquals(parentContext.traceIdString(), jobContext.traceId())
            assertNull(jobContext.parentId())
            assertEquals(jobContext.traceId(), MDC.get("traceId"))
        } finally {
            scope.close()
        }

        val jobSpan = finishedSpans.spans().last()
        assertNull(jobSpan.parentId())
        assertEquals(parentContext.traceIdString(), jobSpan.tag("quartz.scheduling.trace_id"))
    }

    @Test
    fun `parentForRecurringJobs keeps every firing in the scheduling trace`() {
        properties.parentForRecurringJobs = true
        val (parentContext, traceParent) = withParentSpan("schedule") {
            traceContext.capture().getString(properties.jobDataKey)!!
        }

        val scope = traceContext.restore(traceParent, jobKey(), cronTrigger())
        try {
            assertEquals(parentContext.traceIdString(), scope.span.context().traceId())
            assertEquals(parentContext.spanIdString(), scope.span.context().parentId())
        } finally {
            scope.close()
        }

        assertNull(finishedSpans.spans().last().tag("quartz.scheduling.trace_id"))
    }

    @Test
    fun `restore without traceparent creates a finished root span`() {
        val scope = traceContext.restore(null, jobKey())
        try {
            assertNotNull(MDC.get("traceId"))
            assertNull(scope.span.context().parentId())
        } finally {
            scope.close()
        }

        val finished = finishedSpans.spans()
        assertEquals(1, finished.size)
        assertNull(finished.single().parentId())
    }

    @Test
    fun `failed job marks the span with an error before finishing it`() {
        val scope = traceContext.restore(null, jobKey())
        val failure = IllegalStateException("job failed")

        scope.close(failure)

        val jobSpan = finishedSpans.spans().single()
        assertEquals(failure, jobSpan.error())
    }

    @Test
    fun `close is idempotent`() {
        val scope = traceContext.restore(null, jobKey())

        scope.close()
        scope.close()

        assertEquals(1, finishedSpans.spans().size)
    }

    @Test
    fun `unsampled traceparent keeps identifiers in logs but does not report the span`() {
        val traceParent = "00-a3f4b2c1d5e6f708192a3b4c5d6e7f80-9c2f1b3a4d5e6f70-00"

        val scope = traceContext.restore(traceParent, jobKey())
        try {
            assertFalse(scope.span.context().sampled()!!)
            assertEquals("a3f4b2c1d5e6f708192a3b4c5d6e7f80", MDC.get("traceId"))
        } finally {
            scope.close()
        }

        assertTrue(finishedSpans.spans().isEmpty(), "unsampled span must not be reported")
    }

    private fun jobKey() = JobKey.jobKey("demo_job", "jobs")

    private fun oneShotTrigger(): Trigger = TriggerBuilder.newTrigger()
        .withIdentity("demo_trigger", "triggers")
        .startNow()
        .build()

    private fun cronTrigger(): Trigger = TriggerBuilder.newTrigger()
        .withIdentity("demo_trigger", "triggers")
        .withSchedule(CronScheduleBuilder.cronSchedule("0 0/5 * * * ?"))
        .build()
}
