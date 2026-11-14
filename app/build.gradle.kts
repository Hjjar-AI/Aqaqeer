import com.android.build.api.artifact.SingleArtifact
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Wrappers reserve before Gradle starts. Reading this tracked property is safe
// when configuration is reused; never mutate the counter during configuration.
val requestedPackaging = gradle.startParameter.taskNames.any { path ->
    val task = path.substringAfterLast(':')
    task.startsWith("assemble", ignoreCase = true) ||
        task.startsWith("bundle", ignoreCase = true) ||
        task.startsWith("install", ignoreCase = true) ||
        task in setOf("build", "buildNeeded", "buildDependents")
}
val reservedBuildCode = providers.gradleProperty("aqaqeer.buildCode")
check(!requestedPackaging || gradle.startParameter.isDryRun || reservedBuildCode.isPresent) {
    "Use gradlew/gradlew.bat for packaging so a version is reserved before Gradle starts."
}
val currentBuildCode = reservedBuildCode.orElse(
    providers.fileContents(rootProject.layout.projectDirectory.file("local/versioning/last-code.txt"))
        .asText.orElse("0").map { value ->
            value.trim().toInt().also { require(it in 0..2_099_999_999) }.coerceAtLeast(1).toString()
        },
).get().toInt().also { require(it in 1..2_099_999_999) }

android {
    namespace = "com.aqaqeer.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    defaultConfig {
        applicationId = "com.aqaqeer.app"
        minSdk = 26
        targetSdk = 37
        versionCode = currentBuildCode
        versionName = "0.0.$currentBuildCode"
        testInstrumentationRunner = "com.aqaqeer.app.SuiteRunner"
    }
    flavorDimensions += "data"
    productFlavors {
        create("standard") {
            dimension = "data"
            buildConfigField("boolean", "IS_DEMO", "false")
        }
        create("demo") {
            dimension = "data"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "IS_DEMO", "true")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // Arabic is selected locally; every bundle install must include its language resources.
    bundle { language { enableSplit = false } }
    lint {
        // Version discovery is advisory. Keep the tested pins and show upgrade notices as info;
        // upgrading Gradle/Kotlin/AGP together is a separately validated toolchain change.
        informational += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["main"].assets.directories += rootProject.file("licenses").path
    // Public test fixtures only; never included in either application APK.
    sourceSets["test"].resources.directories += rootProject.file("docs/crypto").path
    sourceSets["androidTest"].assets.directories += rootProject.file("docs/crypto").path
    sourceSets["androidTest"].assets.directories += "$projectDir/schemas"
    sourceSets["test"].kotlin.directories += "src/sharedTest/kotlin"
    sourceSets["androidTest"].kotlin.directories += "src/sharedTest/kotlin"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Resolve the current account's Desktop without executing shell configuration.
val accountHome = File(providers.systemProperty("user.home").get())
val xdgConfigDirectory = providers.environmentVariable("XDG_CONFIG_HOME").orNull
    ?.takeIf { it.isNotBlank() }?.let(::File) ?: File(accountHome, ".config")
val xdgDesktopDirectory = File(xdgConfigDirectory, "user-dirs.dirs")
    .takeIf { it.isFile && it.canRead() }?.readLines()
    ?.firstOrNull { it.trimStart().startsWith("XDG_DESKTOP_DIR=") }
    ?.substringAfter('=')?.trim()?.removeSurrounding("\"")
    ?.replace("\$HOME", accountHome.path)?.replace("\${HOME}", accountHome.path)
    ?.let(::File)?.takeIf { it.isAbsolute && it != accountHome }
val apkExportDirectory = providers.gradleProperty("aqaqeer.apkExportDir")
    .orElse(providers.environmentVariable("AQAQEER_APK_EXPORT_DIR"))
    .orNull?.let(::File)
    ?: File(xdgDesktopDirectory ?: File(accountHome, "Desktop"), "Aqaqeer")
require(apkExportDirectory.isAbsolute) { "APK export directory must be absolute." }
val exportApks = providers.gradleProperty("aqaqeer.exportApks").orNull != "false"

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        if (variant.productFlavors.any { it.second == "demo" }) variant.enable = false
    }
    onVariants(selector().all()) { variant ->
        if (exportApks) {
            val variantTaskName = variant.name.replaceFirstChar { it.uppercaseChar() }
            val exportApk = tasks.register<Copy>("export${variantTaskName}Apk") {
                group = "build"
                description = "Copy packaged ${variant.name} APKs to this account's Desktop/Aqaqeer folder."
                from(variant.artifacts.get(SingleArtifact.APK))
                include("*.apk")
                into(File(apkExportDirectory, variant.name))
                rename("^(.+\\.apk)$", "Aqaqeer-v0.0.$currentBuildCode-\$1")
            }
            // The artifact provider supplies the packaging dependency; a failed
            // package cannot trigger copying stale APKs from the output folder.
            tasks.matching { it.name == "assemble$variantTaskName" || it.name == "install$variantTaskName" }
                .configureEach { dependsOn(exportApk) }
        }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
hilt { enableAggregatingTask = true }

dependencies {
    constraints {
        add("ksp", "com.google.guava:guava:33.8.0-jre") {
            because("Fix GHSA-xxph-c9ww-hj94 in Room/Hilt processor classpaths")
        }
    }
    implementation(libs.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material)
    implementation(libs.room.runtime)
    implementation(libs.sqlite)
    implementation(libs.sqlcipher)
    implementation(libs.hilt.android)
    implementation(libs.datastore)
    implementation(libs.serialization)
    implementation(libs.coroutines)
    implementation(libs.splash)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
}

// Main flavor processors share the canonical Room schema directory. Order only
// main processors: test processors depend on main compilation and cannot precede it.
val roomSchemaWriters = setOf("kspDemoDebugKotlin", "kspStandardDebugKotlin", "kspStandardReleaseKotlin")
tasks.matching { it.name in roomSchemaWriters }.configureEach {
    val currentName = name
    mustRunAfter(tasks.matching { it.name in roomSchemaWriters && it.name < currentName })
}

// Large boundary fixtures run sequentially; they must not compete for heap.
tasks.withType<Test>().configureEach { maxHeapSize = "1536m"; maxParallelForks = 1 }
