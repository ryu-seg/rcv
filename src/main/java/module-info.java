module network.brightspots.rcv {
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.dataformat.xml;
  requires java.logging;
  requires javafx.base;
  requires javafx.controls;
  requires javafx.fxml;
  requires javafx.graphics;
  requires java.xml;
  requires org.apache.commons.csv;
  requires org.apache.poi.ooxml;
  // enable reflexive calls from network.brightspots.rcv into javafx.fxml
  opens network.brightspots.rcv;
  // our main module
  exports network.brightspots.rcv;
}
