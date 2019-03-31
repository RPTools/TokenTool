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
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.model.PdfModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PdfViewer_Controller implements Initializable {
  private static final Logger log = LogManager.getLogger(PdfViewer_Controller.class);

  @FXML private AnchorPane pdfAnchorPane;
  @FXML private Pagination pdfViewPagination;
  @FXML private TextField pageNumberTextField;
  @FXML private ScrollPane imageScrollPane;
  @FXML private TilePane imageTilePane;
  @FXML private ProgressIndicator pdfProgressIndicator;
  @FXML private ProgressIndicator extractProgressIndicator;
  @FXML private Pane viewPortPane;
  @FXML private ScrollPane imageTileScrollpane;

  private PdfModel pdfModel;
  private ImageView pdfImageView = new ImageView();

  private static ExecutorService renderPdfPageService;
  private static ExecutorService extractImagesService;
  private AtomicInteger workerThreads = new AtomicInteger(0);
  private AtomicInteger extractThreads = new AtomicInteger(0);

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
    assert pageNumberTextField != null
        : "fx:id=\"pageNumberTextField\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert imageScrollPane != null
        : "fx:id=\"imageScrollPane\" was not injected: check your FXML file '"
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
    assert viewPortPane != null
        : "fx:id=\"viewPortPane\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";
    assert imageTileScrollpane != null
        : "fx:id=\"imageTileScrollpane\" was not injected: check your FXML file '"
            + AppConstants.PDF_VIEW_FXML
            + "'.";

    pdfProgressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    extractProgressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    pdfViewPagination.requestFocus();

    renderPdfPageService = Executors.newWorkStealingPool();
    extractImagesService = Executors.newSingleThreadExecutor();
  }

  public void loadPDF(File pdfFile, TokenTool_Controller tokenTool_Controller, Stage stage) {
    try {
      pdfModel = new PdfModel(pdfFile, tokenTool_Controller);
    } catch (IOException e) {
      log.error("Error loading PDF " + pdfFile.getAbsolutePath(), e);
    }

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

    pdfViewPagination.setPageFactory(
        new Callback<Integer, Node>() {
          public Node call(final Integer pageIndex) {
            workerThreads.incrementAndGet();
            imageTilePane.getChildren().clear();

            // First, blank the page out
            pdfImageView.setImage(null);

            // Execute the render off the UI thread...
            RenderPdfPageTask renderPdfPageTask = new RenderPdfPageTask(pageIndex);
            renderPdfPageService.execute(renderPdfPageTask);

            return pdfImageView;
          }
        });
  }

  private class RenderPdfPageTask extends Task<Void> {
    Integer pageIndex;

    RenderPdfPageTask(Integer pageIndex) {
      this.pageIndex = pageIndex;
    }

    @Override
    protected Void call() throws Exception {
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
      pause.play();

      // Do the actual work
      Image image = pdfModel.getImage(pageIndex);

      pdfImageView.setImage(image);

      // Skip the animation for quick page turns
      long loadTime = System.currentTimeMillis() - startTime;
      if (loadTime < 500) {
        pause.stop();
        pdfProgressIndicator.setVisible(false);
      } else {
        pdfProgressIndicator.setVisible(true);

        FadeTransition fadeTransition =
            new FadeTransition(Duration.millis(500), pdfProgressIndicator);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.0);
        fadeTransition.play();
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
    ArrayList<ToggleButton> imageButtons = new ArrayList<ToggleButton>();

    ExtractPdfImages(Integer pageIndex) {
      this.pageIndex = pageIndex;
    }

    @Override
    protected Void call() throws Exception {
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

        FadeTransition fadeTransition =
            new FadeTransition(Duration.millis(500), extractProgressIndicator);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.0);
        fadeTransition.play();
      }

      return null;
    }

    @Override
    protected void done() {
      Platform.runLater(
          () -> {
            if (extractThreads.decrementAndGet() == 0) {
              log.info("Adding " + imageButtons.size());
              // log.info("imageTilePane.getChildren() " + imageTilePane.getChildren().size());
              try {
                imageTilePane.getChildren().clear();
                imageTilePane.getChildren().addAll(imageButtons);
              } catch (IllegalArgumentException e) {
                log.error("Error adding tiled image buttons.", e);
              }
              // log.info("Done...");
            }
          });
    }
  }

  class getPageImageView extends Task<Node> {
    Integer pageIndex;

    getPageImageView(Integer pageIndex) {
      this.pageIndex = pageIndex;
    }

    @Override
    protected Node call() throws Exception {

      return null;
    }
  }

  // private void extractImages() {
  // imageTilePane.getChildren().clear();
  // pdfModel.extractImages(imageTilePane, pdfViewPagination.getCurrentPageIndex());
  // }

  public void close() {
    pdfModel.close();
  }

  @FXML
  void pdfViewPagination_OnScroll(ScrollEvent event) {
    int delta = 1;
    if (event.getDeltaX() > 1 || event.getDeltaY() > 1) delta = -1;

    pdfViewPagination.setCurrentPageIndex(pdfViewPagination.getCurrentPageIndex() + delta);
  }

  @FXML
  void pdfViewPagination_onMouseClick(MouseEvent event) {
    pdfViewPagination.setCurrentPageIndex(pdfViewPagination.getCurrentPageIndex());
  }

  @FXML
  void pageNumberTextField_onMouseClicked(MouseEvent event) {
    pageNumberTextField.setOpacity(1);
    pageNumberTextField.selectAll();
  }

  @FXML
  void pageNumberTextField_onAction(ActionEvent event) {
    int pageNumber = Integer.parseInt(pageNumberTextField.getText());

    if (pageNumber > pdfViewPagination.getPageCount())
      pageNumber = pdfViewPagination.getPageCount();

    if (pageNumber > 0) pdfViewPagination.setCurrentPageIndex(pageNumber - 1);

    pageNumberTextField.setText(pdfViewPagination.getCurrentPageIndex() + 1 + "");
    pdfViewPagination.requestFocus();
    pageNumberTextField.setOpacity(0);
  }
}
