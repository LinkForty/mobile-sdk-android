import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.maven.publish.vanniktech)
}

android {
    namespace = "com.linkforty.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        // Expose the published version to runtime code so the SDK can report its
        // own version (sdkVersion / X-LinkForty-SDK). Sourced from VERSION_NAME in
        // gradle.properties so it always matches the released artifact.
        buildConfigField("String", "SDK_VERSION", "\"${property("VERSION_NAME")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Optional: enables LinkFortyNavObserver for automatic screen-view tracking.
    // compileOnly so apps that don't use Jetpack Navigation don't pull it in;
    // apps that do already have it on their classpath.
    compileOnly("androidx.navigation:navigation-runtime:2.7.7")

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Maven Central publishing via the Central Portal (central.sonatype.com).
// Coordinates (GROUP / POM_ARTIFACT_ID / VERSION_NAME) and POM metadata (POM_*)
// are read automatically from gradle.properties. Credentials and the signing key
// come from ~/.gradle/gradle.properties (mavenCentralUsername / mavenCentralPassword
// / signingInMemoryKey / signingInMemoryKeyPassword) — never committed.
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        )
    )
}
