/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * TokenTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.tokentool.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.AppPreferences;
import net.rptools.tokentool.controller.PdfViewer_Controller;
import net.rptools.tokentool.controller.TokenTool_Controller;
import net.rptools.tokentool.model.Window_Preferences;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PdfViewer {
  private static final Logger log = LogManager.getLogger(PdfViewer.class);
  private Stage stage;

  public PdfViewer(File selectedPDF, TokenTool_Controller tokenTool_Controller) {
    Parent root;
    FXMLLoader fxmlLoader =
        new FXMLLoader(
            getClass().getResource(AppConstants.PDF_VIEW_FXML),
            ResourceBundle.getBundle(AppConstants.TOKEN_TOOL_BUNDLE));

    try {
      root = fxmlLoader.load();
    } catch (IOException e) {
      log.error("Error loading PdfViewer Stage!", e);
      return;
    }

    PdfViewer_Controller pdfViewerController = fxmlLoader.<PdfViewer_Controller>getController();

    stage = new Stage();
    Scene scene = new Scene(root);

    stage.getIcons().add(new Image(getClass().getResourceAsStream(AppConstants.TOKEN_TOOL_ICON)));
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle(selectedPDF.getName());
    stage.setScene(scene);

    stage.setOnCloseRequest(
        new EventHandler<WindowEvent>() {
          @Override
          public void handle(WindowEvent event) {
            log.debug("Shutting down PDF Viewer...");
            AppPreferences.setPreference(
                AppPreferences.WINDOW_PDF_PREFERENCES, new Window_Preferences(stage).toJson());
            stage.hide();
            pdfViewerController.close();
          }
        });

    String preferencesJson =
        AppPreferences.getPreference(AppPreferences.WINDOW_PDF_PREFERENCES, null);
    if (preferencesJson != null) {
      Window_Preferences window_Preferences =
          new Gson().fromJson(preferencesJson, new TypeToken<Window_Preferences>() {}.getType());
      window_Preferences.setWindow(stage);
    }

    stage.show();
    pdfViewerController.loadPDF(selectedPDF, tokenTool_Controller, stage);
  }
}
