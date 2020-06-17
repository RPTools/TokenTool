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
package net.rptools.tokentool;

import java.io.File;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser.ExtensionFilter;

public class AppConstants {

  public static final String APP_NAME = "TokenTool";

  public static final String TOKEN_TOOL_ICON = "/net/rptools/tokentool/image/token_tool_icon.png";
  public static final String REGION_SELECTOR_ICON =
      "/net/rptools/tokentool/image/region_selector_icon.png";

  public static final String TOKEN_TOOL_BUNDLE = "net.rptools.tokentool.i18n.TokenTool";

  public static final String SPLASH_SCREEN_FXML =
      "/net/rptools/tokentool/view/SplashScreenLoader.fxml";
  public static final String TOKEN_TOOL_FXML = "/net/rptools/tokentool/view/TokenTool.fxml";
  public static final String MANAGE_OVERLAYS_FXML =
      "/net/rptools/tokentool/view/ManageOverlays.fxml";
  public static final String REGION_SELECTOR_FXML =
      "/net/rptools/tokentool/view/RegionSelector.fxml";
  public static final String CREDITS_FXML = "/net/rptools/tokentool/view/Credits.fxml";
  public static final String PDF_VIEW_FXML = "/net/rptools/tokentool/view/PdfView.fxml";

  public static final String RPTOOLS_URL = "http://www.rptools.net";

  public static final String DEFAULT_IMAGE_EXTENSION = ".png";
  public static final String DEFAULT_IMAGE_EXTENSION_DESCRIPTION = "PNG Image";
  public static final String DEFAULT_TOKEN_NAME = "token";
  public static final String DEFAULT_FILE_NAME_SUFFIX = "0001";

  public static final String VALID_FILE_NAME_PATTERN = "[a-zA-Z0-9._ `~!@#$%^&()\\-=+\\[\\]{}',]*";
  public static final String VALID_FILE_NAME_REPLACEMENT_PATTERN =
      "[^a-zA-Z0-9._ `~!@#$%^&()\\-=+\\[\\]{}',]";
  public static final String VALID_FILE_NAME_REPLACEMENT_CHARACTER = "_";

  public static final int THUMB_SIZE = 100;
  public static final int MAX_RECENT_SIZE = 10;

  // UI Defaults
  public static final boolean DEFAULT_OVERLAY_ASPECT = true;
  public static final boolean DEFAULT_OVERLAY_USE_BASE = false;
  public static final boolean DEFAULT_OVERLAY_CLIP_PORTRAIT = true;
  public static final boolean DEFAULT_USE_FILE_NUMBERING = false;
  public static final int DEFAULT_OVERLAY_SIZE = 256;
  public static final double WINDOW_WIDTH = 825;
  public static final double WINDOW_HEIGHT = 825;
  public static final File OVERLAY_DIR = AppSetup.getAppHome("overlays");
  public static final File CACHE_DIR = AppSetup.getAppHome("cache");
  public static final ExtensionFilter IMAGE_EXTENSION_FILTER =
      new ExtensionFilter(DEFAULT_IMAGE_EXTENSION_DESCRIPTION, "*" + DEFAULT_IMAGE_EXTENSION);
  public static final double DEFAULT_PORTRAIT_TRANSPARENCY = 1;
  public static final double DEFAULT_PORTRAIT_BLUR = 0;
  public static final double DEFAULT_PORTRAIT_GLOW = 0;
  public static final double DEFAULT_OVERLAY_TRANSPARENCY = 1;
  // Set by controller using defaults set in FXML
  public static Image DEFAULT_PORTRAIT_IMAGE;
  public static ImageView DEFAULT_BACKGROUND_IMAGE_VIEW;
  public static Image DEFAULT_MASK_IMAGE;
  public static Image DEFAULT_OVERLAY_IMAGE;
  public static double DEFAULT_PORTRAIT_IMAGE_X;
  public static double DEFAULT_PORTRAIT_IMAGE_Y;
  public static double DEFAULT_PORTRAIT_IMAGE_SCALE;
  public static double DEFAULT_PORTRAIT_IMAGE_ROTATE;
  public static boolean DEFAULT_SAVE_PORTRAIT_ON_DRAG;
  public static boolean DEFAULT_USE_BACKGROUND_ON_DRAG;
  public static String DEFAULT_PORTRAIT_NAME_TEXT_FIELD;
  public static boolean DEFAULT_USE_TOKEN_NAME;
  public static String DEFAULT_PORTRAIT_NAME_SUFFIX_TEXT_FIELD;

  // public static final String DEFAULT_TOKEN_EXTENSION = ".rptok";
  // public static final String DEFAULT_TOKEN_EXTENSION_DESCRIPTION = "MapTool Token";

  // public static final ExtensionFilter TOKEN_EXTENSION_FILTER = new ExtensionFilter(
  // DEFAULT_TOKEN_EXTENSION_DESCRIPTION, "*" + DEFAULT_TOKEN_EXTENSION);
}
