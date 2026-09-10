plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dayatlas.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dayatlas.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.6.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A committed, stable keystore - NOT Android's default auto-generated
    // debug.keystore. That default is regenerated (with a new random key)
    // whenever it doesn't already exist on the build machine, which is
    // every single time on a fresh CI runner - so every CI-built release
    // APK ended up signed with a different key, and Android refuses to
    // install an update over an app signed with a different key (the
    // download would silently succeed while the install failed, leaving
    // the old version in place). This is a sideload-only key (not for the
    // Play Store), so committing it is fine - same as RideAtlas/MediaAtlas.
    signingConfigs {
        create("release") {
            storeFile = file("dayatlas-debug.keystore")
            storePassword = "dayatlas123"
            keyAlias = "dayatlas"
            keyPassword = "dayatlas123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        // BuildConfig generation is opt-in since AGP 8 - needed for
        // BuildConfig.VERSION_NAME/DEBUG used by the update checker.
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // OpenStreetMap tile rendering for the route screen - no Google Play
    // Services, no API key. Purely a view component: it only fetches/draws
    // tiles while the map screen is on screen, nothing runs in the
    // background or costs battery outside of that.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    // Local unit tests run against the mockable android.jar, whose
    // org.json.* methods all throw RuntimeException("Stub!"). This puts the
    // real JSON-java implementation on the test classpath ahead of that
    // stub, so DayJson's round-trip test actually parses instead of
    // exploding on the first JSONObject call.
    testImplementation("org.json:json:20250517")
}
