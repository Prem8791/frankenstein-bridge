plugins {
    id("com.android.application")
}

android {
    namespace = "com.frankenbridge.test"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frankenbridge.test"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":broker-api"))
}

