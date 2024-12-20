import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.geometry.Rectangle2D
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.MouseButton
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import kotlin.math.*
import kotlin.random.Random

// Data class for a 3D particle
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

// Configuration for the simulation
data class SimulationConfig(
    val screenBounds: Rectangle2D,
    val count: Int = 300_000,
    val gridSizeX: Int = 64,
    val gridSizeY: Int = 64,
    val gridSizeZ: Int = 64,
    val worldWidth: Float = 100_000f,
    val worldHeight: Float = 100_000f,
    val worldDepth: Float = 100_000f,
    val potentialSmoothingIterations: Int = 2,
    val g: Float = 10f,
    var centerX: Float = (worldWidth*0.5).toFloat(),
    var centerY: Float = (worldHeight*0.5).toFloat(),
    var centerZ: Float = (worldDepth*0.5).toFloat(),
    val minRadius: Double = worldHeight * 0.01,
    val maxRadius: Double = worldHeight * 0.1,
    val massFrom: Double = 1.9,
    val massUntil: Double = 2.0,
    val isDropOutOfBounds: Boolean = false,
    val fov: Double = 1.0,
    val boxSize: Double = worldHeight * 0.1,
    val magicConst: Double = PI,
    val isFullScreen: Boolean = false
)

// Main simulation class
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

    // Предварительный расчет некоторых величин для быстроты
    private val gxGyGz = gX * gY * gZ
    private val gYgZ = gY * gZ
    private val cellWidth = config.worldWidth / gX
    private val cellHeight = config.worldHeight / gY
    private val cellDepth = config.worldDepth / gZ

    // Одномерные массивы для масс и потенциалов, чтобы уменьшить накладные расходы при индексации
    private val massGrid = FloatArray(gxGyGz)
    private val potentialGrid = FloatArray(gxGyGz)
    private val tempPotential = FloatArray(gxGyGz)

    /**
     * Инициализация массива частиц из списка
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
     * Параллелизация осуществляется корутинами, однако следует обеспечить, что step() не вызывается повторно до завершения предыдущего вызова.
     */
    suspend fun step() = coroutineScope {
        // Обнуляем massGrid
        massGrid.fill(0f)

        val totalCount = particleX.size
        // Кол-во потоков будет равно числу доступных процессорных ядер, чтобы распараллелить вычисления
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSize = (totalCount / availableProcessors).coerceAtLeast(1)

        // Распределяем массу по сетке
        val ww = config.worldWidth
        val wh = config.worldHeight
        val wd = config.worldDepth
        val dx = 1f / cellWidth
        val dy = 1f / cellHeight
        val dz = 1f / cellDepth

        // Запуск корутин для распределения масс
        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                for (i in startIndex until endIndex) {
                    val px = particleX[i]
                    val py = particleY[i]
                    val pz = particleZ[i]

                    // Индексация клетки
                    // Используем прямое сравнение вместо coerceIn для скорости
                    var cx = (px * dx).toInt()
                    var cy = (py * dy).toInt()
                    var cz = (pz * dz).toInt()
                    if (cx < 0) cx = 0 else if (cx >= gX) cx = gX - 1
                    if (cy < 0) cy = 0 else if (cy >= gY) cy = gY - 1
                    if (cz < 0) cz = 0 else if (cz >= gZ) cz = gZ - 1

                    // Линейный индекс для одномерного массива
                    val index = cx * gYgZ + cy * gZ + cz
                    massGrid[index] += particleM[i]
                }
            }
        }.awaitAll()

        val g = config.g
        // Формирование tempPotential
        // Потенциал = -g * mass
        // Параллелизация особо не нужна для таких операций, но можем распараллелить если надо
        for (i in massGrid.indices) {
            tempPotential[i] = -g * massGrid[i]
        }

        // Сглаживание потенциала
        // Для каждой итерации сглаживания рассчитываем усредненный потенциал
        repeat(config.potentialSmoothingIterations) {
            // Проходим по внутренним точкам
            for (x in 1 until gX - 1) {
                val xBase = x * gYgZ
                for (y in 1 until gY - 1) {
                    val yBase = y * gZ
                    val xyBase = xBase + yBase
                    for (z in 1 until gZ - 1) {
                        val idx = xyBase + z
                        // Усредняем текущую точку с соседними
                        val p = (
                                tempPotential[idx] +
                                        tempPotential[idx + gYgZ] +    // x+1
                                        tempPotential[idx - gYgZ] +    // x-1
                                        tempPotential[idx + gZ] +      // y+1
                                        tempPotential[idx - gZ] +      // y-1
                                        tempPotential[idx + 1] +       // z+1
                                        tempPotential[idx - 1]         // z-1
                                ) / 7f
                        potentialGrid[idx] = p
                    }
                }
            }

            // Копируем potentialGrid в tempPotential для следующей итерации
            // Вместо copyOf проходим циклом — так быстрее и не создаём новый массив
            val tmp = tempPotential
            for (i in potentialGrid.indices) {
                tmp[i] = potentialGrid[i]
            }
        }

        // Обновление скоростей и позиций частиц
        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                // Заранее рассчитываем границы индексации в potentialGrid, чтобы не вызывать coerceIn
                for (i in startIndex until endIndex) {
                    var px = particleX[i]
                    var py = particleY[i]
                    var pz = particleZ[i]

                    // Индексы для производных потенциала
                    // Зажимаем индексы, чтобы избежать выхода за пределы
                    // Так как мы используем производные, нам нужны индексы от 1 до size-2
                    var cx = (px * dx).toInt()
                    var cy = (py * dy).toInt()
                    var cz = (pz * dz).toInt()

                    if (cx < 1) cx = 1 else if (cx > gX - 2) cx = gX - 2
                    if (cy < 1) cy = 1 else if (cy > gY - 2) cy = gY - 2
                    if (cz < 1) cz = 1 else if (cz > gZ - 2) cz = gZ - 2

                    val base = cx * gYgZ + cy * gZ + cz
                    // Чтение соседних потенциалов для расчета градиента
                    val dVdx = (potentialGrid[base + gYgZ] - potentialGrid[base - gYgZ]) / (2 * cellWidth)
                    val dVdy = (potentialGrid[base + gZ] - potentialGrid[base - gZ]) / (2 * cellHeight)
                    val dVdz = (potentialGrid[base + 1] - potentialGrid[base - 1]) / (2 * cellDepth)

                    // Обновление скоростей
                    var vx = particleVx[i] - dVdx
                    var vy = particleVy[i] - dVdy
                    var vz = particleVz[i] - dVdz

                    // Обновление позиций, зажимание внутри мира
                    px += vx
                    py += vy
                    pz += vz

                    if (px < 0f) { px = 0f; vx = 0f }
                    else if (px > ww) { px = ww; vx = 0f }
                    if (py < 0f) { py = 0f; vy = 0f }
                    else if (py > wh) { py = wh; vy = 0f }
                    if (pz < 0f) { pz = 0f; vz = 0f }
                    else if (pz > wd) { pz = wd; vz = 0f }

                    // Записываем обновления обратно
                    particleX[i] = px
                    particleY[i] = py
                    particleZ[i] = pz
                    particleVx[i] = vx
                    particleVy[i] = vy
                    particleVz[i] = vz
                }
            }
        }.awaitAll()

        // Удаление частиц, вышедших за границы
        dropOutOfBounce()
    }

    /**
     * Отрисовка частиц в 2D проекции
     */
    fun draw2D(gc: GraphicsContext) {
        // Очищаем холст чёрным
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, gc.canvas.width, gc.canvas.height)

        // Масштаб по наименьшей стороне
        val scale = min(gc.canvas.width / config.worldWidth, gc.canvas.height / config.worldHeight)
        val fov = config.fov
        val halfW = config.worldWidth * 0.5
        val halfH = config.worldHeight * 0.5
        val depth = config.worldDepth

        gc.fill = Color.WHITE
        for (i in particleX.indices) {
            val zFactor = (1.0 / (1.0 + (particleZ[i] / depth) * fov))
            val screenX = ((particleX[i] - halfW) * zFactor + halfW) * scale
            val screenY = ((particleY[i] - halfH) * zFactor + halfH) * scale
            val size = particleR[i] * zFactor
            gc.fillOval(screenX, screenY, size, size)
        }
    }

    /**
     * Удаление частиц, вышедших за пределы мира
     * Сжимает массивы, удаляя вышедшие частицы, тем самым уменьшая их размер.
     * Это нужно делать осторожно, чтобы не обращаться к удалённым индексам в дальнейшем.
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
                // Оставляем частицы, которые в пределах
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

            // Сжимаем массивы до newCount
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
     * Установить массу частицы по индексу
     */
    fun setParticleMass(i: Int, mass: Float) {
        particleM[i] = mass
    }
}

