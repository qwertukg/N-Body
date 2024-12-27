package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig

// --- Интерфейс для «стратегии генерации» ---
interface FigureGenerator {
    fun generate(config: SimulationConfig): List<Particle>
}