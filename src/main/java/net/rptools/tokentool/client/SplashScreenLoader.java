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

import java.util.ResourceBundle;
import javafx.animation.FadeTransition;
import javafx.application.Preloader;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import javafx.util.Duration;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.controller.SplashScreen_Controller;

public class SplashScreenLoader extends Preloader {
  private Stage stage;
  private StackPane root;
  private SplashScreen_Controller controller;

  public void start(Stage stage) throws Exception {
    setUserAgentStylesheet(
        STYLESHEET_CASPIAN); // I like the look of the this progress bar better for this screen...

    FXMLLoader fxmlLoader =
        new FXMLLoader(
            getClass().getResource(AppConstants.SPLASH_SCREEN_FXML),
            ResourceBundle.getBundle(AppConstants.TOKEN_TOOL_BUNDLE));
    root = (StackPane) fxmlLoader.load();
    controller = (SplashScreen_Controller) fxmlLoader.getController();
    controller.setVersionLabel(TokenTool.getVersion());

    Scene scene = new Scene(root);
    stage.initStyle(StageStyle.UNDECORATED);
    stage.getIcons().add(new Image(getClass().getResourceAsStream(AppConstants.TOKEN_TOOL_ICON)));
    stage.setScene(scene);
    stage.show();

    this.stage = stage;
  }

  @Override
  public void handleStateChangeNotification(StateChangeNotification evt) {
    if (evt.getType() == StateChangeNotification.Type.BEFORE_START) {
      if (stage.isShowing()) {
        // fade out, hide stage at the end of animation
        FadeTransition ft = new FadeTransition(Duration.millis(1000), stage.getScene().getRoot());
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        final Stage s = stage;
        EventHandler<ActionEvent> eh =
            new EventHandler<ActionEvent>() {
              public void handle(ActionEvent t) {
                s.hide();
              }
            };
        ft.setOnFinished(eh);
        ft.play();
      } else {
        stage.hide();
      }
    }
  }

  @Override
  public void handleApplicationNotification(PreloaderNotification pn) {
    if (pn instanceof ProgressNotification) {
      // expect application to send us progress notifications with progress ranging from 0 to 1.0
      double v = ((ProgressNotification) pn).getProgress();
      controller.setLoadProgress(v);
    } else if (pn instanceof StateChangeNotification) {
      // hide after get any state update from application
      stage.hide();
    }
  }
}
