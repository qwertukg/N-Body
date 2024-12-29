package kz.qwertukg.nBody.nBodyParticleMesh

import kotlinx.coroutines.*
import org.joml.Vector3f
import org.jtransforms.fft.FloatFFT_3D
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sqrt

// Основной класс симуляции
class ParticleMeshSimulation(val config: SimulationConfig) {
    // Массивы координат и характеристик частиц
    lateinit var particleX: FloatArray
    lateinit var particleY: FloatArray
    lateinit var particleZ: FloatArray
    lateinit var particleVx: FloatArray
    lateinit var particleVy: FloatArray
    lateinit var particleVz: FloatArray
    lateinit var particleM: FloatArray
    lateinit var particleR: FloatArray

    // Размеры сетки
    private val gX = config.gridSizeX
    private val gY = config.gridSizeY
    private val gZ = config.gridSizeZ

    // Предварительные расчёты
    private val gxGyGz = gX * gY * gZ
    private val gYgZ = gY * gZ
    private val cellWidth = config.worldWidth / gX
    private val cellHeight = config.worldHeight / gY
    private val cellDepth = config.worldDepth / gZ

    // Одномерные массивы для масс и потенциалов
    private val massGrid = FloatArray(gxGyGz)
    private val potentialGrid = FloatArray(gxGyGz)
    private val tempPotential = FloatArray(gxGyGz)

    fun initSimulation(particles: List<Particle>) {
        val totalCount = particles.size
        particleX = FloatArray(totalCount) { particles[it].x }
        particleY = FloatArray(totalCount) { particles[it].y }
        particleZ = FloatArray(totalCount) { particles[it].z }
        particleVx = FloatArray(totalCount) { particles[it].vx }
        particleVy = FloatArray(totalCount) { particles[it].vy }
        particleVz = FloatArray(totalCount) { particles[it].vz }
        particleM = FloatArray(totalCount) { particles[it].m }
        particleR = FloatArray(totalCount) { particles[it].r }
    }

