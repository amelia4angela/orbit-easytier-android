plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    kotlinOptions {
        jvmTarget = "1.8"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")

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

    // Lifecycle for Compose
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Existing deps
    implementation("androidx.appcompat:appcompat:1.6.1")
}
