package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.figures

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.FigureGenerator
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.OrbitalVelocityUtils
import org.joml.Vector3f
import kotlin.math.sqrt
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