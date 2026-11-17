package com.example.quartz.tracing.starter

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.handler.MutableSpan
import brave.handler.SpanHandler
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.propagation.TraceContext
import brave.sampler.Sampler
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext
import io.micrometer.tracing.brave.bridge.BravePropagator
import io.micrometer.tracing.brave.bridge.BraveTracer
import io.micrometer.tracing.brave.bridge.W3CPropagation
import io.micrometer.tracing.propagation.Propagator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.MDC
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Поднимает Brave Tracing с MDCScopeDecorator — так же, как это делает приложение
 * (Spring Boot + micrometer-tracing-brave): вход span'а в scope наполняет MDC ключами
 * traceId/spanId.
 *
 * Дополнительно собирает завершённые спаны через [SpanHandler]: это позволяет проверять,
 * что span не только попал в scope, но и был завершён и отдан репортёру.
 */
abstract class TracingTestBase {

    protected open val sampler: Sampler = Sampler.ALWAYS_SAMPLE

    protected lateinit var tracing: Tracing
    protected lateinit var tracer: Tracer
    protected lateinit var propagator: Propagator
    protected lateinit var finishedSpans: FinishedSpans

    @BeforeEach
    fun setUpTracing() {
        finishedSpans = FinishedSpans()
        val currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
            .addScopeDecorator(MDCScopeDecorator.get())
            .build()
        tracing = Tracing.newBuilder()
            .currentTraceContext(currentTraceContext)
            .propagationFactory(W3CPropagation())
            .traceId128Bit(true)
            .sampler(sampler)
            .addSpanHandler(finishedSpans)
            .build()
        tracer = BraveTracer(tracing.tracer(), BraveCurrentTraceContext(currentTraceContext))
        propagator = BravePropagator(tracing)
    }

    @AfterEach
    fun tearDownTracing() {
        tracing.close()
        MDC.clear()
    }

    protected fun <T> withParentSpan(name: String, block: () -> T): Pair<TraceContext, T> {
        val span = tracing.tracer().nextSpan().name(name).start()
        try {
            val result = tracing.tracer().withSpanInScope(span).use { block() }
            return span.context() to result
        } finally {
            span.finish()
        }
    }

    class FinishedSpans : SpanHandler() {

        private val collected = CopyOnWriteArrayList<MutableSpan>()

        override fun end(context: TraceContext, span: MutableSpan, cause: Cause): Boolean {
            collected += MutableSpan(span)
            return false
        }

        fun spans(): List<MutableSpan> = collected.toList()
    }
}
