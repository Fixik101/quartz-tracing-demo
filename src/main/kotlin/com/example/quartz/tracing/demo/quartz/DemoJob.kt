package com.example.quartz.tracing.demo.quartz

import mu.KLogging
import org.quartz.JobExecutionContext
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component

@Component
class DemoJob : QuartzJobBean() {

    companion object : KLogging() {
        const val MESSAGE_PARAM_NAME = "MESSAGE"
        const val FAIL_PARAM_NAME = "FAIL"
    }

    override fun executeInternal(context: JobExecutionContext) {
        val message = context.jobDetail.jobDataMap[MESSAGE_PARAM_NAME] as String?
        val fail = context.jobDetail.jobDataMap[FAIL_PARAM_NAME] as Boolean? ?: false

        logger.info("Executing DemoJob with message=$message, fail=$fail")

        if (fail) {
            throw IllegalStateException("DemoJob failed on purpose, message=$message")
        }
    }
}
