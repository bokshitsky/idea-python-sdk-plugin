import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.boksh"
version = providers.gradleProperty("version").getOrElse("1.0.0")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1.7")
        plugin("PythonCore", "251.28293.39")
        pluginVerifier()
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The IntelliJ platform test initializer on the injected test classpath needs JUnit 4 classes
    testRuntimeOnly("junit:junit:4.13.2")
    testImplementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Supported: IDEA/PyCharm 2025.1 and newer (2025.x, 2026.x).
            sinceBuild = "251"
            // No upper bound: keep the plugin installable on newer IDEs
            // instead of capping at the build platform. Check them with ./gradlew verifyPlugin.
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false

    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.7")
            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2026.3")
        }
    }

    publishing {
        token = providers.environmentVariable("IDEA_PYTHON_SDK_PLUGIN_UPLOAD_TOKEN")
    }
}

tasks.test {
    useJUnitPlatform()
}
