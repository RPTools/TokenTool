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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import net.rptools.tokentool.client.TokenTool;
import net.rptools.tokentool.util.I18N;
import net.rptools.tokentool.util.ImageUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

/** Executes only the first time the application is run. */
public class AppSetup {

  private static final String DEFAULT_OVERLAYS = "net/rptools/tokentool/overlays/";
  private static final String USER_HOME;
  private static Logger
      log; // Don't instantiate until install gets and sets user_home/logs directory

  static {
    USER_HOME = System.getProperty("user.home");
    ThreadContext.put(
        "OS", System.getProperty("os.name")); // Added to the JavaFX-Launcher thread...
  }

  public static void install(String versionString) {
    System.setProperty("appHome", getAppHome("logs").getAbsolutePath());
    log = LogManager.getLogger(AppSetup.class);

    File overlayVerFile = new File(getAppHome().getAbsolutePath() + "/version.txt");
    Collection<File> existingOverLays =
        FileUtils.listFiles(
            AppConstants.OVERLAY_DIR, ImageUtil.SUPPORTED_FILE_FILTER, TrueFileFilter.INSTANCE);
    log.info("Overlays installed: " + existingOverLays.size());

    // Only install overlays once or if version.text is missing or version is newer
    // Overlays are stored in a version packaged structure so we can later install only newer
    // overlays if wanted
    String installedVersion = "0";
    try {
      if (overlayVerFile.exists()) {
        installedVersion = FileUtils.readFileToString(overlayVerFile, Charset.defaultCharset());
      } else {
        FileUtils.writeStringToFile(overlayVerFile, versionString, Charset.defaultCharset());
      }
    } catch (IOException ioe) {
      log.error(ioe);
    }

    if (existingOverLays.isEmpty()) {
      try {
        installDefaultOverlays();
      } catch (IOException e) {
        log.error("Error installing overlays upon detecting no overlays are installed...", e);
      }
    } else if (isNewerVersion(TokenTool.getVersion(), installedVersion)) {
      log.info("New version detected.");

      try {
        confirmInstallOverlays(installNewOverlays(installedVersion));
      } catch (IOException e) {
        log.error("Error installing overlays upon detecting a new version installed...", e);
      }

      // Update version file to new version
      try {
        FileUtils.writeStringToFile(overlayVerFile, versionString, Charset.defaultCharset());
      } catch (IOException e) {
        log.error(e);
      }
    }
  }

  private static void confirmInstallOverlays(int overlaysInstalled) {
    if (overlaysInstalled <= 0) {
      return;
    }

    Platform.runLater(
        () -> {
          Alert alert = new Alert(AlertType.INFORMATION);
          alert.setHeaderText(I18N.getString("TokenTool.dialog.confirmation.header"));
          alert.setTitle(I18N.getString("AppSetup.dialog.install.overlays.confirmation.title"));
          alert.setContentText(
              overlaysInstalled
                  + " "
                  + I18N.getString("AppSetup.dialog.install.overlays.confirmation"));

          alert.showAndWait();
        });

    log.info(overlaysInstalled + " New overlays installed.");
  }

  public static void installDefaultOverlays() throws IOException {
    installNewOverlays("0.0"); // Install all overlays
  }

  /*
   * OK so here will will only install any overlays that are in a directory with a newer version
   * than what is installed. So, if a user skips versions and goes from 2 to 2.3 any overlays
   * in 2.1, 2.2, and 2.3 will get installed. I'm doing it this way in case a user reorganizes his
   * directory structure or deletes overlays he doesn't want, we don't reinstall them and annoy
   * the user!
   */
  public static int installNewOverlays(String currentVersion) throws IOException {
    // Create the overlay directory if it doesn't already exist
    File overlayDir = AppConstants.OVERLAY_DIR;
    overlayDir.mkdirs();
    int overlaysInstalled = 0;

    // Copy default overlays from resources
    Reflections reflections = new Reflections(DEFAULT_OVERLAYS, new ResourcesScanner());
    Set<String> resourcePathSet = reflections.getResources(Pattern.compile(".*"));

    for (String resourcePath : resourcePathSet) {
      String resourceName = resourcePath.substring(DEFAULT_OVERLAYS.length());
      InputStream resourceAsStream = AppSetup.class.getResourceAsStream("/" + resourcePath);

      int verIndex = resourceName.indexOf("/");
      String resourceVersion = resourceName.substring(1, verIndex).replace("_", ".");

      if (isNewerVersion(resourceVersion, currentVersion)) {
        File resourceFile =
            new File(overlayDir, resourceName.substring(resourceVersion.length() + 1));
        log.info("Installing overlay: " + resourceFile);

        try {
          FileUtils.copyInputStreamToFile(resourceAsStream, resourceFile);
          overlaysInstalled++;
        } catch (IOException ignored) {
        }
      }
    }

    return overlaysInstalled;
  }

  public static File getAppHome() {
    if (USER_HOME == null) {
      return null;
    }

    String vendor = "";

    if (!TokenTool.getVendor().isEmpty()) {
      vendor += "-" + TokenTool.getVendor();
    }

    File home = new File(USER_HOME + "/." + (AppConstants.APP_NAME + vendor).toLowerCase());
    home.mkdirs();

    return home;
  }

  public static File getAppHome(String subdir) {
    if (USER_HOME == null) {
      return null;
    }

    File home = new File(getAppHome().getPath() + "/" + subdir);
    home.mkdirs();

    return home;
  }

  public static File getTmpDir() {
    return getAppHome("tmp");
  }

  /**
   * A convenience method to break up the version number string into it's component version
   * identifiers and test if version is newer than existing version
   *
   * @param version
   * @param installedVersion
   * @return true if valid version format
   * @author Jamz
   * @since 2.0
   */
  private static boolean isNewerVersion(String version, String installedVersion) {
    if (version.equals("DEVELOPMENT") || installedVersion.equals("DEVELOPMENT")) {
      return false;
    }

    String[] versions = version.indexOf(".") > 0 ? version.split("\\.") : new String[] {version};
    String[] installedVersions =
        installedVersion.indexOf(".") > 0
            ? installedVersion.split("\\.")
            : new String[] {installedVersion};

    int i = 0;
    try {
      for (String ver : versions) {
        int v = Integer.parseInt(ver);
        int iv = 0;

        if (installedVersions.length > i) {
          iv = Integer.parseInt(installedVersions[i]);
        }

        if (v > iv) {
          return true;
        } else if (iv > v) {
          return false;
        }

        i++;
      }
    } catch (NumberFormatException e) {
      log.warn("Unable to parse version, installedVersion: " + version + ", " + installedVersion);
      return false;
    }

    return false;
  }
}
