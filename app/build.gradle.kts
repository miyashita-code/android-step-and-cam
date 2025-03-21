plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rementia.walkingdetection"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rementia.walkingdetection"
        minSdk = 31
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    // Android 基本ライブラリ
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Activity Recognition dependency
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // テスト関連
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // ===== CameraX 関連 =====
    val cameraX_version = "1.4.1" // (安定板が出ていればそちらを参照)

    // Core ライブラリ
    implementation("androidx.camera:camera-core:$cameraX_version")
    // Camera2 実装
    implementation("androidx.camera:camera-camera2:$cameraX_version")
    // ライフサイクル連携
    implementation("androidx.camera:camera-lifecycle:$cameraX_version")
    // (プレビュー用の View が必要なら)
    implementation("androidx.camera:camera-view:$cameraX_version")

    // もし ImageCapture の inMemory API を使うなら(1.3.0 以降で利用可)
    // (↑ camera-core/camera-camera2 に含まれていれば不要な場合も)

}
