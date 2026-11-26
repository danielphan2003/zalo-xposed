plugins {
    alias(libs.plugins.android.application)
}

val moduleVersionName = providers.gradleProperty("moduleVersion").orElse("0.1.0").get()
val moduleVersionCode = moduleVersionName.split('.').map { it.toInt() }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

android {
    namespace = "io.github.danielphan2003.zaloxposed"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.danielphan2003.zaloxposed"
        minSdk = 28
        targetSdk = 37
        versionCode = moduleVersionCode
        versionName = moduleVersionName
        buildConfigField("String", "MODULE_VERSION", "\"$moduleVersionName\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
            // Left unsigned on purpose: LSPatch embeds the module APK as-is and never checks its
            // signature, and an unsigned build is byte-for-byte reproducible from the tag.
            signingConfig = null
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "**/*.kotlin_builtins",
                "**/*.kotlin_module",
                "kotlin/**",
                "META-INF/*.version",
                "DebugProbesKt.bin",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.libxposed.api)

    testImplementation(libs.junit)
    testImplementation(libs.smali)
    testImplementation(libs.json)
}
