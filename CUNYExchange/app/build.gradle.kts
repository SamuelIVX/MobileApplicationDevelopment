plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.cuny.exchange"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cuny.exchange"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

abstract class CheckGoogleServicesTask : DefaultTask() {
    @get:Internal
    abstract val targetFile: RegularFileProperty

    @get:Internal
    abstract val templateFile: RegularFileProperty

    @TaskAction
    fun check() {
        val target = targetFile.asFile.get()
        if (!target.exists()) {
            val template = templateFile.asFile.get()
            throw GradleException(
                """
                |Missing required Firebase configuration file: '${target.path}'.
                |To build CUNYExchange, copy the template configuration:
                |  cp ${template.path} ${target.path}
                |and update it with your Firebase project credentials.
                """.trimMargin()
            )
        }
    }
}

val checkGoogleServices = tasks.register<CheckGoogleServicesTask>("checkGoogleServices") {
    targetFile.set(layout.projectDirectory.file("google-services.json"))
    templateFile.set(layout.projectDirectory.file("../google-services.json.example"))
}

tasks.matching { it.name.startsWith("process") && it.name.endsWith("GoogleServices") }.configureEach {
    dependsOn(checkGoogleServices)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Firebase BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Image loading
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
