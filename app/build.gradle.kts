import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
}
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")

if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}
android {
    namespace = "com.example.rotaoptjv"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            // Çakışan lisans dosyalarını dışarıda bırakıyoruz
            excludes += "/META-INF/{AL2.0,LGPL2.1}"

            // Eğer başka benzer hatalar alırsanız şunları da ekleyebilirsiniz:
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"


            pickFirsts += "lib/**/libjniortools.so"
        }
    }
    defaultConfig {
        applicationId = "com.example.rotaoptjv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // OR-Tools'un desteklediği yaygın mimariler
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
           //Local Properties'ten api okutan kısım
        buildConfigField(
            "String",
            "MAPS_API_KEY",
            "\"${localProps["MAPS_API_KEY"]}\""
        )

        manifestPlaceholders["MAPS_API_KEY"] =
            localProps["MAPS_API_KEY"]?.toString() ?: ""
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Retrofit ve JSON
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.8.9")

    // Google Maps ve konum servisleri
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // OSMDroid (sadece en güncel sürüm)
    implementation("org.osmdroid:osmdroid-android:6.1.14")
    implementation("org.osmdroid:osmdroid-mapsforge:6.1.14")
    implementation("org.osmdroid:osmdroid-shape:6.1.14")

    // Google Maps Services
    implementation("com.google.maps:google-maps-services:0.17.0")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    // RxJava
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")

    // Core
    implementation("androidx.core:core:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // OR-Tools eklemek için dep.
    implementation("com.google.ortools:ortools-java:9.8.3296")
}
