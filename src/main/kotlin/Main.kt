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

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val m: Float,
    val r: Float
)

data class SimulationConfig(
    val screenBounds: Rectangle2D,
    val count: Int = 1_000_000,
    val gridSizeX: Int = 64,
    val gridSizeY: Int = 64,
    val worldWidth: Float = 100000f,//(screenBounds.width * 70).toFloat(),
    val worldHeight: Float = 100000f,//(screenBounds.height * 70).toFloat(),
    val potentialSmoothingIterations: Int = 2,
    val g: Float = 10f,
    val centerX: Float = worldWidth / 2,
    val centerY: Float = worldHeight / 2,
    val minRadius: Float = 0f,
    val maxRadius: Float = (worldHeight * 0.4).toFloat(),
    val massFrom: Int = 1,
    val massUntil: Int = 5,
    val dropOutOfBounds: Boolean = true,
    val fullScreen: Boolean = false,
)

class ParticleMeshSimulation(val config: SimulationConfig, private val initialParticles: List<Particle>) {
    lateinit var particleX: FloatArray
    lateinit var particleY: FloatArray
    lateinit var particleVx: FloatArray
    lateinit var particleVy: FloatArray
    lateinit var particleM: FloatArray
    lateinit var particleR: FloatArray

    private val massGrid: Array<FloatArray> = Array(config.gridSizeX) { FloatArray(config.gridSizeY) }
    private val potentialGrid: Array<FloatArray> = Array(config.gridSizeX) { FloatArray(config.gridSizeY) }
    private val tempPotential: Array<FloatArray> = Array(config.gridSizeX) { FloatArray(config.gridSizeY) }

    private val cellWidth: Float = config.worldWidth / config.gridSizeX
    private val cellHeight: Float = config.worldHeight / config.gridSizeY
    private val invDx: Float = 1f / cellWidth
    private val invDy: Float = 1f / cellHeight

    fun initSimulation() {
        val totalCount = initialParticles.size
        particleX = FloatArray(totalCount)
        particleY = FloatArray(totalCount)
        particleVx = FloatArray(totalCount)
        particleVy = FloatArray(totalCount)
        particleM = FloatArray(totalCount)
        particleR = FloatArray(totalCount)

        for (i in 0 until totalCount) {
            val p = initialParticles[i]
            particleX[i] = p.x
            particleY[i] = p.y
            particleVx[i] = p.vx
            particleVy[i] = p.vy
            particleM[i] = p.m
            particleR[i] = p.r
        }
    }

    fun setParticleMass(i: Int, mass: Float) {
        particleM[i] = mass
    }

