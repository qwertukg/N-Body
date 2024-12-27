package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.figures

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.FigureGenerator
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.OrbitalVelocityUtils
import org.joml.Vector3f
import kotlin.math.*
import kotlin.random.Random

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