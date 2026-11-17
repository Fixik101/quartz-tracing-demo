package com.example.quartz.tracing.demo.service

import com.example.quartz.tracing.demo.quartz.DemoJob
import mu.KLogging
import org.quartz.DateBuilder
import org.quartz.JobBuilder
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class DemoJobScheduler(
    private val scheduler: Scheduler,
) {

    companion object : KLogging()

    fun schedule(message: String, fail: Boolean = false) {
        val jobId = Random.nextLong()

        logger.info("Scheduling DemoJob with id=$jobId, message=$message and fail=$fail")

        // trace-контекст захватывается автоматически стартером:
        // TraceContextCapturingScheduler кладёт его в job data до записи в job store
        val job = JobBuilder.newJob(DemoJob::class.java)
            .withIdentity("demo_job_$jobId")
            .usingJobData(DemoJob.MESSAGE_PARAM_NAME, message)
            .usingJobData(DemoJob.FAIL_PARAM_NAME, fail)
            .build()

        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("demo_job_$jobId")
            .forJob(job)
            .startAt(DateBuilder.futureDate(1, DateBuilder.IntervalUnit.MINUTE))
            .build()

        scheduler.scheduleJob(job, setOf(trigger), true)

        logger.info("DemoJob with id=$jobId scheduled")
    }

    fun scheduleRecurring(message: String) {
        val jobId = Random.nextLong()

        logger.info("Scheduling recurring DemoJob with id=$jobId, message=$message")

        val job = JobBuilder.newJob(DemoJob::class.java)
            .withIdentity("recurring_demo_job_$jobId")
            .usingJobData(DemoJob.MESSAGE_PARAM_NAME, message)
            .build()

        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("recurring_demo_job_$jobId")
            .forJob(job)
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInSeconds(10)
                    .repeatForever()
            )
            .startNow()
            .build()

        scheduler.scheduleJob(job, setOf(trigger), true)

        logger.info("Recurring DemoJob with id=$jobId scheduled")
    }
}
