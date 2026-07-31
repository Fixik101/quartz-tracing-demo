package com.example.quartz.tracing.demo.service

import com.example.quartz.tracing.demo.quartz.DemoJob
import com.example.quartz.tracing.demo.quartz.TracedJobBuilder
import mu.KLogging
import org.quartz.DateBuilder
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class DemoJobScheduler(
    private val scheduler: Scheduler,
) {

    companion object : KLogging()

    fun schedule(message: String) {
        val jobId = Random.nextLong()

        logger.info("Scheduling DemoJob with id=$jobId and message=$message")

        val job = TracedJobBuilder.newJob(DemoJob::class.java)
            .withIdentity("demo_job_$jobId")
            .usingJobData(DemoJob.MESSAGE_PARAM_NAME, message)
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
}
