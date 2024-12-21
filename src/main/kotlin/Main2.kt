import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.ConditionalFeature
import javafx.application.Platform
import javafx.scene.*
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Sphere
import javafx.scene.transform.Rotate
import javafx.stage.Stage

class Minimal3DTest : Application() {


    override fun start(stage: Stage) {
        val root3D = Group()

        // Сфера радиуса 50
        val sphere = Sphere(500.0)
        sphere.material = PhongMaterial(Color.LIGHTBLUE)
        // Уходим в "минус" по оси Z, потому что камера смотрит вдоль -Z по умолчанию
        sphere.translateZ = -800.0

        // Добавим простой рассеянный свет (иначе при PhongMaterial всё будет чёрным)
        val light = AmbientLight(Color.WHITE)

        // Добавляем и сферу, и свет в 3D-группу
        root3D.children.addAll(sphere, light)

        // Камера (PerspectiveCamera) со включённым «истинным» 3D
        val camera = PerspectiveCamera(true).apply {
            // Диапазон видимости
            nearClip = 1.0
            farClip = 2000.0
            // Камера располагается «по умолчанию» в (0,0,0),
            // смотрит вдоль отрицательной оси Z
        }

        // Создаём SubScene с 3D-контентом
        val subScene = SubScene(
            root3D,
            800.0,
            600.0,
            true,
            SceneAntialiasing.DISABLED
        ).apply {
            fill = Color.BLACK  // фон «суб-сцены» чёрный
        }

        subScene.camera = camera

        // Общая сцена, в которую вложена subScene
        val mainScene = Scene(Group(subScene), 800.0, 600.0, true, SceneAntialiasing.DISABLED)

        stage.scene = mainScene
        stage.title = "Minimal 3D Test"
        stage.show()

        val timer = object : AnimationTimer() {
            var angle = 0.0
            override fun handle(now: Long) {
                angle += 0.5
                sphere.rotationAxis = Rotate.Y_AXIS
                sphere.rotate = angle
            }
        }
        timer.start()

        println("SCENE3D supported? " + Platform.isSupported(ConditionalFeature.SCENE3D))

    }
}

fun main() {
    Application.launch(Minimal3DTest::class.java)
}
