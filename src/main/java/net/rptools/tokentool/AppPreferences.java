/*
 * This software Copyright by the RPTools.net development team, and licensed under the Affero GPL Version 3 or, at your option, any later version.
 *
 * TokenTool Source Code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License * along with this source Code. If not, please visit <http://www.gnu.org/licenses/> and specifically the Affero license text
 * at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.tokentool;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import net.rptools.tokentool.client.TokenTool;
import net.rptools.tokentool.controller.TokenTool_Controller;
import net.rptools.tokentool.util.FileSaveUtil;

public class AppPreferences {
	private static final Logger log = LogManager.getLogger(AppPreferences.class);
	private static final Preferences prefs = Preferences.userNodeForPackage(net.rptools.tokentool.client.TokenTool.class);

	private static final String OVERLAY_ASPECT = "overlayAspectToggleButton";
	private static final String OVERLAY_WIDTH = "overlayWidthSpinner";
	private static final String OVERLAY_HEIGHT = "overlayHeightSpinner";
	private static final String OVERLAY_USE_BASE = "overlayUseAsBaseCheckbox";
	private static final String OVERLAY_CLIP_PORTRAIT = "clipPortraitCheckbox";
	private static final String BACKGROUND_COLOR_RED = "backgroundColor_RED";
	private static final String BACKGROUND_COLOR_BLUE = "backgroundColor_BLUE";
	private static final String BACKGROUND_COLOR_GREEN = "backgroundColor_GREEN";
	private static final String BACKGROUND_COLOR_ALPHA = "backgroundColor_ALPHA";
	private static final String LAST_FILE = "lastFileSaved";
	private static final String LAST_PORTRAIT_FILE = "lastPortrait";
	private static final String FILE_NAME_TEXT_FIELD = "fileNameTextField";
	private static final String FILE_NAME_SUFFIX_TEXT_FIELD = "fileNameSuffixTextField";
	private static final String USE_FILE_NUMBERING = "useFileNumberingCheckbox";
	private static final String RECENT_OVERLAY_COUNT = "recent_overlay_count";
	private static final String RECENT_OVERLAY = "recent_overlay_";
	private static final String PORTRAIT_X = "portrait_x";
	private static final String PORTRAIT_Y = "portrait_y";
	private static final String PORTRAIT_ROTATION = "portrait_rotation";
	private static final String PORTRAIT_ZOOM = "portrait_zoom";
	private static final String WINDOW_X = "window_x";
	private static final String WINDOW_Y = "window_y";
	private static final String WINDOW_WIDTH = "window_width";
	private static final String WINDOW_HEIGHT = "window_height";

	public static void savePreferences(TokenTool_Controller tokentool_Controller) {
		log.info("Saving preferences...");

		// Save Overlay details
		prefs.putBoolean(OVERLAY_ASPECT, tokentool_Controller.getOverlayAspect());
		prefs.putDouble(OVERLAY_WIDTH, tokentool_Controller.getOverlayWidth());
		prefs.putDouble(OVERLAY_HEIGHT, tokentool_Controller.getOverlayHeight());
		prefs.putBoolean(OVERLAY_USE_BASE, tokentool_Controller.getOverlayUseAsBase());
		prefs.putBoolean(OVERLAY_CLIP_PORTRAIT, tokentool_Controller.getClipPortraitCheckbox());

		// Save Portrait background color
		prefs.putDouble(BACKGROUND_COLOR_RED, tokentool_Controller.getBackgroundColor().getRed());
		prefs.putDouble(BACKGROUND_COLOR_GREEN, tokentool_Controller.getBackgroundColor().getGreen());
		prefs.putDouble(BACKGROUND_COLOR_BLUE, tokentool_Controller.getBackgroundColor().getBlue());
		prefs.putDouble(BACKGROUND_COLOR_ALPHA, tokentool_Controller.getBackgroundColor().getOpacity());

		// Save naming details
		prefs.putBoolean(USE_FILE_NUMBERING, tokentool_Controller.getUseFileNumberingCheckbox());
		prefs.put(FILE_NAME_TEXT_FIELD, tokentool_Controller.getFileNameTextField());
		prefs.put(FILE_NAME_SUFFIX_TEXT_FIELD, tokentool_Controller.getFileNameSuffixTextField());

		// Save recent overlays used
		log.debug("...saving recent overlay count");
		Map<Path, TreeItem<Path>> recentOverlays = tokentool_Controller.getRecentOverlayTreeItems();
		prefs.putInt(RECENT_OVERLAY_COUNT, recentOverlays.size());

		log.debug("...saving recent overlays");
		int i = 1;
		for (Path path : recentOverlays.keySet())
			prefs.put(RECENT_OVERLAY + i++, path.toString());

		// Save location of save
		log.debug("...saving last file save location");
		try {
			if (FileSaveUtil.getLastFile() != null)
				if (FileSaveUtil.getLastFile().getParentFile().exists())
					prefs.put(LAST_FILE, FileSaveUtil.getLastFile().getCanonicalPath());
		} catch (IOException e) {
			log.error("Error saving last file preference.", e);
		}

		// Save last portrait used
		try {
			File lastPortrait = new File(AppConstants.CACHE_DIR, "last_portrait.png");
			ImageIO.write(SwingFXUtils.fromFXImage(tokentool_Controller.getPortraitImage(), null), "png", lastPortrait);
			prefs.put(LAST_PORTRAIT_FILE, lastPortrait.getCanonicalPath());
		} catch (IOException e) {
			log.error("Error saving last portrait preference.", e);
		}

		// Save last portraits position and zoom
		ImageView portraitImageView = tokentool_Controller.getPortraitImageView();
		prefs.putDouble(PORTRAIT_X, portraitImageView.getTranslateX());
		prefs.putDouble(PORTRAIT_Y, portraitImageView.getTranslateY());
		prefs.putDouble(PORTRAIT_ROTATION, portraitImageView.getRotate());
		prefs.putDouble(PORTRAIT_ZOOM, portraitImageView.getScaleY());

		// Save window size/position
		prefs.putDouble(WINDOW_X, TokenTool.getInstance().getStage().getX());
		prefs.putDouble(WINDOW_Y, TokenTool.getInstance().getStage().getY());
		prefs.putDouble(WINDOW_WIDTH, TokenTool.getInstance().getStage().getWidth());
		prefs.putDouble(WINDOW_HEIGHT, TokenTool.getInstance().getStage().getHeight());
	}

	public static void restorePreferences(TokenTool_Controller tokentool_Controller) {
		log.info("Restoring preferences...");

		// Restore Overlay details
		tokentool_Controller.setOverlayAspect(prefs.getBoolean(OVERLAY_ASPECT, AppConstants.DEFAULT_OVERLAY_ASPECT));
		tokentool_Controller.setOverlayWidth(prefs.getDouble(OVERLAY_WIDTH, AppConstants.DEFAULT_OVERLAY_SIZE));
		tokentool_Controller.setOverlayHeight(prefs.getDouble(OVERLAY_HEIGHT, AppConstants.DEFAULT_OVERLAY_SIZE));
		tokentool_Controller.setOverlayUseAsBase(prefs.getBoolean(OVERLAY_USE_BASE, AppConstants.DEFAULT_OVERLAY_USE_BASE));
		tokentool_Controller.setClipPortraitCheckbox(prefs.getBoolean(OVERLAY_CLIP_PORTRAIT, AppConstants.DEFAULT_OVERLAY_CLIP_PORTRAIT));

		// Save Portrait background color
		double red = prefs.getDouble(BACKGROUND_COLOR_RED, 0);
		double grn = prefs.getDouble(BACKGROUND_COLOR_GREEN, 0);
		double blu = prefs.getDouble(BACKGROUND_COLOR_BLUE, 0);
		double alpha = prefs.getDouble(BACKGROUND_COLOR_ALPHA, 0);

		if (red + grn + blu + alpha > 0)
			tokentool_Controller.setBackgroundColor(new Color(red, grn, blu, alpha));

		// Restore naming details
		tokentool_Controller.setUseFileNumberingCheckbox(prefs.getBoolean(USE_FILE_NUMBERING, AppConstants.DEFAULT_USE_FILE_NUMBERING));
		tokentool_Controller.setFileNameTextField(prefs.get(FILE_NAME_TEXT_FIELD, AppConstants.DEFAULT_TOKEN_NAME));
		tokentool_Controller.setFileNameSuffixTextField(prefs.get(FILE_NAME_SUFFIX_TEXT_FIELD, AppConstants.DEFAULT_FILE_NAME_SUFFIX));

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
		double window_x = prefs.getDouble(WINDOW_X, 0);
		TokenTool.getInstance().getStage().setX(window_x);

		double window_y = prefs.getDouble(WINDOW_Y, 0);
		TokenTool.getInstance().getStage().setY(window_y);

		log.info("Restoring window position x, y: " + window_x + ", " + window_y);

		double window_width = prefs.getDouble(WINDOW_WIDTH, 0);
		if (window_width > 0)
			TokenTool.getInstance().getStage().setWidth(window_width);

		double window_height = prefs.getDouble(WINDOW_HEIGHT, 0);
		if (window_height > 0)
			TokenTool.getInstance().getStage().setHeight(window_height);

		// Restore location of save
		FileSaveUtil.setLastFile(prefs.get(LAST_FILE, null));

		// Restore last portrait used
		String lastPortraitPath = prefs.get(LAST_PORTRAIT_FILE, null);
		if (lastPortraitPath != null)
			if (!lastPortraitPath.isEmpty()) {
				File lastPortrait = new File(lastPortraitPath);
				if (lastPortrait.exists())
					try {
						Image portraitImage = new Image(lastPortrait.toURI().toURL().toExternalForm());

						// Restore last portraits position and zoom
						double x = prefs.getDouble(PORTRAIT_X, 0);
						double y = prefs.getDouble(PORTRAIT_Y, 0);
						double r = prefs.getDouble(PORTRAIT_ROTATION, 0);
						double s = prefs.getDouble(PORTRAIT_ZOOM, 1);

						tokentool_Controller.setPortraitImage(portraitImage, x, y, r, s);
					} catch (MalformedURLException e) {
						log.error("Error loading last portrait preference.", e);
					}
			}
	}
}
