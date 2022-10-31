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

import com.twelvemonkeys.imageio.plugins.psd.PSDImageReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser.ExtensionFilter;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.NodeList;

public class ImageUtil {

  /*
   * These are the file types supported by TokenTool
   */
  public static final String[] SUPPORTED_FILE_FILTER_ARRAY =
      new String[] {".psd", ".png", ".gif", ".jpg", ".jpeg", ".bmp"};
  public static final IOFileFilter SUPPORTED_FILE_FILTER =
      new SuffixFileFilter(SUPPORTED_FILE_FILTER_ARRAY);
  public static final ExtensionFilter SUPPORTED_PDF_EXTENSION_FILTER =
      new ExtensionFilter("PDF Files", "*.pdf");
  /*
   * These are the supported image types used in the new Image class
   */
  public static final FilenameFilter SUPPORTED_IMAGE_FILE_FILTER =
      (dir, name) -> {
        name = name.toLowerCase();

        return name.endsWith(".png")
            || name.endsWith(".gif")
            || name.endsWith(".jpg")
            || name.endsWith(".jpeg")
            || name.endsWith(".bmp");
      };
  /*
   * PSD Support using com.twelvemonkeys.imageio
   */
  public static final FilenameFilter PSD_FILE_FILTER =
      (dir, name) -> name.toLowerCase().endsWith(".psd");
  /*
   * These are the supported types used in the new Image class
   */
  public static final FilenameFilter SUPPORTED_FILENAME_FILTER =
      (dir, name) -> {
        name = name.toLowerCase();

        return name.endsWith(".psd")
            || name.endsWith(".png")
            || name.endsWith(".gif")
            || name.endsWith(".jpg")
            || name.endsWith(".jpeg")
            || name.endsWith(".bmp");
      };
  private static final Logger log = LogManager.getLogger(ImageUtil.class);
  private static final int THUMB_SIZE = 100;
  private static final int COLOR_THRESHOLD = 1;

  public static ImageView getOverlayThumb(ImageView thumbView, Path filePath) throws IOException {
    return getImage(thumbView, filePath, true, THUMB_SIZE);
  }

  public static ImageView getOverlayImage(ImageView thumbView, Path overlayFileURI)
      throws IOException {
    return getImage(thumbView, overlayFileURI, true, 0);
  }

  public static ImageView getMaskImage(ImageView thumbView, Path overlayFileURI)
      throws IOException {
    return getImage(thumbView, overlayFileURI, false, 0);
  }

