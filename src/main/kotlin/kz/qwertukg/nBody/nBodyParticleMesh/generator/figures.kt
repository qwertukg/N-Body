package kz.qwertukg.nBody.nBodyParticleMesh.generator

import kz.qwertukg.nBody.nBodyParticleMesh.Particle
import kz.qwertukg.nBody.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBody.nBodyParticleMesh.fromJson
import org.joml.Vector3f
import kotlin.math.*
import kotlin.random.Random

val starMass = fromJson("src/main/resources/config.json").starMass

/**
 * Генератор «орбитального диска».
 *
 * Количество частиц (кроме центральной чёрной дыры) берётся из [SimulationConfig.count],
 * а пропорции звёзд сохраняются:
 *  * красные карлики — 70 %
 *  * звёзды средней массы — 12,5 %
 *  * массивные звёзды — 5 %
 *  * сверхмассивные — 0,5 %
 *  * звёздные остатки — то, что осталось от бюджета
 */
class OrbitalDiskGenerator : FigureGenerator {

    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // ------------------------------------------------------------
        // 1. Центральная чёрная дыра
        // ------------------------------------------------------------
        val blackHole = Particle(
            cx, cy, cz,
            vx = 0f, vy = 0f, vz = 0f,
            m = starMass,
            r = sqrt(starMass)
        )

        // ------------------------------------------------------------
        // 2. Расчёт бюджета и квот
        // ------------------------------------------------------------
        val budget = (config.count - 1).coerceAtLeast(0)      // -1, потому что чёрная дыра уже учтена

        val redCount          = (budget * 0.70 ).roundToInt()
        val mediumCount       = (budget * 0.125).roundToInt()
        val massiveCount      = (budget * 0.05 ).roundToInt()
        val superMassiveCount = (budget * 0.005).roundToInt()
        val remnantsCount     = budget -
                redCount - mediumCount - massiveCount - superMassiveCount

        // ------------------------------------------------------------
        // 3. Параметры геометрии диска
        // ------------------------------------------------------------
        val halfThickness = config.minRadius           // h/2
        val maxR = config.maxRadius

        // ------------------------------------------------------------
        // 4. Генерация звёзд разных типов
        // ------------------------------------------------------------
        val redDwarfs      = generateStars(redCount,          0.08..0.60, halfThickness, maxR, cx, cy, cz)
        val mediumStars    = generateStars(mediumCount,       0.60..1.50, halfThickness, maxR, cx, cy, cz)
        val massiveStars   = generateStars(massiveCount,      1.50..8.00, halfThickness, maxR, cx, cy, cz)
        val superMassive   = generateStars(superMassiveCount, 8.00..150.0, halfThickness, maxR, cx, cy, cz)
        val remnants       = generateStars(remnantsCount,     0.90..1.10, halfThickness, maxR, cx, cy, cz)

        // ------------------------------------------------------------
        // 5. Итоговый список
        // ------------------------------------------------------------
        return buildList {
            add(blackHole)
            addAll(redDwarfs)
            addAll(mediumStars)
            addAll(massiveStars)
            addAll(superMassive)
            addAll(remnants)
        }
    }

    /**
     * Генерирует [count] частиц со случайными координатами в диске
     * и случайной массой в диапазоне [massRange].
     */
    private fun generateStars(count: Int, massRange: ClosedFloatingPointRange<Double>, halfThickness: Double, maxR: Double, cx: Float, cy: Float, cz: Float) = List(count) {
        val r = Random.nextDouble(0.0, maxR).toFloat()
        val theta = Random.nextDouble(0.0, 2 * PI)
        val x = cx + (r * cos(theta)).toFloat()
        val y = cy + (r * sin(theta)).toFloat()

        val factor = if (maxR > 0) (1 - sqrt(r / maxR)).coerceIn(0.0, 1.0) else 0.0001
        val z = cz + Random.nextDouble(-halfThickness * factor, halfThickness * factor).toFloat()


        val m = Random.nextDouble(massRange.start, massRange.endInclusive).toFloat()
        Particle(x, y, z, 0f, 0f, 0f, m, sqrt(m))
    }
}

/**
 * 1) Лента Мёбиуса (Mobius Strip)
 *    Параметры (в params):
 *      "majorRadius" - радиус «кольца» (по умолчанию берем maxRadius)
 *      "width"       - половина ширины ленты (по умолчанию minRadius)
 *      "twists"      - сколько "поворотов" делает лента (по умолчанию 1)
 */
class MobiusStripGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val majorR = config.params["majorRadius"] ?: config.maxRadius.toFloat()
        val halfW  = config.params["width"]       ?: config.minRadius.toFloat()
        val twists = config.params["twists"]?.toInt() ?: 1

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        // Применим классические параметры "t" в [0..2π * twists], "s" в [-1..1].
        repeat(config.count) {
            // t ~ [0..2π * twists]
            val t = Random.nextFloat() * (2f * PI.toFloat() * twists)
            // s ~ [-1..1]
            val s = Random.nextFloat() * 2f - 1f

            // По классической формуле ленты Мёбиуса (упрощенный вариант):
            // x = (majorR + s * cos(t/2)) * cos(t)
            // y = (majorR + s * cos(t/2)) * sin(t)
            // z = s * sin(t/2)
            val halfT = t / 2f
            val cosHalfT = cos(halfT)
            val sinHalfT = sin(halfT)

            val localR = majorR + s * halfW * cosHalfT
            val x = localR * cos(t)
            val y = localR * sin(t)
            val z = s * halfW * sinHalfT

            val px = cx + x.toFloat()
            val py = cy + y.toFloat()
            val pz = cz + z.toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }



        // Добавляем звезду
        return particles
    }
}

/**
 * 2) "Рёберный" (волнообразный) цилиндр (RidgesCylinder):
 *    Высота ~ 2 * height, радиус меняется по синусоиде вдоль z.
 *    Параметры:
 *      "baseRadius" - базовый радиус (если нет, берём minRadius)
 *      "height"     - полувысота (если нет, берём maxRadius)
 *      "amplitude"  - амплитуда колебаний радиуса (по умолчанию = baseRadius/2)
 *      "freq"       - частота колебаний (в синусе) вдоль оси z (по умолчанию 2π / height)
 */
class RidgesCylinderGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val baseR     = config.params["baseRadius"] ?: config.minRadius.toFloat()
        val halfH     = config.params["height"]     ?: config.maxRadius.toFloat()
        val amplitude = config.params["amplitude"]  ?: (baseR / 2f)
        // Если height == 0, freq -> 1, а так берём ~ (2π / (2*halfH)) = π/halfH
        val freq      = config.params["freq"]       ?: (PI.toFloat() / halfH)

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            // z ~ [-halfH.. +halfH]
            val zVal = Random.nextFloat() * 2f * halfH - halfH
            // радиус = baseR + amplitude * sin(freq * zVal)
            val radial = baseR + amplitude * sin(freq * zVal)

            // Случайно в диске радиуса radial
            val rho = sqrt(Random.nextFloat()) * radial
            val theta = Random.nextDouble(0.0, 2.0 * PI)
            val x = (rho * cos(theta)).toFloat()
            val y = (rho * sin(theta)).toFloat()

            val px = cx + x
            val py = cy + y
            val pz = cz + zVal

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        return particles
    }
}

/**
 * 3) "Синусоидный тор" (SineWaveTorus):
 *    Параметры:
 *      "majorRadius" - радиус большого круга (по умолчанию maxRadius)
 *      "tubeRadius"  - средний радиус трубы (по умолчанию minRadius)
 *      "amplitude"   - амплитуда синусоиды (прибавляется к tubeRadius)
 *      "freq"        - частота синусоиды
 */
class SineWaveTorusGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val majorR   = config.params["majorRadius"] ?: config.maxRadius.toFloat()
        val tubeR    = config.params["tubeRadius"]  ?: config.minRadius.toFloat()
        val amplitude= config.params["amplitude"]   ?: (tubeR.toFloat() / 2f)
        val freq     = config.params["freq"]        ?: 3f // например, 3 колебания на 2π

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            // phi ~ [0..2π] — угол по большому кругу
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            // theta ~ [0..2π] — угол по трубе
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            // Добавляем синусоиду к tubeR: rTube = tubeR + amplitude*sin(freq*phi)
            val rTube = tubeR + amplitude * sin(freq * phi)

            val x = (majorR + rTube * cos(theta)) * cos(phi)
            val y = (majorR + rTube * cos(theta)) * sin(phi)
            val z = rTube * sin(theta)

            val px = cx + x.toFloat()
            val py = cy + y.toFloat()
            val pz = cz + z.toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        return particles
    }
}

/**
 * 5) Сфера с "шумом" (RandomNoiseSphere):
 *    Параметры:
 *      "baseRadius"   - средний радиус сферы (по умолчанию maxRadius)
 *      "noiseAmplitude" - колебания радиуса (по умолчанию = baseRadius/10)
 *      "noiseFreq"    - частота (сколько колебаний по углу?)
 *
 *    Простейший "шум" сделаем синусоиду от (theta + phi),
 *    либо воспользуемся random при каждом угле (тоже вариант).
 */
class RandomNoiseSphereGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val baseR   = config.params["baseRadius"]     ?: config.maxRadius.toFloat()
        val amp     = config.params["noiseAmplitude"] ?: (baseR / 10f)
        val freq    = config.params["noiseFreq"] ?: 12f  // кол-во колебаний

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            // Берём угол phi ~ [0..2π], cosTheta ~ [-1..1]
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            val cosTheta = Random.nextDouble(-1.0, 1.0)
            val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

            // Небольшой "шум": R = baseR + amp*sin(freq*(phi + arccos(cosTheta)))
            // arccos(cosTheta) = θ
            val theta = acos(cosTheta)
            val noiseVal = sin(freq * (phi + theta))

            val R = baseR + amp * noiseVal

            val px = cx + (R * sinTheta * cos(phi)).toFloat()
            val py = cy + (R * sinTheta * sin(phi)).toFloat()
            val pz = cz + (R * cosTheta).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        return particles
    }
}

/** 1) Генератор пирамиды (основание квадратное, вершина в центре). */
class PyramidGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // baseSize — половина стороны основания
        val baseSize = config.params["baseSize"] ?: config.minRadius.toFloat()
        // height — высота пирамиды
        val height = config.params["height"] ?: config.maxRadius.toFloat()

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            // zFrac [0..1], 0 = вершина, 1 = основание
            val zFrac = Random.nextFloat()
            val zVal = -height * zFrac  // вершина в z=0, основание в z=-height

            // "Усечённая" область квадрата: side(t) = 2*(1-t)*baseSize
            val halfSide = (1f - zFrac) * baseSize
            val rx = Random.nextFloat() * 2f * halfSide - halfSide
            val ry = Random.nextFloat() * 2f * halfSide - halfSide

            val px = cx + rx
            val py = cy + ry
            val pz = cz + zVal

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        return particles
    }
}

/** 5) Генератор нескольких "скучкованных" регионов (RandomClusters):
 *    Параметры:
 *      - "clusters" (кол-во кластеров),
 *      - "clusterRadius" (радиус рассеяния внутри каждого кластера),
 *      - "spread" (как далеко кластеры разлетаются от центра).
 */
class RandomClustersGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val clusters = config.params["clusters"] ?: 100f
        val clusterRadius = config.params["clusterRadius"] ?: config.minRadius.toFloat()
        val spread = config.params["spread"] ?: config.maxRadius.toFloat()

        // Превращаем "clusters" в Int (округлим)
        val clusterCount = clusters.toInt().coerceAtLeast(1)

        // Для начала сгенерируем "центры" кластеров
        val clusterCenters = mutableListOf<Vector3f>()

        repeat(clusterCount) {
            // Случайное направление + расстояние
            val dist = Random.nextFloat() * spread
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            val cosTheta = Random.nextDouble(-1.0, 1.0)
            val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

            val px = dist * sinTheta * cos(phi)
            val py = dist * sinTheta * sin(phi)
            val pz = dist * cosTheta

            clusterCenters.add(Vector3f(px.toFloat(), py.toFloat(), pz.toFloat()))
        }

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        // Разделим общее число частиц по кластерам (пример: поровну)
        val perCluster = config.count / clusterCount

        for (k in 0 until clusterCount) {
            val center = clusterCenters[k]
            repeat(perCluster) {
                // Генерируем точку внутри сферического "clusterRadius"
                val alpha = Random.nextFloat() // [0..1]
                val R = clusterRadius * cbrt(alpha)
                val phi = Random.nextDouble(0.0, 2.0 * PI)
                val cosTheta = Random.nextDouble(-1.0, 1.0)
                val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

                val dx = R * sinTheta * cos(phi)
                val dy = R * sinTheta * sin(phi)
                val dz = R * cosTheta

                val px = cx + center.x + dx
                val py = cy + center.y + dy
                val pz = cz + center.z + dz

                val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
                particles.add(Particle(px.toFloat(), py.toFloat(), pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
            }
        }

        // Если в распределении "нехватает" оставшихся частиц (из-за деления), добавим их в последний кластер
        val remainder = config.count % clusterCount
        if (remainder > 0) {
            val center = clusterCenters.last()
            repeat(remainder) {
                val alpha = Random.nextFloat()
                val R = clusterRadius * cbrt(alpha)
                val phi = Random.nextDouble(0.0, 2.0 * PI)
                val cosTheta = Random.nextDouble(-1.0, 1.0)
                val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

                val dx = R * sinTheta * cos(phi)
                val dy = R * sinTheta * sin(phi)
                val dz = R * cosTheta

                val px = cx + center.x + dx
                val py = cy + center.y + dy
                val pz = cz + center.z + dz

                val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
                particles.add(Particle(px.toFloat(), py.toFloat(), pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
            }
        }

        return particles
    }
}

/** 1) Генерация конуса (ось вдоль Z, вершина в центре, основание в плоскости z = -height). */
class ConeGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Пусть minRadius = радиус основания, maxRadius = высота конуса
        val baseRadius = config.minRadius
        val height = config.maxRadius

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            // z идёт от 0 (вершина) до -height (основание)
            val zFrac = Random.nextFloat() // [0..1]
            val zVal = -height * zFrac     // от 0 до -height
            // Радиус на этом "сечении" ~ (1 - zFrac)*baseRadius
            val rLocal = baseRadius * (1f - zFrac)
            // Случайно в круге радиуса rLocal
            val rho = sqrt(Random.nextFloat()) * rLocal
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            val x = (rho * cos(theta)).toFloat()
            val y = (rho * sin(theta)).toFloat()

            val px = cx + x
            val py = cy + y
            val pz = cz + zVal

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
        }

        return particles
    }
}

