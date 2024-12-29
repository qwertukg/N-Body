package kz.qwertukg.nBody.nBodyParticleMesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.PI

// Конфигурация симуляции
@Serializable
data class SimulationConfig(
    val count: Int,
    val screenW: Int = 3440,
    val screenH: Int = 1440,
    val gridSize: Int = 64,
    val worldSize: Float = 1000_000f,
    var g: Float = 6.67430e-11f,
    var dt: Float = 1f,
    var minRadius: Double = worldSize * 0.2, // 0.2 ok
    var maxRadius: Double = worldSize * 0.25, // 0.25 ok
    val massFrom: Float =   1.0f,
    val massUntil: Float =  25_000.01f,
    val magicConst: Float = (1).toFloat(), // 3f is ok
    val isFullScreen: Boolean = true,
    val isDropOutOfBounds: Boolean = true,
    val figureParams: MutableMap<String, Float> = mutableMapOf()
) {
    val gridSizeX: Int = gridSize * screenW/screenH
    val gridSizeY: Int = gridSize
    val gridSizeZ: Int = gridSize

    val worldWidth: Float = worldSize * screenW/screenH
    val worldHeight: Float = worldSize
    val worldDepth: Float = worldSize

    var centerX: Float = worldWidth * 0.5f
    var centerY: Float = worldHeight * 0.5f
    var centerZ: Float = worldDepth * 0.5f
}

fun fromJson(path: String): SimulationConfig {
    val jsonString = File(path).readText()
    return Json.decodeFromString<SimulationConfig>(jsonString)
}
