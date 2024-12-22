import kotlinx.coroutines.*
import kz.qwertukg.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyParticleMesh.SimulationConfig
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL32.GL_PROGRAM_POINT_SIZE
import kotlin.math.max

class SimulationAppLWJGL3(private val simulation: ParticleMeshSimulation) {
    private var window: Long = 0
    private val width = 1400
    private val height = 1400
    private val simulationScope = CoroutineScope(Dispatchers.Default)
    private var simulationJob: Job? = null

    // Параметры симуляции
    private val maxDimension = max(
        simulation.config.worldWidth,
        max(simulation.config.worldHeight, simulation.config.worldDepth)
    )
    private val scaleFactor = maxDimension / 2.0f
    private val zCameraPosition = maxDimension * 1.5f // Камера дальше от центра

    // Рендерер частиц
    private val particleRenderer = ParticleRenderer3(width, height, scaleFactor, zCameraPosition, maxDimension)

    fun start() {
        initWindow()
        initOpenGL()

        particleRenderer.init(simulation.config)

        // Основной цикл
        while (!glfwWindowShouldClose(window)) {
            updateSimulation()
            renderFrame()
        }

        cleanup()
    }

    private fun initWindow() {
        if (!glfwInit()) throw IllegalStateException("Не удалось инициализировать GLFW")

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        window = glfwCreateWindow(width, height, "LWJGL kz.qwertukg.nBodyPM.Particle Simulation", 0, 0)
        if (window == 0L) throw RuntimeException("Не удалось создать GLFW окно")

        glfwMakeContextCurrent(window)
        glfwSwapInterval(0)
        glfwShowWindow(window)
    }

    private fun initOpenGL() {
        GL.createCapabilities()
        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        println("OpenGL Version: ${glGetString(GL_VERSION)}")
        println("GLSL Version: ${glGetString(GL_SHADING_LANGUAGE_VERSION)}")
    }

    private fun updateSimulation() {
        if (simulationJob?.isActive != true) {
            simulationJob = simulationScope.launch { simulation.step() }
        }
    }

    private fun renderFrame() {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glClearColor(0f, 0f, 0f, 1f)

        particleRenderer.updateData(simulation)
        particleRenderer.draw()

        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    private fun cleanup() {
        particleRenderer.destroy()
        glfwDestroyWindow(window)
        glfwTerminate()
    }
}

class ParticleRenderer3(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val scaleFactor: Float,
    private val zCameraPosition: Float,
    private val maxDimension: Float
) {
    private var programId = 0
    private var vaoId = 0
    private var vboId = 0
    private var particleCount = 0
    private val projectionMatrix = FloatArray(16)

    fun init(config: SimulationConfig) {
        programId = createShaderProgram(VERTEX_SHADER_SRC, FRAGMENT_SHADER_SRC)

        glUseProgram(programId)
        val uProjectionLoc = glGetUniformLocation(programId, "uProjection")

        createPerspectiveMatrix(
            fovY = 45f,
            aspect = screenWidth.toFloat() / screenHeight.toFloat(),
            near = 0.1f,
            far = zCameraPosition + maxDimension,
            outM = projectionMatrix
        )

        uploadMatrix(uProjectionLoc, projectionMatrix)

        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 4 * 4, 0L)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 1, GL_FLOAT, false, 4 * 4, (3 * 4).toLong())
        glEnableVertexAttribArray(1)

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
        glEnable(GL_PROGRAM_POINT_SIZE)
    }

    fun updateData(simulation: ParticleMeshSimulation) {
        particleCount = simulation.particleX.size
        if (particleCount == 0) return

        val buffer = BufferUtils.createFloatBuffer(particleCount * 4)
        val worldCenterX = simulation.config.worldWidth / 2
        val worldCenterY = simulation.config.worldHeight / 2
        val worldCenterZ = simulation.config.worldDepth / 2

        for (i in 0 until particleCount) {
            buffer.put((simulation.particleX[i] - worldCenterX) / scaleFactor)
            buffer.put((simulation.particleY[i] - worldCenterY) / scaleFactor)
            buffer.put((simulation.particleZ[i] - worldCenterZ) / scaleFactor)
            buffer.put(max(simulation.particleR[i] / scaleFactor * 500.0f, 2.0f)) // Минимальный размер 2
        }

        buffer.flip()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    fun draw() {
        if (particleCount == 0) return

        glUseProgram(programId)
        glBindVertexArray(vaoId)
        glDrawArrays(GL_POINTS, 0, particleCount)
        glBindVertexArray(0)
        glUseProgram(0)
    }

    fun destroy() {
        glDeleteBuffers(vboId)
        glDeleteVertexArrays(vaoId)
        glDeleteProgram(programId)
    }

    private fun createPerspectiveMatrix(
        fovY: Float, aspect: Float, near: Float, far: Float, outM: FloatArray
    ) {
        val tanHalfFovy = kotlin.math.tan((fovY * Math.PI / 180.0 / 2.0)).toFloat()
        outM.fill(0f)
        outM[0] = 1f / (aspect * tanHalfFovy)
        outM[5] = 1f / tanHalfFovy
        outM[10] = -(far + near) / (far - near)
        outM[11] = -1f
        outM[14] = -(2f * far * near) / (far - near)
    }

    private fun uploadMatrix(location: Int, matrix: FloatArray) {
        val buffer = BufferUtils.createFloatBuffer(16)
        buffer.put(matrix).flip()
        glUniformMatrix4fv(location, false, buffer)
    }

    private fun createShaderProgram(vsSrc: String, fsSrc: String): Int {
        val vs = glCreateShader(GL_VERTEX_SHADER).apply {
            glShaderSource(this, vsSrc)
            glCompileShader(this)
        }

        val fs = glCreateShader(GL_FRAGMENT_SHADER).apply {
            glShaderSource(this, fsSrc)
            glCompileShader(this)
        }

        val program = glCreateProgram().apply {
            glAttachShader(this, vs)
            glAttachShader(this, fs)
            glLinkProgram(this)
        }

        glDeleteShader(vs)
        glDeleteShader(fs)
        return program
    }

    companion object {
        private const val VERTEX_SHADER_SRC = """
            #version 460 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in float aSize;
            uniform mat4 uProjection;
            void main() {
                gl_Position = uProjection * vec4(aPos, 1.0);
                gl_PointSize = aSize;
            }
        """

        private const val FRAGMENT_SHADER_SRC = """
            #version 460 core
            out vec4 FragColor;
            void main() {
                FragColor = vec4(1.0, 1.0, 1.0, 1.0);
            }
        """
    }
}

fun main() {
    val config = SimulationConfig()
    val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)

    val simulation = ParticleMeshSimulation(config)
    simulation.initSimulation(particles)

    SimulationAppLWJGL3(simulation).start()
}
