package com.example.quartz.tracing.starter

import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.quartz.Scheduler
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import java.util.function.Supplier

class QuartzTracingAutoConfigurationTest : TracingTestBase() {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                QuartzAutoConfiguration::class.java,
                QuartzTracingAutoConfiguration::class.java,
            )
        )
        .withPropertyValues("spring.quartz.auto-startup=false")
        .withBean(Tracer::class.java, { tracer })
        .withBean(Propagator::class.java, { propagator })

    @Test
    fun `wraps scheduler and registers the job listener`() {
        contextRunner.run { context ->
            assertTrue(context.getBean(Scheduler::class.java) is TraceContextCapturingScheduler)

            val listeners = context.getBean(SchedulerFactoryBean::class.java)
                .scheduler
                .listenerManager
                .jobListeners
            assertTrue(listeners.any { it is TraceContextJobListener })
        }
    }

    @Test
    fun `can be disabled with a property`() {
        contextRunner
            .withPropertyValues("quartz.tracing.enabled=false")
            .run { context ->
                assertFalse(context.getBean(Scheduler::class.java) is TraceContextCapturingScheduler)

                val listeners = context.getBean(SchedulerFactoryBean::class.java)
                    .scheduler
                    .listenerManager
                    .jobListeners
                assertFalse(listeners.any { it is TraceContextJobListener })
            }
    }
}