  private static ImageView getImage(
      ImageView thumbView, final Path filePath, final boolean overlayWanted, final int THUMB_SIZE)
      throws IOException {
    Image thumb = null;
    String fileURL = filePath.toUri().toURL().toString();

    if (THUMB_SIZE > 0) {
      thumbView.setFitWidth(THUMB_SIZE);
      thumbView.setPreserveRatio(true);
    }

    if (ImageUtil.SUPPORTED_IMAGE_FILE_FILTER.accept(null, fileURL)) {
      thumb = processMagenta(new Image(fileURL), COLOR_THRESHOLD, overlayWanted);
    } else if (ImageUtil.PSD_FILE_FILTER.accept(null, fileURL)) {
      PSDImageReader reader = null;
      int imageIndex = 1;

      // Mask layer should always be layer 1 and overlay image on layer 2. Note, layer 0 will be a
      // combined layer composite
      if (overlayWanted) {
        imageIndex = 2;
      }

      File file = filePath.toFile();

      try (ImageInputStream is = ImageIO.createImageInputStream(file)) {
        if (is == null || is.length() == 0) {
          log.info("Image from file " + file.getAbsolutePath() + " is null");
        }

        Iterator<ImageReader> iterator = ImageIO.getImageReaders(is);
        if (iterator == null || !iterator.hasNext()) {
          throw new IOException("Image file format not supported by ImageIO: " + filePath);
        }

        reader = (PSDImageReader) iterator.next();
        reader.setInput(is);
        BufferedImage thumbBI;
        thumbBI = reader.read(imageIndex);

        if (thumbBI != null) {
          int layerIndex = 0;
          if (overlayWanted) {
            layerIndex = 1;
          }

          IIOMetadata metadata = reader.getImageMetadata(0);
          IIOMetadataNode root =
              (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
          NodeList layerInfoList = root.getElementsByTagName("LayerInfo");

          // Layer index corresponds to imageIndex - 1 in the reader
          IIOMetadataNode layerInfo = (IIOMetadataNode) layerInfoList.item(layerIndex);

          // Get the width & height of the Mask layer so we can create the overlay the same size
          int width = reader.getWidth(0);
          int height = reader.getHeight(0);

          // Get layer offsets, PhotoShop PSD layers can have different widths/heights and all
          // images start at 0,0 with a layer offset applied
          int x = Math.max(Integer.parseInt(layerInfo.getAttribute("left")), 0);
          int y = Math.max(Integer.parseInt(layerInfo.getAttribute("top")), 0);

          // Lets pad the overlay with transparency to make it the same size as the PSD canvas size
          thumb = resizeCanvas(SwingFXUtils.toFXImage(thumbBI, null), width, height, x, y);
        }
      } catch (Exception e) {
        log.error("Processing: " + file.getAbsolutePath(), e);
      } finally {
        // Dispose reader in finally block to avoid memory leaks
        if (reader != null) {
          reader.dispose();
        }
      }
    }

    thumbView.setImage(thumb);

    return thumbView;
  }

  public static Image resizeCanvas(Image imageSource, double newWidth, double newHeight) {
    int offsetX = (int) ((newWidth - imageSource.getWidth()) / 2);
    int offsetY = (int) ((newHeight - imageSource.getHeight()) / 2);

    return resizeCanvas(imageSource, (int) newWidth, (int) newHeight, offsetX, offsetY);
  }

  /*
   * Resize the overall image width/height without scaling the actual image, eg resize the canvas
   */
  public static Image resizeCanvas(
      Image imageSource, int newWidth, int newHeight, int offsetX, int offsetY) {
    int sourceWidth = (int) imageSource.getWidth();
    int sourceHeight = (int) imageSource.getHeight();

    // No work needed here...
    if (sourceWidth == newWidth && sourceHeight == newHeight) {
      return imageSource;
    }

    WritableImage outputImage = new WritableImage(newWidth, newHeight);
    PixelReader pixelReader = imageSource.getPixelReader();
    PixelWriter pixelWriter = outputImage.getPixelWriter();
    WritablePixelFormat<IntBuffer> format = WritablePixelFormat.getIntArgbInstance();

    int[] buffer = new int[sourceWidth * sourceHeight];
    pixelReader.getPixels(0, 0, sourceWidth, sourceHeight, format, buffer, 0, sourceWidth);
    pixelWriter.setPixels(
        offsetX, offsetY, sourceWidth, sourceHeight, format, buffer, 0, sourceWidth);

    return outputImage;
  }

  /*
   * Resize the overall image width/height scaled to the target width/height
   */
  public static Image scaleImage(
      Image source, double targetWidth, double targetHeight, boolean preserveRatio) {
    ImageView imageView = new ImageView(source);
    imageView.setPreserveRatio(preserveRatio);
    imageView.setFitWidth(targetWidth);
    imageView.setFitHeight(targetHeight);
    return imageView.snapshot(null, null);
  }

  /*
   * Return the intersection between the source image and the mask. Note, the mask does not need to be magenta anymore, any non-transparent pixel is considering a mask
   */
  private static Image clipImageWithMask(Image imageSource, Image imageMask) {
    int imageWidth = (int) imageMask.getWidth();
    int imageHeight = (int) imageMask.getHeight();

    WritableImage outputImage = new WritableImage(imageWidth, imageHeight);
    PixelReader pixelReader_Mask = imageMask.getPixelReader();
    PixelReader pixelReader_Source = imageSource.getPixelReader();
    PixelWriter pixelWriter = outputImage.getPixelWriter();

    for (int readY = 0; readY < imageHeight; readY++) {
      for (int readX = 0; readX < imageWidth; readX++) {
        Color pixelColor = pixelReader_Mask.getColor(readX, readY);

        if (pixelColor.equals(Color.TRANSPARENT)) {
          pixelWriter.setColor(readX, readY, pixelReader_Source.getColor(readX, readY));
        }
      }
    }

    return outputImage;
  }

  /*
   * Crop image to smallest width/height based on transparency
   */
  private static Image autoCropImage(Image imageSource) {
    return autoCropImage(imageSource, Color.TRANSPARENT, null);
  }

  public static Image autoCropImage(
      Image imageSource, Color backgroundColor, Image backgroundImage) {
    ImageView croppedImageView = new ImageView(imageSource);
    PixelReader pixelReader = imageSource.getPixelReader();

    int imageWidth = (int) imageSource.getWidth();
    int imageHeight = (int) imageSource.getHeight();
    int minX = imageWidth, minY = imageHeight, maxX = 0, maxY = 0;

    // Find the first and last pixels that are not transparent to create a bounding viewport
    for (int readY = 0; readY < imageHeight; readY++) {
      for (int readX = 0; readX < imageWidth; readX++) {
        Color pixelColor = pixelReader.getColor(readX, readY);

        if (!pixelColor.equals(Color.TRANSPARENT)) {
          if (readX < minX) {
            minX = readX;
          }
          if (readX > maxX) {
            maxX = readX;
          }

          if (readY < minY) {
            minY = readY;
          }
          if (readY > maxY) {
            maxY = readY;
          }
        }
      }
    }

    if (maxX - minX <= 0 || maxY - minY <= 0) {
      return new WritableImage(1, 1);
    }

    // Create a viewport to clip the image using snapshot
    Rectangle2D viewPort = new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    SnapshotParameters parameter = new SnapshotParameters();
    parameter.setViewport(viewPort);
    parameter.setFill(backgroundColor);

    if (backgroundImage != null) {
      return new Group(new ImageView(backgroundImage), croppedImageView).snapshot(parameter, null);
    } else {
      return croppedImageView.snapshot(parameter, null);
    }
  }

  public static Image composePreview(
      StackPane compositeTokenPane,
      ImageView backgroundImageView,
      Color bgColor,
      ImageView portraitImageView,
      ImageView maskImageView,
      ImageView overlayImageView,
      boolean useAsBase,
      boolean clipImage) {

    // Process layout as maskImage may have changed size if the overlay was changed
    compositeTokenPane.layout();
    SnapshotParameters parameter = new SnapshotParameters();
    Image finalImage;
    Group blend;

    // check if there is a mask image
    if (maskImageView.getFitWidth() <= 0 || maskImageView.getFitHeight() <= 0) {
      clipImage = false;
    }

    if (clipImage) {
      // We need to clip the portrait image first then blend the overlay image over it
      // We will first get a snapshot of the portrait equal to the mask overlay image width/height
      // We will then get a snapshot of the background image, if any.
      double x, y, width, height;

      x = maskImageView.getParent().getLayoutX();
      y = maskImageView.getParent().getLayoutY();
      width = maskImageView.getFitWidth();
      height = maskImageView.getFitHeight();

      Rectangle2D viewPort = new Rectangle2D(x, y, width, height);
      Rectangle2D maskViewPort = new Rectangle2D(1, 1, width, height);
      WritableImage newBackgroundImage = new WritableImage((int) width, (int) height);
      WritableImage newImage = new WritableImage((int) width, (int) height);
      WritableImage newMaskImage = new WritableImage((int) width, (int) height);

      ImageView newBackgroundImageView = new ImageView();
      ImageView overlayCopyImageView = new ImageView();
      ImageView clippedImageView = new ImageView();

      parameter.setViewport(viewPort);
      parameter.setFill(bgColor);
      backgroundImageView.snapshot(parameter, newBackgroundImage);

      parameter.setFill(Color.TRANSPARENT);
      portraitImageView.snapshot(parameter, newImage);

      parameter.setViewport(maskViewPort);
      maskImageView.setVisible(true);
      maskImageView.snapshot(parameter, newMaskImage);
      maskImageView.setVisible(false);

      clippedImageView.setFitWidth(width);
      clippedImageView.setFitHeight(height);
      clippedImageView.setImage(clipImageWithMask(newImage, newMaskImage));
      newBackgroundImageView.setImage(clipImageWithMask(newBackgroundImage, newMaskImage));

      // Our masked portrait image is now stored in clippedImageView, lets now blend the overlay
      // image over it
      // We'll create a temporary group to hold our temporary ImageViews's and blend them and take a
      // snapshot
      overlayCopyImageView.setImage(overlayImageView.getImage());
      overlayCopyImageView.setFitWidth(overlayImageView.getFitWidth());
      overlayCopyImageView.setFitHeight(overlayImageView.getFitHeight());
      overlayCopyImageView.setOpacity(overlayImageView.getOpacity());

      if (useAsBase) {
        blend = new Group(newBackgroundImageView, overlayCopyImageView, clippedImageView);
      } else {
        blend = new Group(newBackgroundImageView, clippedImageView, overlayCopyImageView);
      }

      // Last, we'll clean up any excess transparent edges by cropping it
      finalImage = autoCropImage(blend.snapshot(parameter, null));
    } else {
      parameter.setFill(Color.TRANSPARENT);
      finalImage = autoCropImage(compositeTokenPane.snapshot(parameter, null));
    }

    return finalImage;
  }

  public static double getScaleXRatio(ImageView imageView) {
    return imageView.getBoundsInParent().getWidth() / imageView.getImage().getWidth();
  }

  public static double getScaleYRatio(ImageView imageView) {
    return imageView.getBoundsInParent().getHeight() / imageView.getImage().getHeight();
  }

  /*
   * This is for Legacy support but can cause magenta bleed on edges if there is transparency overlap. The preferred overlay storage is now PhotoShop PSD format with layer 1 containing the mask and
   * layer 2 containing the image
   */
  private static Image processMagenta(Image inputImage, int colorThreshold, boolean overlayWanted) {
    int imageWidth = (int) inputImage.getWidth();
    int imageHeight = (int) inputImage.getHeight();

    WritableImage outputImage = new WritableImage(imageWidth, imageHeight);
    PixelReader pixelReader = inputImage.getPixelReader();
    PixelWriter pixelWriter = outputImage.getPixelWriter();

    for (int readY = 0; readY < imageHeight; readY++) {
      for (int readX = 0; readX < imageWidth; readX++) {
        Color pixelColor = pixelReader.getColor(readX, readY);

        if (isMagenta(pixelColor, COLOR_THRESHOLD) == overlayWanted) {
          pixelWriter.setColor(readX, readY, Color.TRANSPARENT);
        } else {
          pixelWriter.setColor(readX, readY, pixelColor);
        }
      }
    }

    return outputImage;
  }

  // Using some fudge factor...
  private static boolean isMagenta(Color color, int fudge) {
    if (color.equals(Color.MAGENTA)) {
      return true;
    }

    double r = color.getRed();
    double g = color.getGreen();
    double b = color.getBlue();

    if (Math.abs(r - b) > fudge) {
      return false;
    }

    return !(g > r - fudge) && !(g > b - fudge);
  }

  public static String getFileType(File imageFile) {
    if (FilenameUtils.getExtension(imageFile.getName()).equalsIgnoreCase("psd")) {
      return "Adobe Photoshop " + I18N.getString("imageUtil.filetype.label.image");
    } else {
      return FilenameUtils.getExtension(imageFile.getName()).toUpperCase()
          + I18N.getString("imageUtil.filetype.label.extension");
    }
  }

  public static byte[] imageToBytes(BufferedImage image) throws IOException {
    return imageToBytes(image, "png");
  }

  public static byte[] imageToBytes(BufferedImage image, String format) throws IOException {
    ByteArrayOutputStream outStream = new ByteArrayOutputStream(10000);
    ImageIO.write(image, format, outStream);

    return outStream.toByteArray();
  }

  public static List<ExtensionFilter> GET_EXTENSION_FILTERS() {
    List<ExtensionFilter> extensionFilters = new ArrayList<>();
    extensionFilters.add(
        new ExtensionFilter(
            I18N.getString("imageUtil.filetype.label.all_images"),
            "*.psd",
            "*.png",
            "*.gif",
            "*.jpg",
            "*.jpeg",
            "*.bmp"));
    extensionFilters.add(
        new ExtensionFilter("PSD" + I18N.getString("imageUtil.filetype.label.files"), "*.psd"));
    extensionFilters.add(
        new ExtensionFilter("PNG" + I18N.getString("imageUtil.filetype.label.files"), "*.png"));
    extensionFilters.add(
        new ExtensionFilter("JPG" + I18N.getString("imageUtil.filetype.label.files"), "*.jpg"));
    extensionFilters.add(
        new ExtensionFilter("JPEG" + I18N.getString("imageUtil.filetype.label.files"), "*.jpeg"));
    extensionFilters.add(
        new ExtensionFilter("BMP" + I18N.getString("imageUtil.filetype.label.files"), "*.bmp"));

    return extensionFilters;
  }
}
