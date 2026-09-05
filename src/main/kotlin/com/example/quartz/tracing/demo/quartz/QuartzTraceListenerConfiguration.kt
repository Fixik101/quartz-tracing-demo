package com.example.quartz.tracing.demo.quartz

import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean

/**
 * Регистрирует [TraceContextJobListener] на [SchedulerFactoryBean] до старта планировщика.
 *
 * JobListener — единственный способ перехватить выполнение job'а на worker-потоке Quartz:
 * [TraceContextJobListener.jobToBeExecuted] восстанавливает trace-контекст из job data
 * (span в scope + MDC) перед [org.quartz.Job.execute], [TraceContextJobListener.jobWasExecuted]
 * закрывает scope после.
 *
 * Захват контекста при планировании listener'ом `jobAdded` здесь НЕ используется: Quartz
 * вызывает его после записи в job store, что ненадёжно для JDBC. Вместо этого захват делает
 * обёртка [TraceContextCapturingScheduler] до persist (см. [QuartzTraceSchedulerConfiguration]).
 */
@Configuration
class QuartzTraceListenerConfiguration(
    private val traceContextJobListener: TraceContextJobListener,
) : SchedulerFactoryBeanCustomizer {

    override fun customize(schedulerFactoryBean: SchedulerFactoryBean) {
        schedulerFactoryBean.setGlobalJobListeners(traceContextJobListener)
    }
}
