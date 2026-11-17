package com.example.quartz.tracing.starter

import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer

/**
 * Время жизни trace-контекста одного запуска job'а.
 *
 * Держит span и его scope вместе, чтобы закрывать их строго парой: сначала scope,
 * затем span. `close` идемпотентен — Quartz может вызвать `jobExecutionVetoed`
 * и `jobWasExecuted` в разных сценариях, а завершить span нужно ровно один раз.
 */
class TraceScope internal constructor(
    internal val span: Span,
    private val scope: Tracer.SpanInScope,
) {

    private var closed = false

    /**
     * Закрывает scope и завершает span. Если job упал, ошибка записывается в span
     * до его завершения — иначе в трейс-системе задача будет выглядеть успешной.
     */
    fun close(error: Throwable? = null) {
        if (closed) {
            return
        }
        closed = true
        if (error != null) {
            span.error(error)
        }
        scope.close()
        span.end()
    }
}
