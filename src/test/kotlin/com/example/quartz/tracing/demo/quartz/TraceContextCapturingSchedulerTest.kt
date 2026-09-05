package com.example.quartz.tracing.demo.quartz

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.quartz.DateBuilder
import org.quartz.Job
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.impl.JobDetailImpl
import org.quartz.impl.StdSchedulerFactory
import java.util.Date
import java.util.UUID

class TraceContextCapturingSchedulerTest : TracingTestBase() {

    private lateinit var scheduler: Scheduler
    private lateinit var tracedScheduler: TraceContextCapturingScheduler

    @BeforeEach
    fun setUpScheduler() {
        scheduler = StdSchedulerFactory().scheduler
        tracedScheduler = TraceContextCapturingScheduler(scheduler)
    }

    @AfterEach
    fun tearDownScheduler() {
        scheduler.shutdown()
    }

    @Test
    fun `captures trace context before delegating to real scheduler`() {
        val delegate = mockk<Scheduler>()
        val jobDetail = jobDetail()
        val trigger = triggerFor(jobDetail)
        val delegatedJob = slot<JobDetail>()
        every { delegate.scheduleJob(capture(delegatedJob), any()) } returns Date()

        val wrapper = TraceContextCapturingScheduler(delegate)
        runWithSpan { wrapper.scheduleJob(jobDetail, trigger) }

        // делегируется тот же объект, но уже с захваченным контекстом в job data —
        // то есть захват происходит до persist, а не после
        assertSame(jobDetail, delegatedJob.captured)
        val traceParent = delegatedJob.captured.jobDataMap
            .getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)
        assertNotNull(traceParent)
        assertTrue(traceParent!!.startsWith("00-"))
    }

    @Test
    fun `scheduleJob stores captured context with job in RAM job store`() {
        val jobDetail = jobDetail()

        val expectedTraceId = runWithSpan {
            val traceId = tracing.tracer().currentSpan()!!.context().traceIdString()
            tracedScheduler.scheduleJob(jobDetail, triggerFor(jobDetail))
            traceId
        }

        val stored = scheduler.getJobDetail(jobDetail.key)
        assertNotNull(stored)
        val traceParent = stored!!.jobDataMap.getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)
        assertNotNull(traceParent)
        assertTrue(traceParent!!.contains(expectedTraceId))
    }

    @Test
    fun `addJob stores captured context with job in RAM job store`() {
        val jobDetail = jobDetail()

        val expectedTraceId = runWithSpan {
            val traceId = tracing.tracer().currentSpan()!!.context().traceIdString()
            tracedScheduler.addJob(jobDetail, true)
            traceId
        }

        val stored = scheduler.getJobDetail(jobDetail.key)
        assertNotNull(stored)
        assertTrue(
            stored!!.jobDataMap.getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)!!.contains(expectedTraceId)
        )
    }

    @Test
    fun `scheduleJobs captures context into every job`() {
        val jobA = jobDetail()
        val jobB = jobDetail()
        val jobsAndTriggers = linkedMapOf(
            jobA to setOf(triggerFor(jobA)),
            jobB to setOf(triggerFor(jobB)),
        )

        val expectedTraceId = runWithSpan {
            val traceId = tracing.tracer().currentSpan()!!.context().traceIdString()
            tracedScheduler.scheduleJobs(jobsAndTriggers, true)
            traceId
        }

        val traceParentA = scheduler.getJobDetail(jobA.key)!!.jobDataMap
            .getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)
        val traceParentB = scheduler.getJobDetail(jobB.key)!!.jobDataMap
            .getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA)
        assertNotNull(traceParentA)
        assertNotNull(traceParentB)
        assertTrue(traceParentA!!.startsWith("00-") && traceParentA.contains(expectedTraceId))
        assertTrue(traceParentB!!.startsWith("00-") && traceParentB.contains(expectedTraceId))
    }

    @Test
    fun `scheduling without active span leaves job data untouched`() {
        val jobDetail = jobDetail()

        tracedScheduler.scheduleJob(jobDetail, triggerFor(jobDetail))

        val stored = scheduler.getJobDetail(jobDetail.key)
        assertNotNull(stored)
        assertNull(stored!!.jobDataMap.getString(QuartzTraceContextUtils.TRACE_CONTEXT_JOB_DATA))
    }

    private fun <T> runWithSpan(block: () -> T): T {
        val span = tracing.tracer().nextSpan().name("schedule").start()
        try {
            return tracing.tracer().withSpanInScope(span).use { block() }
        } finally {
            span.finish()
        }
    }

    private fun jobDetail(): JobDetail {
        val jobDetail = JobDetailImpl()
        jobDetail.key = JobKey.jobKey("job_${UUID.randomUUID()}")
        jobDetail.jobClass = NoopJob::class.java
        jobDetail.setDurability(true)
        return jobDetail
    }

    private fun triggerFor(jobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(jobDetail.key)
            .withIdentity("trigger_${UUID.randomUUID()}")
            .startAt(DateBuilder.futureDate(1, DateBuilder.IntervalUnit.DAY))
            .build()

    class NoopJob : Job {
        override fun execute(context: JobExecutionContext) = Unit
    }
}
