# Spec: Project Scaffold & Dependencies

## Applicable Skills
- `mobile-android-design`: Material Design 3 tokens, Jetpack Compose foundation, typography, shapes, and color system.
- `codebase-design`: Clean architecture package boundaries, single module separation, seam definitions.
- `owasp-security`: Secrets hygiene, credential exclusion (`google-services.json`, keystores).

## Objective
Establish the Android application foundation for CUNYExchange using Kotlin and Jetpack Compose.
This includes configuring Gradle Kotlin DSL build scripts, integrating the Firebase Android BOM
(Authentication, Cloud Firestore, Firebase Storage), enforcing strict repository secrets hygiene
via `.gitignore`, configuring the core package structure following clean architecture seams, and
implementing the base Compose Material 3 theme system matching the audited wireframe design tokens.

## Scope
- Package: `com.cuny.exchange`
- Modifies (creates):
  - `build.gradle.kts` (Project root) — Gradle plugin declarations
  - `app/build.gradle.kts` — Android & Compose configurations, Firebase BOM dependencies, JVM 17 target
  - `gradle/libs.versions.toml` — Version catalog for plugins and libraries
  - `.gitignore` — comprehensive Android + secrets exclusions (`google-services.json`, keystores, `.gradle/`, `build/`)
  - `google-services.json.example` — required Firebase configuration template with placeholders (no real private keys)
  - `app/src/main/java/com/cuny/exchange/ui/theme/` — `Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`
  - `app/src/main/java/com/cuny/exchange/MainActivity.kt` — single Activity entry point with Navigation host container
- Off-limits: all feature screens and business logic (owned by specs `03`–`10`); database schema definitions (owned by spec `02`).

## Non-Goals
- No XML layout files — 100% Jetpack Compose single-activity architecture.
- No third-party dependency injection frameworks (e.g. Hilt/Dagger) — manual constructor injection and ViewModel factories provide cleaner seams for student coursework without annotation processing overhead.
- No multi-module Gradle configuration — single `app` module with clean package separation.
- No production Google Play release signing configuration in this spec.

## Invariants
- `google-services.json`, `local.properties`, and `*.keystore` MUST be listed in `.gitignore` and MUST NOT be committed to version control. [OWASP A02 / secrets-management]
- Java / Kotlin compiler target MUST be set to **Java 17 (JVM 17)**. [toolchain consistency]
- Jetpack Compose BOM and Firebase BOM MUST be used to manage all Compose and Firebase library versions consistently without transitive dependency conflicts. [build determinism]
- The CUNY Blue primary color (`#0033A1`) and Verified Gold accent color (`#FFB71B`) MUST be declared as immutable design tokens and adhere to WCAG 2.2 AA contrast standards against their corresponding surfaces. [better-accessibility / mobile-android-design]

## Requirements
1. WHEN the project is built via `./gradlew assembleDebug`, THE SYSTEM SHALL compile cleanly using minimum SDK 26 (Android 8.0) and target SDK 34 (Android 14).
2. THE SYSTEM SHALL import the Firebase Android BOM (`com.google.firebase:firebase-bom:33.10.0` or latest) and declare dependencies for:
   - `com.google.firebase:firebase-auth`
   - `com.google.firebase:firebase-firestore`
   - `com.google.firebase:firebase-storage`
3. THE SYSTEM SHALL import Jetpack Navigation Compose (`androidx.navigation:navigation-compose:2.8.2`) and Coil (`io.coil-kt:coil-compose:2.7.0`) for asynchronous image loading.
4. THE SYSTEM SHALL exclude `google-services.json`, `*.jks`, `*.keystore`, `.gradle/`, and `build/` in `.gitignore`.
5. THE SYSTEM SHALL commit a `google-services.json.example` template showing the required Firebase project configuration structure with dummy placeholders.
6. THE SYSTEM SHALL establish the following package organization under `com.cuny.exchange`:
   ```
   com.cuny.exchange/
   ├── data/
   │   ├── model/         # Domain data classes (Listing, User, Thread, Message)
   │   └── repository/    # Deep repository interfaces & Firebase implementations
   ├── ui/
   │   ├── components/    # Reusable Compose widgets (cards, chips, safe zone banner)
   │   ├── navigation/    # NavHost, NavRoutes, BottomBar
   │   ├── screens/       # Feature screen composables
   │   └── theme/         # Color, Shape, Typography, Theme
   └── MainActivity.kt
   ```
