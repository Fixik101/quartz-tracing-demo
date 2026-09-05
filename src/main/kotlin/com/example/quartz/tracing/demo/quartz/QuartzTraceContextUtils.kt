package com.example.quartz.tracing.demo.quartz

import brave.Tracing
import brave.propagation.Propagation
import org.quartz.JobDataMap

/**
 * Захват и восстановление trace-контекста для Quartz-задач.
 *
 * Вместо пары traceId/spanId в JobDataMap сохраняется полноценный trace-контекст
 * в стандартном формате W3C traceparent — одна строка, та же, что ходит в HTTP-заголовках.
 * Это сохраняет решение о сэмплинге и связь родитель->ребёнок: при выполнении job'а
 * создаётся НОВЫЙ span с тем же traceId, у которого parentSpanId = spanId момента планирования.
 */
object QuartzTraceContextUtils {

    /** Ключ в JobDataMap, под которым хранится trace-контекст (W3C traceparent). */
    const val TRACE_CONTEXT_JOB_DATA = "TRACE_CONTEXT"

    /** Ключ, под которым W3C-пропагация пишет trace-контекст в carrier. */
    private const val TRACEPARENT_KEY = "traceparent"

    /**
     * Захватывает текущий trace-контекст для сохранения в Quartz job data.
     *
     * Если активного span'а нет (задача планируется вне trace'а), возвращает пустой
     * JobDataMap — job тогда выполнится со своим новым trace'ом.
     */
    fun capture(): JobDataMap {
        val jobDataMap = JobDataMap()
        val tracing = Tracing.current() ?: return jobDataMap
        val span = tracing.tracer().currentSpan() ?: return jobDataMap

        val carrier = mutableMapOf<String, String>()
        val setter = Propagation.Setter<MutableMap<String, String>, String> { map, key, value ->
            map[key] = value
        }
        tracing.propagation().injector(setter).inject(span.context(), carrier)

        carrier[TRACEPARENT_KEY]?.let { jobDataMap.put(TRACE_CONTEXT_JOB_DATA, it) }
        return jobDataMap
    }

    /**
     * Восстанавливает trace-контекст из traceparent и делает его текущим на время
     * выполнения job'а.
     *
     * Из traceparent извлекается контекст родительского span'а, и через
     * [brave.Tracer.nextSpan] создаётся НОВЫЙ span: новый spanId, тот же traceId,
     * parentSpanId = spanId момента планирования, сэмплинг наследуется от traceparent.
     *
     * MDC (ключи traceId/spanId) наполняется автоматически при входе span'а в scope —
     * за это отвечает MDCScopeDecorator, который Spring Boot подключает к CurrentTraceContext.
     *
     * Если traceparent не передан — создаётся новый корневой span (свой trace).
     *
     * Возвращает функцию очистки, которую нужно вызвать в `finally`.
     */
    fun restore(traceContext: String?): () -> Unit {
        val tracing = Tracing.current() ?: return {}
        val tracer = tracing.tracer()

        val carrier = traceContext?.let { mapOf(TRACEPARENT_KEY to it) } ?: emptyMap()
        val getter = Propagation.Getter<Map<String, String>, String> { map, key -> map[key] }
        val extracted = tracing.propagation()
            .extractor(getter)
            .extract(carrier)

        val span = tracer.nextSpan(extracted)
        val scope = tracer.withSpanInScope(span)
        return scope::close
    }
}
