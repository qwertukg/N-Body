import kotlinx.coroutines.*
import kz.qwertukg.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyParticleMesh.SimulationConfig
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SimulationAppLWJGL {
    private var window: Long = 0
    private val width = 1400
    private val height = 1400
    private val simulationScope = CoroutineScope(Dispatchers.Default)
    private var simulationJob: Job? = null

    fun start(simulation: ParticleMeshSimulation) {
        // Инициализация GLFW
        if (!glfwInit()) {
            throw IllegalStateException("Не удалось инициализировать GLFW")
        }

        // Создание окна
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        window = glfwCreateWindow(width, height, "LWJGL kz.qwertukg.nBodyPM.Particle Simulation", 0, 0)
        if (window == 0L) {
            throw RuntimeException("Не удалось создать GLFW окно")
        }

        glfwMakeContextCurrent(window)
        glfwSwapInterval(0) // Вертикальная синхронизация
        glfwShowWindow(window)

        // Инициализация OpenGL
        GL.createCapabilities()

        // Основной цикл
        while (!glfwWindowShouldClose(window)) {
            // Обновляем состояние симуляции
            updateSimulation(simulation)

            // Очистка экрана
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

            // Рендеринг частиц
            draw2D(simulation)

            // Смена буферов
            glfwSwapBuffers(window)
            glfwPollEvents()
        }

        // Завершение работы
        glfwDestroyWindow(window)
        glfwTerminate()
    }

    private fun updateSimulation(simulation: ParticleMeshSimulation) {
        // Запускаем шаг симуляции в отдельной корутине, если предыдущий шаг завершён
        if (simulationJob?.isActive != true) {
            simulationJob = simulationScope.launch {
                simulation.step()
            }
        }
    }

    private fun draw2D(simulation: ParticleMeshSimulation) {
        // Устанавливаем ортографическую проекцию
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, width.toDouble(), height.toDouble(), 0.0, -1.0, 1.0)

        glMatrixMode(GL_MODELVIEW)
        glLoadIdentity()

        // Активируем сглаживание точек и прозрачность
        glEnable(GL_POINT_SMOOTH)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)



        val scale = min(width / simulation.config.worldWidth, height / simulation.config.worldHeight)
        val halfW = simulation.config.worldWidth * 0.5f
        val halfH = simulation.config.worldHeight * 0.5f
        val depth = simulation.config.worldDepth
        val fov = simulation.config.fov

        for (i in simulation.particleX.indices) {
            val zFactor = 1f / (1f + (simulation.particleZ[i] / depth) * fov)
            val screenX = ((simulation.particleX[i] - halfW) * zFactor + halfW) * scale
            val screenY = ((simulation.particleY[i] - halfH) * zFactor + halfH) * scale
            val size = simulation.particleR[i] * zFactor * scale

            drawWhiteCircle2D(screenX, screenY, size, 4)
        }
    }
}

fun drawWhiteCircle2D(cx: Float, cy: Float, radius: Float, segments: Int) {
    glColor3f(1.0f, 1.0f, 1.0f)
    glBegin(GL_TRIANGLE_FAN)
    glVertex2f(cx, cy)
    for (i in 0..segments) {
        val angle = (2.0 * Math.PI * i / segments).toFloat()
        val x = cx + radius * cos(angle)
        val y = cy + radius * sin(angle)
        glVertex2f(x, y)
    }
    glEnd()
}

// Точка входа
fun main() {
    val config = SimulationConfig()
    val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)

    val simulation = ParticleMeshSimulation(config)
    simulation.initSimulation(particles)

    val app = SimulationAppLWJGL()
    app.start(simulation)
}
