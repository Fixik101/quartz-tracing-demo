package com.example.quartz.tracing.demo.controller

import com.example.quartz.tracing.demo.service.DemoJobScheduler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/jobs")
class JobController(
    private val demoJobScheduler: DemoJobScheduler
) {

    @PostMapping("/schedule")
    fun scheduleJob(@RequestBody message: String) = demoJobScheduler.schedule(message)
}