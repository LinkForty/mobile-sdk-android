// End-to-end test app; not published. See e2e/README.md.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.linkforty.e2e"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.linkforty.e2e"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        // Minified like a release build, so the SDK's consumer R8 rules are
        // exercised; debuggable so the driver can read the app's files.
        debug {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":sdk"))
    implementation(libs.coroutines.android)
}
