plugins {
    kotlin("jvm") version "1.9.23"
    application
    id("org.openjfx.javafxplugin") version "0.0.14"
}

group = "kz.qwertukg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val osName = System.getProperty("os.name").toLowerCase()
val osArch = System.getProperty("os.arch")

// Версию JavaFX можно менять при необходимости.
// Для macOS, JavaFX пакеты доступны с суффиксом ":mac" или ":mac-aarch64" для M1/M2.
val javafxVersion = "20"
val platform = when {
    osName.contains("mac") && osArch.contains("aarch64") -> "mac-aarch64"
    osName.contains("mac") -> "mac"
    osName.contains("win") -> "win"
    else -> "linux"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Поддержка корутин для JavaFX
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")

    // JavaFX зависимости
    // Для простоты подключаем основные модули, которых достаточно для Application, Canvas, Scene и т.д.
    implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    testImplementation(kotlin("test"))
}

application {
    // Укажите здесь класс с точкой входа в программу.
    // Предполагается, что main-функция находится в SimulationApp.kt
    mainClass.set("SimulationApp")
}

tasks.withType<JavaExec> {
    // Если понадобится, можно явно указать модули JavaFX:
    // jvmArgs = listOf("--add-modules", "javafx.controls,javafx.graphics")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(18)
}