plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    // Kotlin 2.0+ ships the Compose compiler as a first-class Gradle plugin (version == Kotlin).
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