/** 2) Генерация тора (ось вдоль Z, центр тора в (cx,cy,cz), радиус "трубы" = minRadius, радиус "кольца" = maxRadius). */
class TorusGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Внутренний радиус "трубы" (rTube) и внешний радиус "бублика" (rMajor)
        val rTube = config.minRadius
        val rMajor = config.maxRadius

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            // Угол вокруг большой окружности
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            // Угол вокруг малой окружности (трубы)
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            // Координаты относительно центра (0,0,0), ось тора - ось Z
            val x = (rMajor + rTube * cos(theta)) * cos(phi)
            val y = (rMajor + rTube * cos(theta)) * sin(phi)
            val z = rTube * sin(theta)

            val px = cx + x.toFloat()
            val py = cy + y.toFloat()
            val pz = cz + z.toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        return particles
    }
}

/** 3) Генерация полушара (ось вдоль Z, "нижнее" основание в плоскости z=0). */
class HemisphereGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val rMax = config.maxRadius

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            // Радиус с учётом равномерности по объёму полушара
            val alpha = Random.nextFloat() // [0..1]
            val R = rMax * cbrt(alpha)     // радиус от 0 до rMax
            // Случайное направление, но ограничиваем z >= 0
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            val cosTheta = Random.nextDouble(0.0, 1.0) // т.к. z>=0 => theta в [0..pi/2]
            val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

            val xDir = sinTheta * cos(phi)
            val yDir = sinTheta * sin(phi)
            val zDir = cosTheta

            val px = cx + (R * xDir).toFloat()
            val py = cy + (R * yDir).toFloat()
            val pz = cz + (R * zDir).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        return particles
    }
}

/** 4) Генерация "двойного конуса": конус вверх и конус вниз (ось вдоль Z, вершины соединены в центре). */
class DoubleConeGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val baseRadius = config.minRadius
        val height = config.maxRadius

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            // Случайно выбираем, в какой конус попасть (верхний / нижний)
            val isUpper = Random.nextBoolean()
            // zFrac ~ [0..1], zVal ~ [-height.. height]
            val zFrac = Random.nextFloat()
            val zVal = (if (isUpper) height else -height) * zFrac

            // Радиус на этом уровне ~ zFrac * baseRadius
            val rLocal = baseRadius * zFrac
            val rho = sqrt(Random.nextFloat()) * rLocal
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            val x = rho * cos(theta)
            val y = rho * sin(theta)

            val px = cx + x.toFloat()
            val py = cy + y.toFloat()
            val pz = cz + zVal

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
        }

        return particles
    }
}

class CubeGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Предположим, что minRadius < maxRadius
        val side = config.maxRadius
        val halfSide = side / 2f

        val particles = mutableListOf<Particle>()
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

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
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

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
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        // 1) Генерация координат
        repeat(config.count) {
            val r = Random.nextDouble(config.minRadius, config.maxRadius).toFloat()
            val theta = Random.nextDouble(0.0, 2 * PI)
            val thetaZ = Random.nextDouble(PI/2 - PI/10, PI/2 + PI/10)

            val x = cx + (r * cos(theta) * sin(thetaZ)).toFloat()
            val y = cy + (r * sin(theta) * sin(thetaZ)).toFloat()
            val z = cz + (r * cos(thetaZ)).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(x, y, z, 0f, 0f, 0f, m, sqrt(m)))
        }

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
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

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

        return particles
    }
}

// Volume fill
class RandomOrbitsGenerator : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ
        val particles = mutableListOf<Particle>()

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        repeat(config.count) {
            val x = Random.nextFloat() * config.worldWidth
            val y = Random.nextFloat() * config.worldHeight
            val z = Random.nextFloat() * config.worldDepth
            val m = 1f
            Particle(
                x = x,
                y = y,
                z = z,
                vx = 0f,
                vy = 0f,
                vz = 0f,
                m = m,
                r = sqrt(m)
            ).apply { particles.add(this) }
        }

        return particles
    }
}

class CustomGenerator(val particles: List<List<Float>>) : FigureGenerator {
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        return particles.map {
            Particle(
                x = cx + it[0],
                y = cy + it[1],
                z = cz + it[2],
                vx = it[3],
                vy = it[4],
                vz = it[5],
                m = it[6],
                r = sqrt(it[6])
            )
        }
    }
}