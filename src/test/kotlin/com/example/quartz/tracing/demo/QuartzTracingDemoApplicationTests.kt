package com.example.quartz.tracing.demo

import com.example.quartz.tracing.starter.TraceContextCapturingScheduler
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.quartz.Scheduler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class QuartzTracingDemoApplicationTests {

    @Autowired
    private lateinit var scheduler: Scheduler

    @Test
    fun `starter wraps the scheduler`() {
        assertTrue(scheduler is TraceContextCapturingScheduler)
    }
}
