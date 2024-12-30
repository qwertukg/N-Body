package kz.qwertukg.nBody.nBodyParticleMesh.generator

import kz.qwertukg.nBody.nBodyParticleMesh.Particle
import kz.qwertukg.nBody.nBodyParticleMesh.SimulationConfig

// --- Интерфейс для «стратегии генерации» ---
interface FigureGenerator {
    fun generate(config: SimulationConfig): List<Particle>
}