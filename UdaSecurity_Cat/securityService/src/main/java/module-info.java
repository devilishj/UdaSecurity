module com.udacity.catpoint.securityService {
    requires com.udacity.catpoint.imageService;
    requires com.miglayout.swing;
    requires java.desktop;
    requires java.prefs;
    requires com.google.common;
    requires com.google.gson;
    requires java.sql;
    opens com.udacity.catpoint.data to com.google.gson;
}