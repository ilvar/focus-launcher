import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The visible release version is chosen by a human. Android's versionCode is the commit count,
// so every merge can update an installed build even before the next named release. Release tags
// include both values, e.g. v1.2.0+57, for F-Droid's update checker.
val baseVersion = "1.2.0"
val buildNumber: Int = try {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull() ?: 0
} catch (_: Exception) {
    0
}

android {
    namespace = "pw.rkd.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "pw.rkd.launcher"
        minSdk = 26
        targetSdk = 36
        // 1.1 was published with versionCode 2; commit counts passed that long ago.
        versionCode = maxOf(buildNumber, 2)
        versionName = baseVersion
    }

    // The key for anything that leaves this machine. keystore.properties (git-ignored) says where
    // it lives; without that file the "dist" build simply cannot be signed, which is the point.
    val keystoreProperties = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        create("ciDebug") {
            val ciKey = System.getenv("RKD_CI_KEYSTORE_PATH")
            if (!ciKey.isNullOrBlank()) {
                storeFile = file(ciKey)
                storePassword = System.getenv("RKD_CI_KEYSTORE_PASSWORD")
                keyAlias = "focus-ci"
                keyPassword = System.getenv("RKD_CI_KEYSTORE_PASSWORD")
            }
        }
        create("dist") {
            if (keystoreProperties.containsKey("storeFile")) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (!System.getenv("RKD_CI_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
        // Optimized build for the developer's own phone. Signed with the debug key, so it installs
        // over the debug build without losing data. Never hand this one to anybody else.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // -Pfocus.unsigned leaves the APK unsigned. That is how F-Droid builds it: it then checks
            // that its build is identical to the published APK and ships that one, signature and all.
            signingConfig = if (project.hasProperty("focus.unsigned")) null else signingConfigs.getByName("debug")
            // The build plugin would stamp the git state into the APK. The version already says which
            // commit it is, and the stamp differs between checkouts of the same commit, which is
            // exactly what a reproducible build cannot have.
            vcsInfo { include = false }
        }
        // The same optimized build, signed with the real key: this is the APK on the website.
        // Android refuses to install it over a debug-signed build (different signature), and
        // every future public version must be signed with this same key.
        create("dist") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("dist")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Google's build plugin can add an encrypted list of dependencies to the APK that only Google
    // can read. It serves Play; elsewhere it is an opaque blob in a free-software app (F-Droid
    // asks for it to be left out), and it keeps two builds of the same source from being identical.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Installs the baseline profiles shipped inside the Compose libraries, so a sideloaded build
    // is precompiled instead of interpreted. Without it the first swipes and scrolls stutter.
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)   // the JVM has no org.json of its own; Android does
}
