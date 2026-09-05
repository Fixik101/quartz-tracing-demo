package com.example.quartz.tracing.demo.quartz

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.sampler.Sampler
import io.micrometer.tracing.brave.bridge.W3CPropagation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.MDC

/**
 * Поднимает Brave Tracing с MDCScopeDecorator — так же, как это делает приложение
 * (Spring Boot + micrometer-tracing-brave): вход span'а в scope наполняет MDC ключами
 * traceId/spanId, что проверяется в тестах listener'ов.
 */
abstract class TracingTestBase {

    protected lateinit var tracing: Tracing

    @BeforeEach
    fun setUpTracing() {
        val currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
            .addScopeDecorator(MDCScopeDecorator.get())
            .build()
        tracing = Tracing.newBuilder()
            .currentTraceContext(currentTraceContext)
            .propagationFactory(W3CPropagation())
            .traceId128Bit(true)
            .sampler(Sampler.ALWAYS_SAMPLE)
            .build()
    }

    @AfterEach
    fun tearDownTracing() {
        tracing.close()
        MDC.clear()
    }
}
