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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.converter.IntegerStringConverter;
import javax.imageio.ImageIO;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.AppPreferences;
import net.rptools.tokentool.model.PdfModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PdfViewer_Controller implements Initializable {

  private static final Logger log = LogManager.getLogger(PdfViewer_Controller.class);
  private static ExecutorService renderPdfPageService;
  private static ExecutorService extractImagesService;
  @FXML private AnchorPane pdfAnchorPane;
  @FXML private Pagination pdfViewPagination;
  @FXML private StackPane pdfSideStackPane;
  @FXML private TextField pageNumberTextField;
  @FXML private TilePane imageTilePane;
  @FXML private ProgressIndicator pdfProgressIndicator;
  @FXML private ProgressIndicator extractProgressIndicator;
  @FXML private ProgressBar extractAllImagesProgressBar;
  @FXML private Label extractAllImagesLabel;
  @FXML private Pane viewPortPane;
  @FXML private ScrollPane imageTileScrollpane;
  @FXML private ChoiceBox<String> pdfSaveFormatChoicebox;
  @FXML private TextField pdfMinDimensionTextField;
  @FXML private Button savePageButton;
  @FXML private Button saveAllButton;

  private PdfModel pdfModel;
  private final ImageView pdfImageView = new ImageView();
  private final AtomicInteger workerThreads = new AtomicInteger(0);
  private final AtomicInteger extractThreads = new AtomicInteger(0);

  @Override
  public void initialize(URL url, ResourceBundle rb) {
    assert pdfAnchorPane != null
        : "fx:id=\"pdfAnchorPane\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert pdfViewPagination != null
        : "fx:id=\"pdfViewPagination\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert pdfSideStackPane != null
        : "fx:id=\"pdfSideStackPane\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert pageNumberTextField != null
        : "fx:id=\"pageNumberTextField\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert imageTilePane != null
        : "fx:id=\"imageFlowPane\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert pdfProgressIndicator != null
        : "fx:id=\"pdfProgressIndicator\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert extractProgressIndicator != null
        : "fx:id=\"extractProgressIndicator\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert extractAllImagesProgressBar != null
        : "fx:id=\"extractAllImagesProgressBar\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert extractAllImagesLabel != null
        : "fx:id=\"extractAllImagesLabel\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert viewPortPane != null
        : "fx:id=\"viewPortPane\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert imageTileScrollpane != null
        : "fx:id=\"imageTileScrollpane\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert pdfSaveFormatChoicebox != null
        : "fx:id=\"pdfSaveFormatChoicebox\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert savePageButton != null
        : "fx:id=\"savePageButton\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert saveAllButton != null
        : "fx:id=\"saveAllButton\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";

    pdfProgressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    extractProgressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    pdfViewPagination.requestFocus();

    pdfSaveFormatChoicebox.setItems(AppConstants.VALID_IMAGE_EXTENSIONS);

    pdfMinDimensionTextField.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));

    pdfMinDimensionTextField
        .focusedProperty()
        .addListener(
            (ov, oldV, newV) -> {
              if (!newV) {
                pdfMinDimensionTextField_onAction();
              }
            });

    renderPdfPageService = Executors.newWorkStealingPool();
    extractImagesService = Executors.newSingleThreadExecutor();
  }

  public void loadPDF(File pdfFile, TokenTool_Controller tokenTool_Controller, Stage stage) {
    pdfModel = new PdfModel(pdfFile, tokenTool_Controller);

    pdfViewPagination.setPageCount(pdfModel.numPages());

    // Set paginations's image to resize with the window. Note: Had to use stage because binding to
    // other panes caused "weirdness"
    // ...adjusting the height to account for the pagination buttons (didn't care to see them over
    // the PDF image)
    // ...adjusting the width to account for tiles + scrollbar
    pdfImageView.setPreserveRatio(true);
    pdfImageView.setTranslateY(-7);
    pdfImageView.fitHeightProperty().bind(Bindings.subtract(stage.heightProperty(), 120));
    pdfImageView
        .fitWidthProperty()
        .bind(Bindings.subtract(stage.widthProperty(), imageTileScrollpane.getWidth() + 30));

    pdfSaveFormatChoicebox
        .getSelectionModel()
        .select(
            AppPreferences.getPreference(
                AppPreferences.PDF_IMAGE_SAVE_FORMAT, AppConstants.DEFAULT_IMAGE_EXTENSION));

    pdfMinDimensionTextField.setText(
        AppPreferences.getPreference(
            AppPreferences.PDF_IMAGE_MIN_EXPORT_SIZE,
            AppConstants.DEFAULT_PDF_IMAGE_MIN_EXPORT_SIZE));

    pdfViewPagination.setPageFactory(
        pageIndex -> {
          log.debug("PDF Pagination change!");

          workerThreads.incrementAndGet();
          imageTilePane.getChildren().clear();

          // First, blank the page out
          pdfImageView.setImage(null);

          // Execute the render off the UI thread...
          RenderPdfPageTask renderPdfPageTask = new RenderPdfPageTask(pageIndex);
          renderPdfPageService.execute(renderPdfPageTask);

          return pdfImageView;
        });
  }

  public String getPdfSaveFormatChoiceBoxSelection() {
    return pdfSaveFormatChoicebox.getSelectionModel().getSelectedItem();
  }

  public String getPdfMinDimensionTextFieldText() {
    return pdfMinDimensionTextField.getText();
  }

  public void close() {
    pdfModel.close();
  }

  @FXML
  void pdfViewPagination_OnScroll(ScrollEvent event) {
    int delta = 1;
    if (event.getDeltaX() > 1 || event.getDeltaY() > 1) {
      delta = -1;
    }

    pdfViewPagination.setCurrentPageIndex(pdfViewPagination.getCurrentPageIndex() + delta);
  }

  @FXML
  void pdfViewPagination_onMouseClick() {
    pdfViewPagination.setCurrentPageIndex(pdfViewPagination.getCurrentPageIndex());
  }

  @FXML
  void pageNumberTextField_onMouseClicked() {
    pageNumberTextField.setOpacity(1);
    pageNumberTextField.selectAll();
  }

  @FXML
  void pageNumberTextField_onAction() {
    int pageNumber = Integer.parseInt(pageNumberTextField.getText());

    if (pageNumber > pdfViewPagination.getPageCount()) {
      pageNumber = pdfViewPagination.getPageCount();
    }

    if (pageNumber > 0) {
      pdfViewPagination.setCurrentPageIndex(pageNumber - 1);
    }

    pageNumberTextField.setText(pdfViewPagination.getCurrentPageIndex() + 1 + "");
    pdfViewPagination.requestFocus();
    pageNumberTextField.setOpacity(0);
  }

  @FXML
  void pdfMinDimensionTextField_onAction() {
    int currentPage = pdfViewPagination.getCurrentPageIndex();
    int otherPage = 0;
    if (currentPage == 0) {
      otherPage = pdfViewPagination.getPageCount();
    }

    pdfViewPagination.setCurrentPageIndex(otherPage);
    pdfViewPagination.setCurrentPageIndex(currentPage);

    pdfMinDimensionTextField.selectAll();
  }

  @FXML
  void savePageButton_onAction() {
    log.info("Saving all images from current page...");

    String imageFormat = getPdfSaveFormatChoiceBoxSelection();
    DirectoryChooser directoryChooser = new DirectoryChooser();
    File lastDirectory =
        new File(AppPreferences.getPreference(AppPreferences.LAST_PDF_EXPORT_LOCATION, ""));

    if (lastDirectory.exists()) {
      directoryChooser.setInitialDirectory(lastDirectory);
    }

    File selectedDirectory = directoryChooser.showDialog(pdfAnchorPane.getScene().getWindow());

    if (selectedDirectory != null) {
      AppPreferences.setPreference(
          AppPreferences.LAST_PDF_EXPORT_LOCATION, selectedDirectory.getPath());

      imageTilePane.getChildren().stream()
          .filter(node -> node instanceof ToggleButton)
          .map(node -> (ToggleButton) node)
          .forEach(
              toggleButton -> {
                String imageName = toggleButton.getId();
                log.info("Button image name is: {}", imageName);
                Image buttonImage = ((ImageView) toggleButton.getGraphic()).getImage();

                try {
                  File tempImageFile;
                  tempImageFile = new File(selectedDirectory, imageName + "." + imageFormat);

                  ImageIO.write(
                      SwingFXUtils.fromFXImage(buttonImage, null), imageFormat, tempImageFile);
                } catch (IOException e) {
                  log.error("Unable to write token to file: " + imageName, e);
                } catch (Exception e) {
                  log.error(e);
                }
              });
    }
  }

  @FXML
  void saveAllButton_onAction() {
    log.info("Saving all images from PDF...");

    String imageFormat = getPdfSaveFormatChoiceBoxSelection();
    DirectoryChooser directoryChooser = new DirectoryChooser();
    File lastDirectory =
        new File(AppPreferences.getPreference(AppPreferences.LAST_PDF_EXPORT_LOCATION, ""));

    if (lastDirectory.exists()) {
      directoryChooser.setInitialDirectory(lastDirectory);
    }

    File selectedDirectory = directoryChooser.showDialog(pdfAnchorPane.getScene().getWindow());

    if (selectedDirectory != null) {
      AppPreferences.setPreference(
          AppPreferences.LAST_PDF_EXPORT_LOCATION, selectedDirectory.getPath());

      pdfViewPagination.setOpacity(0.2);
      pdfViewPagination.setDisable(true);
      pdfSideStackPane.setOpacity(0.2);
      pdfSideStackPane.setDisable(true);

      Task task =
          new Task<Void>() {
            @Override
            public Void call() {
              final int pageCount = pdfModel.getPdfPageCount();

              double imageMinDimension = 0;
              if (!pdfMinDimensionTextField.getText().isBlank()) {
                imageMinDimension = Double.parseDouble(pdfMinDimensionTextField.getText());
              }

              updateProgress(0, pageCount);
              extractAllImagesProgressBar.setVisible(true);
              extractAllImagesLabel.setVisible(true);

              for (int page = 0; page < pageCount; page++) {
                pdfModel.extractAllImagesFromPage(
                    selectedDirectory.getPath(), imageFormat, page, imageMinDimension);
                updateProgress(page, pageCount);
                updateMessage("Extracting page " + page + " of " + pageCount);

                if (pdfModel.isClosed()) {
                  break;
                }
              }

              pdfModel.resetImageHashTracker();

              extractAllImagesProgressBar.setVisible(false);
              extractAllImagesLabel.setVisible(false);

              pdfViewPagination.setDisable(false);
              pdfViewPagination.setOpacity(1.0);
              pdfSideStackPane.setDisable(false);
              pdfSideStackPane.setOpacity(1.0);

              return null;
            }
          };

      extractAllImagesProgressBar.progressProperty().bind(task.progressProperty());
      extractAllImagesLabel.textProperty().bind(task.messageProperty());
      new Thread(task).start();
    }
  }

  private class RenderPdfPageTask extends Task<Void> {

    Integer pageIndex;

    RenderPdfPageTask(Integer pageIndex) {
      this.pageIndex = pageIndex;
    }

    @Override
    protected Void call() {
      // For debugging and tracking the thread...
      Thread.currentThread().setName("RenderPdfPageTask-Page-" + (pageIndex + 1));

      long startTime = System.currentTimeMillis();

      pdfProgressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

      // Don't show the progressIndicator for brief transitions...
      PauseTransition pause = new PauseTransition(Duration.millis(400));
      pause.setOnFinished(
          event -> {
            pdfProgressIndicator.setVisible(true);
            pdfProgressIndicator.setOpacity(1);
          });
      Platform.runLater(() -> pause.play());

      // Do the actual work
      Image image = pdfModel.getImage(pageIndex);

      pdfImageView.setImage(image);

      // Skip the animation for quick page turns
      long loadTime = System.currentTimeMillis() - startTime;
      if (loadTime < 500) {
        Platform.runLater(() -> pause.stop());
        pdfProgressIndicator.setVisible(false);
      } else {
        pdfProgressIndicator.setVisible(true);

        Platform.runLater(
            () -> {
              FadeTransition fadeTransition =
                  new FadeTransition(Duration.millis(500), pdfProgressIndicator);
              fadeTransition.setFromValue(1.0);
              fadeTransition.setToValue(0.0);
              fadeTransition.play();
            });
      }

      return null;
    }

    @Override
    protected void done() {
      // Since we are rendering in multiple threads, lets make sure the current page is shown when
      // we are all done!
      if (workerThreads.decrementAndGet() == 0) {
        pdfImageView.setImage(pdfModel.getImage(pdfViewPagination.getCurrentPageIndex()));

        extractThreads.incrementAndGet();
        pdfModel.interrupt();
        extractImagesService.execute(new ExtractPdfImages(pageIndex));
      }
    }
  }

  private class ExtractPdfImages extends Task<Void> {

    Integer pageIndex;
    ArrayList<ToggleButton> imageButtons = new ArrayList<>();

    ExtractPdfImages(Integer pageIndex) {
      this.pageIndex = pageIndex;
    }

    @Override
    protected Void call() {
      // For debugging and tracking the thread...
      Thread.currentThread().setName("ExtractPdfPageTask-Page-" + (pageIndex + 1));

      long startTime = System.currentTimeMillis();

      extractProgressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

      // Don't show the progressIndicator for brief transitions...
      PauseTransition pause = new PauseTransition(Duration.millis(400));
      pause.setOnFinished(
          event -> {
            extractProgressIndicator.setVisible(true);
            extractProgressIndicator.setOpacity(1);
          });
      pause.play();

      // Do the actual work...
      extractProgressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
      log.info("Extracting...");
      imageButtons = pdfModel.extractImages(pdfViewPagination.getCurrentPageIndex());

      // Skip the animation for quick page turns
      long loadTime = System.currentTimeMillis() - startTime;
      if (loadTime < 500) {
        pause.stop();
        extractProgressIndicator.setVisible(false);
      } else {
        extractProgressIndicator.setVisible(true);
        Platform.runLater(
            () -> {
              FadeTransition fadeTransition =
                  new FadeTransition(Duration.millis(500), extractProgressIndicator);
              fadeTransition.setFromValue(1.0);
              fadeTransition.setToValue(0.0);
              fadeTransition.play();
            });
      }

      return null;
    }

    @Override
    protected void done() {
      Platform.runLater(
          () -> {
            if (extractThreads.decrementAndGet() == 0) {
              log.info("Adding " + imageButtons.size());
              try {
                imageTilePane.getChildren().clear();

                Collections.sort(imageButtons, new ToggleButtonComparator());
                Collections.reverse(imageButtons);

                double pdfMinDimension = 0;
                if (!pdfMinDimensionTextField.getText().isBlank()) {
                  pdfMinDimension = Double.parseDouble(pdfMinDimensionTextField.getText());
                }

                final double minSize = pdfMinDimension;
                // Filter out small images
                imageButtons.removeIf(
                    toggleButton ->
                        ((ImageView) toggleButton.getGraphic()).getImage().getWidth() < minSize);
                imageButtons.removeIf(
                    toggleButton ->
                        ((ImageView) toggleButton.getGraphic()).getImage().getHeight() < minSize);

                imageTilePane.getChildren().addAll(imageButtons);
              } catch (IllegalArgumentException e) {
                log.error("Error adding tiled image buttons.", e);
              }
            }
          });
    }
  }

  private static class ToggleButtonComparator implements Comparator<ToggleButton> {

    @Override
    public int compare(ToggleButton tb1, ToggleButton tb2) {
      Double d1 =
          ((ImageView) tb1.getGraphic()).getImage().getWidth()
              * ((ImageView) tb1.getGraphic()).getImage().getHeight();
      Double d2 =
          ((ImageView) tb2.getGraphic()).getImage().getWidth()
              * ((ImageView) tb2.getGraphic()).getImage().getHeight();
      return Double.compare(d1, d2);
    }
  }

  public void exitApplication() {
    pdfModel.interrupt();
    pdfModel.close();
  }
}
