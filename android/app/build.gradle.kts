plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}
val developmentUrl = providers.gradleProperty("BACKEND_BASE_URL").getOrElse("http://10.0.2.2:8081/")
val productionUrl = providers.gradleProperty("PRODUCTION_BACKEND_BASE_URL")
    .orElse(providers.environmentVariable("PRODUCTION_BACKEND_BASE_URL")).getOrElse("")
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val signingPath = providers.environmentVariable("REALITY_KEYSTORE_PATH").orNull
val signingStorePassword = providers.environmentVariable("REALITY_KEYSTORE_PASSWORD").orNull
val signingAlias = providers.environmentVariable("REALITY_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("REALITY_KEY_PASSWORD").orNull
android {
    namespace = "com.reality.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.reality.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    signingConfigs {
        if (listOf(signingPath, signingStorePassword, signingAlias, signingKeyPassword).all { !it.isNullOrBlank() }) {
            create("privateRelease") {
                storeFile = file(requireNotNull(signingPath))
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "BACKEND_BASE_URL", quoted(developmentUrl))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "BACKEND_BASE_URL", quoted(productionUrl))
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("privateRelease")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
kapt { correctErrorTypes = true }
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        require(productionUrl.startsWith("https://") && productionUrl.endsWith("/")) {
            "Set PRODUCTION_BACKEND_BASE_URL to your real HTTPS backend URL ending in /."
        }
    }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.datastore)
    implementation(libs.splash)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.runner)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
}
