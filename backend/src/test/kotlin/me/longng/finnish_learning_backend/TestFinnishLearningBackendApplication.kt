package me.longng.finnish_learning_backend

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<FinnishLearningBackendApplication>().with(TestcontainersConfiguration::class).run(*args)
}
