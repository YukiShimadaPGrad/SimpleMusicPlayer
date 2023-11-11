plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    signingConfigs {
        getByName("debug") {
            storeFile = file("/Users/user/Documents/IntelliJIDEAProjects/keystore")
            storePassword = "password"
            keyAlias = "alias"
            keyPassword = "password"
        }
    }
    namespace = "suyasuya.musicplayer"
    compileSdk = 33

    defaultConfig {
        applicationId = "suyasuya.musicplayer"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        signingConfig = signingConfigs.getByName("debug")

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
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
    
}