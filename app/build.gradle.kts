plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.johnz.diceroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.johnz.diceroller"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        if (buildType.name == "release") {
            outputs.all {
                val output = this as? com.android.build.gradle.api.ApkVariantOutput
                if (output != null) {
                    val vName = versionName
                    val vCode = versionCode
                    // Note: This configures the APK output filename. 
                    // Using .apk extension as this block handles APKs, not AABs.
                    output.outputFileName = "dice-roller-v${vName}(${vCode}).apk"
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ViewModel and Navigation
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    doLast {
        val outDir = file("$buildDir/outputs/bundle/release")
        val aab = outDir.listFiles()?.firstOrNull { it.extension == "aab" }
        if (aab != null) {
            val vName = android.defaultConfig.versionName
            val vCode = android.defaultConfig.versionCode
            val newName = "dice-roller-v${vName}(${vCode}).aab"
            aab.renameTo(File(outDir, newName))
        }
    }
}
