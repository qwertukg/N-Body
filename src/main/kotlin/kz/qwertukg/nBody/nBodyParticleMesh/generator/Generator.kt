package kz.qwertukg.nBody.nBodyParticleMesh.generator

import kz.qwertukg.nBody.nBodyParticleMesh.Particle
import kz.qwertukg.nBody.nBodyParticleMesh.SimulationConfig

var isWithBlackHole = true

// --- Основной класс Generator, использующий «стратегию» ---
class Generator(val config: SimulationConfig) {
    var current: String = ""
    val figureGenerators: MutableMap<String, FigureGenerator> = mutableMapOf()

    fun registerFigure(name: String, generator: FigureGenerator) {
        figureGenerators[name.lowercase()] = generator
    }

    fun generate(figureName: String): List<Particle> {
        val gen = figureGenerators[figureName.lowercase()]
            ?: figureGenerators[figureGenerators.keys.first()] ?:
                throw NotImplementedError("No figures")
        current = figureName
        return if (!isWithBlackHole) gen.generate(config).drop(1) else gen.generate(config)
    }
}