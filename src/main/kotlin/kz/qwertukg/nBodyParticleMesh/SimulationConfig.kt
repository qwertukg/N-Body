package kz.qwertukg.nBodyParticleMesh

// Конфигурация симуляции
data class SimulationConfig(
    var k: Float = 1f,
    val worldSize: Float = 1000_000f * k,
    val count: Int = 300_000,
    val gridSizeX: Int = 64,
    val gridSizeY: Int = 64,
    val gridSizeZ: Int = 64,
    val worldWidth: Float = worldSize,
    val worldHeight: Float = worldSize,
    val worldDepth: Float = worldSize,
    val potentialSmoothingIterations: Int = 60,
    var g: Float = 100000f,
    var centerX: Float = (worldWidth * 0.5).toFloat(),
    var centerY: Float = (worldHeight * 2).toFloat(),
    var centerZ: Float = (worldDepth * 0.5).toFloat(),
    val minRadius: Double = worldSize * 0.01 / 10,
    val maxRadius: Double = worldSize * 0.1 / 10,
    val massFrom: Float = 1.0f,
    val massUntil: Float = 1.0001f,
    val isDropOutOfBounds: Boolean = false,
    val fov: Float = 1f,
    val magicConst: Float = 1f,
    val isFullScreen: Boolean = false,
    var blackHoleIndex: Int? = null
)