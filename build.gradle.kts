plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
	alias(libs.plugins.jooq.codegen)
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

configurations {
	// Prevent Spring BOM from downgrading jOOQ codegen dependencies (3.20.3 → 3.19.x)
	jooqGenerator {
		resolutionStrategy.eachDependency {
			if (requested.group == "org.jooq") {
				useVersion(requested.version!!)
			}
		}
	}
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
	jooqGenerator(libs.postgresql)
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
	testImplementation(libs.mockito.kotlin)
}

// Load .env at configuration time for JOOQ codegen
val envProps = file(".env").takeIf { it.exists() }?.readLines()
	?.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith(";") }
	?.mapNotNull { line ->
		val parts = line.split("=", limit = 2)
		if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
	}?.toMap() ?: emptyMap()

fun envOrDefault(key: String, default: String) =
	System.getenv(key) ?: envProps[key] ?: default

jooq {
	configurations {
		create("main") {
			jooqConfiguration.apply {
				jdbc.apply {
					driver   = "org.postgresql.Driver"
					url      = envOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/finnish_learning_app")
					user     = envOrDefault("SPRING_DATASOURCE_USERNAME", "postgres")
					password = envOrDefault("SPRING_DATASOURCE_PASSWORD", "postgres")
				}
				generator.apply {
					name = "org.jooq.codegen.KotlinGenerator"
					database.apply {
						name         = "org.jooq.meta.postgres.PostgresDatabase"
						inputSchema  = "public"
						excludes     = "flyway_schema_history"
					}
					generate.apply {
						isDeprecated    = false
						isRecords       = true
						isPojos         = false
						isFluentSetters = false
						isDaos          = false
					}
					target.apply {
						packageName = "me.longng.finnish_learning_backend.persistence.generated"
						directory   = "${layout.buildDirectory.get()}/generated-sources/jooq"
					}
				}
			}
		}
	}
}

sourceSets {
	main {
		kotlin {
			srcDir("${layout.buildDirectory.get()}/generated-sources/jooq")
		}
	}
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

tasks.named("compileKotlin") {
	dependsOn(tasks.named("generateJooq"))
}