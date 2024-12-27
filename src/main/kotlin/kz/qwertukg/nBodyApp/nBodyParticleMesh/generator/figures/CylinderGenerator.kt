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