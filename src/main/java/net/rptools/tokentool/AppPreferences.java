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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import net.rptools.tokentool.client.TokenTool;
import net.rptools.tokentool.controller.TokenTool_Controller;
import net.rptools.tokentool.model.Window_Preferences;
import net.rptools.tokentool.util.FileSaveUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AppPreferences {
  private static final Logger log = LogManager.getLogger(AppPreferences.class);
  private static final Preferences prefs = Preferences.userNodeForPackage(TokenTool.class);

  // _PREFERENCES are stored as JSON objects
  private static final String OVERLAY_ASPECT = "overlayAspectToggleButton";
  private static final String OVERLAY_WIDTH = "overlayWidthSpinner";
  private static final String OVERLAY_HEIGHT = "overlayHeightSpinner";
  private static final String OVERLAY_USE_BASE = "overlayUseAsBaseCheckbox";
  private static final String OVERLAY_CLIP_PORTRAIT = "clipPortraitCheckbox";
  private static final String LAST_TOKEN_SAVE_LOCATION = "lastFileSaved";
  private static final String FILE_NAME_TEXT_FIELD = "fileNameTextField";
  private static final String FILE_NAME_SUFFIX_TEXT_FIELD = "fileNameSuffixTextField";
  private static final String USE_FILE_NUMBERING = "useFileNumberingCheckbox";
  private static final String SAVE_PORTRAIT_ON_DRAG = "savePortraitOnDragCheckbox";
  private static final String USE_BACKGROUND_ON_DRAG = "useBackgroundOnDragCheckbox";

  private static final String RECENT_OVERLAY_COUNT = "recent_overlay_count";
  private static final String RECENT_OVERLAY = "recent_overlay_";
  private static final String PORTRAIT_IMAGEVIEW_PREFERENCES = "portraitImageView_preferences";
  private static final String BACKGROUND_IMAGEVIEW_PREFERENCES = "backgroundImageView_preferences";

  private static final String WINDOW_MAIN_PREFERENCES = "windowMain_preferences";
  public static final String WINDOW_MANAGE_OVERLAYS_PREFERENCES =
      "windowManageOverlays_preferences";
  public static final String WINDOW_PDF_PREFERENCES = "windowPdf_preferences";
  public static final String WINDOW_REGION_SELECTOR_PREFERENCES =
      "windowRegionSelector_preferences";
  public static final String WINDOW_CREDITS_PREFERENCES = "windowCredits_preferences";

  public static final String LAST_PDF_FILE = "lastPdfFileSaved";
  public static final String LAST_BACKGROUND_IMAGE_FILE = "lastBackgroundImageFile";
  public static final String LAST_PORTRAIT_IMAGE_FILE = "lastPortraitImageFile";

  private static final String PORTRAIT_TRANSPARENCY = "portraitTransparency";
  private static final String PORTRAIT_BLUR = "portraitBlur";
  private static final String PORTRAIT_GLOW = "portraitGlow";

  private static final String OVERLAY_TRANSPARENCY = "overlayTransparency";
  private static final String PORTRAIT_NAME_TEXT_FIELD = "portraitNameTextField";
  private static final String USE_TOKEN_NAME = "useTokenNameCheckbox";
  private static final String PORTRAIT_NAME_SUFFIX_TEXT_FIELD = "portraitNameSuffixTextField";

  public static void setPreference(String preference, String value) {
    prefs.put(preference, value);
  }

  public static void setPreference(String preference, boolean value) {
    prefs.putBoolean(preference, value);
  }

  public static void setPreference(String preference, int value) {
    prefs.putInt(preference, value);
  }

  public static void setPreference(String preference, double value) {
    prefs.putDouble(preference, value);
  }

  public static String getPreference(String preference, String defaultVal) {
    return prefs.get(preference, defaultVal);
  }

  public static boolean getPreference(String preference, boolean defaultVal) {
    return prefs.getBoolean(preference, defaultVal);
  }

  public static int getPreference(String preference, int defaultVal) {
    return prefs.getInt(preference, defaultVal);
  }

  public static double getPreference(String preference, double devaultVal) {
    return prefs.getDouble(preference, devaultVal);
  }

  public static void savePreferences(TokenTool_Controller tokentool_Controller) {
    log.info("Saving preferences to " + prefs.toString());

    // Save Overlay details
    prefs.putBoolean(OVERLAY_ASPECT, tokentool_Controller.getOverlayAspect());
    prefs.putInt(OVERLAY_WIDTH, tokentool_Controller.getOverlayWidth());
    prefs.putInt(OVERLAY_HEIGHT, tokentool_Controller.getOverlayHeight());
    prefs.putBoolean(OVERLAY_USE_BASE, tokentool_Controller.getOverlayUseAsBase());
    prefs.putBoolean(OVERLAY_CLIP_PORTRAIT, tokentool_Controller.getClipPortraitCheckbox());

    // Save naming details
    prefs.putBoolean(USE_FILE_NUMBERING, tokentool_Controller.getUseFileNumberingCheckbox());
    prefs.put(FILE_NAME_TEXT_FIELD, tokentool_Controller.getFileNameTextField());
    prefs.put(FILE_NAME_SUFFIX_TEXT_FIELD, tokentool_Controller.getFileNameSuffixTextField());
    prefs.put(PORTRAIT_NAME_TEXT_FIELD, tokentool_Controller.getPortraitNameTextField());
    prefs.putBoolean(USE_TOKEN_NAME, tokentool_Controller.getUseTokenNameCheckbox());
    prefs.put(
        PORTRAIT_NAME_SUFFIX_TEXT_FIELD, tokentool_Controller.getPortraitNameSuffixTextField());
    prefs.putBoolean(SAVE_PORTRAIT_ON_DRAG, tokentool_Controller.getSavePortraitOnDragCheckbox());
    prefs.putBoolean(USE_BACKGROUND_ON_DRAG, tokentool_Controller.getUseBackgroundOnDragCheckbox());

    // Save recent overlays used
    log.debug("...saving recent overlay count");
    Map<Path, TreeItem<Path>> recentOverlays = tokentool_Controller.getRecentOverlayTreeItems();
    prefs.putInt(RECENT_OVERLAY_COUNT, recentOverlays.size());

    log.debug("...saving recent overlays");
    int i = 1;
    for (Path path : recentOverlays.keySet()) prefs.put(RECENT_OVERLAY + i++, path.toString());

    // Save location of save
    log.debug("...saving last file save location");
    try {
      if (FileSaveUtil.getLastFile() != null)
        if (FileSaveUtil.getLastFile().getParentFile().exists())
          prefs.put(LAST_TOKEN_SAVE_LOCATION, FileSaveUtil.getLastFile().getCanonicalPath());
    } catch (NullPointerException e) {
      log.warn("No last save location to save...");
    } catch (IOException e) {
      log.error("Error saving last file preference.", e);
    }

    // Save last portrait used
    try {
      File lastPortrait = new File(AppConstants.CACHE_DIR, "last_portrait.png");
      ImageIO.write(
          SwingFXUtils.fromFXImage(tokentool_Controller.getPortraitImage(), null),
          "png",
          lastPortrait);
      prefs.put(
          PORTRAIT_IMAGEVIEW_PREFERENCES,
          tokentool_Controller.getPortrait_Preferences(lastPortrait.getCanonicalPath()));
    } catch (NullPointerException e) {
      log.warn("No portrait to save...");
    } catch (IOException e) {
      log.error("Error saving last portrait preference.", e);
    }

    // Save last background used
    try {
      Image backgroundImage = tokentool_Controller.getBackgroundImage();
      if (backgroundImage != null) {
        File lastBackground = new File(AppConstants.CACHE_DIR, "last_background.png");
        ImageIO.write(
            SwingFXUtils.fromFXImage(tokentool_Controller.getBackgroundImage(), null),
            "png",
            lastBackground);
        prefs.put(
            BACKGROUND_IMAGEVIEW_PREFERENCES,
            tokentool_Controller.getBackground_Preferences(lastBackground.getCanonicalPath()));
      } else {
        prefs.put(
            BACKGROUND_IMAGEVIEW_PREFERENCES, tokentool_Controller.getBackground_Preferences(null));
      }
    } catch (IOException e) {
      log.error("Error saving last background preference.", e);
    }

    // Save window size/position
    prefs.put(
        WINDOW_MAIN_PREFERENCES,
        new Window_Preferences(TokenTool.getInstance().getStage()).toJson());
  }

  public static void restorePreferences(TokenTool_Controller tokentool_Controller) {
    log.info("Restoring preferences from " + prefs.toString());

    // Restore Overlay details
    tokentool_Controller.setOverlayAspect(
        prefs.getBoolean(OVERLAY_ASPECT, AppConstants.DEFAULT_OVERLAY_ASPECT));
    tokentool_Controller.setOverlayWidth(
        prefs.getInt(OVERLAY_WIDTH, AppConstants.DEFAULT_OVERLAY_SIZE));
    tokentool_Controller.setOverlayHeight(
        prefs.getInt(OVERLAY_HEIGHT, AppConstants.DEFAULT_OVERLAY_SIZE));
    tokentool_Controller.setOverlayUseAsBase(
        prefs.getBoolean(OVERLAY_USE_BASE, AppConstants.DEFAULT_OVERLAY_USE_BASE));
    tokentool_Controller.setClipPortraitCheckbox(
        prefs.getBoolean(OVERLAY_CLIP_PORTRAIT, AppConstants.DEFAULT_OVERLAY_CLIP_PORTRAIT));

    // Restore naming details
    tokentool_Controller.setUseFileNumberingCheckbox(
        prefs.getBoolean(USE_FILE_NUMBERING, AppConstants.DEFAULT_USE_FILE_NUMBERING));
    tokentool_Controller.setFileNameTextField(
        prefs.get(FILE_NAME_TEXT_FIELD, AppConstants.DEFAULT_TOKEN_NAME));
    tokentool_Controller.setFileNameSuffixTextField(
        prefs.get(FILE_NAME_SUFFIX_TEXT_FIELD, AppConstants.DEFAULT_FILE_NAME_SUFFIX));
    tokentool_Controller.setUseTokenNameCheckbox(
        prefs.getBoolean(USE_TOKEN_NAME, AppConstants.DEFAULT_USE_TOKEN_NAME));
    tokentool_Controller.setPortraitNameTextField(
        prefs.get(PORTRAIT_NAME_TEXT_FIELD, AppConstants.DEFAULT_PORTRAIT_NAME_TEXT_FIELD));
    tokentool_Controller.setPortraitNameSuffixTextField(
        prefs.get(
            PORTRAIT_NAME_SUFFIX_TEXT_FIELD, AppConstants.DEFAULT_PORTRAIT_NAME_SUFFIX_TEXT_FIELD));
    tokentool_Controller.setUseBackgroundOnDragCheckbox(
        prefs.getBoolean(USE_BACKGROUND_ON_DRAG, AppConstants.DEFAULT_USE_BACKGROUND_ON_DRAG));
    tokentool_Controller.setSavePortraitOnDragCheckbox(
        prefs.getBoolean(SAVE_PORTRAIT_ON_DRAG, AppConstants.DEFAULT_SAVE_PORTRAIT_ON_DRAG));

    // Restore Portrait Effects
    tokentool_Controller
        .getPortraitTransparencySlider()
        .setValue(
            prefs.getDouble(PORTRAIT_TRANSPARENCY, AppConstants.DEFAULT_PORTRAIT_TRANSPARENCY));
    tokentool_Controller
        .getPortraitBlurSlider()
        .setValue(prefs.getDouble(PORTRAIT_BLUR, AppConstants.DEFAULT_PORTRAIT_BLUR));
    tokentool_Controller
        .getPortraitGlowSlider()
        .setValue(prefs.getDouble(PORTRAIT_GLOW, AppConstants.DEFAULT_PORTRAIT_GLOW));

    tokentool_Controller
        .getOverlayTransparencySlider()
        .setValue(prefs.getDouble(OVERLAY_TRANSPARENCY, AppConstants.DEFAULT_OVERLAY_TRANSPARENCY));

    // Restore recent overlays used
    int overlayCount = prefs.getInt(RECENT_OVERLAY_COUNT, 0);
    for (int i = 1; i <= overlayCount; i++) {
      String filePath = prefs.get(RECENT_OVERLAY + i, "");
      if (!filePath.isEmpty())
        if (new File(filePath).exists()) {
          tokentool_Controller.updateRecentOverlayTreeItems(Paths.get(filePath));
        }
    }

    // Restore window size/position
    String windowMain_Preferences =
        prefs.get(
            WINDOW_MAIN_PREFERENCES,
            new Window_Preferences(AppConstants.WINDOW_WIDTH, AppConstants.WINDOW_HEIGHT).toJson());
    tokentool_Controller.setWindoFrom_Preferences(windowMain_Preferences);

    // Restore location of save
    FileSaveUtil.setLastFile(prefs.get(LAST_TOKEN_SAVE_LOCATION, null));

    // Restore last portrait used
    tokentool_Controller.setPortraitFrom_Preferences(
        prefs.get(PORTRAIT_IMAGEVIEW_PREFERENCES, null));
    tokentool_Controller.setBackgroundFrom_Preferences(
        prefs.get(BACKGROUND_IMAGEVIEW_PREFERENCES, null));
  }

  public static void removeAllPreferences() {
    try {
      prefs.clear();
      prefs.flush();
    } catch (BackingStoreException e) {
      log.error("Error removing all preferences from backing store!", e);
    }
  }
}