class SimulationApp : Application() {
    override fun start(primaryStage: Stage) {
        val screenBounds = Screen.getPrimary().bounds
        val config = SimulationConfig(screenBounds)

        val canvas = Canvas(screenBounds.height, screenBounds.height)
        val gc = canvas.graphicsContext2D

        // Генерируем частицы начальным способом (предполагается, что функция существует)
        val particles = generateParticlesCircle(config)

        val simulation = ParticleMeshSimulation(config)
        simulation.initSimulation(particles)

        val root = StackPane(canvas)
        val scene = Scene(root)

        primaryStage.title = "3D Particle Simulation"
        primaryStage.scene = scene
        primaryStage.isFullScreen = config.isFullScreen
        primaryStage.show()

        // Настройка обработчика взаимодействия с пользователем (предполагается, что функция существует)
        scene.controller(simulation, canvas)

        // Коррутинный scope для выполнения шагов симуляции
        val scope = CoroutineScope(Dispatchers.Default)
        var currentJob: Job? = null

        // Анимационный таймер для отрисовки и обновления
        object : AnimationTimer() {
            override fun handle(now: Long) {
                // Не запускаем новый шаг пока предыдущий не закончился - предотвращаем гонки данных
                if (currentJob?.isActive == true) return

                // Вывод текущего количества частиц для отладки
                println("Particle Count: ${simulation.particleX.size}")

                // Запуск нового шага симуляции
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

fun generateParticlesCircle(config: SimulationConfig): List<Particle> {
    var sumM = 0.0
    var sumMx = 0.0
    var sumMy = 0.0
    var sumMz = 0.0

    val particles = MutableList(config.count) {
        val r = Random.nextDouble(config.minRadius, config.maxRadius).toFloat()
        val angle1 = Random.nextDouble(0.0, 2 * PI)
        val angle2 = Random.nextDouble(0.0, PI)

        val x = config.centerX + (r * cos(angle1) * sin(angle2)).toFloat()
        val y = config.centerY + (r * sin(angle1) * sin(angle2)).toFloat()
        val z = config.centerZ + (r * cos(angle2)).toFloat()

        val m = Random.nextDouble(config.massFrom, config.massUntil).toFloat()
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
            val uz = dz / dist

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

fun generateParticlesBox(config: SimulationConfig): List<Particle> {
    val w = config.worldWidth.toDouble()
    val h = config.worldHeight.toDouble()
    val d = config.worldDepth.toDouble()
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
}

fun Scene.controller(simulation: ParticleMeshSimulation, canvas: Canvas) = setOnMouseClicked {
    val closestIndex = findClosestParticle(simulation, it.x.toFloat(), it.y.toFloat(), it.z.toFloat(), canvas.width, canvas.height)
    val h = 0.5
    val t = 0.5
    val centerX = Random.nextDouble(simulation.config.worldWidth*h-simulation.config.worldWidth*t, simulation.config.worldWidth*h+simulation.config.worldWidth*t).toFloat()
    val centerY = Random.nextDouble(simulation.config.worldHeight*h-simulation.config.worldHeight*t, simulation.config.worldHeight*h+simulation.config.worldHeight*t).toFloat()
    val centerZ = Random.nextDouble(simulation.config.worldDepth*h-simulation.config.worldDepth*t, simulation.config.worldDepth*h+simulation.config.worldDepth*t).toFloat()

    when (it.button) {

        MouseButton.PRIMARY -> simulation.setParticleMass(closestIndex, 200_000f)
        MouseButton.SECONDARY -> {
            val particles = generateParticlesCircle(simulation.config)
            simulation.initSimulation(particles)
        }
        else -> {
            simulation.config.centerX = centerX
            simulation.config.centerY = centerY
            simulation.config.centerZ = centerZ
            val particles = generateParticlesCircle(simulation.config)
            simulation.initSimulation(particles)
        }
    }
}

fun findClosestParticle(
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
    val worldZ = z / scale
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
}

fun main() {
    Application.launch(SimulationApp::class.java)
}
