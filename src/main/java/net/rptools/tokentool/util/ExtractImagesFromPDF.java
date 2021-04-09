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
package net.rptools.tokentool.util;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javax.imageio.ImageIO;
import net.rptools.tokentool.controller.TokenTool_Controller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;

/**
 * Extract all images from a PDF using Apache's PdfBox 2.0 This will also walk through all
 * annotations and extract those images as well which is key, some interactive PDF's, such as from
 * Paizo, store different versions of maps as button icons, which will not normally extract using
 * other methods.
 *
 * @author Jamz
 */
public final class ExtractImagesFromPDF {

  private static final Logger log = LogManager.getLogger(ExtractImagesFromPDF.class);
  private static final int imageViewSize = 175;
  private static final int imageButtonSize = 200;

  private final PDDocument document;
  private final Set<COSStream> imageTracker = new HashSet<>();
  private final Set<String> imageHashTracker = new HashSet<>();

  private TokenTool_Controller tokenTool_Controller;
  private ArrayList<ToggleButton> imageButtons = new ArrayList<>();
  private FileSaveUtil fileSaveUtil = new FileSaveUtil();
  private String pdfName;
  private String fileSavePath;
  private String imageFormat;
  private int currentPageNumber;
  private double imageMinDimension;
  private boolean isRunning;
  private boolean interrupt;

  public ExtractImagesFromPDF(
      PDDocument document, String pdfName, TokenTool_Controller tokenTool_Controller) {
    this.tokenTool_Controller = tokenTool_Controller;
    this.document = document;
    this.pdfName = pdfName;
  }

  public ArrayList<ToggleButton> addImages(int pageNumber) throws IOException {
    isRunning = true;
    imageTracker.clear();
    imageButtons.clear();
    this.currentPageNumber = pageNumber;

    extractAnnotationImages(document.getPage(pageNumber), false);
    getImagesFromResources(document.getPage(pageNumber).getResources(), false);

    isRunning = false;
    interrupt = false;
    return imageButtons;
  }

  public void extractAllImagesFromPage(
      String filePath, String imageFormat, int pageNumber, double imageMinDimension)
      throws IOException {
    this.fileSavePath = filePath;
    this.imageFormat = imageFormat;
    this.imageMinDimension = imageMinDimension;
    isRunning = true;
    imageTracker.clear();

    this.currentPageNumber = pageNumber;

    extractAnnotationImages(document.getPage(pageNumber), true);
    getImagesFromResources(document.getPage(pageNumber).getResources(), true);

    isRunning = false;
    interrupt = false;
  }

  public void resetImageHashTracker() {
    imageHashTracker.clear();
  }

  private void getImagesFromResources(PDResources resources, boolean extractOnly)
      throws IOException {
    // Testing various Pathfinder PDF's, various page elements like borders and backgrounds
    // generally come first...
    // ...so lets sort them to the bottom and get the images we really want to the top of the
    // TilePane!
    ArrayList<COSName> xObjectNamesReversed = new ArrayList<>();

    for (COSName xObjectName : resources.getXObjectNames()) {
      xObjectNamesReversed.add(xObjectName);
    }

    Collections.reverse(xObjectNamesReversed);

    for (COSName xObjectName : xObjectNamesReversed) {
      if (interrupt) {
        return;
      }

      PDXObject xObject = resources.getXObject(xObjectName);

      if (xObject instanceof PDFormXObject) {
        getImagesFromResources(((PDFormXObject) xObject).getResources(), extractOnly);
      } else if (xObject instanceof PDImageXObject) {

        if (!imageTracker.contains(xObject.getCOSObject())) {
          imageTracker.add(xObject.getCOSObject());

          String name =
              pdfName + " - pg " + (currentPageNumber + 1) + " - img " + imageTracker.size();

          log.debug("Extracting image... " + name);

          WritableImage image = SwingFXUtils.toFXImage(((PDImageXObject) xObject).getImage(), null);

          if (extractOnly) {
            boolean skipImage = false;

            try {
              MessageDigest md = MessageDigest.getInstance("MD5");
              md.update(xObject.getStream().toByteArray());
              String hash = bytesToHex(md.digest());
              log.debug("MD5 Checksum is {}", hash);

              if (!imageHashTracker.contains(hash)) {
                imageHashTracker.add(hash);
              } else {
                skipImage = true;
                log.debug("MD5 Checksum {} already exists, skipping!", hash);
              }
            } catch (NoSuchAlgorithmException e) {
              e.printStackTrace();
            } finally {
              if (!skipImage
                  && image.getWidth() > imageMinDimension
                  && image.getHeight() > imageMinDimension) {
                ImageIO.write(
                    SwingFXUtils.fromFXImage(image, null),
                    imageFormat,
                    new File(fileSavePath, name + "." + imageFormat));
              }
            }
          } else {
            addTileButton(image, name);
          }
        }
      }
    }
  }

  /*
   * Jamz: A note on what we are doing here...
   *
   * Paizo's Interactive PDF's (amongst others) are sneaky and put map images in the PDF as a "button" with an image resource. So we need to walk through all the forms to find the buttons, then walk
   * through all the button resources for the images. Also, a 'Button Down' may hold the 'Grid' version of the map and 'Button Up' may hold the 'Non-Grid' version. There may also be Player vs GM
   * versions of each for a total of up to 4 images per button!
   *
   * This is the REAL beauty of this function as currently no other tools outside of Full Acrobat extracts these raw images!
   *
   */
  private void extractAnnotationImages(PDPage page, boolean extractOnly) throws IOException {
    for (PDAnnotation annotation : page.getAnnotations()) {
      extractAnnotationImages(annotation, extractOnly);
    }
  }

