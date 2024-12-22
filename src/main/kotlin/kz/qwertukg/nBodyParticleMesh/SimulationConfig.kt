package kz.qwertukg.nBodyParticleMesh

import kotlin.math.E
import kotlin.math.PI

// Конфигурация симуляции
data class SimulationConfig(
    var k: Float = 1f,
    val worldSize: Float = 1000_000f * k,
    val count: Int = 500_000,
    val gridSizeX: Int = 64,
    val gridSizeY: Int = 64,
    val gridSizeZ: Int = 64,
    val worldWidth: Float = worldSize,
    val worldHeight: Float = worldSize,
    val worldDepth: Float = worldSize,
    val potentialSmoothingIterations: Int = 120, // 60 is ok // 80 is ok // 100 is OK! // 120 is OK!!! // 300 is too long wait
    var g: Float = 100000f,
    var centerX: Float = (worldWidth * 0.5).toFloat(),
    var centerY: Float = (worldHeight * 0.5).toFloat(),
    var centerZ: Float = (worldDepth * 0.5).toFloat(),
    val minRadius: Double = worldSize * 0.2, // 0.2
    val maxRadius: Double = worldSize * 0.25, // 0.2
    val massFrom: Float = 1.0f,
    val massUntil: Float = 1.0001f,
    val isDropOutOfBounds: Boolean = false,
    val fov: Float = 1f,
    val magicConst: Float = 3f, // 3f is ok
    val isFullScreen: Boolean = false,
    var blackHoleIndex: Int? = null
)