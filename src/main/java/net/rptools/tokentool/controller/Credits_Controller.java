/*
 * This software Copyright by the RPTools.net development team, and licensed under the Affero GPL Version 3 or, at your option, any later version.
 *
 * TokenTool Source Code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License * along with this source Code. If not, please visit <http://www.gnu.org/licenses/> and specifically the Affero license text
 * at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.tokentool.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.client.TokenTool;

public class Credits_Controller {
	private static final Logger log = LogManager.getLogger(Credits_Controller.class);

	@FXML private Hyperlink rptoolsHyperlink;
	@FXML private Label versionLabel;

	@FXML
	void initialize() {
		assert rptoolsHyperlink != null : "fx:id=\"rptoolsHyperlink\" was not injected: check your FXML file 'Credits.fxml'.";
		assert versionLabel != null : "fx:id=\"versionLabel\" was not injected: check your FXML file 'Credits.fxml'.";

		versionLabel.setText(versionLabel.getText() + " " + TokenTool.getVersion());
	}

	@FXML
	void rptoolsHyperlink_onAction(ActionEvent event) {
		log.info("Launching browser for URL " + AppConstants.RPTOOLS_URL);
		TokenTool.getInstance().getHostServices().showDocument(AppConstants.RPTOOLS_URL);
	}
}