    /**
     * Выполняет расчёт гравитации через FFT и
     * затем обновляет скорости/координаты частиц за время dt.
     *
     * @param dt Шаг времени, на который обновляем систему.
     */
    suspend fun stepWithFFT() = coroutineScope {
        // 1) Обнуляем massGrid
        massGrid.fill(0f)

        val dx = 1f / cellWidth
        val dy = 1f / cellHeight
        val dz = 1f / cellDepth

        val totalCount = particleX.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSize = (totalCount / availableProcessors).coerceAtLeast(1)

        // Распределяем массу частиц по сетке
        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                for (i in startIndex until endIndex) {
                    val px = particleX[i]
                    val py = particleY[i]
                    val pz = particleZ[i]

                    var cx = (px * dx).toInt()
                    var cy = (py * dy).toInt()
                    var cz = (pz * dz).toInt()
                    if (cx < 0) cx = 0 else if (cx >= gX) cx = gX - 1
                    if (cy < 0) cy = 0 else if (cy >= gY) cy = gY - 1
                    if (cz < 0) cz = 0 else if (cz >= gZ) cz = gZ - 1

                    val index = cx * gYgZ + cy * gZ + cz
                    massGrid[index] += particleM[i]
                }
            }
        }.awaitAll()

        // 2) Формируем массив complexData для 3D FFT (размер 2 * gX*gY*gZ)
        val dataSize = gX * gY * gZ
        val complexData = FloatArray(dataSize * 2) { 0f }

        // Заполняем real-часть из massGrid (im=0)
        for (x in 0 until gX) {
            for (y in 0 until gY) {
                for (z in 0 until gZ) {
                    val idx = x*gY*gZ + y*gZ + z
                    val reIndex = 2 * idx
                    complexData[reIndex] = massGrid[idx]
                }
            }
        }

        // 3) Прямое FFT
        val fft3D = FloatFFT_3D(gX.toLong(), gY.toLong(), gZ.toLong())
        fft3D.complexForward(complexData)

        // 4) Решаем уравнение в k-пространстве: Phi_hat(k) = - (4πG / k^2) * rho_hat(k)
        // (или 4πG / k^2, знак можно варьировать)
        val Lx = config.worldWidth
        val Ly = config.worldHeight
        val Lz = config.worldDepth
        val G = config.g

        for (x in 0 until gX) {
            val kxInt = if (x <= gX / 2) x else x - gX
            val kx = (2.0 * PI * kxInt) / Lx

            for (y in 0 until gY) {
                val kyInt = if (y <= gY / 2) y else y - gY
                val ky = (2.0 * PI * kyInt) / Ly

                for (z in 0 until gZ) {
                    val kzInt = if (z <= gZ / 2) z else z - gZ
                    val kz = (2.0 * PI * kzInt) / Lz

                    val idx = x*gY*gZ + y*gZ + z
                    val reIndex = 2 * idx
                    val imIndex = reIndex + 1

                    val re = complexData[reIndex]
                    val im = complexData[imIndex]

                    val kSq = (kx*kx + ky*ky + kz*kz).toFloat()

                    if (kSq < 1e-12f) {
                        // k=0: зануляем
                        complexData[reIndex] = 0f
                        complexData[imIndex] = 0f
                    } else {
                        val scale = -(4.0 * PI * G / kSq).toFloat()
                        complexData[reIndex] = re * scale
                        complexData[imIndex] = im * scale
                    }
                }
            }
        }

        // 5) Обратное FFT (Phi_hat -> Phi)
        fft3D.complexInverse(complexData, true)

        // 6) Сохраняем потенциал в potentialGrid (реальная часть)
        for (x in 0 until gX) {
            for (y in 0 until gY) {
                for (z in 0 until gZ) {
                    val idx = x*gY*gZ + y*gZ + z
                    val reIndex = 2 * idx
                    potentialGrid[idx] = complexData[reIndex]
                }
            }
        }

        // 7) Теперь обновляем частицы на шаг dt
        updateParticlesFromPotential()
    }

    /**
     * Обновляет скорости и координаты всех частиц,
     * используя градиент потенциала.
     *
     * @param dt шаг времени
     */
    private suspend fun updateParticlesFromPotential() = coroutineScope {
        val dt = config.dt
        val totalCount = particleX.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSize = (totalCount / availableProcessors).coerceAtLeast(1)

        val ww = config.worldWidth
        val wh = config.worldHeight
        val wd = config.worldDepth
        val dx = cellWidth
        val dy = cellHeight
        val dz = cellDepth

        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                for (i in startIndex until endIndex) {
                    var px = particleX[i]
                    var py = particleY[i]
                    var pz = particleZ[i]

                    // Индексация в potentialGrid
                    val cx = (px / dx).toInt().coerceIn(1, gX - 2)
                    val cy = (py / dy).toInt().coerceIn(1, gY - 2)
                    val cz = (pz / dz).toInt().coerceIn(1, gZ - 2)

                    val base = cx*gY*gZ + cy*gZ + cz

                    // Численные производные: dΦ/dx, dΦ/dy, dΦ/dz
                    val dPhidx = (potentialGrid[base + gY*gZ] - potentialGrid[base - gY*gZ]) / (2f * dx)
                    val dPhidy = (potentialGrid[base + gZ]     - potentialGrid[base - gZ])     / (2f * dy)
                    val dPhidz = (potentialGrid[base + 1]      - potentialGrid[base - 1])      / (2f * dz)

                    // Ускорение: a = -∇Φ
                    val ax = -dPhidx
                    val ay = -dPhidy
                    val az = -dPhidz

                    // Обновление скоростей
                    var vx = particleVx[i] + ax * dt
                    var vy = particleVy[i] + ay * dt
                    var vz = particleVz[i] + az * dt

                    // Обновление позиций
                    px += vx * dt
                    py += vy * dt
                    pz += vz * dt

                    // Зажимаем внутри мира
                    if (config.isDropOutOfBounds) {
                        if (px < 0f) {
                            px = 0f; vx = 0f
                        } else if (px > ww) {
                            px = ww; vx = 0f
                        }
                        if (py < 0f) {
                            py = 0f; vy = 0f
                        } else if (py > wh) {
                            py = wh; vy = 0f
                        }
                        if (pz < 0f) {
                            pz = 0f; vz = 0f
                        } else if (pz > wd) {
                            pz = wd; vz = 0f
                        }
                    }

                    // Сохранение обратно
                    particleX[i] = px
                    particleY[i] = py
                    particleZ[i] = pz
                    particleVx[i] = vx
                    particleVy[i] = vy
                    particleVz[i] = vz
                }
            }
        }.awaitAll()
    }

    fun setCircularOrbitsAroundCenterOfMassDirect(crossBase: Vector3f = Vector3f(0f, 0f, 1f)) {
        val totalCount = particleX.size
        if (totalCount == 0) return

        // 1) Находим суммарную массу и координаты центра масс
        var totalMass = 0f
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        for (i in 0 until totalCount) {
            val m = particleM[i]
            sumX += particleX[i] * m
            sumY += particleY[i] * m
            sumZ += particleZ[i] * m
            totalMass += m
        }
        if (totalMass < 1e-12f) return  // Вся масса ~0? — выходим

        val cmX = sumX / totalMass
        val cmY = sumY / totalMass
        val cmZ = sumZ / totalMass

        // Шаги по осям (размеры ячеек)
        val dx = cellWidth
        val dy = cellHeight
        val dz = cellDepth

        // 2) Для каждой частицы считаем «круговую» орбиту вокруг (cmX, cmY, cmZ)
        for (i in 0 until totalCount) {
            // Радиус-вектор от ЦМ к частице
            val rx = particleX[i] - cmX
            val ry = particleY[i] - cmY
            val rz = particleZ[i] - cmZ
            val rVec = Vector3f(rx, ry, rz)
            val dist = rVec.length()

            // Если частица практически в центре масс
            if (dist < 1e-8f) {
                particleVx[i] = 0f
                particleVy[i] = 0f
                particleVz[i] = 0f
                continue
            }

            // Индексы в сетке потенциала (зажимаем в [1..gX-2], чтобы не выйти за границы)
            val cx = (particleX[i] / dx).toInt().coerceIn(1, gX - 2)
            val cy = (particleY[i] / dy).toInt().coerceIn(1, gY - 2)
            val cz = (particleZ[i] / dz).toInt().coerceIn(1, gZ - 2)
            val base = cx*gY*gZ + cy*gZ + cz

            // Численный градиент потенциала
            val dPhidx = (potentialGrid[base + gY*gZ] - potentialGrid[base - gY*gZ]) / (2f * dx)
            val dPhidy = (potentialGrid[base + gZ]   - potentialGrid[base - gZ])   / (2f * dy)
            val dPhidz = (potentialGrid[base + 1]    - potentialGrid[base - 1])    / (2f * dz)

            // Сила: F = -m_i * grad(Φ)
            val m_i = particleM[i]
            val fx = -m_i * dPhidx
            val fy = -m_i * dPhidy
            val fz = -m_i * dPhidz

            // Радиальная компонента F_r = F • (rVec / |rVec|)
            val invDist = 1f / dist
            val rxNorm = rx * invDist
            val ryNorm = ry * invDist
            val rzNorm = rz * invDist
            val fRadial = fx*rxNorm + fy*ryNorm + fz*rzNorm

            // Если fRadial < 0, сила направлена «в центр», значит это обычная притягивающая гравитация
            if (fRadial < 0f) {
                // Условие круговой орбиты: m_i * v^2 / r = -fRadial => v = sqrt( r * (-fRadial) / m_i )
                val v = sqrt(dist * (-fRadial) / m_i)

                // Направление скорости: перпендикулярно rVec
                val crossCandidate = Vector3f()
                rVec.cross(crossBase, crossCandidate)
                // Если crossBase оказался почти сонаправлен с rVec, используем запасной вектор (1,0,0)
                if (crossCandidate.length() < 1e-8f) {
                    rVec.cross(Vector3f(1f, 0f, 0f), crossCandidate)
                }
                crossCandidate.normalize()

                // Задаём скорости
                particleVx[i] = crossCandidate.x * v
                particleVy[i] = crossCandidate.y * v
                particleVz[i] = crossCandidate.z * v
            } else {
                // Если fRadial >= 0, значит сила наружу или 0 — для гравитации это необычно
                particleVx[i] = 0f
                particleVy[i] = 0f
                particleVz[i] = 0f
            }
        }
    }
}


