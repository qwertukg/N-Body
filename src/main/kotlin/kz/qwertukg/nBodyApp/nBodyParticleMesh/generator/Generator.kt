package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.figures.DiskGenerator
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.figures.RandomPlaneGenerator
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.figures.SphereGenerator

// --- Интерфейс для «стратегии генерации» ---
interface FigureGenerator {
    fun generate(config: SimulationConfig): List<Particle>
}

// --- Основной класс Generator, использующий «стратегию» ---
class Generator(val config: SimulationConfig) {

    private val figureGenerators: MutableMap<String, FigureGenerator> = mutableMapOf(
        "disc"   to DiskGenerator(),
        "circle" to RandomPlaneGenerator(),
        "ball"   to SphereGenerator()
    )

    fun registerFigure(name: String, generator: FigureGenerator) {
        figureGenerators[name.lowercase()] = generator
    }

    fun generate(figureName: String): List<Particle> {
        val gen = figureGenerators[figureName.lowercase()]
            ?: throw NotImplementedError("Нет генератора для фигуры '$figureName'")

        return gen.generate(config)
    }
}