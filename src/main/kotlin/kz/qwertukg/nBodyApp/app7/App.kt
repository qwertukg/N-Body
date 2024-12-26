package kz.qwertukg.nBodyApp.app7

import kotlinx.coroutines.runBlocking
import kz.qwertukg.nBodyApp.checkProgramLinkStatus
import kz.qwertukg.nBodyApp.checkShaderCompileStatus
import kz.qwertukg.nBodyApp.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBodyApp.old.init
import kz.qwertukg.nBodyApp.updatePoints
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

suspend fun main() = runBlocking {
    val config = SimulationConfig()
    val simulation = init(config)
    val w = simulation.config.screenW
    val h = simulation.config.screenH
    val scale = 2000000f
    val pointSize = 0.0004f
    val zNear = 0.0004f
    val zFar = 10f
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

    val vertexShaderSource = """
        #version 410 core
        layout(location = 0) in vec3 aPos;
        
        uniform mat4 projection;
        uniform mat4 view;
        
        out float fragDistance; // Расстояние до камеры
        
        void main() {
            vec4 viewPosition = view * vec4(aPos, 1.0); // Позиция в пространстве камеры
            fragDistance = -viewPosition.z; // Z отрицательный в пространстве камеры
            gl_Position = projection * viewPosition; // Проекция на экран
        }
    """.trimIndent()

    val geometryShaderSource = """
        #version 410 core
        layout(points) in;
        layout(triangle_strip, max_vertices = 18) out; // edges = 8
        
        uniform float w;         // Ширина экрана
        uniform float h;         // Высота экрана
        uniform float pointSize; // Радиус круга
        
        const int edges = 8; // Количество граней круга
        
        in float fragDistance[]; // Расстояние до камеры
        out float fragDistance2; // Для передачи во фрагментный шейдер
        
        void main() {
            fragDistance2 = fragDistance[0];
            vec4 center = gl_in[0].gl_Position;
        
            float radiusX = pointSize * h / w;
            float radiusY = pointSize;
        
            for (int i = 0; i <= edges; ++i) {
                float angle = i * 2.0 * 3.14159265359 / float(edges);
                vec2 offset = vec2(cos(angle) * radiusX, sin(angle) * radiusY);
        
                gl_Position = center;
                EmitVertex();
        
                gl_Position = center + vec4(offset, 0.0, 0.0);
                EmitVertex();
            }
            EndPrimitive();
        }
    """.trimIndent()

    val fragmentShaderSource = """
        #version 410 core
        
        in float fragDistance2; // Расстояние до камеры
        
        uniform float zNear;
        uniform float zFar;
        
        out vec4 FragColor;
        
        void main() {
            float brightness = 1.0 - clamp((fragDistance2 - zNear) / (zFar - zNear) * 3, 0.0, 1.0);
            FragColor = vec4(vec3(brightness), 1.0);
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

    var camZ = 1f
    var camAngleX = 0f
    var camAngleY = 0f
    val camZStep = 0.1f

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

    glfwSetScrollCallback(window) { _, _, yoffset ->
        camZ = (camZ - yoffset.toFloat() * camZStep).coerceIn(zNear, zFar)
    }

    while (!glfwWindowShouldClose(window)) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Вычисляем положение камеры на орбите
        val cameraPos = Vector3f(
            camZ * cos(camAngleY) * sin(camAngleX),
            camZ * sin(camAngleY),
            camZ * cos(camAngleY) * cos(camAngleX)
        )

        val viewMatrix = Matrix4f()
            .lookAt(cameraPos, Vector3f(0f, 0f, 0f), Vector3f(0f, 1f, 0f))
        viewMatrix.get(viewArray)

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