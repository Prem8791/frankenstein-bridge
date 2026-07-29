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
        // Must remain above the broker version baked into the current ROM (36)
        // so a platform-signed /data/app update can replace it without a ROM rebuild.
        versionCode = 37
        versionName = "16.1"
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
    testImplementation("junit:junit:4.13.2")
}
