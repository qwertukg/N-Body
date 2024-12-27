package kz.qwertukg.nBodyApp

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class Generator(val config: SimulationConfig) {
    fun generate(figureName: String): List<Particle> {
        val self = Generator(config)
        val clazz = self::class

        val generateMethods = clazz.members.filter {
            val methodName = it.name.toLowerCase()
            "generate" in methodName && figureName.toLowerCase() in methodName
        }

        return generateMethods.first().call(self) as List<Particle>
    }


    fun generateParticlesDiskXZ(): List<Particle> {
        var sumM = 0.0
        var sumMx = 0.0
        var sumMy = 0.0
        var sumMz = 0.0

        val particles = MutableList(config.count) {
            // Генерация точек в форме диска
            val minR = config.minRadius // Радиус диска
            val maxR = config.maxRadius // Радиус диска
            val r = sqrt(Random.nextDouble(minR * minR, maxR * maxR)).toFloat() // Радиальное расстояние
            val theta = Random.nextDouble(0.0, 2 * PI) // Угол в плоскости диска

            // Координаты частицы в плоскости XZ
            val x = 0f + (r * cos(theta)).toFloat()
            val z = 0f + (r * sin(theta)).toFloat()
            val y = 0f // Все точки лежат в одной плоскости Y

            // Масса и радиус частицы
            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            val rParticle = sqrt(m) / 100

            // Суммы для центра масс
            sumM += m
            sumMx += m * x
            sumMy += m * y
            sumMz += m * z
            val h = (maxR - minR) / 100.0
            val rndY = Random.nextDouble(y - h, y + h).toFloat() //+ config.worldHeight/3

            Particle(x, rndY, z, 0f, 0f, 0f, m, rParticle)
        }

        val totalMass = sumM.toFloat()
        val centerMassX = (sumMx / sumM).toFloat()
        val centerMassY = (sumMy / sumM).toFloat()
        val centerMassZ = (sumMz / sumM).toFloat()

        // Применяем скорости для орбитального завихрения
        val g = config.g
        val magicConst = config.magicConst

        for (i in particles.indices) {
            val p = particles[i]

            // Направление к центру масс
            val dx = p.x - centerMassX
            val dz = p.z - centerMassZ
            val dist = sqrt(dx * dx + dz * dz)

            if (dist > 0f) {
                // Скорость завихрения для диска
                val v = sqrt(g * totalMass / dist)

                // Нормализованные векторы для орбитального направления
                val ux = -dz / dist
                val uz = dx / dist

                // Рассчитываем скорости
                val vx = ux * v
                val vy = 0f // Частицы движутся только в плоскости XZ
                val vz = uz * v

                particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy, vz * magicConst, p.m, p.r)
            } else {
                particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
            }
        }
        return particles
    }

    fun generateParticlesDiskXY(): List<Particle> {
        var sumM = 0.0
        var sumMx = 0.0
        var sumMy = 0.0
        var sumMz = 0.0

        val particles = MutableList(config.count) {
            // Генерация точек в форме диска
            val minR = config.minRadius // Радиус диска
            val maxR = config.maxRadius // Радиус диска
            val r = sqrt(Random.nextDouble(minR * minR, maxR * maxR)).toFloat() // Радиальное расстояние
            val theta = Random.nextDouble(0.0, 2 * PI) // Угол в плоскости диска

            // Координаты частицы
            val x = 0f + (r * cos(theta)).toFloat()
            val y = 0f + (r * sin(theta)).toFloat()
            val z = 0f // Все точки лежат в одной плоскости Z

            // Масса и радиус частицы
            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            val rParticle = sqrt(m) / 100

            // Суммы для центра масс
            sumM += m
            sumMx += m * x
            sumMy += m * y
            sumMz += m * z
            val h = (maxR - minR) / 2
            val rndZ = Random.nextDouble(z - h, z + h).toFloat()
            Particle(x, y, rndZ, 0f, 0f, 0f, m, rParticle)
        }

        val totalMass = sumM.toFloat()
        val centerMassX = (sumMx / sumM).toFloat()
        val centerMassY = (sumMy / sumM).toFloat()
        val centerMassZ = (sumMz / sumM).toFloat()

        // Применяем скорости для орбитального завихрения
        val g = config.g
        val magicConst = config.magicConst

        for (i in particles.indices) {
            val p = particles[i]

            // Направление к центру масс
            val dx = p.x - centerMassX
            val dy = p.y - centerMassY
            val dist = sqrt(dx * dx + dy * dy)

            if (dist > 0f) {
                // Скорость завихрения для диска
                val v = sqrt(g * totalMass / dist)

                // Нормализованные векторы для орбитального направления
                val ux = -dy / dist
                val uy = dx / dist

                // Рассчитываем скорости
                val vx = ux * v
                val vy = uy * v
                val vz = 0f // Частицы движутся только в плоскости XY

                particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy * magicConst, vz, p.m, p.r)
            } else {
                particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
            }
        }
        return particles
    }

    fun generateParticlesCircle(): List<Particle> {
        var sumM = 0.0
        var sumMx = 0.0
        var sumMy = 0.0
        var sumMz = 0.0

//        val particles = mutableListOf<Particle>().apply {
//            Particle()
//        }

        val particles = MutableList(config.count) {
            val orbitR = Random.nextDouble(config.minRadius, config.maxRadius).toFloat()
            val angle1 = Random.nextDouble(0.0, 2 * PI)
            val angle2 = Random.nextDouble(0.0, PI)

            val x = 0f + (orbitR * cos(angle1) * sin(angle2)).toFloat()
            val y = 0f + (orbitR * sin(angle1) * sin(angle2)).toFloat()
            val z = 0f + (orbitR * cos(angle2)).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            val vx = 0f
            val vy = 0f
            val vz = 0f
            val r = sqrt(m) / 100

            sumM += m
            sumMx += m * x
            sumMy += m * y
            sumMz += m * z

            Particle(x, y, z, vx, vy, vz, m, r)
        }

        val totalMass = sumM.toFloat()
        val centerMassX = (sumMx / sumM).toFloat()
        val centerMassY = (sumMy / sumM).toFloat()
        val centerMassZ = (sumMz / sumM).toFloat()

        // Применяем скорости
        val g = config.g
        val magicConst = config.magicConst

        for (i in particles.indices) {
            val p = particles[i]
            val dx = p.x - centerMassX
            val dy = p.y - centerMassY
            val dz = p.z - centerMassZ
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > 0f) {
                val v = sqrt(g * totalMass / dist)
                val ux = dx / dist
                val uy = dy / dist

                // Correct vz calculation for orbital velocity
                val vx = -uy * v
                val vy = ux * v
                val vz = -ux * v // Adjusted vz to follow the orbital velocity rules

                particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy * magicConst, vz * magicConst, p.m, p.r)
            } else {
                particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
            }
        }
        return particles
    }

    fun generateParticlesTorus(): List<Particle> {
        var sumM = 0.0
        var sumMx = 0.0
        var sumMy = 0.0
        var sumMz = 0.0

        val particles = MutableList(config.count) {
            // Генерация точек в форме тора
            val R = config.minRadius // Большой радиус тора
            val r = config.maxRadius - config.minRadius // Малый радиус тора

            // Углы для параметрического описания тора
            val theta = Random.nextDouble(0.0, 2 * PI) // Угол вдоль большого радиуса
            val phi = Random.nextDouble(0.0, 2 * PI)   // Угол на окружности малого радиуса

            // Координаты частицы
            val x = 0f + ((R + r * cos(phi)) * cos(theta)).toFloat()
            val y = 0f + ((R + r * cos(phi)) * sin(theta)).toFloat()
            val z = 0f + (r * sin(phi)).toFloat()

            // Масса и радиус частицы
            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            val rParticle = sqrt(m) / 100

            // Суммы для центра масс
            sumM += m
            sumMx += m * x
            sumMy += m * y
            sumMz += m * z

            Particle(x, y, z, 0f, 0f, 0f, m, rParticle)
        }

        val totalMass = sumM.toFloat()
        val centerMassX = (sumMx / sumM).toFloat()
        val centerMassY = (sumMy / sumM).toFloat()
        val centerMassZ = (sumMz / sumM).toFloat()

        // Применяем скорости для орбитального завихрения
        val g = config.g
        val magicConst = config.magicConst

        for (i in particles.indices) {
            val p = particles[i]

            // Направление к центру масс малого круга
            val dx = p.x - centerMassX
            val dy = p.y - centerMassY
            val dz = p.z - centerMassZ

            // Рассчитываем расстояние в плоскости малого радиуса
            val distXY = sqrt(dx * dx + dy * dy)
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            if (dist > 0f) {
                // Скорость завихрения для малого радиуса
                val v = sqrt(g * totalMass / distXY)

                // Нормализованные векторы для орбитального направления
                val ux = -dy / distXY
                val uy = dx / distXY

                // Рассчитываем скорости, создавая орбитальное вращение
                val vx = ux * v
                val vy = uy * v
                val vz = 0f // Минимальная скорость вдоль оси Z для завихрения

                particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy * magicConst, vz * magicConst, p.m, p.r)
            } else {
                particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
            }
        }
        return particles
    }

    fun generateParticlesBox(): List<Particle> {
        val particles = MutableList(config.count) {
            val hW = config.worldWidth / 2
            val hH = config.worldHeight / 2
            val hD = config.worldDepth / 2
            val x = Random.nextDouble(-hW.toDouble(), hW.toDouble())
            val y = Random.nextDouble(-hH.toDouble(), hH.toDouble())
            val z = Random.nextDouble(-hD.toDouble(), hD.toDouble())

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat() // TODO
            val vx = 0f
            val vy = 0f
            val vz = 0f
            val particleR = sqrt(m) // TODO

            Particle(x.toFloat(), y.toFloat(), z.toFloat(), vx, vy, vz, m, particleR)
        }

        return particles
    }

    fun generateParticlesLine(): List<Particle> {
        val w = config.worldWidth * 100
        val h = config.worldHeight * 100
        val d = config.worldDepth * 100
        val count = config.count
        val particles = mutableListOf<Particle>()
        repeat(count) {
            val dx = sqrt(w * w) / count
            val dy = sqrt(h * h) / count
            val dz = sqrt(d * d) / count

            val i = it - count / 2
            val x = i * dx
            val y = i * dy
            val z = i * dz

            val iii = -it + count / 2
            val xxx = iii * dx

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat() // TODO
            val vx = 0f
            val vy = 0f
            val vz = 0f
            val r = sqrt(m) // TODO


            particles.addAll(listOf(
                Particle(x, y, z, vx, vy, vz, m, r),
                Particle(config.worldWidth / 2, y, z, vx, vy, vz, m, r),
                Particle(xxx, y, z, vx, vy, vz, m, r),
            ))


        }


        return particles
    }
}