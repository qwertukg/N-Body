import kotlinx.coroutines.runBlocking
import kz.qwertukg.nBodyApp.checkProgramLinkStatus
import kz.qwertukg.nBodyApp.checkShaderCompileStatus
import kz.qwertukg.nBodyApp.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBodyApp.old.init
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import org.joml.Matrix4f

fun updatePoints(simulation: ParticleMeshSimulation, scale: Float): FloatArray {
    val x = simulation.particleX
    val y = simulation.particleY
    val z = simulation.particleZ

    if (x.size != y.size || y.size != z.size) {
        throw IllegalArgumentException("Все массивы должны быть одинаковой длины")
    }

    val updatedPoints = FloatArray(x.size * 3)
    for (i in x.indices) {
        updatedPoints[i * 3] = (x[i] - simulation.config.worldWidth / 2) / scale
        updatedPoints[i * 3 + 1] = (y[i] - simulation.config.worldHeight / 2) / scale
        updatedPoints[i * 3 + 2] = (z[i] - simulation.config.worldDepth / 2) / scale
    }

    return updatedPoints
}

suspend fun main() = runBlocking {
    if (!glfwInit()) {
        throw IllegalStateException("Не удалось инициализировать GLFW")
    }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(800, 600, "3D Точки с геометрическим шейдером", NULL, NULL)
    if (window == NULL) {
        throw RuntimeException("Не удалось создать окно")
    }

    //glfwWindowHint(GLFW_SAMPLES, 4) // 4x MSAA

    glfwMakeContextCurrent(window)
    glfwSwapInterval(0)
    createCapabilities()

    val config = SimulationConfig()
    val simulation = init(config)

    val scale = 1000000f
    val points = updatePoints(simulation, scale)

    // Создание VAO и VBO
    val vao = glGenVertexArrays()
    val vbo = glGenBuffers()

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    glBufferData(GL_ARRAY_BUFFER, points.size * java.lang.Float.BYTES.toLong(), GL_DYNAMIC_DRAW)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(0)
    glBindVertexArray(0)

    // Включение теста глубины
    glEnable(GL_DEPTH_TEST)

    // Матрицы
    val projectionMatrix = Matrix4f().perspective(
        Math.toRadians(45.0).toFloat(),
        800f / 600f,
        0.1f,
        10.0f
    )

    val viewMatrix = Matrix4f().lookAt(
        0f, 0f, 3f,  // Позиция камеры
        0f, 0f, 0f,  // Центр сцены
        0f, 1f, 0f   // Направление "вверх"
    )

    // Шейдеры
    val vertexShaderSource = """
        #version 460 core
        layout(location = 0) in vec3 aPos;
        uniform mat4 projection;
        uniform mat4 view;

        void main() {
            gl_Position = projection * view * vec4(aPos, 1.0);
        }
    """.trimIndent()

    val geometryShaderSource = """
        #version 460 core
        layout(points) in;
        layout(triangle_strip, max_vertices = 130) out; // Рассчитываем max_vertices для edges = 64
        
        const float pointSize = 0.005; // Радиус круга
        const int edges = 64; // Количество граней
        
        void main() {
            vec4 center = gl_in[0].gl_Position; // Центр точки
        
            // Эмитируем вершины круга
            for (int i = 0; i <= edges; ++i) {
                // Текущий угол
                float angle = 2.0 * 3.14159265359 * float(i) / float(edges);
        
                // Рассчитываем смещение для вершин
                float xOffset = cos(angle) * pointSize * 6/8; // h/w для выравнивания круга
                float yOffset = sin(angle) * pointSize;
        
                // Добавляем центральную вершину (для каждого треугольника)
                gl_Position = center;
                EmitVertex();
        
                // Добавляем вершину на окружности
                gl_Position = center + vec4(xOffset, yOffset, 0.0, 0.0);
                EmitVertex();
            }
        
            EndPrimitive();
        }
    """.trimIndent()

    val fragmentShaderSource = """
        #version 460 core
        out vec4 FragColor;

        void main() {
            FragColor = vec4(1.0, 1.0, 1.0, 1.0); // Белый цвет
        }
    """.trimIndent()

    val vertexShader = glCreateShader(GL_VERTEX_SHADER)
    glShaderSource(vertexShader, vertexShaderSource)
    glCompileShader(vertexShader)
    checkShaderCompileStatus(vertexShader)

    val geometryShader = glCreateShader(GL_GEOMETRY_SHADER)
    glShaderSource(geometryShader, geometryShaderSource)
    glCompileShader(geometryShader)
    checkShaderCompileStatus(geometryShader)

    val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(fragmentShader, fragmentShaderSource)
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

    // Получение uniform-локаций
    val projectionLocation = glGetUniformLocation(shaderProgram, "projection")
    val viewLocation = glGetUniformLocation(shaderProgram, "view")
    val pointSizeLocation = glGetUniformLocation(shaderProgram, "pointSize")

    val projectionArray = FloatArray(16)
    val viewArray = FloatArray(16)
    projectionMatrix.get(projectionArray)
    viewMatrix.get(viewArray)

    // Включение MSAA в OpenGL
    //glEnable(GL_MULTISAMPLE)

    val maxSamples = glGetInteger(GL_MAX_SAMPLES)
    println("Максимальное количество MSAA сэмплов: $maxSamples")
    val isMultisampleEnabled = glIsEnabled(GL_MULTISAMPLE)
    println("MSAA включено: $isMultisampleEnabled")
    val numSamples = glGetInteger(GL_SAMPLES)
    println("Количество MSAA сэмплов в текущем контексте: $numSamples")

    while (!glfwWindowShouldClose(window)) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        simulation.step()
        val updatedPoints = updatePoints(simulation, scale)

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
        glUniform1f(pointSizeLocation, 0.01f) // Размер точки

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
