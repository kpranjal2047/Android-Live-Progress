import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val releaseVersionName = requireNotNull(appVersion.getProperty("versionName"))
val releaseVersionCode = requireNotNull(appVersion.getProperty("versionCode")).toInt()
require(releaseVersionName.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")))
require(releaseVersionCode in 1..2_100_000_000)

val privateSigningPropertiesFile = rootProject.file("keystore.properties")
val privateSigningProperties = Properties().apply {
    if (privateSigningPropertiesFile.isFile) {
        privateSigningPropertiesFile.inputStream().use(::load)
    }
}

fun privateSigningProperty(name: String): String? =
    privateSigningProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val privateSigningStoreFile = privateSigningProperty("storeFile")?.let(rootProject::file)
val privateSigningConfigured =
    privateSigningStoreFile?.isFile == true &&
        privateSigningProperty("storePassword") != null &&
        privateSigningProperty("keyAlias") != null &&
        privateSigningProperty("keyPassword") != null

val signDebugWithPrivateKey =
    privateSigningProperty("signDebugWithPrivateKey")?.toBooleanStrictOrNull()
        ?: privateSigningConfigured

android {
    namespace = "com.pranjal.liveprogress"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pranjal.liveprogress"
        minSdk = 36
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (privateSigningConfigured) {
            create("private") {
                storeFile = privateSigningStoreFile
                storePassword = privateSigningProperty("storePassword")
                keyAlias = privateSigningProperty("keyAlias")
                keyPassword = privateSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        val privateSigningConfig = signingConfigs.findByName("private")

        debug {
            if (privateSigningConfig != null && signDebugWithPrivateKey) {
                signingConfig = privateSigningConfig
            }
        }

        release {
            signingConfig = privateSigningConfig
        }
    }
}

val requireReleaseSigning by tasks.registering {
    doLast {
        check(privateSigningConfigured) {
            "Release builds require the permanent private key in keystore.properties. See docs/releases.md."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(requireReleaseSigning)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
