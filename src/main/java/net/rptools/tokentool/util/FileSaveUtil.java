/*
 * This software Copyright by the RPTools.net development team, and licensed under the Affero GPL Version 3 or, at your option, any later version.
 *
 * TokenTool Source Code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License * along with this source Code. If not, please visit <http://www.gnu.org/licenses/> and specifically the Affero license text
 * at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.tokentool.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.scene.control.TextField;
import net.rptools.tokentool.AppConstants;

public class FileSaveUtil {
	private static final Logger log = LogManager.getLogger(FileSaveUtil.class);
	private static File lastFile = null;

	public File getTempFileName(boolean asToken, boolean useNumbering, String tempFileName,
			TextField fileNameSuffix) throws IOException {

		return new File(System.getProperty("java.io.tmpdir"), getFileName(asToken, useNumbering, tempFileName, fileNameSuffix).getName());
	}

	public File getFileName(boolean asToken, boolean useNumbering, String tempFileName, TextField fileNameSuffix)
			throws IOException {
		final String _extension;

		_extension = AppConstants.DEFAULT_IMAGE_EXTENSION;

		if (useNumbering) {
			int dragCounter;
			try {
				dragCounter = Integer.parseInt(fileNameSuffix.getText());
			} catch (NumberFormatException e) {
				dragCounter = 0;
			}

			String leadingZeroes = "%0" + fileNameSuffix.getLength() + "d";

			fileNameSuffix.setText(String.format(leadingZeroes, dragCounter + 1));

			if (tempFileName.isEmpty())
				tempFileName = AppConstants.DEFAULT_TOKEN_NAME;

			if (lastFile != null) {
				return new File(lastFile.getParent(), String.format("%s_" + leadingZeroes + _extension, tempFileName, dragCounter));
			} else {
				return new File(String.format("%s_" + leadingZeroes + _extension, tempFileName, dragCounter));
			}
		} else {
			if (lastFile != null)
				if (tempFileName.isEmpty())
					tempFileName = AppConstants.DEFAULT_TOKEN_NAME + _extension;

			if (!tempFileName.endsWith(_extension))
				tempFileName += _extension;

			if (lastFile != null)
				lastFile = new File(lastFile.getParent(), tempFileName);
			else
				lastFile = new File(tempFileName);

			return lastFile;
		}
	}

	public static File getLastFile() {
		return lastFile;
	}

	public static void setLastFile(File file) {
		lastFile = file;
	}

	public static void setLastFile(String filePath) {
		if (filePath != null)
			lastFile = new File(filePath);
	}

	public static void copyFile(File srcFile, File destDir) {
		try {
			FileUtils.copyFile(srcFile, new File(destDir, srcFile.getName()));
		} catch (Exception e) {
			log.error("Could not copy " + srcFile, e);
		}
	}

	public static boolean makeDir(String dirName, File destDir) {
		if (dirName.isEmpty())
			return false;

		File newDir;
		newDir = new File(destDir, dirName);
		if (newDir.mkdir()) {
			log.info("Created directory: " + newDir.getAbsolutePath());
			return true;
		} else {
			log.error("Could not create directory: " + newDir.getAbsolutePath());
		}

		return false;
	}
}