  private void extractAnnotationImages(PDAnnotation annotation, boolean extractOnly)
      throws IOException {
    PDAppearanceDictionary appearance = annotation.getAppearance();

    if (appearance == null) {
      return;
    }

    extractAnnotationImages(appearance.getDownAppearance(), extractOnly);
    extractAnnotationImages(appearance.getNormalAppearance(), extractOnly);
    extractAnnotationImages(appearance.getRolloverAppearance(), extractOnly);
  }

  private void extractAnnotationImages(PDAppearanceEntry appearance, boolean extractOnly)
      throws IOException {
    if (interrupt) {
      return;
    }

    PDResources resources = appearance.getAppearanceStream().getResources();
    if (resources == null) {
      return;
    }

    for (COSName cosname : resources.getXObjectNames()) {
      PDXObject xObject = resources.getXObject(cosname);

      if (xObject instanceof PDFormXObject) {
        extractAnnotationImages((PDFormXObject) xObject, extractOnly);
      } else if (xObject instanceof PDImageXObject) {
        extractAnnotationImages((PDImageXObject) xObject, extractOnly);
      }
    }
  }

  private void extractAnnotationImages(PDFormXObject form, boolean extractOnly) throws IOException {
    PDResources resources = form.getResources();
    if (resources == null) {
      return;
    }

    for (COSName cosname : resources.getXObjectNames()) {
      PDXObject xObject = resources.getXObject(cosname);

      if (xObject instanceof PDFormXObject) {
        extractAnnotationImages((PDFormXObject) xObject, extractOnly);
      } else if (xObject instanceof PDImageXObject) {
        extractAnnotationImages((PDImageXObject) xObject, extractOnly);
      }
    }
  }

  private void extractAnnotationImages(PDImageXObject xObject, boolean extractOnly)
      throws IOException {
    if (!imageTracker.contains(xObject.getCOSObject())) {
      imageTracker.add(xObject.getCOSObject());

      String name = pdfName + " - pg " + (currentPageNumber + 1) + " - img " + imageTracker.size();
      log.debug("Extracting Annotation, eg button image... " + name);

      WritableImage image = SwingFXUtils.toFXImage(xObject.getImage(), null);
      if (extractOnly) {
        if (image.getWidth() > imageMinDimension && image.getHeight() > imageMinDimension) {
          ImageIO.write(
              SwingFXUtils.fromFXImage(image, null),
              imageFormat,
              new File(fileSavePath, name + "." + imageFormat));
        }
      } else {
        addTileButton(image, name);
      }
    }
  }

  private void addTileButton(Image buttonImage, String imageName) {
    ToggleButton imageButton = new ToggleButton();
    ImageView imageViewNode = new ImageView(buttonImage);
    imageViewNode.setFitWidth(imageViewSize);
    imageViewNode.setFitHeight(imageViewSize);
    imageButton.setPrefWidth(imageButtonSize);
    imageButton.setPrefHeight(imageButtonSize);
    imageViewNode.setPreserveRatio(true);

    imageButton.getStyleClass().add("overlay-toggle-button");
    imageButton.setGraphic(imageViewNode);
    imageButton.setId(imageName);

    //    double imagePixels = buttonImage.getWidth() * buttonImage.getHeight();
    //    imageButton.setText(buttonImage.getWidth() + "x" + buttonImage.getHeight());

    String imageFormat =
        tokenTool_Controller
            .getPdfViewer()
            .getPdfViewerController()
            .getPdfSaveFormatChoiceBoxSelection();

    // Can also drag image to TokenTool pane OR any other place, like MapTool!
    imageButton.setOnDragDetected(
        event -> {
          Dragboard db = imageButton.startDragAndDrop(TransferMode.ANY);
          ClipboardContent content = new ClipboardContent();

          try {
            File tempImageFile;
            tempImageFile = fileSaveUtil.getTempFileName(imageName, imageFormat);

            ImageIO.write(SwingFXUtils.fromFXImage(buttonImage, null), imageFormat, tempImageFile);
            content.putFiles(java.util.Collections.singletonList(tempImageFile));
            tempImageFile.deleteOnExit();
          } catch (IOException e) {
            log.error("Unable to write token to file: " + imageName, e);
          } catch (Exception e) {
            log.error(e);
          } finally {
            content.putImage(buttonImage);
            db.setContent(content);
            event.consume();
          }
          event.consume();
        });

    // Right click sets background vs portrait...
    // Drag will consume the event first so image doesn't reset...
    imageButton.addEventHandler(
        MouseEvent.MOUSE_RELEASED,
        event -> {
          imageButton.setSelected(true);
          tokenTool_Controller.updateImage(
              imageViewNode.getImage(), imageName, event.getButton().equals(MouseButton.SECONDARY));
          event.consume();
        });

    // capture other actions like touch, focus+spacebar, etc
    imageButton.addEventHandler(
        ActionEvent.ACTION,
        event -> {
          imageButton.setSelected(true);
          tokenTool_Controller.updateImage(imageViewNode.getImage(), imageName);
          event.consume();
        });

    if (interrupt) {
      log.debug("I REALLY SHOULD STOP!");
    } else {
      log.debug("Free to go...");
    }

    imageButtons.add(imageButton);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  public void interrupt() {
    if (isRunning) {
      interrupt = true;
    }
  }
}
