import kotlinx.coroutines.runBlocking
import kz.qwertukg.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyParticleMesh.SimulationConfig
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import org.joml.Matrix4f

// Simulation init
val config = SimulationConfig()
val simulation = init(config)

fun updatePoints(simulation: ParticleMeshSimulation, scale: Float): FloatArray {
    val x = simulation.particleX
    val y = simulation.particleY
    val z = simulation.particleZ

    if (x.size != y.size || y.size != z.size) {
        throw IllegalArgumentException("Все массивы должны быть одинаковой длины")
    }

    val updatedPoints = FloatArray(x.size * 3)
    for (i in x.indices) {
        updatedPoints[i * 3] = x[i] / scale
        updatedPoints[i * 3 + 1] = y[i] / scale
        updatedPoints[i * 3 + 2] = z[i] / scale
    }

    return updatedPoints
}

suspend fun main() = runBlocking {
    // Инициализация GLFW
    if (!glfwInit()) {
        throw IllegalStateException("Не удалось инициализировать GLFW")
    }

    // Настройка окна
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(800, 600, "8 точек куба с перспективой", NULL, NULL)
    if (window == NULL) {
        throw RuntimeException("Не удалось создать окно")
    }

    glfwMakeContextCurrent(window)
    glfwSwapInterval(0)
    createCapabilities()

    val scale = 1000000f
    val points = updatePoints(simulation, scale)

    // Создание VAO и VBO
    val vao = glGenVertexArrays()
    val vbo = glGenBuffers()

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    // Выделяем память для буфера
    glBufferData(GL_ARRAY_BUFFER, points.size * java.lang.Float.BYTES.toLong(), GL_DYNAMIC_DRAW)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(0)
    glBindVertexArray(0)

    // Создание матрицы перспективной проекции
    val projection = Matrix4f().perspective(
        Math.toRadians(45.0).toFloat(),
        800f / 600f,
        0.1f,
        10.0f
    )

    val view = Matrix4f().lookAt(
        0f, 0f, 3f,
        0f, 0f, 0f,
        0f, 1f, 0f
    )
    val projectionView = Matrix4f().set(projection).mul(view)

    // Вершинный шейдер
    val vertexShaderSource = """
        #version 460 core
        layout(location = 0) in vec3 aPos;
        uniform mat4 projection;

        void main() {
            gl_Position = projection * vec4(aPos, 1.0);
            gl_PointSize = 20.0;
        }
    """.trimIndent()

    // Фрагментный шейдер
    val fragmentShaderSource = """
        #version 460 core
        out vec4 FragColor;

        void main() {
            FragColor = vec4(1.0, 1.0, 1.0, 1.0);
        }
    """.trimIndent()

    // Компиляция шейдеров
    val vertexShader = glCreateShader(GL_VERTEX_SHADER)
    glShaderSource(vertexShader, vertexShaderSource)
    glCompileShader(vertexShader)
    checkShaderCompileStatus(vertexShader)

    val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(fragmentShader, fragmentShaderSource)
    glCompileShader(fragmentShader)
    checkShaderCompileStatus(fragmentShader)

    // Создание шейдерной программы
    val shaderProgram = glCreateProgram()
    glAttachShader(shaderProgram, vertexShader)
    glAttachShader(shaderProgram, fragmentShader)
    glLinkProgram(shaderProgram)
    checkProgramLinkStatus(shaderProgram)

    // Очистка временных шейдеров
    glDeleteShader(vertexShader)
    glDeleteShader(fragmentShader)

    // Массив для передачи матрицы в OpenGL
    val matrixArray = FloatArray(16)
    projectionView.get(matrixArray)

    // Главный цикл рендеринга
    while (!glfwWindowShouldClose(window)) {
        // Очистка экрана
        glClear(GL_COLOR_BUFFER_BIT)

        // Обновление координат точек
        simulation.step()
        val updatedPoints = updatePoints(simulation, scale)

        // Обновление данных через glMapBufferRange
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val mappedBuffer = glMapBufferRange(
            GL_ARRAY_BUFFER,
            0,
            updatedPoints.size * java.lang.Float.BYTES.toLong(),
            GL_MAP_WRITE_BIT or GL_MAP_INVALIDATE_BUFFER_BIT
        )?.asFloatBuffer()
        mappedBuffer?.put(updatedPoints)?.flip()
        glUnmapBuffer(GL_ARRAY_BUFFER)
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        // Используем шейдерную программу
        glUseProgram(shaderProgram)

        // Передача матрицы перспективы
        val projectionUniformLocation = glGetUniformLocation(shaderProgram, "projection")
        glUniformMatrix4fv(projectionUniformLocation, false, matrixArray)

        // Рисуем точки
        glBindVertexArray(vao)
        glDrawArrays(GL_POINTS, 0, points.size / 3)

        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    // Очистка ресурсов
    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    glDeleteProgram(shaderProgram)

    glfwDestroyWindow(window)
    glfwTerminate()
}