class DirectSimulation(val config: SimulationConfig) {

    // Массивы координат и характеристик частиц
    lateinit var particleX: FloatArray
    lateinit var particleY: FloatArray
    lateinit var particleZ: FloatArray
    lateinit var particleVx: FloatArray
    lateinit var particleVy: FloatArray
    lateinit var particleVz: FloatArray
    lateinit var particleM: FloatArray
    lateinit var particleR: FloatArray

    /**
     * Инициализация массива частиц из списка
     */
    fun initSimulation(particles: List<Particle>) {
        val totalCount = particles.size
        particleX = FloatArray(totalCount) { particles[it].x }
        particleY = FloatArray(totalCount) { particles[it].y }
        particleZ = FloatArray(totalCount) { particles[it].z }
        particleVx = FloatArray(totalCount) { particles[it].vx }
        particleVy = FloatArray(totalCount) { particles[it].vy }
        particleVz = FloatArray(totalCount) { particles[it].vz }
        particleM = FloatArray(totalCount) { particles[it].m }
        particleR = FloatArray(totalCount) { particles[it].r }
    }

    /**
     * Пример добавления частицы (если нужно динамически).
     * Предполагается, что массивы уже имеют нужный размер или перераспределяются.
     */
    fun addParticleToSimulation(particle: Particle): Int {
        val i = particleX.lastIndex
        with(particle) {
            particleX[i] = x
            particleY[i] = y
            particleZ[i] = z
            particleVx[i] = vx
            particleVy[i] = vy
            particleVz[i] = vz
            particleM[i] =  m
            particleR[i] =  r
        }
        return i
    }

