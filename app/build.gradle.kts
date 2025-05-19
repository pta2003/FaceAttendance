import com.android.build.api.dsl.Packaging

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.faceattendance"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.faceattendance"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
    packaging {
        resources {
            excludes += setOf("META-INF/INDEX.LIST")
            pickFirsts += setOf("META-INF/io.netty.versions.properties")
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

// CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")

    // ML Kit for face detection
    implementation("com.google.mlkit:face-detection:16.1.5")

    // TensorFlow Lite for face recognition
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Room for database
    val roomVersion = "2.6.0"
    implementation("androidx.room:room-runtime:${roomVersion}")
    annotationProcessor("androidx.room:room-compiler:${roomVersion}")

    implementation("com.hivemq:hivemq-mqtt-client:1.3.0")

}