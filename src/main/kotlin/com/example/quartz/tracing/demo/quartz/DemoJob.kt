package com.example.quartz.tracing.demo.quartz

import mu.KLogging
import org.quartz.JobExecutionContext
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component

@Component
class DemoJob : QuartzJobBean() {

    companion object : KLogging() {
        const val MESSAGE_PARAM_NAME = "MESSAGE"
    }

    override fun executeInternal(context: JobExecutionContext) {
        val message = context.jobDetail.jobDataMap[MESSAGE_PARAM_NAME] as String?
        logger.info("Executing DemoJob with message=$message")
    }
}
