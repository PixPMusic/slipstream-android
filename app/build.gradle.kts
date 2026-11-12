plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "cc.pixp.slipstream"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "cc.pixp.slipstream"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("slipstream.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("slipstream.versionName").orNull ?: "0.1.0"
    }
    // Release builds are signed only when the Play upload key is supplied by the
    // environment (the protected release workflow). Local release builds stay unsigned.
    val uploadKeystore = providers.environmentVariable("SLIPSTREAM_UPLOAD_KEYSTORE").orNull
    if (uploadKeystore != null) {
        val password = providers.environmentVariable("SLIPSTREAM_UPLOAD_PASSWORD").get()
        val upload = signingConfigs.create("upload") {
            storeFile = file(uploadKeystore)
            storePassword = password
            keyAlias = "upload"
            keyPassword = password
        }
        buildTypes.getByName("release").signingConfig = upload
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.9.1")
    implementation("androidx.media3:media3-ui:1.9.1")
    implementation("androidx.media3:media3-session:1.9.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
