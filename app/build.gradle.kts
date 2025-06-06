plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "org.kbroman.android.polarpvc2"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.kbroman.android.polarpvc2"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}


val cameraxversion = "1.3.4"

dependencies {
    implementation("com.androidplot:androidplot-core:1.5.10")
    implementation("com.github.polarofficial:polar-ble-sdk:5.5.0")
    implementation("com.github.wendykierp:JTransforms:3.1")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("io.reactivex.rxjava3:rxjava:3.1.6")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.camera:camera-core:$cameraxversion")
    implementation("androidx.camera:camera-camera2:$cameraxversion")
    implementation("androidx.camera:camera-lifecycle:$cameraxversion")
    implementation("androidx.camera:camera-video:$cameraxversion")
    implementation("androidx.camera:camera-view:$cameraxversion")
    implementation("androidx.camera:camera-extensions:$cameraxversion")


}
