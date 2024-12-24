import kotlinx.coroutines.*
import kz.qwertukg.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyParticleMesh.SimulationConfig
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL11.*
import java.lang.Math.toRadians
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

fun mapToRange(value: Float, oldMin: Float, oldMax: Float, newMin: Float, newMax: Float): Float {
    // Обратная интерполяция (находим пропорцию в старом диапазоне)
    val proportion = (value - oldMin) / (oldMax - oldMin)
    // Линейная интерполяция в новый диапазон
    return newMin + proportion * (newMax - newMin)
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

var prevX = 0.0
var prevY = 0.0
var dx: Float = 0f
var dy: Float = 0f
var isDrag = false
var zoom = 0.2f
val depth = 50.0
val step = 0.1f
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
    val window = glfwCreateWindow(config.screenW, config.screenH, "LWJGL3 Cube",
        if (config.isFullScreen) glfwGetPrimaryMonitor() else 0, 0)
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
    glfwSetScrollCallback(window) { _, xoffset, yoffset ->
        // Обработка изменения прокрутки
        zoom -= yoffset.toFloat() * step
        zoom = zoom.coerceIn(0.1f, 5.0f) // Ограничиваем zoom между 0.1 и 2.0
    }


    // Устанавливаем обработчик событий мыши (нажатие и отпускание кнопок)
    glfwSetMouseButtonCallback(window) { _, button, action, _ ->
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW_PRESS) {
                isDrag = true
                val cursorPos = glfwGetCursorPos(window)
                prevX = cursorPos.first
                prevY = cursorPos.second
            }
            if (action == GLFW_RELEASE) {
                isDrag = false
            }
        }
    }


    // Устанавливаем обработчик перемещения мыши
    glfwSetCursorPosCallback(window) { _, xpos, ypos ->
        if (isDrag) {
            // Вычисляем разницу между текущими и предыдущими координатами
            dx += (xpos - prevX).toFloat() / 10
            dy += (ypos - prevY).toFloat() / 10

            // Сохраняем текущие координаты как предыдущие
            prevX = xpos
            prevY = ypos
        }
    }

    glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
        if (action == GLFW_PRESS) when (key) {
            GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true) // Закрытие окна
            GLFW_KEY_SPACE -> {
                val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
            GLFW_KEY_Z -> {
                val particles = generateParticlesBox(config)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
            GLFW_KEY_X -> {
                val particles = generateParticlesTorus(config, config.centerX, config.centerY, config.centerZ)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
            GLFW_KEY_C -> {
                val particles = generateParticlesDiskXY(config, config.centerX, config.centerY, config.centerZ)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
            GLFW_KEY_V -> {
                val particles = generateParticlesDiskXZ(config, config.centerX, config.centerY, config.centerZ)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
        }
    }


    // Основной цикл программы
    while (!glfwWindowShouldClose(window)) { // Проверяем, не запросил ли пользователь закрытие окна
        // Очистка экрана и буфера глубины
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT) // Очищаем цвет и глубину

        // Устанавливаем проекционную матрицу
        glMatrixMode(GL_PROJECTION) // Переход в режим матрицы проекции
        glLoadIdentity() // Сбрасываем матрицу
        gluPerspective(depth, config.screenW / config.screenH.toDouble(), 0.01, depth*2) // Устанавливаем перспективную проекцию

        // Устанавливаем модельно-видовую матрицу
        glMatrixMode(GL_MODELVIEW) // Переход в режим модельно-видовой матрицы
        glLoadIdentity() // Сбрасываем матрицу
        glTranslatef(0.0f, 0.0f, -zoom) // Сдвигаем "камеру" на 5 единиц назад

        glRotatef(dy, 1.0f, 0.0f, 0.0f)
        glRotatef(dx, 0.0f, 1.0f, 0.0f)

        simulation.step()

        glBegin(GL_POINTS)
        drawTrianglesAsync(simulation, 2000000f, Triple(0f, 0f, zoom))

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

fun glfwGetCursorPos(window: Long): Pair<Double, Double> {
    val xpos = DoubleArray(1)
    val ypos = DoubleArray(1)
    glfwGetCursorPos(window, xpos, ypos)
    return Pair(xpos[0], ypos[0])
}

suspend fun drawTrianglesAsync(simulation: ParticleMeshSimulation, scale: Float, cameraPosition: Triple<Float, Float, Float>) = coroutineScope {
    val totalIterations = simulation.particleX.size
    val availableCores = Runtime.getRuntime().availableProcessors() // Получаем количество ядер
    val chunkSize = totalIterations / availableCores // Распределяем задачи равномерно по ядрам
    val jobs = mutableListOf<Job>()

    val paddingX = simulation.config.worldWidth / 2 / scale
    val paddingY = simulation.config.worldHeight / 2 / scale
    val paddingZ = simulation.config.worldDepth / 2 / scale
    val c = getOriginalCoordinates(cameraPosition.first, cameraPosition.second, cameraPosition.third, dx, dy)

    for (i in 0 until totalIterations step chunkSize) {
        jobs.add(launch {
            val end = minOf(i + chunkSize, totalIterations)
            for (j in i until end) {
                val x = (simulation.particleX[j] / scale) - paddingX
                val y = (simulation.particleY[j] / scale) - paddingY
                val z = (simulation.particleZ[j] / scale) - paddingZ

                // Проверяем, находится ли точка в области видимости
                val aspect = simulation.config.screenW.toFloat() / simulation.config.screenH
                val fovY = depth
                val zNear = 0.01f
                val zFar = zoom * 10 // Устанавливаем дальнюю плоскость в зависимости от zoom

                // Вычисляем размеры видимого объема на текущей глубине
                val fovRad = Math.toRadians(fovY.toDouble()).toFloat()
                val halfHeight = z * tan(fovRad / 2)
                val halfWidth = halfHeight * aspect

                val isOutOfView = z < zNear

                if (isOutOfView) {
                    glColor3f(1f, 0f, 0f) // Красный цвет для точек за пределами видимости
                } else if (simulation.config.isFading) {
                    // Расстояние до камеры
                    val distance = kotlin.math.sqrt(
                        (x - c.first).pow(2) +
                                (y - c.second).pow(2) +
                                (z - c.third).pow(2)
                    )
                    // Нормализуем расстояние для изменения цвета
                    val normalizedDistance = (1 - distance).coerceIn(0.015f, 1f)
                    glColor3f(normalizedDistance, normalizedDistance, normalizedDistance)
                }

                glVertex3f(x, y, z)
            }
        })
    }



    // Дождаться завершения всех задач
    jobs.forEach { it.join() }
}

fun getOriginalCoordinates(
    x: Float, y: Float, z: Float,
    dx: Float, dy: Float
): Triple<Float, Float, Float> {
    // Углы в радианах
    val dxRadians = toRadians(dx.toDouble()).toFloat()
    val dyRadians = toRadians(dy.toDouble()).toFloat()

    // Синусы и косинусы углов
    val sinDx = sin(-dxRadians) // Отрицательный угол для обратного преобразования
    val cosDx = cos(-dxRadians)
    val sinDy = sin(-dyRadians)
    val cosDy = cos(-dyRadians)

    // Вращение вокруг оси X (dy)
    val rotatedX1 = x
    val rotatedY1 = cosDy * y - sinDy * z
    val rotatedZ1 = sinDy * y + cosDy * z

    // Вращение вокруг оси Y (dx)
    val rotatedX2 = cosDx * rotatedX1 + sinDx * rotatedZ1
    val rotatedY2 = rotatedY1
    val rotatedZ2 = -sinDx * rotatedX1 + cosDx * rotatedZ1

    return Triple(rotatedX2, rotatedY2, rotatedZ2)
}
