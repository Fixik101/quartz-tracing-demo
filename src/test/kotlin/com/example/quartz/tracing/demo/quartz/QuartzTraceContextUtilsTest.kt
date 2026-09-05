package com.example.quartz.tracing.demo.quartz

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class QuartzTraceContextUtilsTest : TracingTestBase() {

    @Test
    fun `capture stores W3C traceparent`() {
        val parentSpan = tracing.tracer().nextSpan().name("schedule").start()
        try {
            tracing.tracer().withSpanInScope(parentSpan).use {
                val jobData = QuartzTraceContextUtils.capture()

                val traceParent = jobData.getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)
                assertNotNull(traceParent)
                assertTrue(traceParent!!.startsWith("00-"), "expected W3C traceparent, got: $traceParent")
                assertTrue(traceParent.contains(parentSpan.context().traceIdString()))
            }
        } finally {
            parentSpan.finish()
        }
    }

    @Test
    fun `capture without active span returns empty job data`() {
        assertNull(QuartzTraceContextUtils.capture().getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA))
    }

    @Test
    fun `restore continues trace with new span and populates MDC`() {
        val parentSpan = tracing.tracer().nextSpan().name("schedule").start()
        val traceParent: String
        try {
            traceParent = tracing.tracer().withSpanInScope(parentSpan).use {
                QuartzTraceContextUtils.capture().getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)!!
            }
        } finally {
            parentSpan.finish()
        }
        val parentContext = parentSpan.context()

        val cleanup = QuartzTraceContextUtils.restore(traceParent)
        try {
            val jobSpanContext = tracing.tracer().currentSpan()!!.context()

            assertEquals(parentContext.traceIdString(), MDC.get("traceId"))
            assertNotNull(MDC.get("spanId"))
            assertEquals(parentContext.traceIdString(), jobSpanContext.traceIdString())
            assertNotEquals(parentContext.spanIdString(), jobSpanContext.spanIdString())
            assertEquals(parentContext.spanIdString(), jobSpanContext.parentIdString())
        } finally {
            cleanup()
        }

        assertNull(MDC.get("traceId"))
        assertNull(MDC.get("spanId"))
    }

    @Test
    fun `restore without traceparent creates fresh root span`() {
        val cleanup = QuartzTraceContextUtils.restore(null)
        try {
            assertNotNull(MDC.get("traceId"))
            assertNotNull(MDC.get("spanId"))
            assertNull(tracing.tracer().currentSpan()?.context()?.parentIdString())
        } finally {
            cleanup()
        }

        assertNull(MDC.get("traceId"))
        assertNull(MDC.get("spanId"))
    }
}
