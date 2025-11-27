plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "wang.imold"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.1.5")
    type.set("IC") // Target IDE Platform
    downloadSources.set(true)
    updateSinceUntilBuild.set(true)
    plugins.set(listOf(/* Plugin Dependencies */))
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    // 移除可能过时的依赖，避免冲突
}

tasks {
    buildSearchableOptions {
        enabled = false // 关闭搜索优化（2024 版部分 API 不兼容旧优化逻辑）
    }
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        // 自动填充插件版本和兼容信息
        version.set(project.version.toString())
        sinceBuild.set("231.0")
        untilBuild.set("243.*")
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
