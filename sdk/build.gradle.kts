plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    `maven-publish`
    signing
}

android {
    namespace = "com.linkforty.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Maven Central publishing
publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = property("GROUP").toString()
            artifactId = property("POM_ARTIFACT_ID").toString()
            version = property("VERSION_NAME").toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set(property("POM_NAME").toString())
                description.set(property("POM_DESCRIPTION").toString())
                url.set(property("POM_URL").toString())

                licenses {
                    license {
                        name.set(property("POM_LICENCE_NAME").toString())
                        url.set(property("POM_LICENCE_URL").toString())
                    }
                }

                developers {
                    developer {
                        id.set(property("POM_DEVELOPER_ID").toString())
                        name.set(property("POM_DEVELOPER_NAME").toString())
                        url.set(property("POM_DEVELOPER_URL").toString())
                    }
                }

                scm {
                    url.set(property("POM_SCM_URL").toString())
                    connection.set(property("POM_SCM_CONNECTION").toString())
                    developerConnection.set(property("POM_SCM_DEV_CONNECTION").toString())
                }
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl

            credentials {
                username = findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["release"])
}
