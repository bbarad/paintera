package bdv.fx.viewer;

import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;

public class CanvasPane extends Pane {

  private final Canvas canvas;

  public CanvasPane(final double width, final double height) {

	canvas = new Canvas(width, height);
	getChildren().add(canvas);
  }

  public Canvas getCanvas() {

	return canvas;
  }

  @Override
  protected void layoutChildren() {

	super.layoutChildren();
	final double x = snappedLeftInset();
	final double y = snappedTopInset();
	final double w = snapSizeX(getWidth()) - x - snappedRightInset();
	final double h = snapSizeY(getHeight()) - y - snappedBottomInset();
	if (x != canvas.getLayoutX() || y != canvas.getLayoutY() || w != canvas.getWidth() || h != canvas.getHeight()) {
	  canvas.setLayoutX(x);
	  canvas.setLayoutY(y);
	  canvas.setWidth(w);
	  canvas.setHeight(h);
	}
  }
}
