plugins {
    id("com.android.application")
}

android {
    namespace = "com.frankenbridge.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frankenbridge.assistant"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
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
