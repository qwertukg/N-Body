package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import org.joml.Vector3f
import kotlin.math.*
import kotlin.random.Random

class CubeGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Предположим, что minRadius < maxRadius
        val side = config.maxRadius - config.minRadius
        val halfSide = side / 2f

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // Случайная точка внутри куба: [ -halfSide, +halfSide ]
            val rx = Random.nextFloat() * side - halfSide  // от -halfSide до +halfSide
            val ry = Random.nextFloat() * side - halfSide
            val rz = Random.nextFloat() * side - halfSide

            val px = cx + rx
            val py = cy + ry
            val pz = cz + rz

            // Масса
            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            val rP = sqrt(m)

            particles.add(Particle(px.toFloat(), py.toFloat(), pz.toFloat(), 0f, 0f, 0f, m, rP))
        }

        // Задаём орбитальные скорости (перенесённый общий код)
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles  = particles,
            centerX    = cx,
            centerY    = cy,
            centerZ    = cz,
            starMass   = starMass,
            g          = config.g,
            magicConst = config.magicConst
        ) { _ ->
            // Для куба можно взять в качестве базового cross-вектора, скажем, ось Z
            Vector3f(0f, 0f, 1f)
        }

        // Добавляем "звезду"
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

class CylinderGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Радиус основания (minRadius) и "полувысота" (maxRadius)
        val radius = config.minRadius
        val halfHeight = config.maxRadius

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // 1) Равномерно внутри круга:
            //    rho ~ [0, radius], но по площади => rho = sqrt(U(0,1)) * radius
            val rho = sqrt(Random.nextFloat()) * radius
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            val x = cx + (rho * cos(theta)).toFloat()
            val y = cy + (rho * sin(theta)).toFloat()

            // 2) Случайно по высоте
            val z = Random.nextFloat() * (2f * halfHeight) - halfHeight  // от -h до +h
            val pz = cz + z

            // Масса
            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            val rP = sqrt(m)

            particles.add(Particle(x, y, pz.toFloat(), 0f, 0f, 0f, m, rP))
        }

        // Задаём орбитальные скорости
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles  = particles,
            centerX    = cx,
            centerY    = cy,
            centerZ    = cz,
            starMass   = starMass,
            g          = config.g,
            magicConst = config.magicConst
        ) { _ ->
            // Для цилиндра, ось которого вдоль z, часто удобно взять ось z
            Vector3f(0f, 0f, 1f)
        }

        // Добавляем "звезду"
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

// --- Генератор «диска» (disk) ---
class DiskGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ
        val particles = mutableListOf<Particle>()

        // 1) Генерация координат
        repeat(config.count) {
            val r = Random.nextDouble(config.minRadius.toDouble(), config.maxRadius.toDouble()).toFloat()
            val theta = Random.nextDouble(0.0, 2 * PI)
            val thetaZ = Random.nextDouble(PI / 2.2, PI / 1.9)

            val x = cx + (r * cos(theta) * sin(thetaZ)).toFloat()
            val y = cy + (r * sin(theta) * sin(thetaZ)).toFloat()
            val z = cz + (r * cos(thetaZ)).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(x, y, z, 0f, 0f, 0f, m, sqrt(m)))
        }

        // 2) Задаём орбитальные скорости (весь дублирующийся код - в едином методе!)
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles = particles,
            centerX = cx,
            centerY = cy,
            centerZ = cz,
            starMass = starMass,
            g = config.g,
            magicConst = config.magicConst
        ) { _ ->  // базовый вектор для cross
            // Для диска берём ось Z
            Vector3f(0f, 0f, 1f)
        }

        // 3) Добавляем звезду
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

// --- Генератор «случайной плоскости» (circle) ---
class RandomPlaneGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // 1) Случайная нормаль
        val phi = Random.nextDouble(0.0, 2.0 * PI)
        val cosTheta = Random.nextDouble(-1.0, 1.0)
        val sinTheta = sqrt(1.0 - cosTheta * cosTheta)

        val nx = (sinTheta * cos(phi)).toFloat()
        val ny = (sinTheta * sin(phi)).toFloat()
        val nz = cosTheta.toFloat()
        val normal = Vector3f(nx, ny, nz).normalize()

        // 2) Строим ортонормированный базис (u, v) в этой плоскости
        val maybeZ = Vector3f(0f, 0f, 1f)
        val u = Vector3f()
        normal.cross(maybeZ, u)
        if (u.length() < 1e-8f) {
            normal.cross(Vector3f(0f, 1f, 0f), u)
        }
        u.normalize()

        val v = Vector3f()
        normal.cross(u, v)
        v.normalize()

        // 3) Генерация координат
        val particles = mutableListOf<Particle>()
        repeat(config.count) {
            val r = Random.nextDouble(config.minRadius.toDouble(), config.maxRadius.toDouble()).toFloat()
            val angle = Random.nextDouble(0.0, 2.0 * PI)

            val localX = (r * cos(angle)).toFloat()
            val localY = (r * sin(angle)).toFloat()

            val pos = Vector3f(u).mul(localX).add(Vector3f(v).mul(localY))

            val px = cx + pos.x
            val py = cy + pos.y
            val pz = cz + pos.z

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        // 4) Орбитальные скорости
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles = particles,
            centerX = cx,
            centerY = cy,
            centerZ = cz,
            starMass = starMass,
            g = config.g,
            magicConst = config.magicConst
        ) { _ ->
            // Для «случайной плоскости» берём её нормаль
            normal
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

// --- Генератор «сферы» (ball) ---
class SphereGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ
        val particles = mutableListOf<Particle>()

        val starMass = 1_000_000_000f
        val g = config.g

        // 1) Генерация координат (равномерная в объёме сферы)
        repeat(config.count) {
            val alpha = Random.nextFloat()
            val rMin3 = config.minRadius * config.minRadius * config.minRadius
            val rMax3 = config.maxRadius * config.maxRadius * config.maxRadius
            val R = ((alpha * (rMax3 - rMin3)) + rMin3).pow(1.0 / 3.0).toFloat()

            val phi = Random.nextDouble(0.0, 2.0 * PI)
            val cosTheta = Random.nextDouble(-1.0, 1.0)
            val sinTheta = sqrt(1.0 - cosTheta * cosTheta)

            val xDir = sinTheta * cos(phi)
            val yDir = sinTheta * sin(phi)
            val zDir = cosTheta

            val px = cx + (R * xDir).toFloat()
            val py = cy + (R * yDir).toFloat()
            val pz = cz + (R * zDir).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        // 2) Орбитальные скорости (вынесены во внешнюю функцию)
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles = particles,
            centerX = cx,
            centerY = cy,
            centerZ = cz,
            starMass = starMass,
            g = g,
            magicConst = config.magicConst
        ) { _ ->
            // Для сферы в качестве базового cross-вектора чаще всего
            // берут что-то типа (0,1,0).
            Vector3f(0f, 1f, 0f)
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}