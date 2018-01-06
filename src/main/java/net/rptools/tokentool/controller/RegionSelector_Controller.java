/*
 * This software Copyright by the RPTools.net development team, and licensed under the Affero GPL Version 3 or, at your option, any later version.
 *
 * TokenTool Source Code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License * along with this source Code. If not, please visit <http://www.gnu.org/licenses/> and specifically the Affero license text
 * at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.tokentool.controller;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class RegionSelector_Controller {
	private static final Logger log = LogManager.getLogger(RegionSelector_Controller.class);
	private TokenTool_Controller tokenTool_Controller;

	@FXML private StackPane selectionStackPane;
	@FXML private Button captureButton;

	@FXML
	void initialize() {
		assert selectionStackPane != null : "fx:id=\"selectionStackPane\" was not injected: check your FXML file 'RegionSelector.fxml'.";
		assert captureButton != null : "fx:id=\"captureButton\" was not injected: check your FXML file 'RegionSelector.fxml'.";
	}

	@FXML
	void captureButton_onAction(ActionEvent event) {
		Stage stage = (Stage) captureButton.getScene().getWindow();
		Scene scene = stage.getScene();
		int x = (int) stage.getX();
		int y = (int) stage.getY();
		int sceneWidth = (int) scene.getWidth();
		int sceneHeight = (int) scene.getHeight();

		try {
			stage.hide();
			// Robot robot = new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice());
			log.info("Rect: " + new Rectangle(x, y, sceneWidth, sceneHeight));
			BufferedImage bi = new Robot().createScreenCapture(new Rectangle(x, y, sceneWidth, sceneHeight));
			tokenTool_Controller.updatePortrait(SwingFXUtils.toFXImage(bi, null));
		} catch (AWTException e) {
			log.error(e);
		}

		stage.close();
	}

	public void setController(TokenTool_Controller controller) {
		tokenTool_Controller = controller;
	}
}
