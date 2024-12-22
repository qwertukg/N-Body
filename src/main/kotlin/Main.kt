import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.MouseButton
import javafx.scene.layout.BackgroundSize
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import kotlin.math.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

// Data class для 3D-частицы
data class Particle(
    val x: Float,
    val y: Float,
    val z: Float,
    val vx: Float,
    val vy: Float,
    val vz: Float,
    val m: Float,
    val r: Float
)

// Конфигурация симуляции
data class SimulationConfig(
    val worldSize: Float = 100_000f,
    val count: Int = 300_000,
    val gridSizeX: Int = 64,
    val gridSizeY: Int = 64,
    val gridSizeZ: Int = 64,
    val worldWidth: Float = worldSize,
    val worldHeight: Float = worldSize,
    val worldDepth: Float = worldSize,
    val potentialSmoothingIterations: Int = 2,
    var g: Float = 10f,
    var centerX: Float = (worldWidth * 0.5).toFloat(),
    var centerY: Float = (worldHeight * 0.5).toFloat(),
    var centerZ: Float = (worldDepth * 0.5).toFloat(),
    val minRadius: Double = worldSize * 0.01,
    val maxRadius: Double = worldSize * 0.1,
    val massFrom: Float = 1.0f,
    val massUntil: Float = 1.0001f,
    val isDropOutOfBounds: Boolean = false,
    val fov: Float = 1f,
    val magicConst: Float = 3f,
    val isFullScreen: Boolean = false
)

// Основной класс симуляции
class ParticleMeshSimulation(val config: SimulationConfig) {
    // Массивы координат и характеристик частиц
    lateinit var particleX: FloatArray
    lateinit var particleY: FloatArray
    lateinit var particleZ: FloatArray
    lateinit var particleVx: FloatArray
    lateinit var particleVy: FloatArray
    lateinit var particleVz: FloatArray
    lateinit var particleM: FloatArray
    lateinit var particleR: FloatArray

    // Размеры сетки
    private val gX = config.gridSizeX
    private val gY = config.gridSizeY
    private val gZ = config.gridSizeZ

    // Предварительные расчёты
    private val gxGyGz = gX * gY * gZ
    private val gYgZ = gY * gZ
    private val cellWidth = config.worldWidth / gX
    private val cellHeight = config.worldHeight / gY
    private val cellDepth = config.worldDepth / gZ

    // Одномерные массивы для масс и потенциалов
    private val massGrid = FloatArray(gxGyGz)
    private val potentialGrid = FloatArray(gxGyGz)
    private val tempPotential = FloatArray(gxGyGz)

    /**
     * Инициализация из списка частиц
     */
    fun initSimulation(particles: List<Particle>) {
        val totalCount = particles.size
        particleX = FloatArray(totalCount) { particles[it].x }
        particleY = FloatArray(totalCount) { particles[it].y }
        particleZ = FloatArray(totalCount) { particles[it].z }
        particleVx = FloatArray(totalCount) { particles[it].vx }
        particleVy = FloatArray(totalCount) { particles[it].vy }
        particleVz = FloatArray(totalCount) { particles[it].vz }
        particleM = FloatArray(totalCount) { particles[it].m }
        particleR = FloatArray(totalCount) { particles[it].r }
    }

