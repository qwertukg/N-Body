import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import org.joml.Matrix4f

fun main() {
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
    glfwSwapInterval(1)
    createCapabilities()

    // Массив точек куба
    val points = floatArrayOf(
        // Задняя грань куба
        -0.5f, -0.5f, -0.5f,  // Нижняя левая задняя вершина
        0.5f, -0.5f, -0.5f,  // Нижняя правая задняя вершина
        -0.5f,  0.5f, -0.5f,  // Верхняя левая задняя вершина
        0.5f,  0.5f, -0.5f,  // Верхняя правая задняя вершина

        // Передняя грань куба
        -0.5f, -0.5f,  0.5f,  // Нижняя левая передняя вершина
        0.5f, -0.5f,  0.5f,  // Нижняя правая передняя вершина
        -0.5f,  0.5f,  0.5f,  // Верхняя левая передняя вершина
        0.5f,  0.5f,  0.5f,   // Верхняя правая передняя вершина

        0f,  0f,  0f, // center
    )

    // Создание VAO и VBO
    val vao = glGenVertexArrays()
    val vbo = glGenBuffers()

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glBufferData(GL_ARRAY_BUFFER, points, GL_STATIC_DRAW)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(0)
    glBindVertexArray(0)

    // Создание матрицы перспективной проекции
    val projection = Matrix4f().perspective(
        Math.toRadians(45.0).toFloat(), // Угол обзора
        800f / 600f,                   // Соотношение сторон окна
        0.1f,                          // Ближняя плоскость отсечения
        100.0f                         // Дальняя плоскость отсечения
    )

    // Вершинный шейдер
    val vertexShaderSource = """
        #version 460 core
        layout(location = 0) in vec3 aPos;
        uniform mat4 projection;

        void main() {
            gl_Position = vec4(aPos, 1.0);
        }
    """.trimIndent()

    // Фрагментный шейдер
    val fragmentShaderSource = """
        #version 460 core
        out vec4 FragColor;

        void main() {
            FragColor = vec4(1.0, 1.0, 1.0, 1.0); // Белый цвет
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
    projection.get(matrixArray)

    // Главный цикл рендеринга
    while (!glfwWindowShouldClose(window)) {
        // Очистка экрана
        glClear(GL_COLOR_BUFFER_BIT)

        // Используем шейдерную программу
        glUseProgram(shaderProgram)

        // Передача матрицы перспективы
        val projectionUniformLocation = glGetUniformLocation(shaderProgram, "projection")
        glUniformMatrix4fv(projectionUniformLocation, false, matrixArray)

        // Рисуем точки
        glBindVertexArray(vao)
        glDrawArrays(GL_POINTS, 0, 9)

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
