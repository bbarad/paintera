package org.janelia.saalfeldlab.paintera

import ch.qos.logback.classic.Level
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.input.MouseEvent
import javafx.stage.Modality
import javafx.stage.Stage
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils
import org.janelia.saalfeldlab.util.n5.universe.N5Factory
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.lang.invoke.MethodHandles
import kotlin.system.exitProcess

class Paintera : Application() {

    val mainWindow = PainteraMainWindow()

    init {
        application = this
    }

    override fun start(primaryStage: Stage) {
        val painteraArgs = PainteraCommandLineArgs()
        val cmd = CommandLine(painteraArgs).apply {
            registerConverter(Level::class.java, LogUtils.Logback.Levels.CmdLineConverter())
        }
        val exitCode = cmd.execute(*parameters.raw.toTypedArray())
        val parsedSuccessfully = (cmd.getExecutionResult() ?: false) && exitCode == 0
        if (!parsedSuccessfully) {
            Platform.exit()
            return
        }
        Platform.setImplicitExit(true)

        val projectPath = painteraArgs.project()?.let { File(it).absoluteFile }
        if (projectPath != null && !projectPath.exists()) {
            /* does the project dir exist? If not, try to make it*/
            projectPath.mkdirs()
        }
        if (projectPath != null && !projectPath.canWrite()) {
            LOG.info("User doesn't have write permissions for project at '$projectPath'. Exiting.")
            PainteraAlerts.alert(Alert.AlertType.ERROR).apply {
                headerText = "Invalid Permissions"
                contentText = "User doesn't have write permissions for project at '$projectPath'. Exiting."
            }.showAndWait()
            Platform.exit()
        } else if (!PainteraAlerts.ignoreLockFileDialog(mainWindow.projectDirectory, projectPath, "_Quit", false)) {
            LOG.info("Paintera project `$projectPath' is locked, will exit.")
            Platform.exit()
        } else {
            try {
                mainWindow.deserialize()
            } catch (error: Exception) {
                LOG.error("Unable to deserialize Paintera project `{}'.", projectPath, error)
                Exceptions.exceptionAlert(Constants.NAME, "Unable to open Paintera project", error, owner = mainWindow.pane.scene?.window).apply {
                    setOnHidden { exitProcess(Error.UNABLE_TO_DESERIALIZE_PROJECT.code) }
                    initModality(Modality.NONE)
                    show()
                }
                return
            }

            mainWindow.properties.loggingConfig.let { config ->
                painteraArgs.logLevel?.let { config.rootLoggerLevel = it }
                painteraArgs.logLevelsByName?.forEach { (name, level) -> name?.let { config.setLogLevelFor(it, level) } }
            }
            painteraArgs.addToViewer(mainWindow.baseView) { mainWindow.projectDirectory.actualDirectory?.absolutePath }

            if (painteraArgs.wereScreenScalesProvided())
                mainWindow.properties.screenScalesConfig.screenScalesProperty().set(ScreenScalesConfig.ScreenScales(*painteraArgs.screenScales()))

            // TODO figure out why this update is necessary?
            mainWindow.properties.screenScalesConfig.screenScalesProperty().apply {
                val scales = ScreenScalesConfig.ScreenScales(*get().scalesCopy.clone())
                set(ScreenScalesConfig.ScreenScales(*scales.scalesCopy.map { it * 0.5 }.toDoubleArray()))
                set(scales)
            }

            primaryStage.scene = Scene(mainWindow.pane)
            primaryStage.scene.addEventFilter(MouseEvent.ANY, mainWindow.mouseTracker)
            mainWindow.setupStage(primaryStage)
            primaryStage.show()

            mainWindow.properties.viewer3DConfig.bindViewerToConfig(mainWindow.baseView.viewer3D())
            // window settings seem to work only when set during runlater
            Platform.runLater {
                mainWindow.properties.windowProperties.let {
                    primaryStage.width = it.widthProperty.get().toDouble()
                    primaryStage.height = it.heightProperty.get().toDouble()
                    it.widthProperty.bind(primaryStage.widthProperty())
                    it.heightProperty.bind(primaryStage.heightProperty())
                    it.isFullScreen.addListener { _, _, newv -> primaryStage.isFullScreen = newv }
                    // have to runLater here because otherwise width and height take weird values
                    Platform.runLater { primaryStage.isFullScreen = it.isFullScreen.value }
                }
            }
        }
    }

    object Constants {
        const val NAME = "Paintera"
        const val PAINTERA_KEY = "paintera"
    }

    enum class Error(val code: Int, val description: String) {
        NO_PROJECT_SPECIFIED(1, "No Paintera project specified"),
        UNABLE_TO_DESERIALIZE_PROJECT(2, "Unable to deserialize Paintera project")
    }

    companion object {

        @JvmStatic
        val n5Factory = N5Factory()

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        @JvmStatic
        fun main(args: Array<String>) = launch(Paintera::class.java, *args)

        @JvmStatic
        lateinit var application: Application
            private set
    }

}


