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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javax.imageio.ImageIO;
import net.rptools.tokentool.AppConstants;
import net.rptools.tokentool.AppPreferences;
import net.rptools.tokentool.client.Credits;
import net.rptools.tokentool.client.ManageOverlays;
import net.rptools.tokentool.client.PdfViewer;
import net.rptools.tokentool.client.RegionSelector;
import net.rptools.tokentool.client.TokenTool;
import net.rptools.tokentool.model.ImageView_Preferences;
import net.rptools.tokentool.model.Window_Preferences;
import net.rptools.tokentool.util.FileSaveUtil;
import net.rptools.tokentool.util.I18N;
import net.rptools.tokentool.util.ImageUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenTool_Controller {

  private static final Logger log = LogManager.getLogger(TokenTool_Controller.class);
  private static ExecutorService executorService;
  private static Thread loadOverlaysThread = new Thread();
  private static AtomicInteger loadCount = new AtomicInteger(0);
  private static int overlayCount;
  private static TreeItem<Path> treeItems;
  private static TreeItem<Path> lastSelectedItem;
  private static TreeItem<Path> recentFolder =
      new TreeItem<>(new File(AppConstants.OVERLAY_DIR, "Recent").toPath(), null);
  private static Map<Path, TreeItem<Path>> recentOverlayTreeItems =
      new LinkedHashMap<>() {
        private static final long serialVersionUID = 2579964060760662199L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, TreeItem<Path>> eldest) {
          return size() > AppConstants.MAX_RECENT_SIZE;
        }
      };
  private static Point dragStart = new Point();
  private static Point currentImageOffset = new Point();
  @FXML private MenuItem fileOpenPDF_Menu;
  @FXML private MenuItem fileManageOverlaysMenu;
  @FXML private MenuItem fileSaveAsMenu;
  @FXML private MenuItem fileExitMenu;
  @FXML private MenuItem editCaptureScreenMenu;
  @FXML private MenuItem editCopyImageMenu;
  @FXML private MenuItem editPasteImageMenu;
  @FXML private MenuItem helpAboutMenu;
  @FXML private TitledPane saveOptionsPane;
  @FXML private TitledPane overlayOptionsPane;
  @FXML private TitledPane backgroundOptionsPane;
  @FXML private TitledPane zoomOptionsPane;
  @FXML private StackPane compositeTokenPane;
  @FXML private BorderPane tokenPreviewPane;
  @FXML private ScrollPane portraitScrollPane;
  @FXML private Group compositeGroup;
  @FXML private Pane dndHighlights;
  @FXML private TreeView<Path> overlayTreeView;
  @FXML private StackPane imagesStackPane;
  @FXML private ImageView backgroundImageView; // The background image layer
  @FXML private ImageView portraitImageView; // The bottom "Portrait" layer
  @FXML private ImageView maskImageView; // The mask layer used to crop the Portrait layer
  @FXML private ImageView overlayImageView; // The overlay layer to apply on top of everything
  @FXML private ImageView tokenImageView; // The final token image created
  @FXML private CheckBox useFileNumberingCheckbox;
  @FXML private CheckBox useTokenNameCheckbox;
  @FXML private CheckBox savePortraitOnDragCheckbox;
  @FXML private CheckBox useBackgroundOnDragCheckbox;
  @FXML private CheckBox overlayUseAsBaseCheckbox;
  @FXML private CheckBox clipPortraitCheckbox;
  @FXML private TextField fileNameTextField;
  @FXML private Label fileNameSuffixLabel;
  @FXML private TextField fileNameSuffixTextField;
  @FXML private ChoiceBox<String> fileSaveFormatChoicebox;
  @FXML private TextField portraitNameTextField;
  @FXML private Label portraitNameSuffixLabel;
  @FXML private TextField portraitNameSuffixTextField;
  @FXML private ChoiceBox<String> portraitSaveFormatChoicebox;
  @FXML private Label overlayNameLabel;
  @FXML private Label overlayInfoLabel;
  @FXML private ColorPicker backgroundColorPicker;
  @FXML private ToggleButton overlayAspectToggleButton;
  @FXML private Slider portraitTransparencySlider;
  @FXML private Slider portraitBlurSlider;
  @FXML private Slider portraitGlowSlider;
  @FXML private Slider overlayTransparencySlider;
  @FXML private Spinner<Integer> overlayWidthSpinner;
  @FXML private Spinner<Integer> overlayHeightSpinner;
  @FXML private ProgressBar overlayTreeProgressBar;
  @FXML private Label progressBarLabel;
  @FXML private MenuButton layerMenuButton;
  @FXML private RadioMenuItem backgroundMenuItem;
  @FXML private RadioMenuItem portraitMenuItem;
  @FXML private RadioMenuItem overlayMenuItem;
  private FileSaveUtil fileSaveUtil = new FileSaveUtil();

  // A custom set of Width/Height sizes to use for Overlays
  private NavigableSet<Integer> overlaySpinnerSteps =
      new TreeSet<>(
          Arrays.asList(
              50, 100, 128, 150, 200, 256, 300, 400, 500, 512, 600, 700, 750, 800, 900, 1000));

  private PdfViewer pdfViewer;

  @FXML
  void initialize() {
    // Note: A Pane is added to the compositeTokenPane so the ScrollPane doesn't
    // consume the mouse events
    assert fileOpenPDF_Menu != null
        : "fx:id=\"fileOpenPDF_Menu\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert fileManageOverlaysMenu != null
        : "fx:id=\"fileManageOverlaysMenu\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert fileSaveAsMenu != null
        : "fx:id=\"fileSaveAsMenu\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert fileExitMenu != null
        : "fx:id=\"fileExitMenu\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert editCaptureScreenMenu != null
        : "fx:id=\"editCaptureScreenMenu\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert editCopyImageMenu != null
        : "fx:id=\"editCopyImageMenu\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert editPasteImageMenu != null
        : "fx:id=\"editPasteImageMenu\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert helpAboutMenu != null
        : "fx:id=\"helpAboutMenu\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert saveOptionsPane != null
        : "fx:id=\"saveOptionsPane\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert overlayOptionsPane != null
        : "fx:id=\"overlayOptionsPane\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert backgroundOptionsPane != null
        : "fx:id=\"backgroundOptionsPane\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert zoomOptionsPane != null
        : "fx:id=\"zoomOptionsPane\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert compositeTokenPane != null
        : "fx:id=\"compositeTokenPane\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert tokenPreviewPane != null
        : "fx:id=\"tokenPreviewPane\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitScrollPane != null
        : "fx:id=\"portraitScrollPane\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert compositeGroup != null
        : "fx:id=\"compositeGroup\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert dndHighlights != null
        : "fx:id=\"dndHighlights\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert overlayTreeView != null
        : "fx:id=\"overlayTreeview\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert backgroundImageView != null
        : "fx:id=\"backgroundImageView\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitImageView != null
        : "fx:id=\"portraitImageView\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert maskImageView != null
        : "fx:id=\"maskImageView\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert overlayImageView != null
        : "fx:id=\"overlayImageView\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert tokenImageView != null
        : "fx:id=\"tokenImageView\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert useFileNumberingCheckbox != null
        : "fx:id=\"useFileNumberingCheckbox\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert useTokenNameCheckbox != null
        : "fx:id=\"useTokenNameCheckbox\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert savePortraitOnDragCheckbox != null
        : "fx:id=\"savePortraitOnDragCheckbox\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert useBackgroundOnDragCheckbox != null
        : "fx:id=\"useBackgroundOnDragCheckbox\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert overlayUseAsBaseCheckbox != null
        : "fx:id=\"overlayUseAsBaseCheckbox\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert clipPortraitCheckbox != null
        : "fx:id=\"clipPortraitCheckbox\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert fileNameTextField != null
        : "fx:id=\"fileNameTextField\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert fileNameSuffixLabel != null
        : "fx:id=\"fileNameSuffixLabel\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert fileNameSuffixTextField != null
        : "fx:id=\"fileNameSuffixTextField\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert fileSaveFormatChoicebox != null
        : "fx:id=\"fileSaveFormatChoicebox\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitNameTextField != null
        : "fx:id=\"portraitNameTextField\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitNameSuffixLabel != null
        : "fx:id=\"portraitNameSuffixLabel\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitNameSuffixTextField != null
        : "fx:id=\"portraitNameSuffixTextField\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitSaveFormatChoicebox != null
        : "fx:id=\"portraitSaveFormatChoicebox\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert overlayNameLabel != null
        : "fx:id=\"overlayNameLabel\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert overlayInfoLabel != null
        : "fx:id=\"overlayInfoLabel\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert backgroundColorPicker != null
        : "fx:id=\"backgroundColorPicker\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert overlayAspectToggleButton != null
        : "fx:id=\"overlayAspectToggleButton\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert portraitTransparencySlider != null
        : "fx:id=\"portraitTransparencySlider\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitBlurSlider != null
        : "fx:id=\"portraitBlurSlider\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitGlowSlider != null
        : "fx:id=\"portraitGlowSlider\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert overlayTransparencySlider != null
        : "fx:id=\"overlayTransparencySlider\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert overlayWidthSpinner != null
        : "fx:id=\"overlayWidthSpinner\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert overlayHeightSpinner != null
        : "fx:id=\"overlayHeightSpinner\" was not injected: check your FXML file 'TokenTool.fxml'.";

    assert overlayTreeProgressBar != null
        : "fx:id=\"overlayTreeProgressIndicator\" was not injected: check your FXML file 'ManageOverlays.fxml'.";

    assert layerMenuButton != null
        : "fx:id=\"layerMenuButton\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert backgroundMenuItem != null
        : "fx:id=\"backgroundMenuItem\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert portraitMenuItem != null
        : "fx:id=\"portraitMenuItem\" was not injected: check your FXML file 'TokenTool.fxml'.";
    assert overlayMenuItem != null
        : "fx:id=\"overlayMenuItem\" was not injected: check your FXML file 'TokenTool.fxml'.";

    // We're getting the defaults set by the FXML before updating them with the saved preferences...
    AppConstants.DEFAULT_MASK_IMAGE = maskImageView.getImage();
    AppConstants.DEFAULT_OVERLAY_IMAGE = overlayImageView.getImage();
    AppConstants.DEFAULT_PORTRAIT_IMAGE = portraitImageView.getImage();
    AppConstants.DEFAULT_PORTRAIT_IMAGE_X = portraitImageView.getTranslateX();
    AppConstants.DEFAULT_PORTRAIT_IMAGE_Y = portraitImageView.getTranslateY();
    AppConstants.DEFAULT_PORTRAIT_IMAGE_SCALE = portraitImageView.getScaleY();
    AppConstants.DEFAULT_PORTRAIT_IMAGE_ROTATE = portraitImageView.getRotate();
    AppConstants.DEFAULT_SAVE_PORTRAIT_ON_DRAG = getSavePortraitOnDragCheckbox();
    AppConstants.DEFAULT_USE_BACKGROUND_ON_DRAG = getUseBackgroundOnDragCheckbox();
    AppConstants.DEFAULT_PORTRAIT_NAME_TEXT_FIELD = getPortraitNameTextField();
    AppConstants.DEFAULT_USE_TOKEN_NAME = getUseTokenNameCheckbox();
    AppConstants.DEFAULT_PORTRAIT_NAME_SUFFIX_TEXT_FIELD = getPortraitNameSuffixTextField();

    executorService =
        Executors.newCachedThreadPool(
            runnable -> {
              loadOverlaysThread = Executors.defaultThreadFactory().newThread(runnable);
              loadOverlaysThread.setDaemon(true);
              return loadOverlaysThread;
            });

    overlayTreeView.setShowRoot(false);
    overlayTreeView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((observable, oldValue, newValue) -> updateCompositeImageView(newValue));

    addPseudoClassToLeafs(overlayTreeView);

    // Bind color picker to compositeTokenPane background fill
    backgroundColorPicker.setValue(Color.TRANSPARENT);
    ObjectProperty<Background> background = compositeTokenPane.backgroundProperty();
    background.bind(
        Bindings.createObjectBinding(
            () -> {
              BackgroundFill fill =
                  new BackgroundFill(
                      backgroundColorPicker.getValue(), CornerRadii.EMPTY, Insets.EMPTY);
              return new Background(fill);
            },
            backgroundColorPicker.valueProperty()));

    // Bind transparency slider to portraitImageView opacity
    portraitTransparencySlider
        .valueProperty()
        .addListener(
            (ov, old_val, new_val) -> {
              portraitImageView.setOpacity(new_val.doubleValue());
              updateTokenPreviewImageView();
            });

    // Set filters and bindings for file name inputs
    UnaryOperator<Change> filter =
        change -> {
          change.setText(FileSaveUtil.cleanFileName(change.getText()));
          return change;
        };

    fileNameTextField.setTextFormatter(new TextFormatter<>(filter));

    portraitNameTextField.setTextFormatter(new TextFormatter<>(filter));
    portraitNameTextField
        .textProperty()
        .bind(fileNameTextField.textProperty().concat(portraitNameSuffixTextField.textProperty()));

    // Bind portrait name to token name if useTokenNameCheckbox is checked
    portraitNameTextField.disableProperty().bind(useTokenNameCheckbox.selectedProperty());
    portraitNameSuffixLabel.disableProperty().bind(useTokenNameCheckbox.selectedProperty().not());
    portraitNameSuffixTextField
        .disableProperty()
        .bind(useTokenNameCheckbox.selectedProperty().not());

    // Get valid image file type formats and populate choice boxes
    fileSaveFormatChoicebox.setItems(AppConstants.VALID_IMAGE_EXTENSIONS);
    portraitSaveFormatChoicebox.setItems(AppConstants.VALID_IMAGE_EXTENSIONS);

    // Bind the use background on drag to save portrait on drag checkbox
    useBackgroundOnDragCheckbox
        .disableProperty()
        .bind(savePortraitOnDragCheckbox.selectedProperty().not());

    useTokenNameCheckbox
        .selectedProperty()
        .addListener(
            (obs, wasSelected, isNowSelected) -> {
              if (isNowSelected) {
                portraitNameTextField
                    .textProperty()
                    .bind(
                        fileNameTextField
                            .textProperty()
                            .concat(portraitNameSuffixTextField.textProperty()));
              } else {
                portraitNameTextField.textProperty().unbind();
              }
            });

    /* Effects */
    GaussianBlur gaussianBlur = new GaussianBlur(0);
    Glow glow = new Glow(0);
    gaussianBlur.setInput(glow);

    // Bind blur slider to portraitImageView opacity
    portraitBlurSlider
        .valueProperty()
        .addListener(
            (ov, old_val, new_val) -> {
              gaussianBlur.setRadius(new_val.doubleValue());
              portraitImageView.setEffect(gaussianBlur);
              updateTokenPreviewImageView();
            });

    // Bind glow slider to portraitImageView opacity
    portraitGlowSlider
        .valueProperty()
        .addListener(
            (ov, old_val, new_val) -> {
              glow.setLevel(new_val.doubleValue());
              portraitImageView.setEffect(gaussianBlur);
              updateTokenPreviewImageView();
            });

    // Bind transparency slider to overlayImageView opacity
    overlayTransparencySlider
        .valueProperty()
        .addListener(
            (ov, old_val, new_val) -> {
              overlayImageView.setOpacity(new_val.doubleValue());
              updateTokenPreviewImageView();
            });

    // Bind width/height spinners to overlay width/height
    overlayWidthSpinner
        .getValueFactory()
        .valueProperty()
        .bindBidirectional(overlayHeightSpinner.getValueFactory().valueProperty());
    overlayWidthSpinner
        .valueProperty()
        .addListener(
            (observable, oldValue, newValue) ->
                overlayWidthSpinner_onTextChanged(oldValue, newValue));
    overlayWidthSpinner
        .getValueFactory()
        .setConverter(
            new StringConverter<>() {
              @Override
              public String toString(Integer object) {
                return object.toString();
              }

              @Override
              public Integer fromString(String string) {
                int value = 256;

                try {
                  value = Integer.parseInt(string);
                } catch (NumberFormatException e) {
                  log.debug("Invalid overlay size entered.", e);
                }

                return value;
              }
            });
    overlayHeightSpinner
        .valueProperty()
        .addListener(
            (observable, oldValue, newValue) ->
                overlayHeightSpinner_onTextChanged(oldValue, newValue));
    overlayHeightSpinner
        .getValueFactory()
        .setConverter(
            new StringConverter<>() {
              @Override
              public String toString(Integer object) {
                return object.toString();
              }

              @Override
              public Integer fromString(String string) {
                int value = 256;

                try {
                  value = Integer.parseInt(string);
                } catch (NumberFormatException e) {
                  log.info("NOPE");
                }

                return value;
              }
            });

    // Bind the background/portrait pane widths to keep things centered.
    // Otherwise StackPane sets width/height to largest value from the ImageView nodes within it
    imagesStackPane.minWidthProperty().bind(compositeTokenPane.widthProperty());
    imagesStackPane.minHeightProperty().bind(compositeTokenPane.heightProperty());
  }

  @FXML
  void removeBackgroundColorButton_OnAction() {
    backgroundColorPicker.setValue(Color.TRANSPARENT);
    updateTokenPreviewImageView();
  }

  @FXML
  void changeBackgroundImageButton_OnAction() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(I18N.getString("TokenTool.openBackgroundImage.filechooser.title"));
    fileChooser.getExtensionFilters().addAll(AppConstants.IMAGE_EXTENSION_FILTER);

    File lastBackgroundImageFile =
        new File(AppPreferences.getPreference(AppPreferences.LAST_BACKGROUND_IMAGE_FILE, ""));

    if (lastBackgroundImageFile.exists()) {
      fileChooser.setInitialDirectory(lastBackgroundImageFile);
    } else if (lastBackgroundImageFile.getParentFile() != null) {
      fileChooser.setInitialDirectory(lastBackgroundImageFile.getParentFile());
    }

    File selectedImageFile = fileChooser.showOpenDialog(compositeGroup.getScene().getWindow());

    if (selectedImageFile != null) {
      try {
        updateBackground(new Image(selectedImageFile.toURI().toString()));
        AppPreferences.setPreference(
            AppPreferences.LAST_BACKGROUND_IMAGE_FILE,
            selectedImageFile.getParentFile().getCanonicalPath());
      } catch (IOException e) {
        log.error("Error loading Image " + selectedImageFile.getAbsolutePath());
      }
    }

    updateTokenPreviewImageView();
    backgroundMenuItem.fire(); // Set current layer to background
  }

  @FXML
  void removeBackgroundImageButton_OnAction() {
    backgroundImageView.setImage(null);
    updateTokenPreviewImageView();
    portraitMenuItem.fire(); // Set current layer to portrait
  }

  @FXML
  void changePortraitImageButton_OnAction() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(I18N.getString("TokenTool.openPortraitImage.filechooser.title"));
    fileChooser.getExtensionFilters().addAll(AppConstants.IMAGE_EXTENSION_FILTER);

    File lastPortraitImageFile =
        new File(AppPreferences.getPreference(AppPreferences.LAST_PORTRAIT_IMAGE_FILE, ""));

    if (lastPortraitImageFile.exists()) {
      fileChooser.setInitialDirectory(lastPortraitImageFile);
    } else if (lastPortraitImageFile.getParentFile() != null) {
      fileChooser.setInitialDirectory(lastPortraitImageFile.getParentFile());
    }

    File selectedImageFile = fileChooser.showOpenDialog(compositeGroup.getScene().getWindow());

    if (selectedImageFile != null) {
      try {
        updatePortrait(new Image(selectedImageFile.toURI().toString()));
        AppPreferences.setPreference(
            AppPreferences.LAST_PORTRAIT_IMAGE_FILE,
            selectedImageFile.getParentFile().getCanonicalPath());
      } catch (IOException e) {
        log.error("Error loading Image " + selectedImageFile.getAbsolutePath());
      }
    }

    updateTokenPreviewImageView();
    portraitMenuItem.fire(); // Set current layer to portrait
  }

  @FXML
  void removePortraitImageButton_OnAction() {
    portraitImageView.setImage(null);
    updateTokenPreviewImageView();
    portraitMenuItem.fire(); // Set current layer to portrait
  }

  @FXML
  void fileOpenPDF_Menu_OnAction() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(I18N.getString("TokenTool.openPDF.filechooser.title"));
    fileChooser.getExtensionFilters().add(ImageUtil.SUPPORTED_PDF_EXTENSION_FILTER);

    File lastPdfFile = new File(AppPreferences.getPreference(AppPreferences.LAST_PDF_FILE, ""));

    if (lastPdfFile.exists()) {
      fileChooser.setInitialDirectory(lastPdfFile);
    } else if (lastPdfFile.getParentFile() != null) {
      fileChooser.setInitialDirectory(lastPdfFile.getParentFile());
    }

    File selectedPDF = fileChooser.showOpenDialog(compositeGroup.getScene().getWindow());

    loadPDF(selectedPDF);
  }

  private void loadPDF(File selectedPDF) {
    if (selectedPDF != null) {
      Platform.runLater(
          () -> {
            try {
              pdfViewer = new PdfViewer(selectedPDF, this);
              AppPreferences.setPreference(
                  AppPreferences.LAST_PDF_FILE, selectedPDF.getParentFile().getCanonicalPath());
            } catch (IOException e) {
              log.error("Error loading PDF " + selectedPDF.getAbsolutePath());
            }
          });
    }
  }

  @FXML
  void fileManageOverlaysMenu_OnAction() {
    new ManageOverlays(this);
  }

  @FXML
  void fileSaveAsMenu_OnAction() {
    saveToken();
  }

  @FXML
  void fileExitMenu_OnAction() {
    exitApplication();
  }

  @FXML
  void editCaptureScreenMenu_OnAction() {
    new RegionSelector(this);
  }

  @FXML
  void editCopyImageMenu_OnAction() {
    Clipboard clipboard = Clipboard.getSystemClipboard();
    ClipboardContent content = new ClipboardContent();

    // for paste as file, e.g. in Windows Explorer
    try {
      File tempTokenFile =
          fileSaveUtil.getTempFileName(
              false,
              useFileNumberingCheckbox.isSelected(),
              fileNameTextField.getText(),
              getFileSaveFormatChoiceboxSelection(),
              fileNameSuffixTextField);

      writeTokenImage(tempTokenFile);
      content.putFiles(java.util.Collections.singletonList(tempTokenFile));
      tempTokenFile.deleteOnExit();
    } catch (Exception e) {
      log.error(e);
    }

    // for paste as image, e.g. in GIMP
    content.putImage(tokenImageView.getImage());

    // Finally, put contents on clip board
    clipboard.setContent(content);
  }

  @FXML
  void editPasteImageMenu_OnAction() {
    Clipboard clipboard = Clipboard.getSystemClipboard();
    Image originalImage = portraitImageView.getImage();

    // Strangely, we get an error if we try to paste an image we put in the clipboard ourselves but
    // File works ok?
    // -Dprism.order=sw also fixes it but not sure why...
    // So lets just check for File first...
    if (clipboard.hasFiles()) {
      clipboard
          .getFiles()
          .forEach(
              file -> {
                try {
                  String fileName = FilenameUtils.getName(file.toURI().toURL().toExternalForm());

                  if (FilenameUtils.isExtension(fileName.toLowerCase(), "pdf")) {
                    loadPDF(file);
                  } else {
                    // Be sure to use ImageIO.read so we can use external libs for other image types
                    updateImage(
                        SwingFXUtils.toFXImage(ImageIO.read(file), null),
                        FilenameUtils.getBaseName(fileName));
                  }
                } catch (Exception e) {
                  log.error("Could not load image from clipboard " + file);
                  e.printStackTrace();
                }
              });
    } else if (clipboard.hasImage()) {
      try {
        Image cbImage = clipboard.getImage();
        if (cbImage != null) {
          updateImage(cbImage);
        }
      } catch (IllegalArgumentException e) {
        log.info(e);
        updatePortrait(originalImage);
      }
    } else if (clipboard.hasUrl()) {
      try {
        updateImage(
            SwingFXUtils.toFXImage(ImageIO.read(new URL(clipboard.getUrl())), null),
            FileSaveUtil.searchURL(clipboard.getUrl()));
      } catch (IOException e) {
        log.warn(e);
      }
    } else if (clipboard.hasString()) {
      try {
        updateImage(
            SwingFXUtils.toFXImage(ImageIO.read(new URL(clipboard.getString())), null),
            FileSaveUtil.searchURL(clipboard.getString()));
      } catch (IOException e) {
        log.warn(e);
      }
    }
  }

  @FXML
  void helpAboutMenu_OnAction() {
    new Credits();
  }

  @FXML
  void helpResetMenu_OnAction() {
    String confirmationText = I18N.getString("TokenTool.dialog.reset.confirmation.text");

    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setHeaderText(I18N.getString("TokenTool.dialog.confirmation.header"));
    alert.setTitle(I18N.getString("TokenTool.dialog.reset.confirmation.title"));
    alert.setContentText(confirmationText);

    Optional<ButtonType> result = alert.showAndWait();

    if (!((result.isPresent()) && (result.get() == ButtonType.OK))) {
      return;
    }

    // OK, reset everything!
    AppPreferences.removeAllPreferences();
    AppPreferences.restorePreferences(this);

    maskImageView.setImage(AppConstants.DEFAULT_MASK_IMAGE);
    overlayImageView.setImage(AppConstants.DEFAULT_OVERLAY_IMAGE);

    portraitImageView.setImage(AppConstants.DEFAULT_PORTRAIT_IMAGE);
    portraitImageView.setTranslateX(AppConstants.DEFAULT_PORTRAIT_IMAGE_X);
    portraitImageView.setTranslateY(AppConstants.DEFAULT_PORTRAIT_IMAGE_Y);
    portraitImageView.setScaleX(AppConstants.DEFAULT_PORTRAIT_IMAGE_SCALE);
    portraitImageView.setScaleY(AppConstants.DEFAULT_PORTRAIT_IMAGE_SCALE);
    portraitImageView.setRotate(AppConstants.DEFAULT_PORTRAIT_IMAGE_ROTATE);

    portraitMenuItem.fire();

    recentOverlayTreeItems.clear();
    lastSelectedItem = null;
    updateOverlayTreeViewRecentFolder(true);

    Platform.runLater(this::updateTokenPreviewImageView);
    TokenTool.getInstance().getStage().setMaximized(false);
  }

  @FXML
  void useFileNumberingCheckbox_OnAction() {
    fileNameSuffixLabel.setDisable(!useFileNumberingCheckbox.isSelected());
    fileNameSuffixTextField.setDisable(!useFileNumberingCheckbox.isSelected());
  }

  @FXML
  void compositeTokenPane_KeyPressed(KeyEvent key) {
    if (key.getCode().isArrowKey()
        || key.getCode().isNavigationKey()
        || key.getCode().isKeypadKey()) {

      double x = getCurrentLayer().getTranslateX();
      double y = getCurrentLayer().getTranslateY();

      switch (key.getCode()) {
        case LEFT:
        case KP_LEFT:
        case NUMPAD4:
          x--;
          break;
        case RIGHT:
        case KP_RIGHT:
        case NUMPAD6:
          x++;
          break;
        case UP:
        case KP_UP:
        case NUMPAD8:
          y--;
          break;
        case DOWN:
        case KP_DOWN:
        case NUMPAD2:
          y++;
          break;
        case HOME:
        case NUMPAD7:
          x--;
          y--;
          break;
        case END:
        case NUMPAD1:
          x--;
          y++;
          break;
        case PAGE_UP:
        case NUMPAD9:
          x++;
          y--;
          break;
        case PAGE_DOWN:
        case NUMPAD3:
          x++;
          y++;
          break;
        default:
          break;
      }

      getCurrentLayer().setTranslateX(x);
      getCurrentLayer().setTranslateY(y);
      currentImageOffset.setLocation(x, y);

      updateTokenPreviewImageView();

      key.consume();
    }
  }

  private ImageView getCurrentLayer() {
    if (backgroundMenuItem.isSelected()) {
      return backgroundImageView;
    } else {
      return portraitImageView;
    }
  }

  @FXML
  void compositeTokenPane_MouseDragged(MouseEvent event) {
    getCurrentLayer().setTranslateX(event.getX() - dragStart.x + currentImageOffset.x);
    getCurrentLayer().setTranslateY(event.getY() - dragStart.y + currentImageOffset.y);

    updateTokenPreviewImageView();
  }

  @FXML
  void compositeTokenPane_MousePressed(MouseEvent event) {
    dragStart.setLocation(event.getX(), event.getY());
    currentImageOffset.setLocation(
        getCurrentLayer().getTranslateX(), getCurrentLayer().getTranslateY());
    portraitImageView.setCursor(Cursor.MOVE);

    // Get focus for arrow keys...
    compositeTokenPane.requestFocus();
  }

  @FXML
  void compositeTokenPane_MouseReleased() {
    portraitImageView.setCursor(Cursor.HAND);
    updateTokenPreviewImageView();
  }

  @FXML
  void compositeTokenPane_MouseEntered() {
    portraitImageView.setCursor(Cursor.HAND); // TODO: Not working...
  }

  @FXML
  void compositeTokenPane_MouseDragExited(MouseDragEvent event) {}

  @FXML
  void compositeTokenPane_MouseExited(MouseEvent event) {}

  @FXML
  void compositeTokenPane_MouseMoved(MouseEvent event) {}

  @FXML
  void compositeTokenPane_OnScroll(ScrollEvent event) {
    // if event is touch enabled, skip this as it will be handled by onZoom & onRotate handlers
    if (event.isDirect()) {
      return;
    }

    double fineAdjustment = 1;
    if (event.isControlDown()) {
      fineAdjustment = 0.1;
    }

    if (event.isShiftDown()) {
      // Note: OK, this is stupid but on Windows shift + mousewheel returns X delta but on Ubuntu it
      // returns Y delta...
      double delta = event.getDeltaY();
      if (delta == 0) {
        delta = event.getDeltaX();
      }

      delta = delta * fineAdjustment;

      double r = getCurrentLayer().getRotate() + delta / 20;

      if (r < -360d || r > 360d) {
        r = 0d;
      }

      getCurrentLayer().setRotate(r);
    } else {
      double scale =
          getCurrentLayer().getScaleY() * Math.pow(1.001, (event.getDeltaY() * fineAdjustment));

      getCurrentLayer().setScaleX(scale);
      getCurrentLayer().setScaleY(scale);
    }

    event.consume();
    updateTokenPreviewImageView();
  }

  @FXML
  void compositeTokenPane_OnZoom(ZoomEvent event) {
    double scale = getCurrentLayer().getScaleY() * event.getZoomFactor();

    getCurrentLayer().setScaleX(scale);
    getCurrentLayer().setScaleY(scale);
  }

  @FXML
  void compositeTokenPane_OnRotate(RotateEvent event) {
    log.info("isDirect(): " + event.isDirect());
    log.info("getTotalAngle" + event.getTotalAngle());

    double r = getCurrentLayer().getRotate() + (event.getAngle() * 0.75);
    if (r < -360d || r > 360d) {
      r = 0d;
    }

    getCurrentLayer().setRotate(r);
    event.consume();
  }

  @FXML
  void compositeTokenPane_DragDropped(DragEvent event) {
    Dragboard db = event.getDragboard();

    // Strangely, we get an error if we try to paste an image we put in the clipboard ourselves but
    // File works ok?
    // -Dprism.order=sw also fixes it but not sure why...
    // So lets just check for File first...
    if (db.hasFiles()) {
      db.getFiles()
          .forEach(
              file -> {
                try {
                  String fileName = FilenameUtils.getName(file.toURI().toURL().toExternalForm());

                  if (FilenameUtils.isExtension(fileName.toLowerCase(), "pdf")) {
                    loadPDF(file);
                  } else {
                    // Be sure to use ImageIO.read so we can use external libs for other image types
                    updateImage(
                        SwingFXUtils.toFXImage(ImageIO.read(file), null),
                        FilenameUtils.getBaseName(fileName));
                  }
                } catch (Exception e) {
                  log.error("Could not load image drom drag-n-drop " + file, e);
                }
              });
      event.setDropCompleted(true);
    } else if (db.hasImage()) {
      updateImage(db.getImage());
      event.setDropCompleted(true);
    } else if (db.hasUrl()) {
      try {
        updateImage(
            SwingFXUtils.toFXImage(ImageIO.read(new URL(db.getUrl())), null),
            FileSaveUtil.searchURL(db.getUrl()));
      } catch (IOException e) {
        log.warn(e);
      }
      event.setDropCompleted(true);
    }
  }

  @FXML
  void compositeTokenPane_DragDone() {
    updateTokenPreviewImageView();
  }

  @FXML
  void compositeTokenPane_DragOver(DragEvent event) {
    if (event.getDragboard().hasImage()
        || event.getDragboard().hasFiles()
        || event.getDragboard().hasUrl()) {
      event.acceptTransferModes(TransferMode.COPY);

      // If object is dragged within the outside 15% of the pane, then drop to the background layer,
      // otherwise drop to the portrait layer
      int borderWidth =
          (int) (Math.min(dndHighlights.getWidth(), dndHighlights.getHeight()) * 0.15);
      if (event.getX() < borderWidth
          || event.getY() < borderWidth
          || event.getX() > compositeTokenPane.getWidth() - borderWidth
          || event.getY() > compositeTokenPane.getHeight() - borderWidth) {

        StackPane.setMargin(dndHighlights, new Insets(0));
        dndHighlights.setStyle(
            "-fx-border-color: #ffff0055; -fx-border-width: " + borderWidth + "px");
        backgroundMenuItem.fire();
      } else {
        StackPane.setMargin(dndHighlights, new Insets(borderWidth));
        dndHighlights.setStyle("-fx-background-color: #00ff0055");
        portraitMenuItem.fire();
      }
    } else {
      // Set Pane color to an alpha red?
      event.acceptTransferModes(TransferMode.ANY);
      dndHighlights.setStyle("-fx-background-color: #ff000055");
    }
  }

  @FXML
  void compositeTokenPane_DragExited() {
    dndHighlights.setStyle("");
  }

  @FXML
  void tokenImageView_OnDragDetected(MouseEvent event) {
    Dragboard db = tokenImageView.startDragAndDrop(TransferMode.COPY);
    ClipboardContent content = new ClipboardContent();

    boolean saveAsToken = false;

    try {
      File tempTokenFile;
      File tempPortraitFile;
      ArrayList<File> tempFiles = new ArrayList<>();

      // Here we don't advance the fileNameSuffix so portrait name has same number, we'll advance it
      // after the second call
      tempTokenFile =
          fileSaveUtil.getTempFileName(
              saveAsToken,
              useFileNumberingCheckbox.isSelected(),
              getFileNameTextField(),
              getFileSaveFormatChoiceboxSelection(),
              fileNameSuffixTextField,
              false);
      writeTokenImage(tempTokenFile);
      tempFiles.add(tempTokenFile);

      tempPortraitFile =
          fileSaveUtil.getTempFileName(
              saveAsToken,
              useFileNumberingCheckbox.isSelected(),
              getPortraitNameTextField(),
              getPortraitSaveFormatChoiceboxSelection(),
              fileNameSuffixTextField,
              true);
      if (savePortraitOnDragCheckbox.isSelected()) {
        tempPortraitFile = writePortraitImage(tempPortraitFile);
        if (tempPortraitFile != null) {
          tempFiles.add(tempPortraitFile);
        }
      }

      content.putFiles(tempFiles);

      tempTokenFile.deleteOnExit();
      tempPortraitFile.deleteOnExit();
    } catch (Exception e) {
      log.error(e);
    } finally {
      if (event.isPrimaryButtonDown()) {
        content.putImage(tokenImageView.getImage());
      } else {
        content.putImage(getPortraitImage());
      }

      db.setContent(content);
      event.consume();
    }
  }

  @FXML
  void tokenImageView_OnDragDone(DragEvent event) {
    if (event.getAcceptedTransferMode() != null) {
      updateOverlayTreeViewRecentFolder(true);
    }
  }

  @FXML
  void overlayUseAsBaseCheckbox_OnAction() {
    if (overlayUseAsBaseCheckbox.isSelected()) {
      compositeGroup.toBack();
    } else {
      portraitScrollPane.toBack();
    }

    // Always keep background image in back...
    // backgroundImagePane.toBack();

    updateTokenPreviewImageView();
  }

  @FXML
  void backgroundColorPicker_OnAction() {
    updateTokenPreviewImageView();
  }

  @FXML
  void overlayAspectToggleButton_OnAction() {
    if (overlayAspectToggleButton.isSelected()) {
      overlayImageView.setPreserveRatio(true);
      maskImageView.setPreserveRatio(true);
      overlayWidthSpinner
          .getValueFactory()
          .valueProperty()
          .bindBidirectional(overlayHeightSpinner.getValueFactory().valueProperty());
    } else {
      overlayImageView.setPreserveRatio(false);
      maskImageView.setPreserveRatio(false);
      overlayWidthSpinner
          .getValueFactory()
          .valueProperty()
          .unbindBidirectional(overlayHeightSpinner.getValueFactory().valueProperty());
    }

    updateTokenPreviewImageView();
  }

  @FXML
  void backgroundMenuItem_OnAction(ActionEvent event) {
    String menuText = ((RadioMenuItem) event.getSource()).getText();
    layerMenuButton.setText(menuText + I18N.getString("controls.layers.menu.layer.text"));
    backgroundMenuItem.setSelected(true);
  }

  @FXML
  void portraitMenuItem_OnAction(ActionEvent event) {
    String menuText = ((RadioMenuItem) event.getSource()).getText();
    layerMenuButton.setText(menuText + I18N.getString("controls.layers.menu.layer.text"));
    portraitMenuItem.setSelected(true);
  }

  void overlayWidthSpinner_onTextChanged(int oldValue, int newValue) {
    if (newValue < overlaySpinnerSteps.first()) {
      newValue = overlaySpinnerSteps.first();
    }

    if (newValue > overlaySpinnerSteps.last()) {
      newValue = overlaySpinnerSteps.last();
    }

    if (getOverlayAspect()) {
      if (newValue > oldValue) {
        overlayWidthSpinner.getValueFactory().setValue(overlaySpinnerSteps.ceiling(newValue));
      } else {
        overlayWidthSpinner.getValueFactory().setValue(overlaySpinnerSteps.floor(newValue));
      }
    }

    overlayImageView.setFitWidth(overlayWidthSpinner.getValue());
    maskImageView.setFitWidth(overlayWidthSpinner.getValue());

    updateTokenPreviewImageView();
  }

  void overlayHeightSpinner_onTextChanged(int oldValue, int newValue) {
    if (newValue < overlaySpinnerSteps.first()) {
      newValue = overlaySpinnerSteps.first();
    }

    if (newValue > overlaySpinnerSteps.last()) {
      newValue = overlaySpinnerSteps.last();
    }

    if (getOverlayAspect()) {
      if (newValue > oldValue) {
        overlayHeightSpinner.getValueFactory().setValue(overlaySpinnerSteps.ceiling(newValue));
      } else {
        overlayHeightSpinner.getValueFactory().setValue(overlaySpinnerSteps.floor(newValue));
      }
    }

    overlayImageView.setFitHeight(overlayHeightSpinner.getValue());
    maskImageView.setFitHeight(overlayHeightSpinner.getValue());

    updateTokenPreviewImageView();
  }

  public Map<Path, TreeItem<Path>> getRecentOverlayTreeItems() {
    return recentOverlayTreeItems;
  }

  public void updateRecentOverlayTreeItems(Path filePath) {
    try {
      TreeItem<Path> recentOverlay =
          new TreeItem<>(filePath, ImageUtil.getOverlayThumb(new ImageView(), filePath));

      // Remove first so if it is on the list it forces to top of list
      recentOverlayTreeItems.remove(filePath);
      recentOverlayTreeItems.put(filePath, recentOverlay);
    } catch (IOException e) {
      log.error("Error loading recent overlay preference for " + filePath.toString());
    }
  }

  public void expandOverlayOptionsPane(boolean expand) {
    overlayOptionsPane.setExpanded(expand);
  }

  public void expandBackgroundOptionsPane(boolean expand) {
    backgroundOptionsPane.setExpanded(expand);
  }

  public void updateOverlayTreeview(TreeItem<Path> overlayTreeItems) {
    overlayTreeView.setRoot(overlayTreeItems);
  }

  public void updateTokenPreviewImageView() {
    tokenImageView.setImage(
        ImageUtil.composePreview(
            compositeTokenPane,
            backgroundImageView,
            backgroundColorPicker.getValue(),
            portraitImageView,
            maskImageView,
            overlayImageView,
            overlayUseAsBaseCheckbox.isSelected(),
            clipPortraitCheckbox.isSelected()));
    tokenImageView.setPreserveRatio(true);
  }

  private void saveToken() {
    String fileExtension = getFileSaveFormatChoiceboxSelection();
    FileChooser fileChooser = new FileChooser();
    log.info("***** Saving Token as a {}", fileExtension);

    try {
      File tokenFile =
          fileSaveUtil.getFileName(
              false,
              useFileNumberingCheckbox.isSelected(),
              fileNameTextField.getText(),
              getFileSaveFormatChoiceboxSelection(),
              fileNameSuffixTextField,
              true);
      fileChooser.setInitialFileName(tokenFile.getName());
      if (tokenFile.getParentFile() != null) {
        if (tokenFile.getParentFile().isDirectory()) {
          fileChooser.setInitialDirectory(tokenFile.getParentFile());
        }
      }
    } catch (IOException e1) {
      log.error("Error writing token!", e1);
    }

    fileChooser.getExtensionFilters().addAll(AppConstants.IMAGE_EXTENSION_FILTER);
    fileChooser.setTitle(I18N.getString("TokenTool.save.filechooser.title"));
    fileChooser.setSelectedExtensionFilter(AppConstants.IMAGE_EXTENSION_FILTER);

    File tokenSaved = fileChooser.showSaveDialog(saveOptionsPane.getScene().getWindow());

    if (tokenSaved == null) {
      return;
    }

    writeTokenImage(tokenSaved);

    updateFileNameTextField(FilenameUtils.getBaseName(tokenSaved.getName()));
    FileSaveUtil.setLastFile(tokenSaved);
    updateOverlayTreeViewRecentFolder(true);
  }

  private boolean writeTokenImage(File tokenFile) {
    try {
      String imageType = getFileSaveFormatChoiceboxSelection();
      Image tokenImage;
      if (clipPortraitCheckbox.isSelected()) {
        tokenImage =
            ImageUtil.resizeCanvas(
                tokenImageView.getImage(), getOverlayWidth(), getOverlayHeight());
      } else {
        tokenImage = tokenImageView.getImage();
      }

      BufferedImage imageRGB = SwingFXUtils.fromFXImage(tokenImage, null);

      log.debug("Writing token image as: " + imageType);
      boolean writeSuccessful = ImageIO.write(imageRGB, imageType, tokenFile);

      if (!writeSuccessful) {
        // Remove alpha-channel from buffered image
        BufferedImage image = SwingFXUtils.fromFXImage(tokenImage, null);
        imageRGB = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.OPAQUE);
        Graphics2D graphics = imageRGB.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        log.debug("Writing token image as:" + imageType);
        writeSuccessful = ImageIO.write(imageRGB, imageType, tokenFile);
      }

      return writeSuccessful;
    } catch (IOException e) {
      log.error("Unable to write token to file: " + tokenFile.getAbsolutePath(), e);
    } catch (IndexOutOfBoundsException e) {
      log.error(
          "Image width/height out of bounds: " + getOverlayWidth() + " x " + getOverlayHeight(), e);
    }

    return false;
  }

  private File writePortraitImage(File tokenFile) {
    boolean writeSuccessful = false;

    try {
      String imageType = getPortraitSaveFormatChoiceboxSelection();
      Image tokenImage;
      tokenImage = getPortraitImage();
      BufferedImage imageRGB = SwingFXUtils.fromFXImage(tokenImage, null);

      if (!useBackgroundOnDragCheckbox.isSelected()) {
        writeSuccessful = ImageIO.write(imageRGB, imageType, tokenFile);
      }

      if (!writeSuccessful) {
        if (useBackgroundOnDragCheckbox.isSelected()) {
          if (getBackgroundColor() != Color.TRANSPARENT || getBackgroundImage() != null) {
            tokenImage =
                ImageUtil.autoCropImage(tokenImage, getBackgroundColor(), getBackgroundImage());
          } else {
            //            ImageUtil.autoCropImage(tokenImage, Color.BLACK, null);
          }
        } else {
          //          ImageUtil.autoCropImage(tokenImage, Color.BLACK, null);
        }

        String newFileName =
            FilenameUtils.removeExtension(tokenFile.getAbsolutePath()) + "." + imageType;
        tokenFile = new File(newFileName);

        // Remove alpha-channel from buffered image
        BufferedImage image = SwingFXUtils.fromFXImage(tokenImage, null);
        imageRGB = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.OPAQUE);
        Graphics2D graphics = imageRGB.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        log.debug("Writing token image as:" + imageType);
        writeSuccessful = ImageIO.write(imageRGB, imageType, tokenFile);
      }

      return tokenFile;

    } catch (IOException e) {
      log.error("Unable to write token to file: " + tokenFile.getAbsolutePath(), e);
    } catch (IndexOutOfBoundsException e) {
      log.error(
          "Image width/height out of bounds: " + getOverlayWidth() + " x " + getOverlayHeight(), e);
    }

    if (writeSuccessful) {
      return tokenFile;
    } else {
      return null;
    }
  }

  public void updateOverlayTreeViewRecentFolder(boolean selectMostRecent) {
    if (lastSelectedItem != null) {
      updateRecentOverlayTreeItems(lastSelectedItem.getValue());
    }

    log.debug("recentOverlayTreeItems size : " + recentOverlayTreeItems.size());

    // Update Recent Overlay List
    if (!recentOverlayTreeItems.isEmpty()) {
      // Remember current selection (adding/removing tree items messes with the selection model)
      // int selectedItem = overlayTreeView.getSelectionModel().getSelectedIndex();
      overlayTreeView.getSelectionModel().clearSelection();

      // Clear current folder
      recentFolder.getChildren().clear();

      // Add recent list to recentFolder in reverse order so most recent is at the top
      ListIterator<Entry<Path, TreeItem<Path>>> iter =
          new ArrayList<>(recentOverlayTreeItems.entrySet())
              .listIterator(recentOverlayTreeItems.size());
      while (iter.hasPrevious()) {
        recentFolder.getChildren().add(iter.previous().getValue());
      }

      if (overlayTreeView.getRoot().getChildren().indexOf(recentFolder) != -1) {
        overlayTreeView.getRoot().getChildren().remove(recentFolder);
      }
      overlayTreeView.getRoot().getChildren().add(recentFolder);

      // Auto expand recent folder...
      recentFolder.setExpanded(true);

      addPseudoClassToLeafs(overlayTreeView);

      // Set the selected index back to what it was unless...
      if (selectMostRecent) {
        overlayTreeView.getSelectionModel().select(recentFolder.getChildren().get(0));
      }
    } else {
      overlayTreeView.getSelectionModel().clearSelection();
      recentFolder.getChildren().clear();
      overlayTreeView.getRoot().getChildren().remove(recentFolder);
    }
  }

  private void addPseudoClassToLeafs(TreeView<Path> tree) {
    PseudoClass leaf = PseudoClass.getPseudoClass("leaf");

    tree.setCellFactory(
        tv -> {
          TreeCell<Path> cell = new TreeCell<>();
          cell.itemProperty()
              .addListener(
                  (obs, oldValue, newValue) -> {
                    if (newValue == null) {
                      cell.setText("");
                      cell.setGraphic(null);
                    } else {
                      cell.setText(newValue.toFile().getName());
                      cell.setGraphic(cell.getTreeItem().getGraphic());
                    }
                  });
          cell.treeItemProperty()
              .addListener(
                  (obs, oldTreeItem, newTreeItem) ->
                      cell.pseudoClassStateChanged(
                          leaf, newTreeItem != null && newTreeItem.isLeaf()));
          return cell;
        });
  }

  public void updateImage(Image image, String imageName, boolean setBackground) {
    if (setBackground) {
      backgroundMenuItem.fire();
    } else {
      portraitMenuItem.fire();
    }

    updateImage(image, imageName);
  }

  public void updateImage(Image image) {
    updateImage(image, null);
  }

  public void updateImage(Image image, String imageName) {
    if (backgroundMenuItem.isSelected()) {
      updateBackground(image);
    } else {
      updatePortrait(image);
      updateFileNameTextField(imageName);
    }

    dndHighlights.setStyle("");
  }

  private void updateBackground(Image newBackgroundImage) {
    backgroundImageView.setImage(newBackgroundImage);

    backgroundImageView.setTranslateX(0);
    backgroundImageView.setTranslateY(0);
    backgroundImageView.setFitWidth(newBackgroundImage.getWidth());
    backgroundImageView.setFitHeight(newBackgroundImage.getHeight());
    backgroundImageView.setScaleX(1);
    backgroundImageView.setScaleY(1);
    backgroundImageView.setRotate(0d);

    updateTokenPreviewImageView();
  }

  private void updatePortrait(Image newPortraitImage) {
    portraitImageView.setImage(newPortraitImage);

    portraitImageView.setTranslateX(0);
    portraitImageView.setTranslateY(0);
    portraitImageView.setScaleX(1);
    portraitImageView.setScaleY(1);
    portraitImageView.setRotate(0d);

    updateTokenPreviewImageView();
  }

  private void updateCompositeImageView(TreeItem<Path> treeNode) {
    // Node removed...
    if (treeNode == null) {
      return;
    }

    // I'm not a leaf on the wind! (Sub directory node)
    if (treeNode.getChildren().size() > 0) {
      return;
    }

    try {
      Path filePath = treeNode.getValue();
      lastSelectedItem = treeNode;

      // Set the Image Views
      maskImageView = ImageUtil.getMaskImage(maskImageView, filePath);
      overlayImageView = ImageUtil.getOverlayImage(overlayImageView, filePath);

      // Set the text label
      overlayNameLabel.setText(FilenameUtils.getBaseName(filePath.toFile().getName()));
      overlayInfoLabel.setText(
          (int) overlayImageView.getImage().getWidth()
              + " x "
              + (int) overlayImageView.getImage().getHeight());

      updateTokenPreviewImageView();
    } catch (IOException e) {
      // Not a valid URL, most likely this is just because it's a directory node.
      e.printStackTrace();
    }
  }

  public Color getBackgroundColor() {
    return backgroundColorPicker.getValue();
  }

  public void setBackgroundColor(Color newColor) {
    backgroundColorPicker.setValue(newColor);
  }

  public void refreshCache() {
    overlayTreeProgressBar.setStyle("");
    overlayTreeProgressBar.setVisible(true);
    overlayTreeProgressBar.setOpacity(1.0);
    overlayNameLabel.setOpacity(0.0);
    overlayInfoLabel.setOpacity(0.0);
    progressBarLabel.setVisible(true);
    updateOverlayTreeview(null);

    try {
      loadCount.set(0);
      overlayCount =
          (int) Files.walk(AppConstants.OVERLAY_DIR.toPath()).filter(Files::isRegularFile).count();
      log.info("overlayCount: " + overlayCount);

      treeItems = cacheOverlays(AppConstants.OVERLAY_DIR, null);
    } catch (IOException e) {
      log.error("Error reloading overlay cache!", e);
    }
  }

  private void treeViewFinish() {
    log.debug("***treeViewFinish called");
    // Sort the nodes off of root
    sortTreeNodes(treeItems);

    updateOverlayTreeview(treeItems);
    addPseudoClassToLeafs(overlayTreeView);
    updateOverlayTreeViewRecentFolder(false);

    overlayTreeProgressBar.setStyle("-fx-accent: forestgreen;");
    progressBarLabel.setVisible(false);

    Platform.runLater(
        () -> {
          FadeTransition progressBarFadeOut = new FadeTransition(Duration.millis(2000));
          progressBarFadeOut.setNode(overlayTreeProgressBar);
          progressBarFadeOut.setFromValue(1.0);
          progressBarFadeOut.setToValue(0.0);
          progressBarFadeOut.setCycleCount(1);
          progressBarFadeOut.setAutoReverse(false);
          progressBarFadeOut.playFromStart();

          FadeTransition nameFadeIn = new FadeTransition(Duration.millis(4000));
          nameFadeIn.setNode(overlayNameLabel);
          nameFadeIn.setFromValue(0.0);
          nameFadeIn.setToValue(1.0);
          nameFadeIn.setCycleCount(1);
          nameFadeIn.setAutoReverse(false);
          nameFadeIn.playFromStart();

          FadeTransition infoFadeIn = new FadeTransition(Duration.millis(4000));
          infoFadeIn.setNode(overlayInfoLabel);
          infoFadeIn.setFromValue(0.0);
          infoFadeIn.setToValue(1.0);
          infoFadeIn.setCycleCount(1);
          infoFadeIn.setAutoReverse(false);
          infoFadeIn.playFromStart();
        });
  }

  private TreeItem<Path> cacheOverlays(File dir, TreeItem<Path> parent) throws IOException {
    log.debug("Caching " + dir.getAbsolutePath());

    TreeItem<Path> root = new TreeItem<>(dir.toPath());
    root.setExpanded(false);
    File[] files = dir.listFiles();
    final String I18N_CACHE_TEXT = I18N.getString("TokenTool.treeview.caching");

    final Task<Void> task =
        new Task<>() {
          @Override
          protected Void call() throws Exception {
            for (File file : files) {
              if (loadOverlaysThread.isInterrupted()) {
                break;
              }

              if (file.isDirectory()) {
                cacheOverlays(file, root);
              } else {
                Path filePath = file.toPath();
                TreeItem<Path> imageNode =
                    new TreeItem<>(filePath, ImageUtil.getOverlayThumb(new ImageView(), filePath));
                root.getChildren().add(imageNode);
                loadCount.getAndIncrement();
                overlayTreeProgressBar
                    .progressProperty()
                    .set(loadCount.doubleValue() / overlayCount);
              }
            }

            if (parent != null) {
              // When we show the overlay image, the TreeItem value is empty so we need to
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

              parent
                  .getChildren()
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
            }

            return null;
          }
        };

    overlayTreeProgressBar
        .progressProperty()
        .addListener(
            observable ->
                Platform.runLater(
                    () ->
                        progressBarLabel.setText(
                            I18N_CACHE_TEXT
                                + Math.round(overlayCount - loadCount.doubleValue())
                                + "...")));

    // Only add this listener to the parent task so it's only called once
    if (parent == null) {
      overlayTreeProgressBar
          .progressProperty()
          .addListener(
              observable ->
                  Platform.runLater(
                      () -> {
                        if (overlayTreeProgressBar.getProgress() >= 1) {
                          treeViewFinish();
                        }
                      }));
    }

    executorService.execute(task);
    return root;
  }

  private TreeItem<Path> sortTreeNodes(TreeItem<Path> tree) {
    // Sort the nodes off of root
    tree.getChildren()
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

    return tree;
  }

  /*
   * getter/setter methods, mainly for user preferences
   */
  public int getOverlayWidth() {
    return overlayWidthSpinner.getValue();
  }

  public void setOverlayWidth(int newValue) {
    overlayWidthSpinner.getValueFactory().setValue(overlaySpinnerSteps.ceiling(newValue));
  }

  public int getOverlayHeight() {
    return overlayHeightSpinner.getValue();
  }

  public void setOverlayHeight(int newValue) {
    overlayHeightSpinner.getValueFactory().setValue(overlaySpinnerSteps.ceiling(newValue));
  }

  public boolean getOverlayAspect() {
    return overlayAspectToggleButton.isSelected();
  }

  public void setOverlayAspect(boolean selected) {
    if (selected != overlayAspectToggleButton.isSelected()) {
      overlayAspectToggleButton.fire();
    }
  }

  public boolean getOverlayUseAsBase() {
    return overlayUseAsBaseCheckbox.isSelected();
  }

  public void setOverlayUseAsBase(boolean selected) {
    if (selected != overlayUseAsBaseCheckbox.isSelected()) {
      overlayUseAsBaseCheckbox.fire();
    }
  }

  public boolean getClipPortraitCheckbox() {
    return clipPortraitCheckbox.isSelected();
  }

  public void setClipPortraitCheckbox(boolean selected) {
    if (selected != clipPortraitCheckbox.isSelected()) {
      clipPortraitCheckbox.fire();
    }
  }

  public String getFileNameTextField() {
    return fileNameTextField.getText();
  }

  public void setFileNameTextField(String text) {
    fileNameTextField.setText(text);
  }

  public String getPortraitNameTextField() {
    return portraitNameTextField.getText();
  }

  public void setPortraitNameTextField(String text) {
    if (!portraitNameTextField.isDisabled()) {
      portraitNameTextField.setText(text);
    }
  }

  public boolean getUseTokenNameCheckbox() {
    return useTokenNameCheckbox.isSelected();
  }

  public void setUseTokenNameCheckbox(boolean selected) {
    if (selected != useTokenNameCheckbox.isSelected()) {
      useTokenNameCheckbox.fire();
    }
  }

  public String getPortraitNameSuffixTextField() {
    return portraitNameSuffixTextField.getText();
  }

  public void setPortraitNameSuffixTextField(String text) {
    portraitNameSuffixTextField.setText(text);
  }

  public void updateFileNameTextField(String text) {
    if (!getUseFileNumberingCheckbox()) {
      if (text == null || text.isEmpty()) {
        fileNameTextField.setText(AppConstants.DEFAULT_TOKEN_NAME);
      } else {
        fileNameTextField.setText(FileSaveUtil.cleanFileName(text));
      }
    }
  }

  public boolean getUseFileNumberingCheckbox() {
    return useFileNumberingCheckbox.isSelected();
  }

  public void setUseFileNumberingCheckbox(boolean selected) {
    if (selected != useFileNumberingCheckbox.isSelected()) {
      useFileNumberingCheckbox.fire();
    }
  }

  public String getFileNameSuffixTextField() {
    return fileNameSuffixTextField.getText();
  }

  public void setFileNameSuffixTextField(String text) {
    fileNameSuffixTextField.setText(text);
  }

  public boolean getSavePortraitOnDragCheckbox() {
    return savePortraitOnDragCheckbox.isSelected();
  }

  public void setSavePortraitOnDragCheckbox(boolean selected) {
    if (selected != savePortraitOnDragCheckbox.isSelected()) {
      savePortraitOnDragCheckbox.fire();
    }
  }

  public boolean getUseBackgroundOnDragCheckbox() {
    return useBackgroundOnDragCheckbox.isSelected();
  }

  public void setUseBackgroundOnDragCheckbox(boolean selected) {
    if (selected != useBackgroundOnDragCheckbox.isSelected()) {
      useBackgroundOnDragCheckbox.fire();
    }
  }

  public String getFileSaveFormatChoiceboxSelection() {
    return fileSaveFormatChoicebox.getSelectionModel().getSelectedItem();
  }

  public void setFileSaveFormatChoiceboxSelection(String item) {
    fileSaveFormatChoicebox.getSelectionModel().select(item);
  }

  public String getPortraitSaveFormatChoiceboxSelection() {
    return portraitSaveFormatChoicebox.getSelectionModel().getSelectedItem();
  }

  public void setPortraitSaveFormatChoiceboxSelection(String item) {
    portraitSaveFormatChoicebox.getSelectionModel().select(item);
  }

  // For user preferences...
  public void setWindowFrom_Preferences(String preferencesJson) {
    if (preferencesJson != null) {
      Window_Preferences window_Preferences =
          new Gson().fromJson(preferencesJson, new TypeToken<Window_Preferences>() {}.getType());
      window_Preferences.setWindow(TokenTool.getInstance().getStage());
    }
  }

  public Image getPortraitImage() {
    return portraitImageView.getImage();
  }

  public String getPortrait_Preferences(String filePath) {
    return new ImageView_Preferences(portraitImageView, filePath).toJson();
  }

  public void setPortraitFrom_Preferences(String preferencesJson) {
    if (preferencesJson != null) {
      ImageView_Preferences imageView_Preferences =
          new Gson().fromJson(preferencesJson, new TypeToken<ImageView_Preferences>() {}.getType());
      portraitImageView = imageView_Preferences.toImageView(portraitImageView);
    } else {
      log.debug("No Preferences currently saved.");
    }
  }

  public Image getBackgroundImage() {
    return backgroundImageView.getImage();
  }

  public String getBackground_Preferences(String filePath) {
    return new ImageView_Preferences(backgroundImageView, filePath, getBackgroundColor()).toJson();
  }

  public void setBackgroundFrom_Preferences(String preferencesJson) {
    if (preferencesJson != null) {
      ImageView_Preferences imageView_Preferences =
          new Gson().fromJson(preferencesJson, new TypeToken<ImageView_Preferences>() {}.getType());
      backgroundImageView = imageView_Preferences.toImageView(backgroundImageView);

      setBackgroundColor(imageView_Preferences.getBackgroundColor());
    } else {
      backgroundImageView.setImage(null);
      setBackgroundColor(Color.TRANSPARENT);
    }
  }

  public Slider getPortraitTransparencySlider() {
    return portraitTransparencySlider;
  }

  public Slider getPortraitBlurSlider() {
    return portraitBlurSlider;
  }

  public Slider getPortraitGlowSlider() {
    return portraitGlowSlider;
  }

  public Slider getOverlayTransparencySlider() {
    return overlayTransparencySlider;
  }

  public PdfViewer getPdfViewer() {
    return pdfViewer;
  }

  public void exitApplication() {
    try {
      // Lets update the recent list to current overlay...
      updateOverlayTreeViewRecentFolder(true);
    } catch (NullPointerException npe) {
      log.info("Unable to updateOverlayTreeViewRecentFolder on exit.");
    }

    try {
      AppPreferences.savePreferences(this);
      log.info("Exiting application.");
      executorService.shutdownNow();
    } catch (Exception e) {
      // log.error("Error saving preferences!", e);
      System.out.println("Error saving preferences!  " + e);
    } finally {
      Platform.exit();
    }
  }
}
