package kz.qwertukg.nBody

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kz.qwertukg.nBody.nBodyParticleMesh.ParticleMeshSimulation
import org.lwjgl.opengl.GL46.*
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.min


// Проверка статуса компиляции шейдера
fun checkShaderCompileStatus(shader: Int) {
    val status = glGetShaderi(shader, GL_COMPILE_STATUS)
    if (status == GL_FALSE) {
        val log = glGetShaderInfoLog(shader)
        throw RuntimeException("Ошибка компиляции шейдера: $log")
    }
}

// Проверка статуса линковки программы
fun checkProgramLinkStatus(program: Int) {
    val status = glGetProgrami(program, GL_LINK_STATUS)
    if (status == GL_FALSE) {
        val log = glGetProgramInfoLog(program)
        throw RuntimeException("Ошибка линковки программы: $log")
    }
}

private const val MIN_CHUNK = 4_096       // опытно-подобранный размер куска

/**
 * Записывает координаты всех частиц **напрямую** в persistently-mapped VBO.
 *
 * @param buf   FloatBuffer, полученный при map-е VBO.
 * @param scale Масштаб, как в рендере (например 2_000_000f).
 * @return      Кол-во записанных вершин (= кол-во частиц).
 */
suspend fun ParticleMeshSimulation.writePositionsToVbo(buf: FloatBuffer, scale: Float): Int = coroutineScope {
    val n         = particleX.size
    val hw        = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val chunkSize = maxOf(MIN_CHUNK, ceil(n.toDouble() / hw).toInt())

    buf.limit(n * 3)   // 3 координаты на частицу
    buf.position(0)

    (0 until n step chunkSize).map { start ->
        launch(Dispatchers.Default) {
            var dst = start * 3
            val end = min(start + chunkSize, n)
            for (i in start until end) {
                buf.put(dst    , particleX[i] / scale)
                buf.put(dst + 1, particleY[i] / scale)
                buf.put(dst + 2, particleZ[i] / scale)
                dst += 3
            }
        }
    }.joinAll()

    n
}



var focusIndex = 0

fun ParticleMeshSimulation.nextFocus() {
    focusIndex++
    focusIndex = if (focusIndex >= particleX.count()) 0 else focusIndex
}

fun ParticleMeshSimulation.prevFocus() {
    focusIndex--
    focusIndex = if (focusIndex < 0) particleX.count() - 1 else focusIndex
}
