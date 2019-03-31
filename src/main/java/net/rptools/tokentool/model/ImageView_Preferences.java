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
import java.io.File;
import java.net.MalformedURLException;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * Store and return needed ImageView attributes as a JSON for easy storage in user preferences
 */
public class ImageView_Preferences {
  private static final Logger log = LogManager.getLogger(ImageView_Preferences.class);

  double translateX, translateY, rotation, scale;
  String filePath;
  Color backgroundColor; // We'll save the background color with the background imageview...

  public ImageView_Preferences(ImageView imageView, String filePath) {
    setRotation(imageView.getRotate());
    setScale(imageView.getScaleY());
    setTranslateX(imageView.getTranslateX());
    setTranslateY(imageView.getTranslateY());
    setFileURI(filePath);
  }

  public ImageView_Preferences(ImageView imageView, String filePath, Color color) {
    setRotation(imageView.getRotate());
    setScale(imageView.getScaleY());
    setTranslateX(imageView.getTranslateX());
    setTranslateY(imageView.getTranslateY());
    setFileURI(filePath);

    setBackgroundColor(color);
  }

  public double getTranslateX() {
    return translateX;
  }

  public void setTranslateX(double translateX) {
    this.translateX = translateX;
  }

  public double getTranslateY() {
    return translateY;
  }

  public void setTranslateY(double translateY) {
    this.translateY = translateY;
  }

  public double getRotation() {
    return rotation;
  }

  public void setRotation(double rotation) {
    this.rotation = rotation;
  }

  public double getScale() {
    return scale;
  }

  public void setScale(double scale) {
    this.scale = scale;
  }

  public String getFileURI() {
    return filePath;
  }

  public void setFileURI(String fileURI) {
    this.filePath = fileURI;
  }

  public Color getBackgroundColor() {
    // stupid error even though it tests as an instanceof Color!? wtf...
    // com.google.gson.internal.LinkedTreeMap cannot be cast to
    // javafx.graphics@10.0.1/com.sun.prism.paint.Paint
    // return backgroundColor;

    return new Color(
        backgroundColor.getRed(),
        backgroundColor.getGreen(),
        backgroundColor.getBlue(),
        backgroundColor.getOpacity());
  }

  public void setBackgroundColor(Color backgroundColor) {
    this.backgroundColor = backgroundColor;
  }

  public ImageView toImageView(ImageView imageView) {
    if (filePath != null) {
      try {
        log.debug("Loading image from preferences " + filePath);
        Image image = new Image(new File(filePath).toURI().toURL().toExternalForm());
        imageView.setImage(image);
        imageView.setFitWidth(image.getWidth());
        imageView.setFitHeight(image.getHeight());
      } catch (MalformedURLException e) {
        log.error("Unable to load image " + filePath, e);
      }
    }

    imageView.setTranslateX(getTranslateX());
    imageView.setTranslateY(getTranslateY());
    imageView.setRotate(getRotation());
    imageView.setScaleX(getScale());
    imageView.setScaleY(getScale());

    return imageView;
  }

  public String toJson() {
    String json = new Gson().toJson(this).toString();
    log.debug("JSON output: " + json);
    return json;
  }
}
