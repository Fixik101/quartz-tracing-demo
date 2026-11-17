package com.example.quartz.tracing.starter

import brave.sampler.Sampler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.quartz.JobKey
import org.slf4j.MDC

class UnsampledTraceContextTest : TracingTestBase() {

    override val sampler: Sampler = Sampler.NEVER_SAMPLE

    private val properties = QuartzTracingProperties()
    private val traceContext by lazy { QuartzTraceContext(tracer, propagator, properties) }

    @Test
    fun `unsampled parent context is captured so logs stay correlated`() {
        val (parentContext, jobData) = withParentSpan("schedule") {
            traceContext.capture()
        }

        val traceParent = jobData.getString(properties.jobDataKey)
        assertNotNull(traceParent, "unsampled context must still be propagated for log correlation")
        assertTrue(traceParent!!.endsWith("-00"), "expected unsampled traceparent, got: $traceParent")
        assertTrue(traceParent.contains(parentContext.traceIdString()))
    }

    @Test
    fun `job continues unsampled trace without reporting spans`() {
        val (parentContext, traceParent) = withParentSpan("schedule") {
            traceContext.capture().getString(properties.jobDataKey)!!
        }

        val scope = traceContext.restore(traceParent, JobKey.jobKey("demo_job", "jobs"))
        try {
            assertFalse(scope.span.context().sampled()!!)
            assertEquals(parentContext.traceIdString(), MDC.get("traceId"))
        } finally {
            scope.close()
        }

        assertTrue(finishedSpans.spans().isEmpty(), "unsampled spans must not be reported")
    }
}
