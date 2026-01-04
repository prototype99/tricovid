import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    id("org.jetbrains.compose") version "1.5.10"
}

group = "com.mmu.tracker"
version = "1.0-SNAPSHOT"

sourceSets {
    main {
        kotlin.srcDirs("src")
        java.srcDirs("src")
    }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.software/public/compose/releases")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("com.konghq:unirest-java:3.11.07")
    // GSON is already in lib, but for Gradle it's better to declare it
    implementation("com.google.code.gson:gson:2.10.1")
}

compose.desktop {
    application {
        mainClass = "com.mmu.tracker.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "tricovid"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
