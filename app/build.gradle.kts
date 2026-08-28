plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.lozza.tellygrid"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.lozza.tellygrid"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-alpha.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val epgUrl = providers.gradleProperty("EPG_URL")
            .orElse("https://lozza.github.io/TellyGrid/guide.xml")
            .get()
        buildConfigField("String", "EPG_URL", "\"${epgUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.03.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
