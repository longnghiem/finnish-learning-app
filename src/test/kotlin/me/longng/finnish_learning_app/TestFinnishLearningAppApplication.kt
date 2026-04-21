package me.longng.finnish_learning_app

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<FinnishLearningAppApplication>().with(TestcontainersConfiguration::class).run(*args)
}
