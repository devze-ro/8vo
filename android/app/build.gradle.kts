import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
}

android {
    namespace = "ro.devze.octavo"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "ro.devze.octavo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DOCTAVO_REPO_ROOT=${rootProject.projectDir.parentFile.absolutePath}"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

dependencies {
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

val checkExactDependencies by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies the exact clean public source dependencies used by 8vo."
    workingDir(rootProject.projectDir.parentFile)
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "scripts\\check_dependencies.ps1"
    )
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(checkExactDependencies)
}
