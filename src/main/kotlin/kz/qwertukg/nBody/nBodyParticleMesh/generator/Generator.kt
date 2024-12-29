package kz.qwertukg.nBody.nBodyParticleMesh.generator

import kz.qwertukg.nBody.nBodyParticleMesh.Particle
import kz.qwertukg.nBody.nBodyParticleMesh.SimulationConfig

// --- Основной класс Generator, использующий «стратегию» ---
class Generator(val config: SimulationConfig) {
    val figureGenerators: MutableMap<String, FigureGenerator> = mutableMapOf()

    fun registerFigure(name: String, generator: FigureGenerator) {
        figureGenerators[name.lowercase()] = generator
    }

    fun generate(figureName: String): List<Particle> {
        val gen = figureGenerators[figureName.lowercase()]
            ?: figureGenerators[figureGenerators.keys.first()] ?:
                throw NotImplementedError("No figures")

        return gen.generate(config)
    }
}