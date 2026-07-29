plugins {
    id("com.android.library")
}

android {
    namespace = "com.frankenbridge.broker.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        aidl = true
    }
}
