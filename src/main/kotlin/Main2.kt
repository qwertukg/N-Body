import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.application.ConditionalFeature
import javafx.scene.*
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Sphere
import javafx.scene.transform.Rotate
import javafx.stage.Stage

class Minimal3DCheck : Application() {

    override fun start(stage: Stage) {
        println("start() called!")  // чтобы убедиться, что именно этот класс запускается

        // 1) Создаём группу для 3D-объектов
        val group3D = Group()

        // 2) Сфера радиуса 50, располагаем её в (0,0,0)
        val sphere = Sphere(50.0).apply {
            material = PhongMaterial(Color.LIGHTBLUE)
        }

        // 3) Добавляем фоновое (окружающее) освещение,
        //    чтобы сфера с PhongMaterial не была чёрной.
        val ambientLight = AmbientLight(Color.WHITE)

        // 4) Добавляем сферу и свет в 3D-группу
        group3D.children.addAll(sphere, ambientLight)

        // 5) Камера (PerspectiveCamera) — настраиваем "истинное" 3D
        val camera = PerspectiveCamera(true).apply {
            // Диапазон видимости
            nearClip = 0.1
            farClip = 2_000.0
            // Отодвигаем камеру по оси Z "назад" (в отрицательную сторону),
            // чтобы сфера (которая в (0,0,0)) была "перед" камерой.
            translateZ = -600.0
        }

        // 6) Создаём SubScene с 3D-контентом,
        //    включаем Z-буфер и антиалайзинг.
        val subScene = SubScene(
            group3D,
            800.0,
            600.0,
            true,
            SceneAntialiasing.BALANCED
        ).apply {
            this.camera = camera
            this.fill = Color.BLACK  // фон суб-сцены
        }


        // 7) Кладём SubScene в корневой узел JavaFX (Group или Pane)
        val root = Group(subScene)
        // 8) Создаём основную сцену
        val mainScene = Scene(root, 800.0, 600.0, true, SceneAntialiasing.BALANCED)
        stage.scene = mainScene
        stage.title = "Minimal 3D Check"
        stage.show()

        // 9) Для наглядности вращаем сферу вокруг Y
        val timer = object : AnimationTimer() {
            var angle = 0.0
            override fun handle(now: Long) {
                angle += 1.0
                sphere.rotationAxis = Rotate.Y_AXIS
                sphere.rotate = angle
            }
        }
        timer.start()

        // Проверим, поддерживается ли SCENE3D
        println("SCENE3D supported? ${Platform.isSupported(ConditionalFeature.SCENE3D)}")
    }
}

fun main() {
    Application.launch(Minimal3DCheck::class.java)
}