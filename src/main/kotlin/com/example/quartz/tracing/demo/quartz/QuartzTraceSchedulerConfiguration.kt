package com.example.quartz.tracing.demo.quartz

import org.quartz.Scheduler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.quartz.SchedulerFactoryBean

/**
 * Подменяет выдаваемый наружу [Scheduler] обёрткой [TraceContextCapturingScheduler].
 *
 * Обёртка объявлена @Primary-бином: всё, что инжектит [Scheduler] (в т.ч.
 * [com.example.quartz.tracing.demo.service.DemoJobScheduler]), планирует через неё,
 * и trace-контекст захватывается до записи job'а в store (работает и для RAM,
 * и для JDBC хранилища — см. [TraceContextCapturingScheduler]).
 *
 * Сам SchedulerFactoryBean от Spring Boot не заменяется — конфигурация
 * `spring.quartz.*` (job store, datasource и т.п.) продолжает действовать как обычно.
 * Зависимость от SchedulerFactoryBean гарантирует, что планировщик уже создан
 * (afterPropertiesSet выполнен), когда мы забираем его через `getObject`.
 */
@Configuration(proxyBeanMethods = false)
class QuartzTraceSchedulerConfiguration {

    @Bean
    @Primary
    fun traceContextAwareScheduler(schedulerFactoryBean: SchedulerFactoryBean): Scheduler =
        TraceContextCapturingScheduler(schedulerFactoryBean.getObject()!!)
}
