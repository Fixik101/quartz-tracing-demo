package com.example.quartz.tracing.starter

import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import org.quartz.CalendarIntervalTrigger
import org.quartz.CronTrigger
import org.quartz.DailyTimeIntervalTrigger
import org.quartz.JobDataMap
import org.quartz.JobKey
import org.quartz.SimpleTrigger
import org.quartz.Trigger

/**
 * Захват и восстановление trace-контекста для Quartz-задач.
 *
 * Вместо пары traceId/spanId в `JobDataMap` сохраняется полноценный trace-контекст
 * в стандартном формате W3C traceparent — одна строка, та же, что ходит в HTTP-заголовках.
 * Это сохраняет решение о сэмплинге и связь родитель->ребёнок.
 *
 * Класс работает только против абстракций Micrometer Tracing ([Tracer] и [Propagator])
 * и не знает, какой бридж подключён — Brave или OpenTelemetry. Оба объекта приходят
 * конструктором как Spring-бины, реализации подставляет автоконфигурация Boot.
 */
class QuartzTraceContext(
    private val tracer: Tracer,
    private val propagator: Propagator,
    private val properties: QuartzTracingProperties,
) {

    companion object {

        /** Ключ, под которым W3C-пропагация пишет trace-контекст в carrier. */
        private const val TRACEPARENT_KEY = "traceparent"
    }

    private val setter = Propagator.Setter<MutableMap<String, String>> { map, key, value ->
        map?.set(key, value)
    }

    private val getter = Propagator.Getter<Map<String, String>> { map, key -> map[key] }

    /**
     * Захватывает текущий trace-контекст для сохранения в job data.
     *
     * Если активного span'а нет (задача планируется вне trace'а), возвращает пустой
     * `JobDataMap` — job выполнится со своим новым trace'ом.
     *
     * Неотсэмплированный (`noop`) span тоже пропагируется: флаг сэмплинга в traceparent
     * останется `00`, span в трейс-систему не уйдёт, но `traceId` в логах задачи совпадёт
     * с `traceId` запроса — связь логов через асинхронную границу не рвётся.
     */
    fun capture(): JobDataMap {
        val jobDataMap = JobDataMap()
        val span = tracer.currentSpan() ?: return jobDataMap

        val carrier = mutableMapOf<String, String>()
        propagator.inject(span.context(), carrier, setter)

        carrier[TRACEPARENT_KEY]?.let { jobDataMap.put(properties.jobDataKey, it) }
        return jobDataMap
    }

    /**
     * Восстанавливает trace-контекст из traceparent на время выполнения job'а.
     *
     * Для разового триггера из traceparent извлекается контекст родительского span'а,
     * и из него стартует НОВЫЙ span: новый spanId, тот же traceId,
     * parentSpanId = spanId момента планирования, сэмплинг наследуется от traceparent.
     *
     * У повторяющегося триггера в job data лежит один и тот же traceparent на все будущие
     * срабатывания, поэтому родителем он не становится: иначе все запуски задачи срослись
     * бы в один бесконечно растущий trace. Каждый запуск стартует свой корневой span,
     * а traceId планирования остаётся тегом `quartz.scheduling.trace_id` — по нему запуск
     * находится из трейса запроса. Прежнее поведение возвращает флаг
     * [QuartzTracingProperties.parentForRecurringJobs].
     *
     * Имя span'а — ключ job'а, в теги попадают ключи job'а и триггера, чтобы задача
     * была узнаваемой в трейс-системе. Если traceparent не передан — создаётся новый
     * корневой span (свой trace).
     */
    fun restore(traceContext: String?, jobKey: JobKey, trigger: Trigger? = null): TraceScope {
        val traceParent = traceContext?.takeIf { properties.parentForRecurringJobs || !trigger.repeats() }

        val builder = if (traceParent != null) {
            propagator.extract(mapOf(TRACEPARENT_KEY to traceParent), getter)
        } else {
            tracer.spanBuilder()
        }
        builder.name(jobKey.toString())
            .tag("quartz.job.key", jobKey.name)
            .tag("quartz.job.group", jobKey.group)

        trigger?.key?.let {
            builder.tag("quartz.trigger.key", it.name)
            builder.tag("quartz.trigger.group", it.group)
        }

        if (traceContext != null && traceParent == null) {
            schedulingTraceId(traceContext)?.let { builder.tag("quartz.scheduling.trace_id", it) }
        }

        val span = builder.start()
        val scope = tracer.withSpan(span)
        return TraceScope(span, scope)
    }

    /**
     * traceId из W3C traceparent — `00-<trace-id>-<parent-id>-<flags>`.
     *
     * Строка приходит из job data, то есть может быть записана старым форматом
     * или руками, поэтому формат проверяем, а не доверяем ему.
     */
    private fun schedulingTraceId(traceparent: String): String? =
        traceparent.split('-').takeIf { it.size == 4 && it[1].length == 32 }?.get(1)
}

/**
 * Повторится ли триггер после первого срабатывания.
 *
 * Общего метода с числом повторов у [Trigger] нет: интерфейсы разных типов триггеров
 * отвечают по-разному. Для неизвестного типа считаем триггер разовым — задача сохраняет
 * прежнее поведение и не теряет связь с трейсом планирования.
 */
internal fun Trigger?.repeats(): Boolean = when (this) {
    null -> false
    is SimpleTrigger -> repeatCount != 0
    is CronTrigger -> true
    is CalendarIntervalTrigger -> true
    is DailyTimeIntervalTrigger -> repeatCount != 0
    else -> false
}
