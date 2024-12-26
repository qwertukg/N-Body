package kz.qwertukg.nBodyApp

import org.lwjgl.opengl.GL46.*


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