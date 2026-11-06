---
id: experience/acme-backend
type: experience
title: Backend developer, Acme Oy
tags: [kotlin, spring-boot, kafka, postgresql]
summary: Built and ran Kotlin/Spring Boot services for a fictional logistics company, including a Kafka event pipeline that replaced nightly batch jobs.
period: 2022-03 – 2024-08
skills:
  - { name: Kotlin, level: strong }
  - { name: Spring Boot, level: strong }
  - { name: Kafka, level: working }
  - { name: PostgreSQL, level: strong }
---
Acme Oy (fictional) ships parcels across the Nordics. Example Person worked in a team of five
on the shipment-tracking backend.

- Designed a Kafka pipeline that publishes shipment events, so customer dashboards update within
  seconds instead of after the nightly batch run.
- Wrote integration tests with Testcontainers (PostgreSQL and Kafka); the team made them a
  required check for every merge.
- Reduced the p95 latency of the tracking API from 800 ms to 120 ms by adding missing indexes and
  removing an N+1 query.