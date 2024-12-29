package kz.qwertukg.nBody.nBodyParticleMesh.generator

import kz.qwertukg.nBody.nBodyParticleMesh.Particle
import kz.qwertukg.nBody.nBodyParticleMesh.SimulationConfig
import org.joml.Vector3f
import kotlin.math.sqrt

/**
 * Утилита для общего кода расчёта орбитальных скоростей.
 *
 * crossBaseProducer(rVec):
 *  - возвращает "базовый" вектор, с которым нужно сделать cross(rVec, base),
 *    чтобы получить направление скорости.
 *  - Например, для диска это всегда zAxis (0,0,1).
 *  - Для «случайной плоскости» — это normal плоскости.
 *  - Для сферы — обычно yAxis (0,1,0) или что-то ещё.
 */
object OrbitalVelocityUtils {

    /**
     * Вычисляет скорость для круговой орбиты вокруг [star],
     * учитывая масштаб мира, задаваемый в [config].
     *
     * @param point частица (планета), для которой хотим задать орбитальную скорость
     * @param star  частица-звезда (большая масса)
     * @param config параметры симуляции, содержащие масштаб и g
     * @param crossBase вектор, относительно которого ищется перпендикулярное направление (по умолчанию ось Z)
     */
    fun computeCircularOrbit(
        point: Particle,
        star: Particle,
        config: SimulationConfig,
        crossBase: Vector3f = Vector3f(0f, 0f, 1f)
    ): Particle {
        // Вектор от звезды к точке (в координатах "мира")
        val rVec = Vector3f(
            point.x - star.x,
            point.y - star.y,
            point.z - star.z
        )
        val distWorld = rVec.length()

        // Если расстояние слишком маленькое, вернём точку без скорости
        if (distWorld < 1e-8f) {
            return point.copy(vx = 0f, vy = 0f, vz = 0f)
        }

        // -- 1) Переводим расстояние в "нормированные" (масштабированные) единицы. --
        // Допустим, за базовый масштаб берём worldSize (или worldHeight, или
        // среднее из worldWidth/worldHeight/worldDepth — по ситуации).
        val scale = config.worldSize
        // "Нормированное" расстояние
        val distScaled = distWorld / scale

        // -- 2) Считаем скорость в этих нормированных координатах. --
        // Формула круговой орбиты: v_scaled = sqrt(G * M / r_scaled)
        // Здесь G берём из config.g, а массу — из star.m
        val vScaled = sqrt(config.g * star.m / distScaled)

        // -- 3) Переводим скорость обратно в координаты "мира". --
        // При обратном переходе к "мировым" координатам умножаем на scale.
        val vWorld = vScaled * scale

        // Определяем направление скорости: оно должно быть перпендикулярно rVec
        val crossCandidate = Vector3f()
        rVec.cross(crossBase, crossCandidate)

        // Если crossCandidate близок к нулю (rVec ~|| base), берём запасной вектор (1,0,0)
        if (crossCandidate.length() < 1e-8f) {
            rVec.cross(Vector3f(1f, 0f, 0f), crossCandidate)
        }

        // Нормируем направление
        crossCandidate.normalize()

        // Координаты скорости в мировых единицах
        val vx = crossCandidate.x * vWorld
        val vy = crossCandidate.y * vWorld
        val vz = crossCandidate.z * vWorld

        // Возвращаем копию [point] с новыми скоростями
        return point.copy(vx = vx, vy = vy, vz = vz)
    }



    fun assignOrbitalVelocities(
        particles: MutableList<Particle>,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        starMass: Float,
        g: Float,
        magicConst: Float,
        crossBaseProducer: (Vector3f) -> Vector3f
    ) {
        for (i in particles.indices) {
            val p = particles[i]

            // Радиус-вектор от центра звезды до частицы
            val rVec = Vector3f(
                p.x - centerX,
                p.y - centerY,
                p.z - centerZ
            )
            val dist = rVec.length()

            if (dist > 1e-8f) {
                // Модуль скорости для круговой орбиты
                val v = sqrt((g * starMass) / dist)

                // Генерация базового направления скорости
                val base = crossBaseProducer(rVec).normalize()
                val crossCandidate = Vector3f()
                rVec.cross(base, crossCandidate)

                if (crossCandidate.length() < 1e-8f) {
                    // Используем запасной вектор (например, x-ось)
                    rVec.cross(Vector3f(1f, 0f, 0f), crossCandidate)
                }

                crossCandidate.normalize()

                val vx = crossCandidate.x * v * magicConst
                val vy = crossCandidate.y * v * magicConst
                val vz = crossCandidate.z * v * magicConst

                // Присваиваем скорость частице
                particles[i] = p.copy(vx = vx, vy = vy, vz = vz)
            } else {
                // Частица в центре
                particles[i] = p.copy(vx = 0f, vy = 0f, vz = 0f)
            }
        }
    }

}