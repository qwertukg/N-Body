import javafx.scene.transform.Scale
import kotlinx.coroutines.*
import kz.qwertukg.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyParticleMesh.SimulationConfig
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL11.*
import kotlin.math.tan

fun clamp(value: Float, min: Float, max: Float): Float {
    return when {
        value < min -> min
        value > max -> max
        else -> value
    }
}

fun init(config: SimulationConfig): ParticleMeshSimulation {
    val particles = generateParticlesCircle(config,
        config.centerX,
        config.centerY,
        config.centerZ
    )
    return ParticleMeshSimulation(config).apply {
        initSimulation(particles)
    }
}

suspend fun main() = runBlocking {
    // Simulation init
    var config = SimulationConfig()
    var simulation = init(config)
    //simulation.step()

    // Инициализация GLFW
    if (!glfwInit()) {
        throw IllegalStateException("Не удалось инициализировать GLFW")
    }

    // Создаем окно
    val monitor =  glfwGetPrimaryMonitor()
    val window = glfwCreateWindow(config.screenW, config.screenH, "LWJGL3 Cube",  monitor, 0)
    if (window == 0L) {
        throw RuntimeException("Не удалось создать окно GLFW")
    }

    // Устанавливаем контекст текущего окна
    glfwMakeContextCurrent(window)
    glfwShowWindow(window)
    createCapabilities() // Инициализация OpenGL для текущего окна

    // Настройки OpenGL
    glEnable(GL_DEPTH_TEST) // Включаем тест глубины, чтобы правильно отображать объекты в 3D

    // Устанавливаем обработчик клавиш
    var zoom = 0.2f
    val depth = 50.0
    val step = 0.1f
    glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
        if (action == GLFW_PRESS) when (key) {
            GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true) // Закрытие окна
            GLFW_KEY_SPACE -> {
                config = SimulationConfig()
                simulation = init(config)
            }
        }

        if (action == GLFW_REPEAT) when (key) {
            GLFW_KEY_W -> if (zoom > 0.2) zoom -= step
            GLFW_KEY_S -> if (zoom < depth) zoom += step
        }

    }

    // Основной цикл программы
    var angle = 0.0f // Угол вращения
    while (!glfwWindowShouldClose(window)) { // Проверяем, не запросил ли пользователь закрытие окна
        // Очистка экрана и буфера глубины
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT) // Очищаем цвет и глубину

        // Устанавливаем проекционную матрицу
        glMatrixMode(GL_PROJECTION) // Переход в режим матрицы проекции
        glLoadIdentity() // Сбрасываем матрицу
        gluPerspective(depth, config.screenW / config.screenH.toDouble(), 0.1, depth*2) // Устанавливаем перспективную проекцию

        // Устанавливаем модельно-видовую матрицу
        glMatrixMode(GL_MODELVIEW) // Переход в режим модельно-видовой матрицы
        glLoadIdentity() // Сбрасываем матрицу
        glTranslatef(0.0f, 0.0f, -zoom) // Сдвигаем "камеру" на 5 единиц назад

        // Вращение куба
        angle += 0.1f//speed * deltaTime
        if (angle >= 360.0f) angle = 0f
        glRotatef(angle, 0.0f, 1.0f, 0.0f)
        // Параметры: угол (в градусах), оси вращения x, y, z


        // Рисуем куб
        simulation.step()

        glBegin(GL_POINTS)
        glColor3f(1f, 1f, 1f)
        drawTrianglesAsync(simulation, 2000000f)

        // Добавить оставшиеся грани здесь (левая, правая, верхняя, нижняя)
        glEnd() // Завершаем рисование куба

        // Меняем буферы местами (для отображения следующего кадра)
        glfwSwapBuffers(window)

        // Обрабатываем события (например, нажатия клавиш или движение мыши)
        glfwPollEvents()
    }

    // Закрываем и уничтожаем окно
    glfwDestroyWindow(window)
    glfwTerminate() // Завершаем работу GLFW
}

// Функция для задания перспективной проекции
fun gluPerspective(fovY: Double, aspect: Double, zNear: Double, zFar: Double) {
    // fovY: угол обзора по вертикали в градусах
    // aspect: соотношение сторон окна (ширина / высота)
    // zNear: минимальное расстояние до видимой области (ближняя плоскость отсечения)
    // zFar: максимальное расстояние до видимой области (дальняя плоскость отсечения)

    val fH = tan(fovY / 360 * Math.PI).toFloat() * zNear // Вычисляем высоту ближней плоскости
    val fW = fH * aspect // Вычисляем ширину ближней плоскости
    glFrustum(-fW, fW, -fH, fH, zNear, zFar) // Устанавливаем параметры отсечения
}

suspend fun drawTrianglesAsync(simulation: ParticleMeshSimulation, scale: Float) = coroutineScope {
    val totalIterations = simulation.particleX.size
    val availableCores = Runtime.getRuntime().availableProcessors() // Получаем количество ядер
    val chunkSize = totalIterations / availableCores // Распределяем задачи равномерно по ядрам
    val jobs = mutableListOf<Job>()

    val paddingX = simulation.config.worldWidth / 2 / scale
    val paddingY = simulation.config.worldHeight / 2 / scale
    val paddingZ = simulation.config.worldDepth / 2 / scale
    for (i in 0 until totalIterations step chunkSize) {
        jobs.add(launch {
            val end = minOf(i + chunkSize, totalIterations)
            for (j in i until end) {
                val x = (simulation.particleX[j] / scale) - paddingX
                val y = (simulation.particleY[j] / scale) - paddingY
                val z = (simulation.particleZ[j] / scale) - paddingZ

                glVertex3f(x, y ,z)
            }
        })
    }

    // Дождаться завершения всех задач
    jobs.forEach { it.join() }
}

