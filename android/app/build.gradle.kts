import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
}

fun exactDependencyPath(environmentName: String, siblingName: String): String {
    val environmentPath = System.getenv(environmentName)
    val candidate = if (!environmentPath.isNullOrBlank()) {
        file(environmentPath)
    } else {
        val localCheckout =
            rootProject.projectDir.parentFile.resolve("local/dependencies/$siblingName")
        if (localCheckout.isDirectory) {
            localCheckout
        } else {
            rootProject.projectDir.parentFile.parentFile.resolve(siblingName)
        }
    }
    return candidate.canonicalPath.replace('\\', '/')
}

val ground0Path = exactDependencyPath("OCTAVO_GROUND0_DIR", "ground0")
val reader0Path = exactDependencyPath("OCTAVO_READER0_DIR", "reader0")
val ui0Path = exactDependencyPath("OCTAVO_UI0_DIR", "ui0")
val readerview0Path =
    exactDependencyPath("OCTAVO_READERVIEW0_DIR", "readerview0")

android {
    namespace = "ro.devze.octavo"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "ro.devze.octavo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.8.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notAnnotation"] =
            "ro.devze.octavo.ExternalProcessRestartProbe"

        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DOCTAVO_REPO_ROOT=${rootProject.projectDir.parentFile.absolutePath}",
                    "-DOCTAVO_GROUND0_DIR=$ground0Path",
                    "-DOCTAVO_READER0_DIR=$reader0Path",
                    "-DOCTAVO_UI0_DIR=$ui0Path",
                    "-DOCTAVO_READERVIEW0_DIR=$readerview0Path"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("androidTest").assets.directories.add(
            "../../testdata")
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
        "scripts\\check_dependencies.ps1",
        "-Target",
        "AndroidEpub"
    )
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(checkExactDependencies)
}