    /**
     * Один шаг симуляции: рассчитываем силу между всеми парами (i,j), O(N^2).
     *
     * Формула:
     *   \(\mathbf{F}_{i \leftarrow j} = G \frac{m_i m_j}{r^2} \hat{r}_{ij}\),
     * где \(\hat{r}_{ij}\) — единичный вектор от i к j.
     * Ускорение для i: \(\mathbf{a}_i = \frac{\mathbf{F}_{i \leftarrow j}}{m_i}\).
     *
     * Чтобы не затирать обновлённые скорости "на лету", сначала считаем
     * все ускорения (ax, ay, az) в отдельных массивах, затем обновляем vx, vy, vz.
     */
    suspend fun step() = coroutineScope {
        val totalCount = particleX.size
        val dt = 10f
        val G = config.g

        // Аккумуляторы ускорений
        val ax = FloatArray(totalCount) { 0f }
        val ay = FloatArray(totalCount) { 0f }
        val az = FloatArray(totalCount) { 0f }

        // Параллельно обрабатываем частицы
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSize = (totalCount / availableProcessors).coerceAtLeast(1)

        // 1) Считаем ускорения от всех j для каждой i
        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                for (i in startIndex until endIndex) {
                    // Расчёт силы от всех остальных частиц j
                    var aX = 0f
                    var aY = 0f
                    var aZ = 0f

                    for (j in 0 until totalCount) {
                        if (i == j) continue
                        val dx = particleX[j] - particleX[i]
                        val dy = particleY[j] - particleY[i]
                        val dz = particleZ[j] - particleZ[i]

                        val r2 = dx*dx + dy*dy + dz*dz
                        if (r2 > 1e-12f) {
                            val r = sqrt(r2)
                            // a_i = G * m_j / r^2 * (rVector / r) = G * m_j / r^3 * rVector
                            val invR3 = 1f / (r2 * r)
                            aX += G * particleM[j] * dx * invR3
                            aY += G * particleM[j] * dy * invR3
                            aZ += G * particleM[j] * dz * invR3
                        }
                    }

                    // ax[i], ay[i], az[i] - ускорения
                    ax[i] = aX
                    ay[i] = aY
                    az[i] = aZ
                }
            }
        }.awaitAll()

        // 2) Обновляем скорости и координаты (метод Эйлера для простоты)
        for (i in 0 until totalCount) {
            particleVx[i] += ax[i] * dt
            particleVy[i] += ay[i] * dt
            particleVz[i] += az[i] * dt

            particleX[i] += particleVx[i] * dt
            particleY[i] += particleVy[i] * dt
            particleZ[i] += particleVz[i] * dt
        }
    }
}

