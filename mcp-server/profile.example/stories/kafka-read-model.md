---
id: stories/kafka-read-model
type: story
title: Making the progress dashboard fast with a Kafka read model
tags: [kafka, cqrs, postgresql, performance]
summary: Fictional story. The dashboard got slow as answers piled up, so the stats were moved to a table kept up to date from Kafka events.
---
**Situation:** the progress dashboard computed statistics from every stored quiz answer on each
page load. With a growing answer table, the page took several seconds.

**Task:** make the dashboard fast without making the quiz itself slower or less reliable.

**Action:** the answer is still written to the database first (the source of truth). After that,
an event is published to a Kafka topic, and a consumer updates one pre-aggregated row per user and
topic. The dashboard reads only those rows.

**Result:** the dashboard loads in well under 100 ms regardless of how many answers exist, and a
Kafka outage only delays the statistics; it never loses a quiz answer.