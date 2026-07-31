package com.example.quartz.tracing.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class QuartzTracingDemoApplication

fun main(args: Array<String>) {
    runApplication<QuartzTracingDemoApplication>(*args)
}
