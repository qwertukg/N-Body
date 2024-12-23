package kz.qwertukg.nBodyParticleMesh

import javafx.scene.canvas.Canvas
import kotlin.math.E
import kotlin.math.PI

// Конфигурация симуляции
data class SimulationConfig(
    val count: Int = 300_000,
    val screenW: Int = 3440,
    val screenH: Int = 1440,
    val gridSize: Int = 64,
    val gridSizeX: Int = gridSize * screenW/screenH,
    val gridSizeY: Int = gridSize,
    val gridSizeZ: Int = gridSize,
    val worldSize: Float = 500_000f,
    val worldWidth: Float = worldSize * screenW/screenH,
    val worldHeight: Float = worldSize,
    val worldDepth: Float = worldSize,
    val potentialSmoothingIterations: Int = 60, //60 ok //80 OK //100 Endless dance OK! //110 Alien eyes OK! //120 is MAX!!! //140 SUPERMAX!!! // 300 is too long wait
    var g: Float = 9.81f,
    var centerX: Float = (worldWidth * 0.5).toFloat(),
    var centerY: Float = (worldHeight * 0.5).toFloat(),
    var centerZ: Float = (worldDepth * 0.5).toFloat(),
    val minRadius: Double = worldSize * 0.2, // 0.2 ok
    val maxRadius: Double = worldSize * 0.25, // 0.25 ok
    val massFrom: Float =   10000f,
    val massUntil: Float =  10001f,
    val isDropOutOfBounds: Boolean = false,
    val fov: Float = 1f,
    val magicConst: Float = 3f, // 3f is ok
    val isFullScreen: Boolean = true,
    var blackHoleIndex: Int? = null
)