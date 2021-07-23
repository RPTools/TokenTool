module net.rptools.tokentool {
  requires javafx.base;
  requires javafx.controls;
  requires javafx.fxml;
  requires javafx.graphics;
  requires javafx.swing;
  requires java.prefs;
  requires com.google.gson;
  requires common.io;
  requires imageio.psd;
  requires org.apache.logging.log4j;
  requires jai.imageio.jpeg2000;
  requires org.apache.commons.io;
  requires org.apache.pdfbox;
  requires sentry;

  opens net.rptools.tokentool to
      javafx.fxml;
  opens net.rptools.tokentool.client to
      javafx.graphics;
  opens net.rptools.tokentool.controller to
      javafx.fxml;
  opens net.rptools.tokentool.model to
      com.google.gson;
}
