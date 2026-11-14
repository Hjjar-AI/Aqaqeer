import com.android.build.api.artifact.SingleArtifact
import java.io.File
import java.util.ArrayDeque

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Generated from the actual production runtime graph during an authorized release build.
abstract class RuntimeNoticeInventory : DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val coordinates: org.gradle.api.provider.ListProperty<String>

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun writeInventory() {
        val output = outputDirectory.file("runtime-components.txt").get().asFile
        output.parentFile.mkdirs()
        output.writeText("Production runtime components; license texts are bundled in assets.\n" +
            coordinates.get().joinToString("\n", postfix = "\n"))
    }
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
    val distributionApks = providers.gradleProperty("aqaqeer.releaseApkSet").orNull == "distribution"
    if (distributionApks) {
        require(gradle.startParameter.taskNames.all { it.contains("Release") }) {
            "Distribution APK sets require explicit release-only tasks."
        }
        splits.abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    if (providers.gradleProperty("aqaqeer.unitCoverage").orNull == "true") {
        buildTypes.getByName("debug").enableUnitTestCoverage = true
        testCoverage.jacocoVersion = "0.8.16"
    }
    // Arabic is selected locally; every bundle install must include its language resources.
    bundle { language { enableSplit = false } }
    lint {
        // Version discovery is advisory. Keep the tested pins and show upgrade notices as info;
        // upgrading Gradle/Kotlin/AGP together is a separately validated toolchain change.
        informational += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
    buildFeatures { compose = true; buildConfig = true }
    // AGP 9.5 Compose Preview task creation requires Android unit-test resources,
    // including when APK dependency discovery realizes the Preview runfiles task.
    testOptions { unitTests.isIncludeAndroidResources = true }
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

val runtimeNoticeInventory = tasks.register<RuntimeNoticeInventory>("generateRuntimeNoticeInventory") {
    coordinates.set(configurations.named("standardReleaseRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }
        .map { root ->
            val seen = mutableSetOf<org.gradle.api.artifacts.component.ComponentIdentifier>()
            val pending = ArrayDeque<org.gradle.api.artifacts.result.ResolvedComponentResult>()
            val values = sortedSetOf<String>()
            pending.add(root)
            while (pending.isNotEmpty()) {
                val component = pending.removeFirst()
                if (!seen.add(component.id)) continue
                val id = component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
                if (id != null) values.add("${id.group}:${id.module}:${id.version}")
                component.dependencies.forEach { dependency ->
                    require(dependency is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                        "Cannot inventory an unresolved production dependency."
                    }
                    pending.add(dependency.selected)
                }
            }
            values.toList()
        })
    outputDirectory.set(layout.buildDirectory.dir("generated/runtime-notices"))
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        if (variant.productFlavors.any { it.second == "demo" }) variant.enable = false
    }
    onVariants(selector().all()) { variant ->
        if (variant.buildType == "release") {
            variant.sources.assets?.addGeneratedSourceDirectory(runtimeNoticeInventory, RuntimeNoticeInventory::outputDirectory)
        }
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
            tasks.named { it == "assemble$variantTaskName" || it == "install$variantTaskName" }
                .configureEach { dependsOn(exportApk) }
        }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
hilt { enableAggregatingTask = true }

dependencies {
    constraints {
        add("ksp", "com.google.guava:guava:33.7.2-jre") {
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
// Filter names lazily: matching(Task) realizes unrelated AGP Compose Preview tasks.
val roomSchemaWriters = setOf("kspDemoDebugKotlin", "kspStandardDebugKotlin", "kspStandardReleaseKotlin")
tasks.named { it in roomSchemaWriters }.configureEach {
    val currentName = name
    mustRunAfter(tasks.named { it in roomSchemaWriters && it < currentName })
}

// Large boundary fixtures run sequentially; they must not compete for heap.
tasks.withType<Test>().configureEach { maxHeapSize = "1536m"; maxParallelForks = 1 }
