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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OverlayTreeItem extends TreeItem<Path> {

  private static final Logger log = LogManager.getLogger(OverlayTreeItem.class);

  private boolean isFirstTimeChildren = true;
  private boolean isFirstTimeLeaf = true;
  private boolean isLeaf;

  public OverlayTreeItem(Path f) {
    super(f);
  }

  public OverlayTreeItem(File f) {
    super(f.toPath());
  }

  public boolean isDirectory() {
    return Files.isDirectory(getValue());
  }

  @Override
  public ObservableList<TreeItem<Path>> getChildren() {
    if (isFirstTimeChildren) {
      isFirstTimeChildren = false;

      /*
       * First getChildren() call, so we actually go off and determine the children of the File contained in this TreeItem.
       */
      super.getChildren().setAll(buildChildren());
    }
    return super.getChildren();
  }

  @Override
  public boolean isLeaf() {
    if (isFirstTimeLeaf) {
      isFirstTimeLeaf = false;
      try {
        // try-with-resources statement ensures that each resource is closed at the end of the
        // statement otherwise stream is left open and directory can not be deleted!
        try (Stream<Path> files = Files.list(getValue()).filter(Files::isDirectory)) {
          isLeaf = files.findAny().isEmpty();
        }

      } catch (IOException e) {
        log.error(e);
      }
    }
    return isLeaf;
  }

  /**
   * Returning a collection of type ObservableList containing TreeItems, which represent all
   * children of this TreeITem.
   *
   * @return an ObservableList<TreeItem<File>> containing TreeItems, which represent all children
   *     available in this TreeItem. If the handed TreeItem is a leaf, an empty list is returned.
   */
  private ObservableList<TreeItem<Path>> buildChildren() {
    if (Files.isDirectory(getValue())) {
      try (Stream<Path> files = Files.list(getValue())) {
        return files
            .filter(Files::isDirectory)
            .map(OverlayTreeItem::new)
            .collect(Collectors.toCollection(FXCollections::observableArrayList));

      } catch (IOException e) {
        e.printStackTrace();
        return FXCollections.emptyObservableList();
      }
    }

    return FXCollections.emptyObservableList();
  }
}
