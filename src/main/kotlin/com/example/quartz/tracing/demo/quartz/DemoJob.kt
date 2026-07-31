package com.example.quartz.tracing.demo.quartz

import mu.KLogging
import org.quartz.JobDataMap
import org.springframework.stereotype.Component

@Component
class DemoJob : TracedQuartzJobBean() {

    companion object : KLogging() {
        const val MESSAGE_PARAM_NAME = "MESSAGE"
    }

    override fun executeWithTraceContext(dataMap: JobDataMap) {
        val message = dataMap[MESSAGE_PARAM_NAME] as String?
        logger.info("Executing DemoJob with message=$message")
    }
}