    /**
     * Один шаг симуляции
     */
    suspend fun step() = coroutineScope {
        // 1) Обнуляем massGrid
        massGrid.fill(0f)

        val totalCount = particleX.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSize = (totalCount / availableProcessors).coerceAtLeast(1)

        // Предрасчёты для индексации
        val ww = config.worldWidth
        val wh = config.worldHeight
        val wd = config.worldDepth
        val dx = 1f / cellWidth
        val dy = 1f / cellHeight
        val dz = 1f / cellDepth

        // 2) Распределяем массу по сетке (параллельно)
        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                for (i in startIndex until endIndex) {
                    val px = particleX[i]
                    val py = particleY[i]
                    val pz = particleZ[i]

                    // Индексация клетки
                    var cx = (px * dx).toInt()
                    var cy = (py * dy).toInt()
                    var cz = (pz * dz).toInt()
                    if (cx < 0) cx = 0 else if (cx >= gX) cx = gX - 1
                    if (cy < 0) cy = 0 else if (cy >= gY) cy = gY - 1
                    if (cz < 0) cz = 0 else if (cz >= gZ) cz = gZ - 1

                    val index = cx * gYgZ + cy * gZ + cz
                    massGrid[index] += particleM[i]
                }
            }
        }.awaitAll()

        // 3) Формируем tempPotential (пока без сглаживания)
        val g = config.g
        for (i in massGrid.indices) {
            tempPotential[i] = -g * massGrid[i]
        }

        // 4) Сглаживание потенциала (можно распараллелить по итерациям)
        repeat(config.potentialSmoothingIterations) {
            parallelSmoothPotential()
        }

        // 5) Обновление скоростей и позиций частиц (параллельно)
        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                for (i in startIndex until endIndex) {
                    var px = particleX[i]
                    var py = particleY[i]
                    var pz = particleZ[i]

                    // Индексы в potentialGrid
                    var cx = (px * dx).toInt()
                    var cy = (py * dy).toInt()
                    var cz = (pz * dz).toInt()
                    if (cx < 1) cx = 1 else if (cx > gX - 2) cx = gX - 2
                    if (cy < 1) cy = 1 else if (cy > gY - 2) cy = gY - 2
                    if (cz < 1) cz = 1 else if (cz > gZ - 2) cz = gZ - 2

                    val base = cx * gYgZ + cy * gZ + cz

                    // Градиент потенциала
                    val dVdx = (potentialGrid[base + gYgZ] - potentialGrid[base - gYgZ]) / (2 * cellWidth)
                    val dVdy = (potentialGrid[base + gZ] - potentialGrid[base - gZ]) / (2 * cellHeight)
                    val dVdz = (potentialGrid[base + 1] - potentialGrid[base - 1]) / (2 * cellDepth)

                    // Обновление скоростей
                    var vx = particleVx[i] - dVdx
                    var vy = particleVy[i] - dVdy
                    var vz = particleVz[i] - dVdz

                    // Обновление позиций
                    px += vx
                    py += vy
                    pz += vz

                    // Зажимаем внутри мира
                    if (px < 0f) { px = 0f; vx = 0f }
                    else if (px > ww) { px = ww; vx = 0f }
                    if (py < 0f) { py = 0f; vy = 0f }
                    else if (py > wh) { py = wh; vy = 0f }
                    if (pz < 0f) { pz = 0f; vz = 0f }
                    else if (pz > wd) { pz = wd; vz = 0f }

                    particleX[i] = px
                    particleY[i] = py
                    particleZ[i] = pz
                    particleVx[i] = vx
                    particleVy[i] = vy
                    particleVz[i] = vz
                }
            }
        }.awaitAll()

        // 6) Удаление вышедших за границы (если включено)
        dropOutOfBounce()
    }

    /**
     * Параллельное сглаживание потенциала
     * Для каждой итерации мы берём из tempPotential, пишем в potentialGrid,
     * а потом копируем potentialGrid обратно в tempPotential.
     */
    private suspend fun parallelSmoothPotential() = coroutineScope {
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSizeX = (gX / availableProcessors).coerceAtLeast(1)

        // Разбиваем проход по оси X на несколько корутин
        val jobs = mutableListOf<Deferred<Unit>>()
        for (startX in 1 until gX-1 step chunkSizeX) {
            val endX = min(startX + chunkSizeX, gX-1)
            jobs += async(Dispatchers.Default) {
                for (x in startX until endX) {
                    val xBase = x * gYgZ
                    for (y in 1 until gY - 1) {
                        val yBase = y * gZ
                        val xyBase = xBase + yBase
                        for (z in 1 until gZ - 1) {
                            val idx = xyBase + z
                            val p = (
                                    tempPotential[idx] +
                                            tempPotential[idx + gYgZ] +
                                            tempPotential[idx - gYgZ] +
                                            tempPotential[idx + gZ] +
                                            tempPotential[idx - gZ] +
                                            tempPotential[idx + 1] +
                                            tempPotential[idx - 1]
                                    ) / 7f
                            potentialGrid[idx] = p
                        }
                    }
                }
            }
        }
        jobs.awaitAll()

        // Копируем back potentialGrid -> tempPotential
        // Желательно делать это тоже параллельно
        val copyChunkSize = (gxGyGz / availableProcessors).coerceAtLeast(1)
        (0 until gxGyGz step copyChunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + copyChunkSize, gxGyGz)
                for (i in startIndex until endIndex) {
                    tempPotential[i] = potentialGrid[i]
                }
            }
        }.awaitAll()
    }

    /**
     * Рисуем частицы в 2D
     */
    fun draw2D(gc: GraphicsContext) {
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, gc.canvas.width, gc.canvas.height)

        val scale = min(gc.canvas.width / config.worldWidth, gc.canvas.height / config.worldHeight)
        val fov = config.fov
        val halfW = config.worldWidth * 0.5
        val halfH = config.worldHeight * 0.5
        val depth = config.worldDepth

        gc.fill = Color.WHITE
        for (i in particleX.indices) {
            val zFactor = 1.0 / (1.0 + (particleZ[i] / depth) * fov)
            val screenX = ((particleX[i] - halfW) * zFactor + halfW) * scale
            val screenY = ((particleY[i] - halfH) * zFactor + halfH) * scale
            val size = particleR[i] * zFactor
            gc.fillOval(screenX, screenY, size, size)
        }
    }

    /**
     * Удаление частиц, вышедших за пределы мира
     */
    suspend fun dropOutOfBounce() = withContext(Dispatchers.Default) {
        if (config.isDropOutOfBounds) {
            val ww = config.worldWidth
            val wh = config.worldHeight
            val wd = config.worldDepth
            var newCount = 0
            val oldCount = particleX.size

            for (i in 0 until oldCount) {
                val px = particleX[i]
                val py = particleY[i]
                val pz = particleZ[i]
                if (px > 0f && px < ww && py > 0f && py < wh && pz > 0f && pz < wd) {
                    particleX[newCount] = px
                    particleY[newCount] = py
                    particleZ[newCount] = pz
                    particleVx[newCount] = particleVx[i]
                    particleVy[newCount] = particleVy[i]
                    particleVz[newCount] = particleVz[i]
                    particleM[newCount] = particleM[i]
                    particleR[newCount] = particleR[i]
                    newCount++
                }
            }

            if (newCount < oldCount) {
                particleX = particleX.copyOf(newCount)
                particleY = particleY.copyOf(newCount)
                particleZ = particleZ.copyOf(newCount)
                particleVx = particleVx.copyOf(newCount)
                particleVy = particleVy.copyOf(newCount)
                particleVz = particleVz.copyOf(newCount)
                particleM = particleM.copyOf(newCount)
                particleR = particleR.copyOf(newCount)
            }
        }
    }

    /**
     * Пример установки массы частиц
     */
    fun setParticleMass(i: Int, mass: Float) {
        particleM[i] = mass
    }
}

