package org.janelia.saalfeldlab.paintera.ui.source.state

import bdv.viewer.Source
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.DoubleExpression
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.RadioButton
import javafx.scene.control.TitledPane
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import org.janelia.saalfeldlab.fx.TextFields
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.extensions.createObjectBinding
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.CloseButton
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.Consumer

class StatePane(
    private val state: SourceState<*, *>,
    private val sourceInfo: SourceInfo,
    activeSourceRadioButtonGroup: ToggleGroup,
    remove: Consumer<Source<*>>,
    width: DoubleExpression
) {

    private val _name = state.nameProperty()

    private val _isCurrentSource = sourceInfo.isCurrentSource(state.dataSource)

    private val _isVisible = state.isVisibleProperty

    var name: String
        get() = _name.get()
        set(name) = _name.set(name)

    val isCurrentSource: Boolean
        get() = _isCurrentSource.get()

    var isVisible: Boolean
        get() = _isVisible.get()
        set(isVisible) = _isVisible.set(isVisible)

    private val _pane = TitledPane(null, state.preferencePaneNode()).also {
        it.prefWidthProperty().bind(width)
        it.maxWidthProperty().bind(width)
        it.isExpanded = false
        it.alignment = Pos.CENTER_RIGHT
        LOG.debug("_pane width is {} ({})", it.width, width)
    }

    // TODO can we infer this somehow from _pane?
    private val arrowWidth = 50.0

    private val graphicWidth = width.subtract(arrowWidth)

    val pane: Node
        get() = _pane

    init {
        val closeButton = Button(null, CloseButton.createFontAwesome(2.0)).apply {
            onAction = EventHandler { remove.accept(state.dataSource) }
            tooltip = Tooltip("Remove source")
        }
        val activeSource = RadioButton().apply {
            tooltip = Tooltip("Select as active source")
            selectedProperty().addListener { _, _, new -> if (new) sourceInfo.currentSourceProperty().set(state.dataSource) }
            _isCurrentSource.addListener { _, _, newv -> if (newv) isSelected = true }
            isSelected = isCurrentSource
            toggleGroup = activeSourceRadioButtonGroup
        }
        val visibilityIconViewVisible = FontAwesome[FontAwesomeIcon.EYE, 2.0].apply { stroke = Color.BLACK }
        val visibilityIconViewInvisible = FontAwesome[FontAwesomeIcon.EYE_SLASH, 2.0].apply {
            stroke = Color.GRAY
            fill = Color.GRAY
        }

        val visibilityButton = Button(null).also {
            it.onAction = EventHandler { isVisible = !isVisible }
            it.graphicProperty().bind(_isVisible.createObjectBinding { if (isVisible) visibilityIconViewVisible else visibilityIconViewInvisible })
        }.apply {
            maxWidth = 20.0
            tooltip = Tooltip("Toggle visibility")
        }
        val nameField = TextFields.editableOnDoubleClick().apply {
            textProperty().bindBidirectional(_name)
            tooltip = Tooltip().also {
                it.textProperty().bind(_name.createObjectBinding { "Source ${_name.value}: Double click to change name, enter to confirm, escape to discard." })
            }
            backgroundProperty().bind(editableProperty().createObjectBinding { if (isEditable) EDITABLE_BACKGROUND else UNEDITABLE_BACKGROUND })
            HBox.setHgrow(this, Priority.ALWAYS)
        }
        val titleBox = HBox(
            nameField,
            Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
            activeSource,
            visibilityButton,
            closeButton
        ).apply {
            alignment = Pos.CENTER
            padding = Insets(0.0, RIGHT_PADDING, 0.0, LEFT_PADDING)
        }
        with(TitledPaneExtensions) {
            _pane.graphicsOnly(titleBox)
        }
        // TODO how to get underlined in TextField?
//        nameField.underlineProperty().bind(_isCurrentSource)

    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private const val LEFT_PADDING = 0.0

        private const val RIGHT_PADDING = 0.0

        private val EDITABLE_BACKGROUND = Background(BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets(-1.4, 0.0, 1.0, 2.0)))

        private val UNEDITABLE_BACKGROUND = Background(BackgroundFill(Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5), CornerRadii.EMPTY, Insets.EMPTY))

    }

}
