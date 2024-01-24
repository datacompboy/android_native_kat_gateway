plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.datacompboy.nativekatgateway"
    compileSdk = 32

    defaultConfig {
        applicationId = "com.datacompboy.nativekatgateway"
        minSdk = 32
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

dependencies {
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("de.greenrobot:eventbus:2.4.0")
    implementation("com.google.ar.sceneform:sceneform-base:1.17.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
/*
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
 */
}