7. THE SYSTEM SHALL implement `Color.kt` defining:
   - `CunyBlue`: `#0033A1` (Primary)
   - `VerifiedGold`: `#FFB71B` (Accent / Verified Badge only)
   - `SurfaceLight`: `#FFFFFF` / `SurfaceDark`: `#1A2238`
   - `GroundLight`: `#F2F4F8` / `GroundDark`: `#0E1424`
   - `TextPrimary`: `#0A0A0A` / `TextSecondary`: `#6B6B6B`
   - `DangerRed`: `#D64045` / `SuccessGreen`: `#2E7D32`
8. THE SYSTEM SHALL implement `Theme.kt` with a `CUNYExchangeTheme` composable supporting both light and dark modes with WCAG 2.2 AA compliant contrast.
9. THE SYSTEM SHALL configure `Shape.kt` declaring:
   - Container shape: `RoundedCornerShape(16.dp)` (Cards, sheets)
   - Control shape: `RoundedCornerShape(12.dp)` (Buttons, text fields)
   - Tag shape: `CircleShape` (Pill chips)

## Failure Modes & Edge Cases
1. **Missing `google-services.json`:** WHEN `google-services.json` is missing in a fresh clone, the build plugin SHALL fail with an actionable diagnostic pointing to `google-services.json.example`.
2. **Unsupported Device API (<26):** WHEN installed on a device running Android 7.1 or lower (API <26), Google Play / package installer SHALL reject installation due to `minSdk = 26`.
3. **Missing System Fonts:** WHEN custom font `Geist Sans` fails to load, `Type.kt` SHALL fall back gracefully to system `FontFamily.Default` without crashing.

## Acceptance Criteria
- [ ] `./gradlew assembleDebug` compiles successfully with returncode 0.
- [ ] `git check-ignore google-services.json` returns exit code 0.
- [ ] `google-services.json.example` exists in repository root with valid JSON schema.
- [ ] `CUNYExchangeTheme` renders a Composable preview in both light and dark modes without runtime crashes.
- [ ] Package structure matching R6 exists under `app/src/main/java/com/cuny/exchange`.

## Design

### `app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.cuny.exchange"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cuny.exchange"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Firebase BOM
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.10.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
```

### `app/src/main/java/com/cuny/exchange/ui/theme/Color.kt`
```kotlin
package com.cuny.exchange.ui.theme

import androidx.compose.ui.graphics.Color

val CunyBlue = Color(0xFF0033A1)
val CunyBlueDark = Color(0xFF00226B)
val CunyBlueLight = Color(0xFFE6EEFA)

val VerifiedGold = Color(0xFFFFB71B)
val VerifiedGoldLight = Color(0xFFFFF7E6)

val GroundLight = Color(0xFFF2F4F8)
val GroundDark = Color(0xFF0E1424)

val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1A2238)

val TextPrimaryLight = Color(0xFF0A0A0A)
val TextPrimaryDark = Color(0xFFF0F4F8)

val TextSecondaryLight = Color(0xFF6B6B6B)
val TextSecondaryDark = Color(0xFF94A3B8)

val BorderLight = Color(0xFFDDE1E9)
val BorderDark = Color(0xFF2E3A59)

val DangerRed = Color(0xFFD64045)
val SuccessGreen = Color(0xFF2E7D32)
```

## Current State
- `[verified]` Mobile coursework homeworks 1–5 exist in `~/Documents/school/CSClasses/MobileApplicationDevelopment`.
- `[verified]` Target Android build environment uses JDK 17 and Gradle 8+.
- `[assumed]` Teammate has Android Studio Ladybug (or Iguana+) installed locally with Android SDK 34.

## Tests
- `gradle_build_passes (R1)` — `./gradlew assembleDebug` completes with returncode 0.
- `secrets_not_tracked (R4)` — `git check-ignore google-services.json` returns exitcode 0.
- `compose_theme_renders (R8)` — `CUNYExchangeTheme` preview executes and instantiates MaterialTheme colors without throwing exceptions.
- `java_target_is_17 (R1)` — gradle configuration asserts `JavaVersion.VERSION_17`.

## Constraints
- Dependencies: none (seeding spec).
- Downstream: `02-data-model`, `03-auth`, `04-home-feed` build directly onto this scaffold.
- Oracle Contract: Set-level Oracle 3 (photo quotas) and Design Tokens in `README.md`.

## Context
- Firebase Android BOM: https://firebase.google.com/docs/android/setup
- Compose Material 3 Guidelines: https://developer.android.com/develop/ui/compose/designsystems/material3
