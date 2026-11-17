package com.example.quartz.tracing.starter

import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import org.quartz.Scheduler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.quartz.SchedulerFactoryBean

/**
 * Автоконфигурация стартера: собирает проброс trace-контекста в Quartz-задачи.
 *
 * Порядок важен: конфигурация применяется после [QuartzAutoConfiguration], иначе
 * `SchedulerFactoryBean` ещё не объявлен и `@ConditionalOnBean` не сработает.
 * Пакет [SchedulerFactoryBeanCustomizer] в Boot 4 — `org.springframework.boot.quartz.autoconfigure`
 * (в Boot 3 он лежал в `org.springframework.boot.autoconfigure.quartz`).
 */
@AutoConfiguration(
    after = [QuartzAutoConfiguration::class],
    afterName = [
        "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
        "org.springframework.boot.micrometer.tracing.brave.autoconfigure.BraveAutoConfiguration",
        "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration",
    ],
)
@ConditionalOnClass(Scheduler::class, Tracer::class, Propagator::class)
@ConditionalOnBean(SchedulerFactoryBean::class, Tracer::class, Propagator::class)
@ConditionalOnProperty(prefix = "quartz.tracing", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QuartzTracingProperties::class)
class QuartzTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun quartzTraceContext(
        tracer: Tracer,
        propagator: Propagator,
        properties: QuartzTracingProperties,
    ): QuartzTraceContext = QuartzTraceContext(tracer, propagator, properties)

    @Bean
    @ConditionalOnMissingBean
    fun traceContextJobListener(
        traceContext: QuartzTraceContext,
        properties: QuartzTracingProperties,
    ): TraceContextJobListener = TraceContextJobListener(traceContext, properties)

    @Bean
    @Primary
    @ConditionalOnMissingBean(TraceContextCapturingScheduler::class)
    fun traceContextAwareScheduler(
        schedulerFactoryBean: SchedulerFactoryBean,
        traceContext: QuartzTraceContext,
    ): Scheduler = TraceContextCapturingScheduler(schedulerFactoryBean.getObject()!!, traceContext)

    @Bean
    fun quartzTracingSchedulerCustomizer(
        traceContextJobListener: TraceContextJobListener,
    ): SchedulerFactoryBeanCustomizer =
        SchedulerFactoryBeanCustomizer { it.setGlobalJobListeners(traceContextJobListener) }
}
