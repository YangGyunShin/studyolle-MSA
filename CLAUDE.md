# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

StudyOlle is a study group management platform built with Spring Boot 3.4.7 and Java 21. Users create accounts, form study groups, schedule events/meetings, and receive notifications. The frontend uses server-side rendered Thymeleaf templates with Bootstrap.

## Build & Run Commands

```bash
# Build (includes npm install for frontend assets)
./gradlew build

# Run the application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.studyolle.PackageDependencyTests"

# Run a single test method
./gradlew test --tests "com.studyolle.PackageDependencyTests.cycleCheck"

# Clean build
./gradlew clean build
```

## Prerequisites

- Java 21 (configured via Gradle toolchain)
- PostgreSQL running on `localhost:54321` with database `studyolle` (user: `user`, password: `1234`)
- Node.js is managed by Gradle (auto-downloaded), no manual install needed

## Architecture

### Module Structure

The codebase follows a **modular architecture** under `src/main/java/com/studyolle/`:

- **`modules/`** — Domain modules, each containing `entity/`, `dto/`, `controller/`, `service/`, `repository/`, `validator/`, and `event/` sub-packages:
  - `account` — User registration, email verification, profile, settings
  - `study` — Study group CRUD, publish/close/recruit lifecycle
  - `event` — Meetings within studies (FCFS or manager-confirmed enrollment)
  - `notification` — User notifications (created via Spring event listeners)
  - `tag` — User-created topic tags (open-ended)
  - `zone` — Geographical regions (pre-loaded from `zones_kr.csv`)
  - `main` — Home page and search

- **`infra/`** — Cross-cutting infrastructure:
  - `config/` — SecurityConfig, AppConfig (PasswordEncoder, ModelMapper), WebConfig (NotificationInterceptor), AsyncConfig
  - `mail/` — EmailService interface with HtmlEmailService and ConsoleEmailService implementations

### Package Dependency Rules (enforced by ArchUnit)

These rules are tested in `PackageDependencyTests.java` — violations will fail the build:

- `study` module is only accessed by `event` and `main`
- `event` module can access `study`, `account`, and `event`
- `account` module can access `tag`, `zone`, and `account`
- No circular dependencies between modules
- All modules are only accessed from within `com.studyolle.modules`

### Key Patterns

- **Entity Graphs**: `Study` and `Event` use `@NamedEntityGraph` to prevent N+1 queries. When adding new queries involving lazy collections, define appropriate entity graphs.
- **Domain Events**: Study lifecycle changes publish Spring events (`StudyCreatedEvent`, `StudyUpdateEvent`) consumed by notification listeners. This decouples modules.
- **State Machines**: `Study` has publish/close/recruit states with one-time or cooldown-gated transitions. `Event` has two enrollment types: `FCFS` (auto-accept) and `CONFIRMATIVE` (manager approval).
- **QueryDSL**: Q-type classes are generated at `build/generated/sources/annotationProcessor/java/main`. Run a build if Q-classes are missing.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.4.7, Spring Security 6.x |
| Templates | Thymeleaf + thymeleaf-extras-springsecurity6 |
| ORM | Spring Data JPA + QueryDSL 5.1.0 (Jakarta) |
| Database | PostgreSQL (H2 available for testing) |
| Build | Gradle 8.x with Node.js plugin for npm assets |
| Mapping | ModelMapper 3.2.2, Lombok |
| Testing | JUnit 5, Spring Security Test, ArchUnit 1.4.1 |