    suspend fun step() = coroutineScope {
        // Очистка massGrid
        for (x in massGrid.indices) {
            val row = massGrid[x]
            for (y in row.indices) {
                row[y] = 0f
            }
        }

        // Разбиваем частицы по чанкам
        val totalCount = particleX.size
        val availableProc = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSize = (totalCount / availableProc).coerceAtLeast(1)

        // Локальные массивы для massGrid суммирования
        val localResults = Array(availableProc) {
            Array(config.gridSizeX) { FloatArray(config.gridSizeY) }
        }

        // Формируем список диапазонов частиц для каждого потока
        val ranges = (0 until availableProc).map { p ->
            val start = p * chunkSize
            val end = if (p == availableProc - 1) totalCount else (start + chunkSize).coerceAtMost(totalCount)
            start until end
        }

        // Теперь создаём ровно availableProc задач, каждая обрабатывает свой диапазон
        val massDistributionTasks = ranges.mapIndexed { index, range ->
            async(Dispatchers.Default) {
                val localMass = localResults[index]
                val cwx = cellWidth
                val cwy = cellHeight
                val gXmax = config.gridSizeX - 1
                val gYmax = config.gridSizeY - 1

                for (i in range) {
                    val px = particleX[i]
                    val py = particleY[i]
                    var cx = (px / cwx).toInt()
                    var cy = (py / cwy).toInt()
                    if (cx < 0) cx = 0 else if (cx > gXmax) cx = gXmax
                    if (cy < 0) cy = 0 else if (cy > gYmax) cy = gYmax
                    localMass[cx][cy] += particleM[i]
                }
            }
        }

        massDistributionTasks.awaitAll()


        // Суммируем все локальные результаты в massGrid
        for (x in 0 until config.gridSizeX) {
            val row = massGrid[x]
            for (y in 0 until config.gridSizeY) {
                var sum = 0f
                for (t in 0 until availableProc) {
                    sum += localResults[t][x][y]
                }
                row[y] = sum
            }
        }

        // Вычисляем потенциал
        runParallelGridOperation(0 until config.gridSizeX, 0 until config.gridSizeY, availableProc) { xx, yy ->
            tempPotential[xx][yy] = -config.g * massGrid[xx][yy]
        }

        // Сглаживание потенциала
        repeat(config.potentialSmoothingIterations) {
            runParallelGridOperation(1 until (config.gridSizeX - 1), 1 until (config.gridSizeY - 1), availableProc) { xx, yy ->
                val tp = tempPotential
                potentialGrid[xx][yy] = (
                        tp[xx][yy] +
                                tp[xx + 1][yy] +
                                tp[xx - 1][yy] +
                                tp[xx][yy + 1] +
                                tp[xx][yy - 1]) / 5f
            }

            // Копируем potentialGrid в tempPotential
            for (x in 0 until config.gridSizeX) {
                val src = potentialGrid[x]
                val dst = tempPotential[x]
                for (y in 0 until config.gridSizeY) {
                    dst[y] = src[y]
                }
            }
        }

        // Рассчёт сил и обновление скоростей
        val gXmax = config.gridSizeX - 2
        val gYmax = config.gridSizeY - 2
        val forceTasks = (0 until particleX.size step chunkSize).map {
            async(Dispatchers.Default) {
                val end = (it + chunkSize).coerceAtMost(particleX.size)
                val cwx = cellWidth
                val cwy = cellHeight
                val invDX = invDx
                val invDY = invDy
                val pg = potentialGrid
                for (i in it until end) {
                    val px = particleX[i]
                    val py = particleY[i]

                    var cx = (px / cwx).toInt()
                    var cy = (py / cwy).toInt()
                    if (cx < 1) cx = 1 else if (cx > gXmax) cx = gXmax
                    if (cy < 1) cy = 1 else if (cy > gYmax) cy = gYmax

                    val Vxm1 = pg[cx - 1][cy]
                    val Vxp1 = pg[cx + 1][cy]
                    val Vym1 = pg[cx][cy - 1]
                    val Vyp1 = pg[cx][cy + 1]

                    val dVdx = (Vxp1 - Vxm1) * 0.5f * invDX
                    val dVdy = (Vyp1 - Vym1) * 0.5f * invDY

                    val ax = -dVdx
                    val ay = -dVdy

                    particleVx[i] += ax
                    particleVy[i] += ay
                }
            }
        }
        forceTasks.awaitAll()

        // Обновляем позиции частиц
        val pxTasks = (particleX.indices step chunkSize).map {
            async(Dispatchers.Default) {
                val end = (it + chunkSize).coerceAtMost(particleX.size)
                val ww = config.worldWidth
                val wh = config.worldHeight
                for (i in it until end) {
                    val newX = particleX[i] + particleVx[i]
                    val newY = particleY[i] + particleVy[i]
                    particleX[i] = if (newX < 0f) 0f else if (newX > ww) ww else newX
                    particleY[i] = if (newY < 0f) 0f else if (newY > wh) wh else newY
                }
            }
        }
        pxTasks.awaitAll()
    }

    private suspend fun runParallelGridOperation(
        xRange: IntRange,
        yRange: IntRange,
        processors: Int,
        block: (xx: Int, yy: Int) -> Unit
    ) = coroutineScope {
        val xCount = xRange.count()
        val xChunkSize = (xCount / processors).coerceAtLeast(1)
        val tasks = (xRange step xChunkSize).map { startX ->
            async(Dispatchers.Default) {
                val endX = (startX + xChunkSize - 1).coerceAtMost(xRange.last)
                for (xx in startX..endX) {
                    for (yy in yRange) {
                        block(xx, yy)
                    }
                }
            }
        }
        tasks.awaitAll()
    }

    fun draw(gc: GraphicsContext) {
        // Очищаем экран чёрным цветом
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, gc.canvas.width, gc.canvas.height)

        gc.fill = Color.WHITE
        val scale = min(gc.canvas.width / config.worldWidth, gc.canvas.height / config.worldHeight)

        val pX = particleX
        val pY = particleY
        val pR = particleR

        // Отрисовка всех частиц
        for (i in particleX.indices) {
            val x = pX[i] * scale
            val y = pY[i] * scale
            val r = pR[i]
            gc.fillOval(x, y, r.toDouble(), r.toDouble())
        }

