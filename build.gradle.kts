buildscript {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    dependencies {
        // AGP's supported override keeps built-in Kotlin enabled; match the catalog's
        // Compose/serialization compiler version. GHSA-r937-wjx7-w2jp.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        constraints {
            add("classpath", "com.google.guava:guava:33.7.2-jre") {
                because("Fix GHSA-xxph-c9ww-hj94 in the build tooling")
            }
            add("classpath", "org.apache.commons:commons-lang3:3.21.0") {
                because("Fix GHSA-j288-q9x7-2f5v")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") {
                because("Fix GHSA-3677-xxcr-wjqv")
            }
            for (module in listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on")) {
                add("classpath", "org.bouncycastle:$module:1.85") {
                    because("Align Bouncy Castle modules on the patched 1.85 release")
                }
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("Fix GHSA-2363-cqg2-863c")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
