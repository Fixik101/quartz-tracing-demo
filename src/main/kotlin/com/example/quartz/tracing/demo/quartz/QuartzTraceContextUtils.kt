package com.example.quartz.tracing.demo.quartz

import brave.Tracer
import brave.Tracing
import brave.propagation.TraceContextOrSamplingFlags
import brave.propagation.TraceIdContext
import org.quartz.JobDataMap
import org.slf4j.MDC
import java.util.UUID

object QuartzTraceContextUtils {

    const val TRACE_ID_KEY = "traceId"
    const val SPAN_ID_KEY = "spanId"

    const val TRACE_ID_JOB_DATA = "TRACE_ID"
    const val SPAN_ID_JOB_DATA = "SPAN_ID"

    /**
     * Захватывает текущий trace контекст для сохранения в Quartz job data.
     */
    fun capture(): JobDataMap {
        val jobDataMap = JobDataMap()
        val context = Tracing.current()
            ?.tracer()
            ?.currentSpan()
            ?.context() ?: return jobDataMap

        jobDataMap.put(TRACE_ID_JOB_DATA, context.traceIdString())
        jobDataMap.put(SPAN_ID_JOB_DATA, context.spanIdString())

        return jobDataMap
    }

    /**
     * Восстанавливает trace контекст — и в MDC (для логгера), и в Brave Tracer (для capture).
     *
     * Создаёт span, привязанный к переданному traceId, и делает его текущим через
     * [Tracer.withSpanInScope]. После этого [Tracing.current().tracer().currentSpan()]
     * вернёт активный span с правильным traceId, а [capture()] захватит актуальные данные.
     *
     * Возвращает функцию очистки, которую нужно вызвать в `finally`.
     */
    fun restore(traceId: String?): () -> Unit {
        val resolvedTraceId = traceId ?: UUID.randomUUID().toString().replace("-", "")

        val tracer = Tracing.current()?.tracer()
        val scope = tracer?.let { tr ->
            val traceIdContext = parseTraceIdContext(resolvedTraceId)
            val span = tr.nextSpan(TraceContextOrSamplingFlags.create(traceIdContext))
            tr.withSpanInScope(span)
        }

        val actualTraceId = scope?.let { tracer.currentSpan()?.context()?.traceIdString() } ?: resolvedTraceId
        val actualSpanId = scope?.let { tracer.currentSpan()?.context()?.spanIdString() }
        MDC.put(TRACE_ID_KEY, actualTraceId)
        MDC.put(SPAN_ID_KEY, actualSpanId)

        return {
            MDC.remove(TRACE_ID_KEY)
            MDC.remove(SPAN_ID_KEY)
            scope?.close()
        }
    }

    private fun parseTraceIdContext(traceId: String): TraceIdContext {
        val builder = TraceIdContext.newBuilder()
        if (traceId.length > 16) {
            builder.traceIdHigh(java.math.BigInteger(traceId.take(16), 16).toLong())
            builder.traceId(java.math.BigInteger(traceId.substring(16), 16).toLong())
        } else {
            builder.traceId(java.math.BigInteger(traceId, 16).toLong())
        }
        return builder.build()
    }
}
