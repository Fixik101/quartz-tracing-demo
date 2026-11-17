package com.example.quartz.tracing.starter

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Настройки проброса trace-контекста в Quartz-задачи.
 *
 * `enabled` позволяет выключить автоконфигурацию целиком (например, в тестах),
 * `jobDataKey` — имя ключа в `JobDataMap`, под которым лежит W3C traceparent.
 */
@ConfigurationProperties("quartz.tracing")
class QuartzTracingProperties {

    var enabled: Boolean = true

    var jobDataKey: String = "TRACE_CONTEXT"

    /**
     * Считать трейс планирования родителем для всех запусков повторяющегося триггера.
     *
     * По умолчанию `false`: у cron-задачи каждый запуск — свой корневой trace, иначе
     * все будущие срабатывания навсегда прирастают к одному трейсу планирования.
     */
    var parentForRecurringJobs: Boolean = false
}
