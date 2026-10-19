---
id: projects/finnish-learning-app
type: project
title: Language-learning web app (side project)
tags: [kotlin, spring-boot, react, kafka, aws-bedrock]
summary: Fictional side project, a vocabulary quiz app with spaced repetition, a Kafka-fed progress dashboard, and AI feedback on written sentences.
skills:
  - { name: Kotlin, level: strong }
  - { name: React, level: working }
  - { name: AWS Bedrock, level: familiar }
---
A sample project description. Example Person built a vocabulary quiz app as a side project:

- Spring Boot + Kotlin backend with PostgreSQL, jOOQ and Flyway.
- React + TypeScript frontend and a native Android client.
- Quiz answers are published to Kafka; a consumer keeps a pre-aggregated statistics table that
  the dashboard reads (see the story `stories/kafka-read-model`).
- An AI feature grades a user's sentence and suggests a correction, with a daily quota per user.