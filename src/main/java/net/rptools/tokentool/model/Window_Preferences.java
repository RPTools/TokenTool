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
package net.rptools.tokentool.model;

import com.google.gson.Gson;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * Store and return needed Stage (window) attributes as a JSON for easy storage in user preferences
 */
public class Window_Preferences {

  private static final Logger log = LogManager.getLogger(Window_Preferences.class);

  double windowX = 0, windowY = 0;
  double windowWidth, windowHeight;

  public Window_Preferences(Stage stage) {
    windowX = stage.getX();
    windowY = stage.getY();
    windowWidth = stage.getWidth();
    windowHeight = stage.getHeight();
  }

  public Window_Preferences(double windowWidth, double windowHeight) {
    this.windowWidth = windowWidth;
    this.windowHeight = windowHeight;
  }

  public void setWindow(Stage stage) {
    stage.setX(windowX);
    stage.setY(windowY);
    if (windowWidth > 10) {
      stage.setWidth(windowWidth);
    }
    if (windowHeight > 10) {
      stage.setHeight(windowHeight);
    }
  }

  public double getWindowX() {
    return windowX;
  }

  public void setWindowX(double windowX) {
    this.windowX = windowX;
  }

  public double getWindowY() {
    return windowY;
  }

  public void setWindowY(double windowY) {
    this.windowY = windowY;
  }

  public double getWindowWidth() {
    return windowWidth;
  }

  public void setWindowWidth(double windowWidth) {
    this.windowWidth = windowWidth;
  }

  public double getWindowHeight() {
    return windowHeight;
  }

  public void setWindowHeight(double windowHeight) {
    this.windowHeight = windowHeight;
  }

  public String toJson() {
    String json = new Gson().toJson(this);
    log.debug("JSON output: " + json);
    return json;
  }
}
