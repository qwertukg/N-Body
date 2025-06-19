package kz.qwertukg.nBody

import kotlinx.coroutines.runBlocking
import kz.qwertukg.nBody.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBody.nBodyParticleMesh.fromJson
import kz.qwertukg.nBody.nBodyParticleMesh.generator.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import org.joml.Matrix4f
import org.joml.Vector3f
import java.lang.Byte.BYTES
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

suspend fun main() = runBlocking {
    val config = fromJson("src/main/resources/config.json")
    val simulation = ParticleMeshSimulation(config)
    val generator = Generator(config)
    generator.registerFigure(GLFW_KEY_1.toString(), DiskGenerator())
    generator.registerFigure(GLFW_KEY_2.toString(), MobiusStripGenerator())
    generator.registerFigure(GLFW_KEY_3.toString(), SphereGenerator())
    generator.registerFigure(GLFW_KEY_4.toString(), CubeGenerator())
    generator.registerFigure(GLFW_KEY_5.toString(), CylinderGenerator())
    generator.registerFigure(GLFW_KEY_6.toString(), ConeGenerator())
    generator.registerFigure(GLFW_KEY_7.toString(), TorusGenerator())
    generator.registerFigure(GLFW_KEY_8.toString(), HemisphereGenerator())
    generator.registerFigure(GLFW_KEY_9.toString(), DoubleConeGenerator())
    generator.registerFigure(GLFW_KEY_0.toString(), RidgesCylinderGenerator())
    generator.registerFigure(GLFW_KEY_Q.toString(), PyramidGenerator())
    generator.registerFigure(GLFW_KEY_W.toString(), SineWaveTorusGenerator())
    generator.registerFigure(GLFW_KEY_E.toString(), RandomClustersGenerator())
    generator.registerFigure(GLFW_KEY_R.toString(), RandomNoiseSphereGenerator())
    generator.registerFigure(GLFW_KEY_T.toString(), RandomOrbitsGenerator())
    generator.registerFigure(GLFW_KEY_Y.toString(), OrbitalDiskGenerator())
    generator.registerFigure(GLFW_KEY_U.toString(), CustomGenerator(config.particles))

    val w = simulation.config.screenW
    val h = simulation.config.screenH
    val scale = 2000000f
    val pointSize = 0.0002f
    val zNear = 0.001f
    val zFar = 10f
    var camZ = 0.3f
    var camAngleX = 0.3f
    var camAngleY = -0.99f
    val camZStep = 0.01f
    var isWithSpin = true
    val dtStep = 0.1f

    // initial figure
    generator.generate(generator.figureGenerators.keys.random()).apply { simulation.initSimulation(this) }
    simulation.stepWithFFT()
    if (isWithSpin) simulation.setCircularOrbitsAroundCenterOfMassDirect()

    if (!glfwInit()) {
        throw IllegalStateException("Не удалось инициализировать GLFW")
    }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(w, h, "Gravity simulator based on Particle Mesh Algorithm", if (config.isFullScreen) glfwGetPrimaryMonitor() else 0, NULL)
    if (window == NULL) {
        throw RuntimeException("Не удалось создать окно")
    }

    glfwWindowHint(GLFW_SAMPLES, 4) // 4x MSAA

    glfwMakeContextCurrent(window)
    glfwSwapInterval(0)
    createCapabilities()

    glEnable(GL_PROGRAM_POINT_SIZE)

    // Проверяем, что есть поддержка ARB_buffer_storage
//    require(getCapabilities().GL_ARB_buffer_storage) {
//        "Видео-драйвер не поддерживает GL_ARB_buffer_storage; " +
//                "persist-mapped VBO здесь работать не будет."
//    }

    val vao = glGenVertexArrays()
    val vbo = glGenBuffers()

    /* >>> VBO-BEGIN (кроссплатформенный) <<< */
    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    /* 1) Вычисляем размер буфера: текущее N частиц + 20 % (с запасом) */
    val safety       = (simulation.particleX.size * 4f).toInt()
    val maxParticles = maxOf(safety, simulation.particleX.size + 1_000)
    val bytes        = maxParticles.toLong() * 3L * BYTES  // 3 координаты × 4 байта

    /* 2) Проверяем поддержку persist-mapped VBO на данной платформе */
    val hasPersistent = getCapabilities().GL_ARB_buffer_storage

    /* 3) Создаём буфер и получаем ByteBuffer-view */
    val vboByte: ByteBuffer = if (hasPersistent) {
        /* --- быстрый путь (Windows/Linux, OpenGL ≥ 4.4) --- */
        val storageFlags = GL_DYNAMIC_STORAGE_BIT or
                GL_MAP_WRITE_BIT       or
                GL_MAP_PERSISTENT_BIT  or
                GL_MAP_COHERENT_BIT
        glBufferStorage(GL_ARRAY_BUFFER, bytes, storageFlags)

        val mapFlags = GL_MAP_WRITE_BIT       or
                GL_MAP_PERSISTENT_BIT  or
                GL_MAP_COHERENT_BIT
        glMapBufferRange(GL_ARRAY_BUFFER, 0, bytes, mapFlags, null)
            ?: error("glMapBufferRange вернул null")
    } else {
        /* --- фолбэк для macOS (OpenGL 4.1) --- */
        glBufferData(GL_ARRAY_BUFFER, bytes, GL_DYNAMIC_DRAW)
        memAlloc(bytes.toInt())          // клиент-буфер в RAM
    }

    /* 4) FloatBuffer-view для записи координат */
    val vboFloat = vboByte.order(ByteOrder.nativeOrder()).asFloatBuffer()

    /* 5) Описываем атрибут вершин */
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * BYTES, 0)
    glEnableVertexAttribArray(0)
    glBindVertexArray(0)
    val hasPersistentVbo = hasPersistent
    /* <<< VBO-END (кроссплатформенный) <<< */

    glEnable(GL_DEPTH_TEST)

    val projectionMatrix = Matrix4f().perspective(
        Math.toRadians(45.0).toFloat(),
        w / h.toFloat(),
        zNear,
        zFar
    )

    val vertexShader = glCreateShader(GL_VERTEX_SHADER)
    glShaderSource(vertexShader, VERTEX_SHADER_SRC)
    glCompileShader(vertexShader)
    checkShaderCompileStatus(vertexShader)

    val geometryShader = glCreateShader(GL_GEOMETRY_SHADER)
    glShaderSource(geometryShader, GEOMETRY_SHADER_SRC)
    glCompileShader(geometryShader)
    checkShaderCompileStatus(geometryShader)

    val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(fragmentShader, FRAGMENT_SHADER_SRC)
    glCompileShader(fragmentShader)
    checkShaderCompileStatus(fragmentShader)

    val shaderProgram = glCreateProgram()
    glAttachShader(shaderProgram, vertexShader)
    glAttachShader(shaderProgram, geometryShader)
    glAttachShader(shaderProgram, fragmentShader)
    glLinkProgram(shaderProgram)
    checkProgramLinkStatus(shaderProgram)

    glDeleteShader(vertexShader)
    glDeleteShader(geometryShader)
    glDeleteShader(fragmentShader)

    val projectionLocation = glGetUniformLocation(shaderProgram, "projection")
    val viewLocation = glGetUniformLocation(shaderProgram, "view")
    val pointSizeLocation = glGetUniformLocation(shaderProgram, "pointSize")
    val zNearLocation = glGetUniformLocation(shaderProgram, "zNear")
    val zFarLocation = glGetUniformLocation(shaderProgram, "zFar")
    val wLocation = glGetUniformLocation(shaderProgram, "w")
    val hLocation = glGetUniformLocation(shaderProgram, "h")

    val projectionArray = FloatArray(16)
    val viewArray = FloatArray(16)
    projectionMatrix.get(projectionArray)

    var lastMouseX = 0.0
    var lastMouseY = 0.0
    var isDragging = false

    glfwSetCursorPosCallback(window) { _, xpos, ypos ->
        if (isDragging) {
            val dx = xpos - lastMouseX
            val dy = ypos - lastMouseY
            camAngleX -= dx.toFloat() * 0.005f
            camAngleY += dy.toFloat() * 0.005f
        }
        lastMouseX = xpos
        lastMouseY = ypos
    }

    glfwSetMouseButtonCallback(window) { _, button, action, _ ->
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            isDragging = action == GLFW_PRESS
        }
    }

    // control
    glfwSetKeyCallback(window) { _, key, _, action, _ ->
        if (action == GLFW_PRESS) {

            when (key) {
                GLFW_KEY_ESCAPE -> System.exit(0)
                GLFW_KEY_RIGHT -> simulation.nextFocus()
                GLFW_KEY_LEFT -> simulation.prevFocus()
                GLFW_KEY_X -> isWithBlackHole = !isWithBlackHole
                GLFW_KEY_Z -> isWithSpin = !isWithSpin
                GLFW_KEY_SPACE -> {
//                    focusIndex = 0
//                    config.dt = 1f
                    config.minRadius = max(Random.nextFloat() * (config.worldSize * 0.4f), 0.01f).toDouble()
                    config.maxRadius = Random.nextFloat() * (config.worldSize * 0.4) + config.minRadius
                    generator.generate(generator.current).apply { simulation.initSimulation(this) }
                    if (isWithSpin) simulation.setCircularOrbitsAroundCenterOfMassDirect()
                }
                else -> if (key.toString() in generator.figureGenerators.keys) {
                    generator.generate(key.toString()).apply { simulation.initSimulation(this) }
                    if (isWithSpin) simulation.setCircularOrbitsAroundCenterOfMassDirect()
                }
            }
        }
    }

    glfwSetScrollCallback(window) { _, _, yoffset ->
        camZ = (camZ - yoffset.toFloat() * camZStep).coerceIn(zNear, zFar)
    }

    while (!glfwWindowShouldClose(window)) {

        // speed control
        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) {
            config.dt += dtStep
        }
        if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) {
            config.dt -= dtStep
        }

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        /* 1️⃣ Физика */
        simulation.stepWithFFT()

        /* 2️⃣ Пишем точки прямо в persist-mapped VBO */
        val verts = simulation.writePositionsToVbo(vboFloat, scale)   // ← новая функция

        if (!hasPersistent) {
            /* ❶ Ограничиваем объём передаваемых данных ровно verts*3 float’ов */
            vboFloat.limit(verts * 3)   // limit = нужное кол-во float’ов
            vboFloat.position(0)        // позиция = 0 (rewind без обнуления limit)

            /* ❷ Копируем в видеопамять одной командой */
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            glBufferSubData(GL_ARRAY_BUFFER, 0, vboFloat)   // FloatBuffer overload

            /* ❸ Готовим буфер к следующему кадру */
            vboFloat.clear()            // position = 0, limit = capacity
        }

        /* 3️⃣ Обновляем камеру.
               Координаты первой частицы берём напрямую из симулятора
               и масштабируем так же, как в writePositionsToVbo (÷ scale). */
        val focusPos = if (isWithBlackHole) Vector3f(
            simulation.particleX[focusIndex] / scale,
            simulation.particleY[focusIndex] / scale,
            simulation.particleZ[focusIndex] / scale
        )else Vector3f(
            simulation.config.centerX / scale,
            simulation.config.centerY / scale,
            simulation.config.centerZ / scale
        )

        val cameraPos = Vector3f(
            camZ * cos(camAngleY) * sin(camAngleX),
            camZ * sin(camAngleY),
            camZ * cos(camAngleY) * cos(camAngleX)
        ).add(focusPos)

        val upVec = if (cos(camAngleY) >= 0f) Vector3f(0f, 1f, 0f) else Vector3f(0f, -1f, 0f)

        val viewMatrix = Matrix4f().lookAt(cameraPos, focusPos, upVec)
        viewMatrix.get(viewArray)

        /* 4️⃣ Рендер */
        glUseProgram(shaderProgram)

        glUniformMatrix4fv(projectionLocation, false, projectionArray)
        glUniform1f(pointSizeLocation, pointSize)
        glUniform1f(zNearLocation,  zNear)
        glUniform1f(zFarLocation,   zFar)
        glUniform1f(wLocation,      w.toFloat())
        glUniform1f(hLocation,      h.toFloat())

        glUniformMatrix4fv(viewLocation, false, viewArray)
        glBindVertexArray(vao)
        glDrawArrays(GL_POINTS, 0, verts)        // ← verts = число частиц

        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    glDeleteProgram(shaderProgram)

    glfwDestroyWindow(window)
    glfwTerminate()
}