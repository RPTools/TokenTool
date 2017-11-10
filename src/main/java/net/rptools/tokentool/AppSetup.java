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
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

import net.rptools.tokentool.client.TokenTool;
import net.rptools.tokentool.util.ImageUtil;

/**
 * Executes only the first time the application is run.
 */
public class AppSetup {
	private static Logger log; // Don't instantiate until install gets and sets user_home/logs directory

	private static final String DEFAULT_OVERLAYS = "net/rptools/tokentool/overlays/v2";
	private static final String USER_HOME;

	static {
		USER_HOME = System.getProperty("user.home");
	}

	public static void install(String versionString) {
		System.setProperty("appHome", getAppHome("logs").getAbsolutePath());
		log = LogManager.getLogger(AppSetup.class);

		File overlayVer = new File(getAppHome().getAbsolutePath() + "/version.txt");
		Collection<File> existingOverLays = FileUtils.listFiles(AppConstants.OVERLAY_DIR, ImageUtil.SUPPORTED_FILE_FILTER, TrueFileFilter.INSTANCE);
		log.info("Overlays installed: " + existingOverLays.size());

		// Only install overlays once or if version.text is missing or version is newer
		// Overlays are stored in a version packaged structure so we can later install only newer overlays if wanted
		String installedVersion = "0";
		try {
			if (overlayVer.exists()) {
				installedVersion = FileUtils.readFileToString(overlayVer, Charset.defaultCharset());
			} else {
				FileUtils.writeStringToFile(overlayVer, versionString, Charset.defaultCharset());
			}
		} catch (IOException ioe) {
			log.error(ioe);
		}

		if (existingOverLays.isEmpty() || isNewerVersion(TokenTool.getVersion(), installedVersion)) {
			try {
				installDefaultOverlays();
			} catch (IOException e) {
				log.error(e);
			}

			// Update version file to new version
			try {
				FileUtils.writeStringToFile(overlayVer, versionString, Charset.defaultCharset());
			} catch (IOException e) {
				log.error(e);
			}
		}
	}

	public static void installDefaultOverlays() throws IOException {
		// Create the overlay directory
		File overlayDir = AppConstants.OVERLAY_DIR;
		overlayDir.mkdirs();

		// Copy default overlays from resources
		// https://dzone.com/articles/get-all-classes-within-package
		Reflections reflections = new Reflections(DEFAULT_OVERLAYS, new ResourcesScanner());
		Set<String> resourcePathSet = reflections.getResources(Pattern.compile(".*"));

		for (String resourcePath : resourcePathSet) {
			URL inputUrl = AppSetup.class.getClassLoader().getResource(resourcePath);
			String resourceName = resourcePath.substring(DEFAULT_OVERLAYS.length());

			try {
				log.info("Installing overlay: " + resourceName);
				FileUtils.copyURLToFile(inputUrl, new File(overlayDir, resourceName));
			} catch (IOException e) {
				log.error("ERROR writing " + inputUrl);
				log.error(e);
			}
		}
	}

	public static File getAppHome() {
		if (USER_HOME == null) {
			return null;
		}

		String vendor = "";

		if (!TokenTool.getVendor().isEmpty())
			vendor += "-" + TokenTool.getVendor();

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

	/**
	 * A convenience method to break up the version number string into it's component version identifiers and test if version is newer than existing version
	 * 
	 * @author Jamz
	 * @since 2.0
	 * 
	 * @param installed
	 *            version
	 * @param running
	 *            version
	 * @return true if valid version format
	 */
	private static boolean isNewerVersion(String version, String installedVersion) {
		String[] versions = version.indexOf(".") > 0 ? version.split("\\.") : new String[] { version };
		String[] installedVersions = installedVersion.indexOf(".") > 0 ? installedVersion.split("\\.") : new String[] { installedVersion };

		int i = 0;
		for (String ver : versions) {
			int v = Integer.parseInt(ver);
			int iv = 0;

			if (installedVersions.length > i)
				iv = Integer.parseInt(installedVersions[i]);

			if (v > iv) {
				log.info("New version detected.");
				return true;
			} else if (iv > v) {
				return false;
			}

			i++;
		}

		return false;
	}
}
