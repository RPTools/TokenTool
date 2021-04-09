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

import io.sentry.Sentry;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.application.Preloader;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javax.imageio.spi.IIORegistry;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.AppPreferences;
import net.rptools.tokentool.AppSetup;
import net.rptools.tokentool.controller.TokenTool_Controller;
import net.rptools.tokentool.util.I18N;
import net.rptools.tokentool.util.ImageUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * @author Jamz
 *     <p>To see splashscreen during testing, use JVM arg:
 *     -Djavafx.preloader=net.rptools.tokentool.client.SplashScreenLoader Otherwise splashscreen
 *     will only show when defined as JavaFX-Preloader-Class in the JAR manifest.
 */
public class TokenTool extends Application {

  private static final boolean DEV_MODE_FAST_LOAD = false; // Skip overlay cache load if true
  private static TokenTool appInstance;
  private static Logger log; // Don't instantiate until we set user_home/logs directory in AppSetup
  private static BorderPane root;
  private static TokenTool_Controller tokentool_Controller;
  private static int overlayCount = 0;
  private static int loadCount = 1;

  private static TreeItem<Path> overlayTreeItems;
  private static Stage stage;

  private static final Properties buildProperties = new Properties();

  static {
    // This will inject additional data tags in log4j2 which will be picked up by Sentry.io
    System.setProperty("log4j2.isThreadContextMapInheritable", "true");
    ThreadContext.put(
        "OS", System.getProperty("os.name")); // Added to the JavaFX Application Thread thread...

    InputStream inputStream = ClassLoader.getSystemResourceAsStream("build.properties");
    try {
      buildProperties.load(inputStream);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static TokenTool getInstance() {
    return appInstance;
  }

  public static String getVersion() {
    return buildProperties.getProperty("version", "DEVELOPMENT");
  }

  public static String getVendor() {
    return buildProperties.getProperty("vendor", "RPTools");
  }

  public static String getEnvironment() {
    return buildProperties.getProperty("environment", "development");
  }

  public static String getSentryDsn() {
    return buildProperties.getProperty("sentryDSN", "");
  }

  //  public static String getLoggerFileName() {
  //    Logger loggerImpl = log;
  //    Appender appender = loggerImpl.getAppenders().get("LogFile");
  //
  //    if (appender != null) {
  //      return ((FileAppender) appender).getFileName();
  //    } else {
  //    return "NOT_CONFIGURED";
  //    }
  //  }

  @Override
  public void init() throws Exception {
    // Since we are using multiple plugins (Twelve Monkeys for PSD and JAI for jpeg2000)
    // in the same uber jar, the META-INF/services/javax.imageio.spi.ImageReaderSpi
    // gets overwritten. So we need to register them manually:
    // https://github.com/jai-imageio/jai-imageio-core/issues/29
    IIORegistry registry = IIORegistry.getDefaultInstance();
    registry.registerServiceProvider(new com.github.jaiimageio.jpeg2000.impl.J2KImageReaderSpi());

    appInstance = this;

    // Lets install/update the overlays if newer version
    AppSetup.install(getVersion());
    log = LogManager.getLogger(TokenTool.class);

    Sentry.init(
        options -> {
          options.setDsn(getSentryDsn());
          options.setEnvironment(getEnvironment());
          options.setRelease("tokentool@" + getVersion());
        });

    // Log some basic info
    log.info("Environment: {}", getEnvironment());
    log.info("Sentry DSN: {}", getSentryDsn());

    if (!Sentry.isEnabled()) {
      log.info("Not in Production mode and thus will not log any events to Sentry.io");
    }

    log.info("Vendor: " + getVendor());
    log.info("Release: " + getVersion());
    log.info("OS: " + ThreadContext.get("OS"));
    log.info("3D Hardware Available? " + Platform.isSupported(ConditionalFeature.SCENE3D));

    // Now lets cache any overlays we find and update preLoader with progress
    overlayCount =
        (int) Files.walk(AppConstants.OVERLAY_DIR.toPath()).filter(Files::isRegularFile).count();
    overlayTreeItems = cacheOverlays(AppConstants.OVERLAY_DIR, null);

    // All Done!
    notifyPreloader(new Preloader.ProgressNotification(1.0));
  }

  @Override
  public void start(Stage primaryStage) {
    stage = primaryStage;
    setUserAgentStylesheet(STYLESHEET_MODENA); // Setting the style back to the new Modena
    FXMLLoader fxmlLoader =
        new FXMLLoader(
            getClass().getResource(AppConstants.TOKEN_TOOL_FXML),
            ResourceBundle.getBundle(AppConstants.TOKEN_TOOL_BUNDLE));

    try {
      root = fxmlLoader.load();
    } catch (IOException e) {
      log.error("Error loading " + AppConstants.TOKEN_TOOL_FXML, e);
    }

    tokentool_Controller = fxmlLoader.getController();

    Scene scene = new Scene(root);
    primaryStage.setTitle(I18N.getString("TokenTool.stage.title"));
    primaryStage
        .getIcons()
        .add(new Image(getClass().getResourceAsStream(AppConstants.TOKEN_TOOL_ICON)));
    primaryStage.setScene(scene);

    // Load all the overlays into the treeview
    tokentool_Controller.updateOverlayTreeview(overlayTreeItems);

    // Restore saved settings
    AppPreferences.restorePreferences(tokentool_Controller);
    tokentool_Controller.updateTokenPreviewImageView();

    // Add recent list to treeview
    tokentool_Controller.updateOverlayTreeViewRecentFolder(true);

    // Set the Overlay Options accordion to be default open view
    tokentool_Controller.expandOverlayOptionsPane(true);

    primaryStage.setOnCloseRequest(e -> tokentool_Controller.exitApplication());
    primaryStage.show();

    // Finally, update token preview image after everything is done loading
    Platform.runLater(() -> tokentool_Controller.updateTokenPreviewImageView());
  }

  @Override
  public void stop() {
    // Make sure any hanging threads are closed as well...
    System.exit(0);
  }

  public Stage getStage() {
    return stage;
  }

  /**
   * @throws IOException
   * @author Jamz
   * @since 2.0
   *     <p>This method loads and processes all the overlays found in user.home/overlays and it can
   *     take a minute to load as it creates thumbnail versions for the comboBox so we call this
   *     during the init and display progress in the preLoader (splash screen).
   */
  private TreeItem<Path> cacheOverlays(File dir, TreeItem<Path> parent) throws IOException {
    TreeItem<Path> root = new TreeItem<>(dir.toPath());
    root.setExpanded(false);

    if (DEV_MODE_FAST_LOAD) {
      return root;
    }

    log.debug("caching " + dir.getAbsolutePath());

    File[] files = dir.listFiles();
    for (File file : files) {
      if (file.isDirectory()) {
        cacheOverlays(file, root);
      } else {
        Path filePath = file.toPath();
        TreeItem<Path> imageNode =
            new TreeItem<>(filePath, ImageUtil.getOverlayThumb(new ImageView(), filePath));
        root.getChildren().add(imageNode);

        notifyPreloader(new Preloader.ProgressNotification((double) loadCount++ / overlayCount));
      }
    }

    if (parent != null) {
      // When we show the overlay image, the TreeItem value is "" so we need to
      // sort those to the bottom for a cleaner look and keep sub dir's at the top.
      // If a node has no children then it's an overlay, otherwise it's a directory...
      root.getChildren()
          .sort(
              (o1, o2) -> {
                if (o1.getChildren().size() == 0 && o2.getChildren().size() == 0) {
                  return 0;
                } else if (o1.getChildren().size() == 0) {
                  return Integer.MAX_VALUE;
                } else if (o2.getChildren().size() == 0) {
                  return Integer.MIN_VALUE;
                } else {
                  return o1.getValue().compareTo(o2.getValue());
                }
              });

      parent.getChildren().add(root);
    }

    return root;
  }

  /**
   * Legacy, it simply launches the FX Application which calls init() then start(). Also sets/calls
   * the preloader class
   *
   * @param args the command line arguments
   * @author Jamz
   * @since 2.0
   */
  public static void main(String[] args) {
    System.setProperty("javafx.preloader", "net.rptools.tokentool.client.SplashScreenLoader");

    launch(args);
  }
}
