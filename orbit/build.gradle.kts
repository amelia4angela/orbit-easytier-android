import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.easytier"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.easytier.easytiervpn"
        minSdk = 23
        targetSdk = 34
        versionCode = 9
        versionName = "0.1.7"
    }

    signingConfigs {
        create("orbit") {
            // Signed with the project's own keystore (see ~/.gradle/gradle.properties)
            storeFile = file(project.findProperty("orbitStoreFile") as String? ?: "/root/.orbit.keystore")
            storePassword = project.findProperty("orbitStorePassword") as String?
            keyAlias = project.findProperty("orbitKeyAlias") as String? ?: "orbit"
            keyPassword = project.findProperty("orbitKeyPassword") as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("orbit")
        }
        debug {
            signingConfig = signingConfigs.getByName("orbit")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
    }

    applicationVariants.configureEach {
        outputs.configureEach {
            (this as com.android.build.gradle.internal.api.ApkVariantOutputImpl).outputFileName =
                "Orbit-v${versionName}.apk"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

// KGP 2.x: `android.kotlinOptions.jvmTarget` (String) is a hard error, use the compilerOptions DSL.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    // Liquid Glass rendering (blur / refraction / dispersion) — skydoves/Cloudy
    //
    // DISABLED: cloudy 1.0.0-alpha01 is compiled against Kotlin 2.4.0 + Compose 1.11.x and
    // carries `minCompileSdk=37` AAR metadata. This project is pinned to Kotlin 1.9.22 /
    // Compose BOM 2024.02.00 / AGP 8.2.2 / compileSdk 34, so resolving it fails the build at
    // `checkDebugAarMetadata` (27 AAR-metadata violations) and its 2.4 Kotlin metadata is
    // unreadable by the 1.9.22 compiler anyway. Every published cloudy release (0.1.x -> 1.0.0)
    // needs a Kotlin 2.x toolchain. The glass layer therefore renders with Compose-native
    // primitives (Modifier.blur / RenderEffect on API 31+) behind the same
    // `Modifier.glassSurface(level, radius, intensity)` API, so the library can be dropped back
    // in unchanged once the toolchain is upgraded.
    // implementation("com.github.skydoves:cloudy:1.0.0-alpha01")

    // Lifecycle for Compose
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Existing deps
    implementation("androidx.appcompat:appcompat:1.6.1")
}
