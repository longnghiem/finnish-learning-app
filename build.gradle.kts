plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
}

group = "me.longng"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.spring.boot.starter.flyway)
	implementation(libs.spring.boot.starter.jooq)
	implementation(libs.spring.boot.starter.security)
	implementation(libs.spring.boot.starter.validation)
	implementation(libs.spring.boot.starter.webmvc)
	implementation(libs.flyway.database.postgresql)
	implementation(libs.kotlin.reflect)
	implementation(libs.springdoc.openapi.starter.webmvc.ui)
	implementation(libs.jackson.module.kotlin)
	runtimeOnly(libs.postgresql)
	testImplementation(libs.spring.boot.starter.flyway.test)
	testImplementation(libs.spring.boot.starter.jooq.test)
	testImplementation(libs.spring.boot.starter.security.test)
	testImplementation(libs.spring.boot.starter.validation.test)
	testImplementation(libs.spring.boot.starter.webmvc.test)
	testImplementation(libs.spring.boot.testcontainers)
	testImplementation(libs.kotlin.test.junit5)
	testImplementation(libs.testcontainers.junit.jupiter)
	testImplementation(libs.testcontainers.postgresql)
	testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	val envFile = file(".env")
	if (envFile.exists()) {
		envFile.readLines()
			.filter { it.isNotBlank() && !it.startsWith("#") }
			.map { it.split("=", limit = 2) }
			.filter { it.size == 2 }
			.forEach { (key, value) -> environment(key, value) }
	}
}
