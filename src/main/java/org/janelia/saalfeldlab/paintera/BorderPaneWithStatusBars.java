package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms;
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfigNode;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfig;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfigNode;
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.ui.Crosshair;
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs;
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Source;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import net.imglib2.RealPoint;

public class BorderPaneWithStatusBars
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final BorderPane pane;

	private final AnchorPane statusBar;

	private final ScrollPane sideBar;

	private final Label currentSourceStatus;

	private final Label viewerCoordinateStatus;

	private final Label worldCoordinateStatus;

	private final Label valueStatus;

	private final ResizeOnLeftSide resizeSideBar;

	private final CrosshairConfig crosshairConfig = new CrosshairConfig();

	@SuppressWarnings( "unused" )
	private final Map< ViewerAndTransforms, Crosshair > crossHairs;

	private final OrthoSliceConfig orthoSliceConfig;

	private final Map< ViewerAndTransforms, OrthoSliceFX > orthoSlices;

	private final ObservableObjectValue< ViewerAndTransforms > currentFocusHolderWithState;

	private final Button saveProjectButton;

	public final ObservableObjectValue< ViewerAndTransforms > currentFocusHolder()
	{
		return this.currentFocusHolderWithState;
	}

	public BorderPane getPane()
	{
		return this.pane;
	}

	public void setViewerCoordinateStatus( final RealPoint p )
	{
		InvokeOnJavaFXApplicationThread.invoke( () -> viewerCoordinateStatus.setText( p == null ? "N/A" : String.format( "(% 4d, % 4d)", ( int ) p.getDoublePosition( 0 ), ( int ) p.getDoublePosition( 1 ) ) ) );
	}

	public void setWorldCoorinateStatus( final RealPoint p )
	{
		InvokeOnJavaFXApplicationThread.invoke( () -> worldCoordinateStatus.setText( p == null ? "N/A" : CoordinateDisplayListener.worldToString( p ) ) );
	}

	public void setCurrentValue( final String s )
	{
		InvokeOnJavaFXApplicationThread.invoke( () -> valueStatus.setText( s ) );
	}

	public Collection< OrthoSliceFX > orthoSlices()
	{
		return this.orthoSlices.values();
	}

	public BorderPaneWithStatusBars(
			final PainteraBaseView center,
			final Supplier< String > project )
	{
		LOG.debug( "Construction {}", BorderPaneWithStatusBars.class.getName() );
		this.pane = new BorderPane( center.orthogonalViews().pane() );

		this.currentFocusHolderWithState = currentFocusHolder( center.orthogonalViews() );

		this.currentSourceStatus = new Label();
		this.viewerCoordinateStatus = new Label();
		this.worldCoordinateStatus = new Label();
		this.valueStatus = new Label();
		final CheckBox showStatusBar = new CheckBox();
		showStatusBar.setFocusTraversable( false );
		showStatusBar.setTooltip( new Tooltip( "If not selected, status bar will only show on mouse-over" ) );

		final HBox statusDisplays = new HBox( 5, viewerCoordinateStatus, worldCoordinateStatus, valueStatus );

		this.crossHairs = makeCrosshairs( center.orthogonalViews(), crosshairConfig, Colors.CREMI, Color.WHITE.deriveColor( 0, 1, 1, 0.5 ) );

		this.orthoSlices = makeOrthoSlices( center.orthogonalViews(), center.viewer3D().meshesGroup(), center.sourceInfo() );
		this.orthoSliceConfig = new OrthoSliceConfig(
				orthoSlices.get( center.orthogonalViews().topLeft() ),
				orthoSlices.get( center.orthogonalViews().topRight() ),
				orthoSlices.get( center.orthogonalViews().bottomLeft() ),
				center.orthogonalViews().topLeft().viewer().visibleProperty(),
				center.orthogonalViews().topRight().viewer().visibleProperty(),
				center.orthogonalViews().bottomLeft().viewer().visibleProperty(),
				center.sourceInfo().hasVisibleSources() );

		center.sourceInfo().currentNameProperty().addListener( ( obs, oldv, newv ) -> {
			currentSourceStatus.textProperty().unbind();
			Optional.ofNullable( newv ).ifPresent( currentSourceStatus.textProperty()::bind );
		} );

		this.statusBar = new AnchorPane(
				currentSourceStatus,
				statusDisplays,
				showStatusBar );

		final Tooltip currentSourceStatusToolTip = new Tooltip();
		currentSourceStatusToolTip.textProperty().bind( currentSourceStatus.textProperty() );
		currentSourceStatus.setTooltip( currentSourceStatusToolTip );

		currentSourceStatus.setMaxWidth( 95.0 );
		viewerCoordinateStatus.setMaxWidth( 115.0 );
		worldCoordinateStatus.setMaxWidth( 245.0 );

		viewerCoordinateStatus.setFont( Font.font( "Monospaced" ) );
		worldCoordinateStatus.setFont( Font.font( "Monospaced" ) );

		AnchorPane.setLeftAnchor( currentSourceStatus, 0.0 );
		AnchorPane.setLeftAnchor( statusDisplays, 100.0 );
		AnchorPane.setRightAnchor( showStatusBar, 0.0 );

		final BooleanProperty isWithinMarginOfBorder = new SimpleBooleanProperty();
		pane.addEventFilter( MouseEvent.MOUSE_MOVED, e -> isWithinMarginOfBorder.set( e.getY() < pane.getHeight() && pane.getHeight() - e.getY() <= statusBar.getHeight() ) );
		statusBar.visibleProperty().addListener( ( obs, oldv, newv ) -> pane.setBottom( newv ? statusBar : null ) );
		statusBar.visibleProperty().bind( isWithinMarginOfBorder.or( showStatusBar.selectedProperty() ) );
		showStatusBar.setSelected( true );

		currentSourceStatus.setMaxWidth( 45 );

		final BiConsumer< Source< ? >, Exception > onRemoveException = ( s, e ) -> {
			LOG.warn( "Unable to remove source: {}", e.getMessage() );
		};

		final SourceTabs sourceTabs = new SourceTabs(
				center.sourceInfo().currentSourceIndexProperty(),
				MakeUnchecked.onException( center.sourceInfo()::removeSource, onRemoveException ),
				center.sourceInfo() );

		final TitledPane sourcesContents = new TitledPane( "sources", sourceTabs.get() );
		sourcesContents.setExpanded( false );

		final VBox settingsContents = new VBox(
				new CrosshairConfigNode( crosshairConfig ).getContents(),
				new OrthoSliceConfigNode( orthoSliceConfig ).getContents() );
		final TitledPane settings = new TitledPane( "settings", settingsContents );
		settings.setExpanded( false );

		saveProjectButton = new Button( "Save" );

		this.sideBar = new ScrollPane( new VBox( sourcesContents, settings, saveProjectButton ) );
		this.sideBar.setHbarPolicy( ScrollBarPolicy.NEVER );
		this.sideBar.setVbarPolicy( ScrollBarPolicy.AS_NEEDED );
		this.sideBar.setVisible( true );
		this.sideBar.prefWidthProperty().set( 250 );
		sourceTabs.widthProperty().bind( sideBar.prefWidthProperty() );
		settingsContents.prefWidthProperty().bind( sideBar.prefWidthProperty() );

		resizeSideBar = new ResizeOnLeftSide( sideBar, sideBar.prefWidthProperty(), dist -> Math.abs( dist ) < 5 );
	}

	public void toggleSideBar()
	{
		if ( pane.getRight() == null )
		{
			pane.setRight( sideBar );
			resizeSideBar.install();
		}

		else
		{
			resizeSideBar.remove();
			pane.setRight( null );
		}
	}

	public ObjectProperty< EventHandler< ActionEvent > > saveProjectButtonOnActionProperty()
	{
		return this.saveProjectButton.onActionProperty();
	}

	public static Map< ViewerAndTransforms, Crosshair > makeCrosshairs(
			final OrthogonalViews< ? > views,
			final CrosshairConfig config,
			final Color onFocusColor,
			final Color offFocusColor )
	{
		final Map< ViewerAndTransforms, Crosshair > map = new HashMap<>();
		map.put( views.topLeft(), makeCrossHairForViewer( views.topLeft().viewer(), config, onFocusColor, offFocusColor ) );
		map.put( views.topRight(), makeCrossHairForViewer( views.topRight().viewer(), config, onFocusColor, offFocusColor ) );
		map.put( views.bottomLeft(), makeCrossHairForViewer( views.bottomLeft().viewer(), config, onFocusColor, offFocusColor ) );
		config.setOnFocusColor( onFocusColor );
		config.setOutOfFocusColor( offFocusColor );
		return map;
	}

	public static Crosshair makeCrossHairForViewer(
			final ViewerPanelFX viewer,
			final CrosshairConfig config,
			final Color onFocusColor,
			final Color offFocusColor )
	{
		final Crosshair ch = new Crosshair();
		viewer.getDisplay().addOverlayRenderer( ch );
		ch.regularColorProperty().bind( config.outOfFocusColorProperty() );
		ch.highlightColorProperty().bind( config.onFocusColorProperty() );
		ch.wasChangedProperty().addListener( ( obs, oldv, newv ) -> viewer.getDisplay().drawOverlays() );
		ch.isVisibleProperty().bind( config.showCrosshairsProperty() );
		ch.isHighlightProperty().bind( viewer.focusedProperty() );
		return ch;
	}

	public static Map< ViewerAndTransforms, OrthoSliceFX > makeOrthoSlices(
			final OrthogonalViews< ? > views,
			final Group scene,
			final SourceInfo sourceInfo )
	{
		final Map< ViewerAndTransforms, OrthoSliceFX > map = new HashMap<>();
		map.put( views.topLeft(), new OrthoSliceFX( scene, views.topLeft().viewer() ) );
		map.put( views.topRight(), new OrthoSliceFX( scene, views.topRight().viewer() ) );
		map.put( views.bottomLeft(), new OrthoSliceFX( scene, views.bottomLeft().viewer() ) );
		return map;
	}

	public static ObservableObjectValue< ViewerAndTransforms > currentFocusHolder( final OrthogonalViews< ? > views )
	{
		final ViewerAndTransforms tl = views.topLeft();
		final ViewerAndTransforms tr = views.topRight();
		final ViewerAndTransforms bl = views.bottomLeft();
		final ReadOnlyBooleanProperty focusTL = tl.viewer().focusedProperty();
		final ReadOnlyBooleanProperty focusTR = tr.viewer().focusedProperty();
		final ReadOnlyBooleanProperty focusBL = bl.viewer().focusedProperty();

		return Bindings.createObjectBinding(
				() -> {
					return focusTL.get() ? tl : focusTR.get() ? tr : focusBL.get() ? bl : null;
				},
				focusTL,
				focusTR,
				focusBL );

	}

}
