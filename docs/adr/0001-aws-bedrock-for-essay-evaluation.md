## Context

The app already evaluates single sentences via Groq's OpenAI-compatible API. A second,
larger exercise was wanted: a 300–2,500 character Finnish essay against a chosen prompt,
returning a CEFR sub-level, a topic-relevance judgement, and applicable corrections.

Main goals: **keep cost near zero** (Free-Tier `t3.micro`, $1 budget alert), and **keep inference in the EU**
alongside the `eu-north-1` deployment. One constraint: the Groq sentence feature in backend is frozen,
so the new feature had to be additive.

## Decision

Build the essay evaluator as a separate vertical slice on **AWS Bedrock**:

| Choice | Value                                                                            |
|---|----------------------------------------------------------------------------------|
| Model | Claude Haiku 4.5                                                                 |
| Invoked as | EU cross-region inference profile `eu.anthropic.claude-haiku-4-5-20251001-v1:0`  |
| API | AWS SDK for Java v2 `software.amazon.awssdk:bedrockruntime` 2.53.3, **Converse** |
| HTTP client | `url-connection-client`; while `apache5-client` and `netty-nio-client` excluded  |
| Region | `eu-north-1`, in-region with the deployment                                      |
| Structured output | JSON Schema via `outputConfig.textFormat`                                        |
| Production auth | EC2 instance profile `fin-app-ec2-bedrock` + policy `fin-app-bedrock-invoke`     |
| Local auth | Bedrock long-term API key (`AWS_BEARER_TOKEN_BEDROCK`), expiry set, gitignored   |
| Cost control | 20 evaluations per user per Europe/Helsinki day, checked before the call         |
