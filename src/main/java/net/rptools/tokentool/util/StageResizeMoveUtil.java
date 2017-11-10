/*
 * This software Copyright by the RPTools.net development team, and licensed under the Affero GPL Version 3 or, at your option, any later version.
 *
 * TokenTool Source Code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License * along with this source Code. If not, please visit <http://www.gnu.org/licenses/> and specifically the Affero license text
 * at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.tokentool.util;

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class StageResizeMoveUtil {
	public static void addResizeListener(Stage stage) {
		addResizeListener(stage, 0, 0, Double.MAX_VALUE, Double.MAX_VALUE);
	}

	public static void addResizeListener(Stage stage, double minWidth, double minHeight, double maxWidth, double maxHeight) {
		ResizeListener resizeListener = new ResizeListener(stage);
		stage.getScene().addEventHandler(MouseEvent.MOUSE_MOVED, resizeListener);
		stage.getScene().addEventHandler(MouseEvent.MOUSE_PRESSED, resizeListener);
		stage.getScene().addEventHandler(MouseEvent.MOUSE_DRAGGED, resizeListener);
		stage.getScene().addEventHandler(MouseEvent.MOUSE_EXITED, resizeListener);
		stage.getScene().addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, resizeListener);

		resizeListener.setMinWidth(minWidth);
		resizeListener.setMinHeight(minHeight);
		resizeListener.setMaxWidth(maxWidth);
		resizeListener.setMaxHeight(maxHeight);

		ObservableList<Node> children = stage.getScene().getRoot().getChildrenUnmodifiable();
		for (Node child : children) {
			addListenerDeeply(child, resizeListener);
		}
	}

	private static void addListenerDeeply(Node node, EventHandler<MouseEvent> listener) {
		node.addEventHandler(MouseEvent.MOUSE_MOVED, listener);
		node.addEventHandler(MouseEvent.MOUSE_PRESSED, listener);
		node.addEventHandler(MouseEvent.MOUSE_DRAGGED, listener);
		node.addEventHandler(MouseEvent.MOUSE_EXITED, listener);
		node.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, listener);
		if (node instanceof Parent) {
			Parent parent = (Parent) node;
			ObservableList<Node> children = parent.getChildrenUnmodifiable();
			for (Node child : children) {
				addListenerDeeply(child, listener);
			}
		}
	}

	static class ResizeListener implements EventHandler<MouseEvent> {
		private Stage stage;
		private Cursor cursorEvent = Cursor.DEFAULT;
		private int border = 10;
		private double startX = 0;
		private double startY = 0;

		// Max and min sizes for controlled stage
		private double minWidth;
		private double maxWidth;
		private double minHeight;
		private double maxHeight;

		// For stage movement
		private double xOffset;
		private double yOffset;

		public ResizeListener(Stage stage) {
			this.stage = stage;
		}

		public void setMinWidth(double minWidth) {
			this.minWidth = minWidth;
		}

		public void setMaxWidth(double maxWidth) {
			this.maxWidth = maxWidth;
		}

		public void setMinHeight(double minHeight) {
			this.minHeight = minHeight;
		}

		public void setMaxHeight(double maxHeight) {
			this.maxHeight = maxHeight;
		}

		@Override
		public void handle(MouseEvent mouseEvent) {
			EventType<? extends MouseEvent> mouseEventType = mouseEvent.getEventType();
			Scene scene = stage.getScene();

			double mouseEventX = mouseEvent.getSceneX(),
					mouseEventY = mouseEvent.getSceneY(),
					sceneWidth = scene.getWidth(),
					sceneHeight = scene.getHeight();

			if (MouseEvent.MOUSE_MOVED.equals(mouseEventType)) {
				if (mouseEventX < border && mouseEventY < border) {
					cursorEvent = Cursor.NW_RESIZE;
				} else if (mouseEventX < border && mouseEventY > sceneHeight - border) {
					cursorEvent = Cursor.SW_RESIZE;
				} else if (mouseEventX > sceneWidth - border && mouseEventY < border) {
					cursorEvent = Cursor.NE_RESIZE;
				} else if (mouseEventX > sceneWidth - border && mouseEventY > sceneHeight - border) {
					cursorEvent = Cursor.SE_RESIZE;
				} else if (mouseEventX < border) {
					cursorEvent = Cursor.W_RESIZE;
				} else if (mouseEventX > sceneWidth - border) {
					cursorEvent = Cursor.E_RESIZE;
				} else if (mouseEventY < border) {
					cursorEvent = Cursor.N_RESIZE;
				} else if (mouseEventY > sceneHeight - border) {
					cursorEvent = Cursor.S_RESIZE;
				} else {
					cursorEvent = Cursor.MOVE;
				}
				scene.setCursor(cursorEvent);
			} else if (MouseEvent.MOUSE_EXITED.equals(mouseEventType) || MouseEvent.MOUSE_EXITED_TARGET.equals(mouseEventType)) {
				scene.setCursor(Cursor.DEFAULT);
			} else if (MouseEvent.MOUSE_PRESSED.equals(mouseEventType)) {
				startX = stage.getWidth() - mouseEventX;
				startY = stage.getHeight() - mouseEventY;

				if (Cursor.MOVE.equals(cursorEvent)) {
					xOffset = stage.getX() - mouseEvent.getScreenX();
					yOffset = stage.getY() - mouseEvent.getScreenY();
				}
			} else if (MouseEvent.MOUSE_DRAGGED.equals(mouseEventType)) {
				if (!Cursor.DEFAULT.equals(cursorEvent) && !Cursor.MOVE.equals(cursorEvent)) {
					if (!Cursor.W_RESIZE.equals(cursorEvent) && !Cursor.E_RESIZE.equals(cursorEvent)) {
						double minHeight = stage.getMinHeight() > (border * 2) ? stage.getMinHeight() : (border * 2);
						if (Cursor.NW_RESIZE.equals(cursorEvent) || Cursor.N_RESIZE.equals(cursorEvent)
								|| Cursor.NE_RESIZE.equals(cursorEvent)) {
							if (stage.getHeight() > minHeight || mouseEventY < 0) {
								setStageHeight(stage.getY() - mouseEvent.getScreenY() + stage.getHeight());
								stage.setY(mouseEvent.getScreenY());
							}
						} else {
							if (stage.getHeight() > minHeight || mouseEventY + startY - stage.getHeight() > 0) {
								setStageHeight(mouseEventY + startY);
							}
						}
					}

					if (!Cursor.N_RESIZE.equals(cursorEvent) && !Cursor.S_RESIZE.equals(cursorEvent)) {
						double minWidth = stage.getMinWidth() > (border * 2) ? stage.getMinWidth() : (border * 2);
						if (Cursor.NW_RESIZE.equals(cursorEvent) || Cursor.W_RESIZE.equals(cursorEvent)
								|| Cursor.SW_RESIZE.equals(cursorEvent)) {
							if (stage.getWidth() > minWidth || mouseEventX < 0) {
								setStageWidth(stage.getX() - mouseEvent.getScreenX() + stage.getWidth());
								stage.setX(mouseEvent.getScreenX());
							}
						} else {
							if (stage.getWidth() > minWidth || mouseEventX + startX - stage.getWidth() > 0) {
								setStageWidth(mouseEventX + startX);
							}
						}
					}
				} else if (Cursor.MOVE.equals(cursorEvent)) {
					stage.setX(mouseEvent.getScreenX() + xOffset);
					stage.setY(mouseEvent.getScreenY() + yOffset);
				}
			}
		}

		private void setStageWidth(double width) {
			width = Math.min(width, maxWidth);
			width = Math.max(width, minWidth);
			stage.setWidth(width);
		}

		private void setStageHeight(double height) {
			height = Math.min(height, maxHeight);
			height = Math.max(height, minHeight);
			stage.setHeight(height);
		}

	}
}
