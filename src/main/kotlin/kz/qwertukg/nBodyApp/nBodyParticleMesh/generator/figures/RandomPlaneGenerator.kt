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