/**
 * Главный класс приложения
 */
class SimulationApp : Application() {
    override fun start(primaryStage: Stage) {
        val screenBounds = Screen.getPrimary().bounds

        // Размер холста — например, квадратный, чтобы не было искажений
        val canvas = Canvas(screenBounds.height, screenBounds.height)
        val gc = canvas.graphicsContext2D

        // Simulation
        val config = SimulationConfig()
        val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)
        val simulation = ParticleMeshSimulation(config)
        simulation.initSimulation(particles)

        val root = StackPane(canvas)
        val scene = Scene(root)

        primaryStage.title = "3D Particle Simulation"
        primaryStage.scene = scene
        primaryStage.isFullScreen = config.isFullScreen
        primaryStage.show()

        // Пример простого обработчика: можно добавить логику нажатия мышкой и т.д.
        scene.controllerCreateBodyGroup(simulation, canvas)

        // Создаём корутину для обновления
        val scope = CoroutineScope(Dispatchers.Default)
        var currentJob: Job? = null

        // Анимационный таймер
        object : AnimationTimer() {
            override fun handle(now: Long) {
                // Если предыдущий шаг ещё не завершён, пропускаем кадр
                if (currentJob?.isActive == true) return

                // Запуск нового шага
                currentJob = scope.launch {
                    simulation.step()
                    withContext(Dispatchers.JavaFx) {
                        simulation.draw2D(gc)
                    }
                }
            }
        }.start()
    }
}

