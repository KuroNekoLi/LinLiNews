package com.linli.news

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class LinLiNewsApplication

/**
 * 啟動 Spring Boot 應用程式。
 */
fun main(args: Array<String>) {
    runApplication<LinLiNewsApplication>(*args)
}
