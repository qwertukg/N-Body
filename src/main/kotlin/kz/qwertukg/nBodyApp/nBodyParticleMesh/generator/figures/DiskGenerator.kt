package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.figures

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.FigureGenerator
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.OrbitalVelocityUtils
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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