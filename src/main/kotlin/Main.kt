import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.layout.StackPane
import javafx.stage.Screen
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import kz.qwertukg.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyParticleMesh.SimulationConfig
import kotlin.math.*
import kotlin.random.Random

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

        primaryStage.title = "3D kz.qwertukg.nBodyPM.Particle Simulation"
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
    val config = simulation.config

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


    setOnMousePressed { event ->
        if (event.isPrimaryButtonDown) {
            val (dx, dy) = if (!event.isControlDown) screenToWorldXY(event.sceneX, event.sceneY, config.centerZ)
            else config.worldWidth / 2 to config.worldWidth / 2
            generateBlackHole(dx, dy, simulation)
        }

        if (event.isSecondaryButtonDown) {
            val particles = generateParticlesBox(config)
            simulation.initSimulation(particles)
            simulation.config.blackHoleIndex = null
        }
    }

    setOnMouseDragged { event ->
        if (event.isPrimaryButtonDown) {
            val (dx, dy) = screenToWorldXY(event.sceneX, event.sceneY, config.centerZ)
            generateBlackHole(dx, dy, simulation)
        }
    }

    setOnMouseReleased { event ->
            dropBlackHole(simulation)
    }
}

suspend fun main() {
    // Simulation
    /*val config = kz.qwertukg.nBodyPM.SimulationConfig()
    val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)
    val simulation = kz.qwertukg.nBodyPM.ParticleMeshSimulation(config)
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

fun generateParticlesBox(config: SimulationConfig): List<Particle> {
    val particles = MutableList(config.count) {
        val x = Random.nextFloat() * config.worldWidth
        val y = Random.nextFloat() * config.worldHeight
        val z = Random.nextFloat() * config.worldDepth

        val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat() // TODO
        val vx = 0f
        val vy = 0f
        val vz = 0f
        val particleR = sqrt(m) // TODO

        Particle(x, y, z, vx, vy, vz, m, particleR)
    }

    return particles
}

fun generateBlackHole(x: Float, y: Float, simulation: ParticleMeshSimulation) {
    val m = 1_000_000f
    val currentI = simulation.config.blackHoleIndex
    if (currentI == null) {
        val z = simulation.config.worldDepth / 2
        val vx = 0f
        val vy = 0f
        val vz = 0f
        val r = 0.01f
        val particle = Particle(x, y, z, vx, vy, vz, m, r)
        val i = simulation.addParticleToSimulation(particle)
        simulation.config.blackHoleIndex = i
    } else {
        simulation.particleX[currentI] = x
        simulation.particleY[currentI] = y
        simulation.particleM[currentI] = m
        println(simulation.particleM[currentI])
    }
}

fun dropBlackHole(simulation: ParticleMeshSimulation) {
    val currentI = simulation.config.blackHoleIndex
    if (currentI != null) {
        simulation.particleM[currentI] = 0f

    }
}