fun generateParticlesCircle(config: SimulationConfig, cx: Float, cy: Float, cz: Float): List<Particle> {
    var sumM = 0.0
    var sumMx = 0.0
    var sumMy = 0.0
    var sumMz = 0.0

    val particles = MutableList(config.count) {
        val r = Random.nextDouble(config.minRadius, config.maxRadius).toFloat()
        val angle1 = Random.nextDouble(0.0, 2 * PI)
        val angle2 = Random.nextDouble(0.0, PI)

        val x = cx + (r * cos(angle1) * sin(angle2)).toFloat()
        val y = cy + (r * sin(angle1) * sin(angle2)).toFloat()
        val z = cz + (r * cos(angle2)).toFloat()

        val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
        val vx = 0f
        val vy = 0f
        val vz = 0f
        val particleR = sqrt(m)

        sumM += m
        sumMx += m * x
        sumMy += m * y
        sumMz += m * z

        Particle(x, y, z, vx, vy, vz, m, particleR)
    }

    val totalMass = sumM.toFloat()
    val centerMassX = (sumMx / sumM).toFloat()
    val centerMassY = (sumMy / sumM).toFloat()
    val centerMassZ = (sumMz / sumM).toFloat()

    // Применяем скорости
    val g = config.g
    val magicConst = (config.magicConst).toFloat() // TODO

    for (i in particles.indices) {
        val p = particles[i]
        val dx = p.x - centerMassX
        val dy = p.y - centerMassY
        val dz = p.z - centerMassZ
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > 0f) {
            val v = sqrt(g * totalMass / dist)
            val ux = dx / dist
            val uy = dy / dist

            // Correct vz calculation for orbital velocity
            val vx = -uy * v
            val vy = ux * v
            val vz = -ux * v // Adjusted vz to follow the orbital velocity rules

            particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy * magicConst, vz * magicConst, p.m, p.r)
        } else {
            particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
        }
    }
    return particles
}

fun Scene.controllerCreateBodyGroup(simulation: ParticleMeshSimulation, canvas: Canvas) {
    val sens = 100
    var initialMouseX = 0.0
    var initialMouseY = 0.0
    val config = simulation.config

    var baseXPositions = FloatArray(0)
    var baseYPositions = FloatArray(0)
    var baseZPositions = FloatArray(0)

    // Преобразование экранных координат в мировые координаты при заданном Z
    fun screenToWorldXY(screenX: Double, screenY: Double, currentZ: Float): Pair<Float, Float> {
        val scale = min(canvas.width / config.worldWidth, canvas.height / config.worldHeight)
        val halfW = config.worldWidth * 0.5f
        val halfH = config.worldHeight * 0.5f
        val zFactor = 1.0 / (1.0 + (currentZ / config.worldDepth) * config.fov)
        val invZFactor = 1.0 / zFactor
        val worldX = (((screenX / scale) - halfW) * invZFactor + halfW).toFloat()
        val worldY = (((screenY / scale) - halfH) * invZFactor + halfH).toFloat()
        return Pair(worldX, worldY)
    }

    val g = config.g
    setOnMousePressed { event ->
        if (event.isPrimaryButtonDown) {
            //config.g = 0f
            val (dx, dy) = screenToWorldXY(event.sceneX, event.sceneY, config.centerZ)
            val particles = generateParticlesCircle(config, dx, dy, config.centerZ)
            simulation.initSimulation(particles)
            baseXPositions = simulation.particleX.copyOf()
            baseYPositions = simulation.particleY.copyOf()
            baseZPositions = simulation.particleZ.copyOf()
            initialMouseX = event.sceneX
            initialMouseY = event.sceneY
        }
    }

    setOnMouseDragged { event ->
        if (!event.isControlDown) {
            val dx = (event.sceneX - initialMouseX) * sens
            val dy = (event.sceneY - initialMouseY) * sens
            for (i in 0 until simulation.particleX.size) {
                simulation.particleX[i] = baseXPositions[i] + dx.toFloat()
                simulation.particleY[i] = baseYPositions[i] + dy.toFloat()
            }
        } else {
            val dy = (event.sceneY - initialMouseY) * sens * 2
            for (i in 0 until simulation.particleX.size) {
                simulation.particleZ[i] = baseZPositions[i] + dy.toFloat()
            }
        }
    }

    setOnMouseReleased { event ->
        config.g = g
    }
}

