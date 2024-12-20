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
    val count: Int = 300000,
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
    lateinit var particleX: FloatArray
    lateinit var particleY: FloatArray
    lateinit var particleZ: FloatArray
    lateinit var particleVx: FloatArray
    lateinit var particleVy: FloatArray
    lateinit var particleVz: FloatArray
    lateinit var particleM: FloatArray
    lateinit var particleR: FloatArray

    private val massGrid: Array<Array<FloatArray>> = Array(config.gridSizeX) {
        Array(config.gridSizeY) {
            FloatArray(config.gridSizeZ)
        }
    }
    private val potentialGrid: Array<Array<FloatArray>> = Array(config.gridSizeX) {
        Array(config.gridSizeY) {
            FloatArray(config.gridSizeZ)
        }
    }
    private val tempPotential: Array<Array<FloatArray>> = Array(config.gridSizeX) {
        Array(config.gridSizeY) {
            FloatArray(config.gridSizeZ)
        }
    }

    private val cellWidth = config.worldWidth / config.gridSizeX
    private val cellHeight = config.worldHeight / config.gridSizeY
    private val cellDepth = config.worldDepth / config.gridSizeZ

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

    suspend fun step() = coroutineScope {
        // Clear mass grid
        massGrid.forEach { plane -> plane.forEach { row -> row.fill(0f) } }

        val totalCount = particleX.size
        val chunkSize = (totalCount / Runtime.getRuntime().availableProcessors().coerceAtLeast(1)).coerceAtLeast(1)

        // Distribute mass
        (0 until totalCount step chunkSize).map {
            async(Dispatchers.Default) {
                for (i in it until (it + chunkSize).coerceAtMost(totalCount)) {
                    val cx = (particleX[i] / cellWidth).toInt().coerceIn(0, config.gridSizeX - 1)
                    val cy = (particleY[i] / cellHeight).toInt().coerceIn(0, config.gridSizeY - 1)
                    val cz = (particleZ[i] / cellDepth).toInt().coerceIn(0, config.gridSizeZ - 1)
                    massGrid[cx][cy][cz] += particleM[i]
                }
            }
        }.awaitAll()

        // Calculate potential
        for (x in 0 until config.gridSizeX) {
            for (y in 0 until config.gridSizeY) {
                for (z in 0 until config.gridSizeZ) {
                    tempPotential[x][y][z] = -config.g * massGrid[x][y][z]
                }
            }
        }

        // Smooth potential
        repeat(config.potentialSmoothingIterations) {
            for (x in 1 until config.gridSizeX - 1) {
                for (y in 1 until config.gridSizeY - 1) {
                    for (z in 1 until config.gridSizeZ - 1) {
                        potentialGrid[x][y][z] = (
                                tempPotential[x][y][z] +
                                        tempPotential[x + 1][y][z] + tempPotential[x - 1][y][z] +
                                        tempPotential[x][y + 1][z] + tempPotential[x][y - 1][z] +
                                        tempPotential[x][y][z + 1] + tempPotential[x][y][z - 1]
                                ) / 7f
                    }
                }
            }
        }

        // Update velocities and positions
        (0 until totalCount step chunkSize).map {
            async(Dispatchers.Default) {
                for (i in it until (it + chunkSize).coerceAtMost(totalCount)) {
                    val cx = (particleX[i] / cellWidth).toInt().coerceIn(1, config.gridSizeX - 2)
                    val cy = (particleY[i] / cellHeight).toInt().coerceIn(1, config.gridSizeY - 2)
                    val cz = (particleZ[i] / cellDepth).toInt().coerceIn(1, config.gridSizeZ - 2)

                    val dVdx = (potentialGrid[cx + 1][cy][cz] - potentialGrid[cx - 1][cy][cz]) / (2 * cellWidth)
                    val dVdy = (potentialGrid[cx][cy + 1][cz] - potentialGrid[cx][cy - 1][cz]) / (2 * cellHeight)
                    val dVdz = (potentialGrid[cx][cy][cz + 1] - potentialGrid[cx][cy][cz - 1]) / (2 * cellDepth)

                    particleVx[i] -= dVdx
                    particleVy[i] -= dVdy
                    particleVz[i] -= dVdz

                    particleX[i] = (particleX[i] + particleVx[i]).coerceIn(0f, config.worldWidth)
                    particleY[i] = (particleY[i] + particleVy[i]).coerceIn(0f, config.worldHeight)
                    particleZ[i] = (particleZ[i] + particleVz[i]).coerceIn(0f, config.worldDepth)
                }
            }
        }.awaitAll()

        // Удаляем частицы, вышедшие за пределы мира
        dropOutOfBounce()
    }

    fun draw2D(gc: GraphicsContext) {
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, gc.canvas.width, gc.canvas.height)

        val scale = min(gc.canvas.width / config.worldWidth, gc.canvas.height / config.worldHeight)
        val fov = config.fov // Field of view, adjust this value to control perspective depth

        for (i in particleX.indices) {
            val depthFactor = 1.0 / (1.0 + (particleZ[i] / config.worldDepth) * fov)

            // Calculate adjusted screen positions based on depth
            val screenX = ((particleX[i] - config.worldWidth / 2) * depthFactor + config.worldWidth / 2) * scale
            val screenY = ((particleY[i] - config.worldHeight / 2) * depthFactor + config.worldHeight / 2) * scale

            // Apply perspective scaling to size
            val size = particleR[i] * depthFactor

            gc.fill = Color.WHITE
            gc.fillOval(screenX, screenY, size, size)
        }
    }

    suspend fun dropOutOfBounce() = withContext(Dispatchers.Default) {
        // Удаление частиц, вышедших за пределы мира
        // Выполняется после отрисовки
        if (config.isDropOutOfBounds) {
            var newCount = 0
            val ww = config.worldWidth
            val wh = config.worldHeight
            val wd = config.worldDepth

            for (i in particleX.indices) {
                val px = particleX[i]
                val py = particleY[i]
                val pz = particleZ[i]
                // Проверяем, осталась ли частица в пределах мира
                if (px > 0f && px < ww && py > 0f && py < wh && pz > 0f && pz < wd) {
                    // Если частица в пределах, переносим её данные в "новую" позицию newCount
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

            // Сжимаем массивы до newCount, фактически удаляя вышедшие частицы
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

        val particles = generateParticlesCircle(config)
        val simulation = ParticleMeshSimulation(config)
        simulation.initSimulation(particles)

        val root = StackPane(canvas)
        val scene = Scene(root)

        primaryStage.title = "3D Particle Simulation"
        primaryStage.scene = scene
        primaryStage.isFullScreen = config.isFullScreen
        primaryStage.show()

        // Добавляем обработчик клика левой кнопкой мыши
        scene.controller(simulation, canvas)

        object : AnimationTimer() {
            val scope = CoroutineScope(Dispatchers.Default)
            override fun handle(now: Long) {
                println("N: ${simulation.particleVx.size}") // TODO
                scope.launch {
                    simulation.step()

                    withContext(Dispatchers.JavaFx) {
                        simulation.draw2D(gc)
                    }
                }
            }
        }.start()
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

            MouseButton.PRIMARY -> simulation.setParticleMass(closestIndex, 100_000f)
            MouseButton.SECONDARY -> {
                //simulation.setParticleMass(closestIndex, -100_000f)
//                simulation.config.centerX = centerX
//                simulation.config.centerY = centerY
//                simulation.config.centerZ = centerZ
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

}

fun main() {
    Application.launch(SimulationApp::class.java)
}
