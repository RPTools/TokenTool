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
package net.rptools.tokentool.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.AppSetup;
import net.rptools.tokentool.model.OverlayTreeItem;
import net.rptools.tokentool.util.FileSaveUtil;
import net.rptools.tokentool.util.I18N;
import net.rptools.tokentool.util.ImageUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ManageOverlays_Controller {
  private static final Logger log = LogManager.getLogger(ManageOverlays_Controller.class);
  private static Thread loadOverlaysThread = new Thread();
  private static ExecutorService executorService;
  private static File currentDirectory;
  private static File lastSelectedDirectory;
  private static ToggleGroup overlayToggleGroup = new ToggleGroup();

  @FXML private FlowPane overlayViewFlowPane;
  @FXML private TreeView<Path> overlayTreeView;
  @FXML private VBox detailsVBox;
  @FXML private Label overlayName;
  @FXML private Label overlayDescription;
  @FXML private Label overlayDimensions;
  @FXML private ImageView overlayLayerImage;
  @FXML private ImageView overlayLayerMask;
  @FXML private Button addOverlayButton;
  @FXML private Button deleteOverlayButton;
  @FXML private Button addFolderButton;
  @FXML private Button deleteFolderButton;
  @FXML private Button restoreButton;

  @FXML
  void initialize() {
    assert overlayViewFlowPane != null
        : "fx:id=\"overlayViewFlowPane\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert overlayTreeView != null
        : "fx:id=\"overlayTreeView\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert detailsVBox != null
        : "fx:id=\"detailsVBox\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert overlayName != null
        : "fx:id=\"overlayName\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert overlayDescription != null
        : "fx:id=\"overlayDescription\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert overlayDimensions != null
        : "fx:id=\"overlayDimensions\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert overlayLayerImage != null
        : "fx:id=\"overlayLayerImage\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert overlayLayerMask != null
        : "fx:id=\"overlayLayerMask\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert addOverlayButton != null
        : "fx:id=\"addOverlayButton\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert deleteOverlayButton != null
        : "fx:id=\"deleteOverlayButton\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert addFolderButton != null
        : "fx:id=\"addFolderButton\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert deleteFolderButton != null
        : "fx:id=\"deleteFolderButton\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";
    assert restoreButton != null
        : "fx:id=\"restoreButton\" was not injected: check your FXML file '"
            + AppConstants.MANAGE_OVERLAYS_FXML
            + "'.";

    executorService =
        Executors.newSingleThreadScheduledExecutor(
            runable -> {
              loadOverlaysThread = Executors.defaultThreadFactory().newThread(runable);
              loadOverlaysThread.setDaemon(true);
              return loadOverlaysThread;
            });

    // Add a listener to the TreeView
    overlayTreeView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((observable, oldValue, newValue) -> loadImages(newValue));

    displayTreeView();
  }

  public void displayTreeView() {
    TreeItem<Path> root = new OverlayTreeItem(AppConstants.OVERLAY_DIR);
    root.setExpanded(true);
    overlayTreeView.setRoot(root);

    overlayTreeView.setCellFactory(
        treeView ->
            new TreeCell<Path>() {
              @Override
              public void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                if (empty) {
                  setText(null);
                } else {
                  setText(path.getFileName().toString());
                }
              }
            });
  }

  private void loadImages(TreeItem<Path> treeItem) {
    overlayViewFlowPane.getChildren().clear();
    if (treeItem != null) loadImages(treeItem.getValue().toFile());
  }

  private void loadImages(File dir) {
    // Clear Details panel
    clearDetails();

    currentDirectory = dir;
    File[] files = dir.listFiles(ImageUtil.SUPPORTED_FILENAME_FILTER);

    Task<Void> task =
        new Task<Void>() {
          @Override
          public Void call() {
            for (File file : files) {
              Path filePath = file.toPath();

              if (loadOverlaysThread.isInterrupted()) {
                Platform.runLater(() -> overlayViewFlowPane.getChildren().clear());
                break;
              }

              try {
                ToggleButton overlayButton = new ToggleButton();
                ImageView imageViewNode = ImageUtil.getOverlayThumb(new ImageView(), filePath);

                overlayButton.getStyleClass().add("overlay-toggle-button");
                overlayButton.setGraphic(imageViewNode);
                overlayButton.setUserData(file);
                overlayButton.setToggleGroup(overlayToggleGroup);

                overlayButton.addEventHandler(
                    ActionEvent.ACTION,
                    event -> {
                      // No modifier keys used so add toggle group back to all buttons
                      resetToggleGroup();

                      // Also set button to selected due to resetting toggle groups & no unselecting
                      // needed, makes for better interface IMO
                      overlayButton.setSelected(true);

                      // Update the Details panel with the last selected overlay
                      File overlayFile = (File) overlayButton.getUserData();
                      updateDetails(
                          overlayFile,
                          (ImageView) overlayButton.getGraphic(),
                          overlayButton.isSelected());

                      // Consume the event, no more logic needed
                      event.consume();
                    });

                overlayButton.setOnMouseClicked(
                    new EventHandler<MouseEvent>() {
                      @Override
                      public void handle(MouseEvent event) {
                        // Allow multiple selections if shortcutKey+left_mouse is pressed
                        if (event.getButton().equals(MouseButton.PRIMARY)
                            && event.isShortcutDown()) {
                          // Update the Details panel with the last selected overlay
                          File overlayFile = (File) overlayButton.getUserData();
                          updateDetails(overlayFile, (ImageView) overlayButton.getGraphic(), true);

                          // Remove the toggle group to allow multiple toggle button selection
                          overlayButton.setToggleGroup(null);

                          // Select the button
                          overlayButton.setSelected(true);

                          // Consume the event, no more logic needed
                          event.consume();
                        }
                      }
                    });

                Platform.runLater(() -> overlayViewFlowPane.getChildren().add(overlayButton));
              } catch (IOException e) {
                log.error("Loading image: " + filePath.getFileName(), e);
              }
            }

            return null;
          }
        };

    loadOverlaysThread.interrupt();
    executorService.execute(task);
  }

  private void updateDetails(File overlayFile, ImageView overlayImage, boolean selected) {
    if (selected) {
      int w = (int) overlayImage.getImage().getWidth();
      int h = (int) overlayImage.getImage().getHeight();

      overlayName.setText(FilenameUtils.getBaseName(overlayFile.getName()));
      overlayDescription.setText(ImageUtil.getFileType(overlayFile));
      overlayDimensions.setText(w + " x " + h);
      overlayLayerImage.setImage(overlayImage.getImage());
      ;

      try {
        overlayLayerMask = ImageUtil.getMaskImage(overlayLayerMask, overlayFile.toPath());
      } catch (IOException e) {
        log.error("Updating details for: " + overlayFile.getAbsolutePath(), e);
      }
    } else {
      clearDetails();
    }
  }

  private void clearDetails() {
    overlayName.setText("");
    overlayDescription.setText("");
    overlayDimensions.setText("");
    overlayLayerImage.setImage(null);
    overlayLayerMask.setImage(null);
  }

  private void resetToggleGroup() {
    for (Node overlay : overlayViewFlowPane.getChildren()) {
      ToggleButton overlayButton = (ToggleButton) overlay;
      if (overlayButton.getToggleGroup() == null) overlayButton.setToggleGroup(overlayToggleGroup);
    }
  }

  private boolean confirmDelete(LinkedList<File> overlayFiles) {
    String confirmationText = I18N.getString("ManageOverlays.dialog.delete.confirmation");

    if (overlayFiles.isEmpty()) return false;
    else if (overlayFiles.size() == 1) {
      confirmationText += overlayFiles.get(0).getName() + "?";
    } else {
      confirmationText +=
          I18N.getString("ManageOverlays.dialog.delete.confirmation.these")
              + overlayFiles.size()
              + I18N.getString("ManageOverlays.dialog.delete.confirmation.overlays");
    }

    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setTitle(I18N.getString("ManageOverlays.dialog.delete.title"));
    alert.setContentText(confirmationText);

    Optional<ButtonType> result = alert.showAndWait();

    if ((result.isPresent()) && (result.get() == ButtonType.OK)) {
      return true;
    }

    return false;
  }

  private boolean confirmDelete(File dir) {
    String confirmationText = I18N.getString("ManageOverlays.dialog.delete.dir.confirmation");
    long dirSize =
        FileUtils.listFiles(dir, ImageUtil.SUPPORTED_FILE_FILTER, TrueFileFilter.INSTANCE).size();

    if (dirSize == 0) {
      confirmationText +=
          dir.getName() + I18N.getString("ManageOverlays.dialog.delete.dir.confirmation.directory");
    } else {
      confirmationText +=
          dir.getName()
              + I18N.getString("ManageOverlays.dialog.delete.dir.directory_containing")
              + dirSize
              + I18N.getString("ManageOverlays.dialog.delete.dir.overlays");
    }

    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setTitle(I18N.getString("ManageOverlays.dialog.delete.dir.title"));
    alert.setContentText(confirmationText);

    Optional<ButtonType> result = alert.showAndWait();

    if ((result.isPresent()) && (result.get() == ButtonType.OK)) {
      return true;
    }

    return false;
  }

  @FXML
  void deleteOverlayButton_onAction(ActionEvent event) {
    LinkedList<File> overlayFiles = new LinkedList<File>();

    for (Node overlay : overlayViewFlowPane.getChildren()) {
      ToggleButton overlayButton = (ToggleButton) overlay;
      if (overlayButton.isSelected()) overlayFiles.add((File) overlayButton.getUserData());
    }

    if (confirmDelete(overlayFiles)) {
      for (File file : overlayFiles) {
        log.info("Deleting: " + file.getName());
        file.delete();
      }

      loadImages(overlayTreeView.getSelectionModel().getSelectedItem());
    }
  }

  @FXML
  void deleteFolderButton_onAction(ActionEvent event) {
    if (currentDirectory.equals(AppConstants.OVERLAY_DIR)) return;

    if (confirmDelete(currentDirectory)) {
      try {
        FileUtils.forceDelete(currentDirectory);
      } catch (IOException e) {
        log.info("Deleting: " + currentDirectory.getAbsolutePath());
      }

      displayTreeView();
    }
  }

  @FXML
  void addOverlayButton_onAction(ActionEvent event) {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(I18N.getString("ManageOverlays.filechooser.overlay.title"));
    fileChooser.getExtensionFilters().addAll(ImageUtil.GET_EXTENSION_FILTERS());

    if (lastSelectedDirectory != null) fileChooser.setInitialDirectory(lastSelectedDirectory);

    List<File> selectedFiles =
        fileChooser.showOpenMultipleDialog((Stage) addOverlayButton.getScene().getWindow());

    if (selectedFiles != null) {
      for (File selectedFile : selectedFiles) {
        FileSaveUtil.copyFile(selectedFile, currentDirectory);
      }

      lastSelectedDirectory = selectedFiles.get(0).getParentFile();
      loadImages(overlayTreeView.getSelectionModel().getSelectedItem());
    }
  }

  @FXML
  void addFolderButton_onAction(ActionEvent event) {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle(I18N.getString("ManageOverlays.filechooser.folder.title"));
    dialog.setContentText(I18N.getString("ManageOverlays.filechooser.folder.content_text"));

    Optional<String> result = dialog.showAndWait();
    result.ifPresent(
        name -> {
          if (FileSaveUtil.makeDir(name, currentDirectory)) {
            displayTreeView();
          }
          ;
        });
  }

  @FXML
  void restoreButton_onAction(ActionEvent event) {
    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setTitle(I18N.getString("ManageOverlays.dialog.restore.overlays.title"));
    alert.setContentText(I18N.getString("ManageOverlays.dialog.restore.overlays.content_text"));

    Optional<ButtonType> result = alert.showAndWait();

    if ((result.isPresent()) && (result.get() == ButtonType.OK)) {
      log.info("Restoring default images...");
      try {
        AppSetup.installDefaultOverlays();
      } catch (IOException e) {
        log.error("Error restoring default overlays!", e);
      }

      displayTreeView();
    }
  }

  @FXML
  void overlayViewFlowPane_DragDone(DragEvent event) {
    loadImages(overlayTreeView.getSelectionModel().getSelectedItem());
  }

  @FXML
  void overlayViewFlowPane_DragDropped(DragEvent event) {
    Dragboard db = event.getDragboard();
    if (db.hasImage()) {
      try {
        // Prompt for name & return file name
        File newOverlayFile = new File(currentDirectory.getCanonicalPath() + "/somefilename.png");
        ImageIO.write(SwingFXUtils.fromFXImage(db.getImage(), null), "png", newOverlayFile);
      } catch (IOException e) {
        log.error("Error writing new overlay image.", e);
      }

      loadImages(overlayTreeView.getSelectionModel().getSelectedItem());
      event.setDropCompleted(true);
    } else if (db.hasFiles()) {
      db.getFiles()
          .forEach(
              file -> {
                FileSaveUtil.copyFile(file, currentDirectory);
              });
      loadImages(overlayTreeView.getSelectionModel().getSelectedItem());
      event.setDropCompleted(true);
    } else if (db.hasUrl()) {
      FileSaveUtil.copyFile(new File(db.getUrl()), currentDirectory);
      loadImages(overlayTreeView.getSelectionModel().getSelectedItem());
      event.setDropCompleted(true);
    }
  }

  @FXML
  void overlayViewFlowPane_DragOver(DragEvent event) {
    if (event.getDragboard().hasImage()
        || event.getDragboard().hasFiles()
        || event.getDragboard().hasUrl()) {
      event.acceptTransferModes(TransferMode.COPY);
    } else {
      event.acceptTransferModes(TransferMode.ANY);
    }
  }
}
