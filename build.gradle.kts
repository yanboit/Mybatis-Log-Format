plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "wang.imold"
version = "2.2-SNAPSHOT"

repositories {
    mavenCentral()
}

intellij {
    // 编译依赖版本 2020.1（最低兼容版本）
    version.set("2020.1")
    type.set("IC")
    downloadSources.set(false)
    updateSinceUntilBuild.set(true)
    plugins.set(listOf())
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    // 兼容 2020 需要 JDK 11
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        version.set(project.version.toString())
        // --------------------------
        // 核心：兼容 2020 ~ 最新版
        // --------------------------
        sinceBuild.set("201")    // 2020.1
        untilBuild.set("253.*")  // 2025.3 全覆盖
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}