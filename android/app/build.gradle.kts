plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.copt.galaxymonkey"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.copt.galaxymonkey"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

val natives: Configuration by configurations.creating

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
    natives(variantOf(libs.gdx.platform) { classifier("natives-arm64-v8a") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-armeabi-v7a") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-x86") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-x86_64") })

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

tasks.register("copyNatives") {
    doLast {
        val jniLibs = file("src/main/jniLibs")
        configurations["natives"].files.forEach { jar ->
            val abi = Regex("natives-(.*)\\.jar").find(jar.name)?.groupValues?.get(1) ?: return@forEach
            val dest = jniLibs.resolve(abi)
            dest.mkdirs()
            copy { from(zipTree(jar)); into(dest); include("*.so") }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn("copyNatives")
}
