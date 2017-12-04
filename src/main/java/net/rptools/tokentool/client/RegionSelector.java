/*
 * This software Copyright by the RPTools.net development team, and licensed under the Affero GPL Version 3 or, at your option, any later version.
 *
 * TokenTool Source Code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License * along with this source Code. If not, please visit <http://www.gnu.org/licenses/> and specifically the Affero license text
 * at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.tokentool.client;

import java.util.ResourceBundle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.controller.RegionSelector_Controller;
import net.rptools.tokentool.controller.TokenTool_Controller;
import net.rptools.tokentool.util.StageResizeMoveUtil;

public class RegionSelector {
	private static final Logger log = LogManager.getLogger(RegionSelector.class);

	private RegionSelector_Controller regionSelector_Controller;
	private Stage stage;

	public RegionSelector(TokenTool_Controller tokenTool_Controller) {
		try {
			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(AppConstants.REGION_SELECTOR_FXML), ResourceBundle.getBundle(AppConstants.TOKEN_TOOL_BUNDLE));
			Parent root = (Parent) fxmlLoader.load();
			regionSelector_Controller = (RegionSelector_Controller) fxmlLoader.getController();
			regionSelector_Controller.setController(tokenTool_Controller);

			stage = new Stage();
			Scene scene = new Scene(root);

			scene.setFill(Color.TRANSPARENT);
			// scene.setFill(new Color(0, 0, 0, 0.0));
			stage.initOwner(TokenTool.getInstance().getStage());
			stage.initStyle(StageStyle.TRANSPARENT);
			stage.initModality(Modality.APPLICATION_MODAL);
			stage.getIcons().add(new Image(getClass().getResourceAsStream(AppConstants.REGION_SELECTOR_ICON)));
			stage.setScene(scene);

			StageResizeMoveUtil.addResizeListener(stage);

			stage.show();
		} catch (Exception e) {
			log.error(e);
		}
	}

	public void close() {
		stage.close();
	}
}