        // Удаление частиц, вышедших за пределы мира
        // Выполняется после отрисовки
        if (config.dropOutOfBounds) {
            var newCount = 0
            val ww = config.worldWidth
            val wh = config.worldHeight

            for (i in particleX.indices) {
                val px = pX[i]
                val py = pY[i]
                // Проверяем, осталась ли частица в пределах мира
                if (px > 0f && px < ww && py > 0f && py < wh) {
                    // Если частица в пределах, переносим её данные в "новую" позицию newCount
                    particleX[newCount] = px
                    particleY[newCount] = py
                    particleVx[newCount] = particleVx[i]
                    particleVy[newCount] = particleVy[i]
                    particleM[newCount] = particleM[i]
                    particleR[newCount] = particleR[i]
                    newCount++
                }
            }

            // Сжимаем массивы до newCount, фактически удаляя вышедшие частицы
            particleX = particleX.copyOf(newCount)
            particleY = particleY.copyOf(newCount)
            particleVx = particleVx.copyOf(newCount)
            particleVy = particleVy.copyOf(newCount)
            particleM = particleM.copyOf(newCount)
            particleR = particleR.copyOf(newCount)
        }

    }
}

fun main() {
    Application.launch(SimulationApp::class.java)
}

class SimulationApp : Application() {
    override fun start(primaryStage: Stage) {

        val screenBounds = Screen.getPrimary().bounds
        val screenWidth = screenBounds.height // TODO width
        val screenHeight = screenBounds.height

        val canvas = Canvas(screenWidth, screenHeight)
        val gc = canvas.graphicsContext2D

        val root = StackPane(canvas)
        val scene = Scene(root, screenWidth, screenHeight)

        val config = SimulationConfig(screenBounds)
        val particles = generateCircularOrbitParticles(config)

        val simulation = ParticleMeshSimulation(config, particles)
        simulation.initSimulation()

        primaryStage.title = "Particle Mesh Simulation"
        primaryStage.scene = scene
        primaryStage.isFullScreen = config.fullScreen
        primaryStage.show()


        // Добавляем обработчик клика левой кнопкой мыши
        scene.setOnMouseClicked {
            val closestIndex = findClosestParticle(simulation, it.x.toFloat(), it.y.toFloat(), canvas.width, canvas.height)
            when (it.button) {
                MouseButton.PRIMARY -> simulation.setParticleMass(closestIndex, 100_000f)
                MouseButton.SECONDARY -> simulation.setParticleMass(closestIndex, -100_000f)
                else -> simulation.initSimulation()
            }
        }

        object : AnimationTimer() {
            val scope = CoroutineScope(Dispatchers.Default)
            override fun handle(now: Long) {
                println("N size: ${simulation.particleX.size}") // TODO
                scope.launch {
                    simulation.step()
                    withContext(Dispatchers.JavaFx) {
                        simulation.draw(gc)
                    }
                }
            }
        }.start()
    }
}

fun generateCircularOrbitParticles(config: SimulationConfig): List<Particle> {
    val particles = mutableListOf<Particle>()
    val doubleMin = config.minRadius.toDouble()
    val doubleMax = config.maxRadius.toDouble()

    // Сначала генерируем позиции и массы
    var sumM = 0.0
    var sumMx = 0.0
    var sumMy = 0.0

    for (i in 0 until config.count) {
        val r = Random.nextDouble(doubleMin, doubleMax).toFloat()
        val angle = Random.nextDouble(0.0, 2.0 * Math.PI)

        val px = config.centerX + r * cos(angle).toFloat()
        val py = config.centerY + r * sin(angle).toFloat()

        val m = 1f//Random.nextInt(config.massFrom, config.massUntil).toFloat()
        val pr = 1f//sqrt(m)

        particles.add(Particle(px, py, 0f, 0f, m, pr))
        sumM += m
        sumMx += m * px
        sumMy += m * py
    }

    val totalMass = sumM.toFloat()
    val centerMassX = (sumMx / sumM).toFloat()
    val centerMassY = (sumMy / sumM).toFloat()

    // Применяем скорости
    val g = config.g
    val magicConst = (2.05).toFloat() // TODO должно зависить от maxRadius
    for (i in particles.indices) {
        val p = particles[i]
        val dx = p.x - centerMassX
        val dy = p.y - centerMassY
        val dist = hypot(dx, dy)
        if (dist > 0f) {
            val v = sqrt(g * totalMass / dist)
            val ux = dx / dist
            val uy = dy / dist
            val vx = -uy * v
            val vy = ux * v
            particles[i] = Particle(p.x, p.y, vx * magicConst, vy * magicConst, p.m, p.r)
        } else {
            particles[i] = Particle(p.x, p.y, 0f, 0f, p.m, p.r)
        }
    }

    return particles
}

fun findClosestParticle(
    simulation: ParticleMeshSimulation,
    x: Float,
    y: Float,
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
}