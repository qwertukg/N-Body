package kz.qwertukg.nBody

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kz.qwertukg.nBody.nBodyParticleMesh.Particle
import kz.qwertukg.nBody.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBody.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBody.nBodyParticleMesh.fromJson
import kz.qwertukg.nBody.nBodyParticleMesh.generator.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.stb.STBTTBakedChar
import org.lwjgl.stb.STBTruetype.stbtt_BakeFontBitmap
import org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad
import org.lwjgl.system.MemoryStack
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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

    generator.generate(GLFW_KEY_Y.toString()).apply { simulation.initSimulation(this) }
    simulation.stepWithFFT()
    simulation.setCircularOrbitsAroundCenterOfMassDirect()

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
    val points = updatePoints(simulation, scale)

    if (!glfwInit()) {
        throw IllegalStateException("Не удалось инициализировать GLFW")
    }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(w, h, "3D Точки с геометрическим шейдером", if (config.isFullScreen) glfwGetPrimaryMonitor() else 0, NULL)
    if (window == NULL) {
        throw RuntimeException("Не удалось создать окно")
    }

    glfwWindowHint(GLFW_SAMPLES, 4) // 4x MSAA

    glfwMakeContextCurrent(window)
    glfwSwapInterval(0)
    createCapabilities()

    val vao = glGenVertexArrays()
    val vbo = glGenBuffers()

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    glBufferData(GL_ARRAY_BUFFER, points.size * java.lang.Float.BYTES.toLong(), GL_DYNAMIC_DRAW)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(0)
    glBindVertexArray(0)

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

    glfwSetKeyCallback(window) { _, key, _, action, _ ->
        if (action == GLFW_PRESS) {
            val dtStep = 0.1f
            when (key) {
                GLFW_KEY_ESCAPE -> System.exit(0)
                GLFW_KEY_UP ->  config.dt += dtStep
                GLFW_KEY_DOWN -> config.dt -= dtStep
                GLFW_KEY_SPACE -> {
                    config.minRadius = Random.nextFloat() * (config.worldSize * 0.5)
                    config.maxRadius = Random.nextFloat() * (config.worldSize * 0.5) + config.minRadius
                    generator.generate(generator.current).apply { simulation.initSimulation(this) }
                    runBlocking {
                        simulation.stepWithFFT()
                    }
                    simulation.setCircularOrbitsAroundCenterOfMassDirect()
                }
                else -> {
                    generator.generate(key.toString()).apply { simulation.initSimulation(this) }
                    runBlocking {
                        simulation.stepWithFFT()
                    }
                    simulation.setCircularOrbitsAroundCenterOfMassDirect()
                }
            }
        }
    }

    glfwSetScrollCallback(window) { _, _, yoffset ->
        camZ = (camZ - yoffset.toFloat() * camZStep).coerceIn(zNear, zFar)
    }

    while (!glfwWindowShouldClose(window)) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        simulation.stepWithFFT()
        val updatedPoints = updatePoints(simulation, scale)

        // Вычисляем положение камеры на орбите
        val cameraPos = Vector3f(
            camZ * cos(camAngleY) * sin(camAngleX),
            camZ * sin(camAngleY),
            camZ * cos(camAngleY) * cos(camAngleX)
        )

        val viewMatrix = Matrix4f()
            .lookAt(cameraPos, Vector3f(0f, 0f, 0f), Vector3f(0f, 1f, 0f))
        viewMatrix.get(viewArray)

        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val mappedBuffer = glMapBufferRange(
            GL_ARRAY_BUFFER,
            0,
            updatedPoints.size * java.lang.Float.BYTES.toLong(),
            GL_MAP_WRITE_BIT or GL_MAP_INVALIDATE_BUFFER_BIT
        )?.asFloatBuffer()
        mappedBuffer?.put(updatedPoints)?.flip()
        glUnmapBuffer(GL_ARRAY_BUFFER)

        glUseProgram(shaderProgram)

        glUniformMatrix4fv(projectionLocation, false, projectionArray)
        glUniformMatrix4fv(viewLocation, false, viewArray)
        glUniform1f(pointSizeLocation, pointSize)
        glUniform1f(zNearLocation, zNear)
        glUniform1f(zFarLocation, zFar)
        glUniform1f(wLocation, w.toFloat())
        glUniform1f(hLocation, h.toFloat())

        glBindVertexArray(vao)
        glDrawArrays(GL_POINTS, 0, points.size / 3)

        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    glDeleteProgram(shaderProgram)

    glfwDestroyWindow(window)
    glfwTerminate()
}