suspend fun main() {
    // Simulation
    /*val config = SimulationConfig()
    val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)
    val simulation = ParticleMeshSimulation(config)
    simulation.initSimulation(particles)
    val iterations = 10000
    val dt = measureTimeMillis {
        repeat(iterations) {
            simulation.step()
        }
    }
    val tpi = 1000 / (dt / iterations)
    println("Duration time for ${config.count} bodies: $dt millis. Time per iteration: $tpi")*/

    System.setProperty("javafx.animation.fullspeed", "true")
    Application.launch(SimulationApp::class.java)
}


//==========================

/*fun generateParticlesBox(config: SimulationConfig): List<Particle> {
    val cX = config.centerX.toFloat()
    val cY = config.centerY
    val cZ = config.centerZ
    val particles = MutableList(config.count) {
        val x = Random.nextDouble(cX - config.boxSize, cX + config.boxSize).toFloat()
        val y = Random.nextDouble(cY - config.boxSize, cY + config.boxSize).toFloat()
        val z = Random.nextDouble(cZ - config.boxSize, cZ + config.boxSize).toFloat()

        val m = Random.nextDouble(config.massFrom, config.massUntil).toFloat() // TODO
        val vx = 0f
        val vy = 0f
        val vz = 0f
        val particleR = sqrt(m) // TODO

        Particle(x, y, z, vx, vy, vz, m, particleR)
    }

    return particles
}*/

/*fun Scene.controller(simulation: ParticleMeshSimulation, canvas: Canvas) = setOnMouseClicked {
    val closestIndex = findClosestParticle(simulation, it.x.toFloat(), it.y.toFloat(), it.z.toFloat(), canvas.width, canvas.height)
    val h = 0.5
    val t = 0.5
    val centerX = Random.nextDouble(simulation.config.worldWidth*h-simulation.config.worldWidth*t, simulation.config.worldWidth*h+simulation.config.worldWidth*t).toFloat()
    val centerY = Random.nextDouble(simulation.config.worldHeight*h-simulation.config.worldHeight*t, simulation.config.worldHeight*h+simulation.config.worldHeight*t).toFloat()
    val centerZ = Random.nextDouble(simulation.config.worldDepth*h-simulation.config.worldDepth*t, simulation.config.worldDepth*h+simulation.config.worldDepth*t).toFloat()

    when (it.button) {

        MouseButton.PRIMARY -> simulation.setParticleMass(closestIndex, 200_000f)
        MouseButton.SECONDARY -> {
            val particles = generateParticlesCircle(simulation.config, simulation.config.centerX, simulation.config.centerY, simulation.config.centerZ)
            simulation.initSimulation(particles)
        }
        else -> {
            simulation.config.centerX = centerX
            simulation.config.centerY = centerY
            simulation.config.centerZ = centerZ
            val particles = generateParticlesCircle(simulation.config, simulation.config.centerX, simulation.config.centerY, simulation.config.centerZ)
            simulation.initSimulation(particles)
        }
    }
}*/

/*fun findClosestParticle(
    simulation: ParticleMeshSimulation,
    x: Float,
    y: Float,
    z: Float,
    canvasWidth: Double,
    canvasHeight: Double,
    tolerance: Int = 10
): Int {
    // Вычисляем масштаб, как в draw()
    val scale = min(canvasWidth / simulation.config.worldWidth, canvasHeight / simulation.config.worldHeight)

    // Переводим экранные координаты клика в координаты мира
    val worldX = x / scale
    val worldY = y / scale
    val worldTolerance = tolerance / scale

    for (i in simulation.particleX.indices) {
        val sX = simulation.particleX[i]
        val sY = simulation.particleY[i]
        val dx = abs(sX - worldX)
        val dy = abs(sY - worldY)

        // Проверяем, находится ли частица в пределах worldTolerance по осям X и Y
        if (dx < worldTolerance && dy < worldTolerance) {
            return i
        }
    }

    return 0
}*/



