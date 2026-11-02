package com.freakcam.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import com.freakcam.FreakCam;

public class FreakCamConfig {

    private static File configFile;
    private static String selectedCamera = "";
    private static int captureWidth = 640;
    private static int captureHeight = 480;
    private static int captureFps = 30;
    private static boolean webcamEnabled = true;
    private static boolean mirrorWebcam = false;
    private static float heightOffset = 1.40f;
    private static float webcamScale = 1.00f;
    private static float cropOffsetX = 0.0f;
    private static float cropOffsetY = 0.0f;
    private static float zoomLevel = 1.0f;
    private static boolean showNameTagInPreview = true;
    private static boolean showOffIconHud = true;
    private static boolean showOffIcon3D = true;
    private static float offIconX = 0.02f;
    private static float offIconY = 0.02f;
    private static float offIconScale = 1.00f;
    private static boolean offIconShadow = true;
    private static int offIconStyle = 0;

    // Item 1: Visual Effects & Frames
    private static int webcamFrameShape = 0; // 0=Rectangle, 1=Circle, 2=RoundedRect, 3=Hexagon, 4=Heart, 5=Diamond
    private static boolean webcamBorderEnabled = true;
    private static float webcamBorderWidth = 2.0f;
    private static int webcamBorderMode = 0; // 0=Solid, 1=RGB Rainbow
    private static int webcamBorderColor = 0x00FFCC; // Aqua/Cyan default

    private static boolean chromaKeyEnabled = false;
    private static int chromaKeyColor = 0x00FF00; // Green default
    private static float chromaKeyTolerance = 0.25f;
    private static float chromaKeySmoothness = 0.08f;

    public static void load(File configDir) {
        configFile = new File(configDir, "freakcam.properties");
        if (!configFile.exists()) {
            FreakCam.LOG.info("No FreakCam config found, using defaults.");
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(configFile);
            Properties props = new Properties();
            props.load(fis);
            fis.close();

            selectedCamera = props.getProperty("camera", "");
            captureWidth = Integer.parseInt(props.getProperty("captureWidth", "640"));
            captureHeight = Integer.parseInt(props.getProperty("captureHeight", "480"));
            captureFps = Integer.parseInt(props.getProperty("captureFps", "30"));

            // Auto migrate old configs or invalid high resolutions to fast 480p
            if (captureWidth <= 64 || captureWidth > 1280) {
                captureWidth = 640;
                captureHeight = 480;
            }
            webcamEnabled = Boolean.parseBoolean(props.getProperty("webcamEnabled", "true"));
            mirrorWebcam = Boolean.parseBoolean(props.getProperty("mirrorWebcam", "false"));
            heightOffset = Float.parseFloat(props.getProperty("heightOffset", "1.40"));
            webcamScale = Float.parseFloat(props.getProperty("webcamScale", "1.00"));
            cropOffsetX = Float.parseFloat(props.getProperty("cropOffsetX", "0.0"));
            cropOffsetY = Float.parseFloat(props.getProperty("cropOffsetY", "0.0"));
            zoomLevel = Float.parseFloat(props.getProperty("zoomLevel", "1.0"));
            showNameTagInPreview = Boolean.parseBoolean(props.getProperty("showNameTagInPreview", "true"));
            showOffIconHud = Boolean.parseBoolean(props.getProperty("showOffIconHud", "true"));
            showOffIcon3D = Boolean.parseBoolean(props.getProperty("showOffIcon3D", "true"));
            offIconX = Float.parseFloat(props.getProperty("offIconX", "0.02"));
            offIconY = Float.parseFloat(props.getProperty("offIconY", "0.02"));
            offIconScale = Float.parseFloat(props.getProperty("offIconScale", "1.00"));
            offIconShadow = Boolean.parseBoolean(props.getProperty("offIconShadow", "true"));
            offIconStyle = Integer.parseInt(props.getProperty("offIconStyle", "0"));

            webcamFrameShape = Integer.parseInt(props.getProperty("webcamFrameShape", "0"));
            webcamBorderEnabled = Boolean.parseBoolean(props.getProperty("webcamBorderEnabled", "true"));
            webcamBorderWidth = Float.parseFloat(props.getProperty("webcamBorderWidth", "2.0"));
            webcamBorderMode = Integer.parseInt(props.getProperty("webcamBorderMode", "0"));
            webcamBorderColor = Integer.parseInt(props.getProperty("webcamBorderColor", "65484")); // 0x00FFCC

            chromaKeyEnabled = Boolean.parseBoolean(props.getProperty("chromaKeyEnabled", "false"));
            chromaKeyColor = Integer.parseInt(props.getProperty("chromaKeyColor", "65280")); // 0x00FF00
            chromaKeyTolerance = Float.parseFloat(props.getProperty("chromaKeyTolerance", "0.25"));
            chromaKeySmoothness = Float.parseFloat(props.getProperty("chromaKeySmoothness", "0.08"));
            FreakCam.LOG.info("Loaded FreakCam config. Camera: " + selectedCamera);
        } catch (Exception e) {
            FreakCam.LOG.error("Failed to load FreakCam config", e);
        }
    }

    public static void save() {
        if (configFile == null) return;
        try {
            configFile.getParentFile()
                .mkdirs();
            Properties props = new Properties();
            props.setProperty("camera", selectedCamera);
            props.setProperty("captureWidth", String.valueOf(captureWidth));
            props.setProperty("captureHeight", String.valueOf(captureHeight));
            props.setProperty("captureFps", String.valueOf(captureFps));
            props.setProperty("webcamEnabled", String.valueOf(webcamEnabled));
            props.setProperty("mirrorWebcam", String.valueOf(mirrorWebcam));
            props.setProperty("heightOffset", String.valueOf(heightOffset));
            props.setProperty("webcamScale", String.valueOf(webcamScale));
            props.setProperty("cropOffsetX", String.valueOf(cropOffsetX));
            props.setProperty("cropOffsetY", String.valueOf(cropOffsetY));
            props.setProperty("zoomLevel", String.valueOf(zoomLevel));
            props.setProperty("showNameTagInPreview", String.valueOf(showNameTagInPreview));
            props.setProperty("showOffIconHud", String.valueOf(showOffIconHud));
            props.setProperty("showOffIcon3D", String.valueOf(showOffIcon3D));
            props.setProperty("offIconX", String.valueOf(offIconX));
            props.setProperty("offIconY", String.valueOf(offIconY));
            props.setProperty("offIconScale", String.valueOf(offIconScale));
            props.setProperty("offIconShadow", String.valueOf(offIconShadow));
            props.setProperty("offIconStyle", String.valueOf(offIconStyle));

            props.setProperty("webcamFrameShape", String.valueOf(webcamFrameShape));
            props.setProperty("webcamBorderEnabled", String.valueOf(webcamBorderEnabled));
            props.setProperty("webcamBorderWidth", String.valueOf(webcamBorderWidth));
            props.setProperty("webcamBorderMode", String.valueOf(webcamBorderMode));
            props.setProperty("webcamBorderColor", String.valueOf(webcamBorderColor));

            props.setProperty("chromaKeyEnabled", String.valueOf(chromaKeyEnabled));
            props.setProperty("chromaKeyColor", String.valueOf(chromaKeyColor));
            props.setProperty("chromaKeyTolerance", String.valueOf(chromaKeyTolerance));
            props.setProperty("chromaKeySmoothness", String.valueOf(chromaKeySmoothness));
            FileOutputStream fos = new FileOutputStream(configFile);
            props.store(fos, "FreakCam Configuration");
            fos.close();
            FreakCam.LOG.info("Saved FreakCam config.");
        } catch (Exception e) {
            FreakCam.LOG.error("Failed to save FreakCam config", e);
        }
    }

    public static String getSelectedCamera() {
        return selectedCamera;
    }

    public static void setSelectedCamera(String camera) {
        selectedCamera = camera;
    }

    public static int getCaptureWidth() {
        return captureWidth;
    }

    public static int getCaptureHeight() {
        return captureHeight;
    }

    public static void setCaptureResolution(int w, int h) {
        captureWidth = w;
        captureHeight = h;
    }

    public static int getCaptureFps() {
        return captureFps;
    }

    public static void setCaptureFps(int fps) {
        captureFps = fps;
    }

    public static boolean isWebcamEnabled() {
        return webcamEnabled;
    }

    public static void setWebcamEnabled(boolean enabled) {
        webcamEnabled = enabled;
    }

    public static boolean isMirrorWebcam() {
        return mirrorWebcam;
    }

    public static void setMirrorWebcam(boolean mirror) {
        mirrorWebcam = mirror;
    }

    public static float getHeightOffset() {
        return heightOffset;
    }

    public static void setHeightOffset(float offset) {
        heightOffset = Math.max(0.1f, Math.min(3.0f, offset));
    }

    public static float getWebcamScale() {
        return webcamScale;
    }

    public static void setWebcamScale(float scale) {
        webcamScale = Math.max(0.2f, Math.min(3.0f, scale));
    }

    public static float getCropOffsetX() {
        return cropOffsetX;
    }

    public static void setCropOffsetX(float x) {
        cropOffsetX = Math.max(-0.5f, Math.min(0.5f, x));
    }

    public static float getCropOffsetY() {
        return cropOffsetY;
    }

    public static void setCropOffsetY(float y) {
        cropOffsetY = Math.max(-0.5f, Math.min(0.5f, y));
    }

    public static float getZoomLevel() {
        return zoomLevel;
    }

    public static void setZoomLevel(float zoom) {
        zoomLevel = Math.max(1.0f, Math.min(4.0f, zoom));
    }

    public static void resetCrop() {
        cropOffsetX = 0.0f;
        cropOffsetY = 0.0f;
        zoomLevel = 1.0f;
    }

    public static boolean isShowNameTagInPreview() {
        return showNameTagInPreview;
    }

    public static void setShowNameTagInPreview(boolean show) {
        showNameTagInPreview = show;
    }

    public static boolean isShowOffIconHud() {
        return showOffIconHud;
    }

    public static void setShowOffIconHud(boolean show) {
        showOffIconHud = show;
    }

    public static boolean isShowOffIcon3D() {
        return showOffIcon3D;
    }

    public static void setShowOffIcon3D(boolean show) {
        showOffIcon3D = show;
    }

    public static float getOffIconX() {
        return offIconX;
    }

    public static void setOffIconX(float x) {
        offIconX = Math.max(0.0f, Math.min(1.0f, x));
    }

    public static float getOffIconY() {
        return offIconY;
    }

    public static void setOffIconY(float y) {
        offIconY = Math.max(0.0f, Math.min(1.0f, y));
    }

    public static float getOffIconScale() {
        return offIconScale;
    }

    public static void setOffIconScale(float scale) {
        offIconScale = Math.max(0.2f, Math.min(3.0f, scale));
    }

    public static boolean isOffIconShadow() {
        return offIconShadow;
    }

    public static void setOffIconShadow(boolean shadow) {
        offIconShadow = shadow;
    }

    public static int getOffIconStyle() {
        return offIconStyle;
    }

    public static void setOffIconStyle(int style) {
        offIconStyle = Math.max(0, Math.min(1, style));
    }

    // Item 1 Getters & Setters
    public static int getWebcamFrameShape() {
        if (webcamFrameShape < 0 || webcamFrameShape > 3) {
            webcamFrameShape = 0;
        }
        return webcamFrameShape;
    }

    public static void setWebcamFrameShape(int shape) {
        if (shape < 0 || shape > 3) {
            shape = 0;
        }
        webcamFrameShape = shape;
    }

    public static boolean isWebcamBorderEnabled() {
        return webcamBorderEnabled;
    }

    public static void setWebcamBorderEnabled(boolean enabled) {
        webcamBorderEnabled = enabled;
    }

    public static float getWebcamBorderWidth() {
        return webcamBorderWidth;
    }

    public static void setWebcamBorderWidth(float width) {
        webcamBorderWidth = Math.max(1.0f, Math.min(8.0f, width));
    }

    public static int getWebcamBorderMode() {
        return webcamBorderMode;
    }

    public static void setWebcamBorderMode(int mode) {
        webcamBorderMode = Math.max(0, Math.min(2, mode));
    }

    public static int getWebcamBorderColor() {
        return webcamBorderColor;
    }

    public static void setWebcamBorderColor(int color) {
        webcamBorderColor = color & 0xFFFFFF;
    }

    public static boolean isChromaKeyEnabled() {
        return chromaKeyEnabled;
    }

    public static void setChromaKeyEnabled(boolean enabled) {
        chromaKeyEnabled = enabled;
    }

    public static int getChromaKeyColor() {
        return chromaKeyColor;
    }

    public static void setChromaKeyColor(int color) {
        chromaKeyColor = color & 0xFFFFFF;
    }

    public static float getChromaKeyTolerance() {
        return chromaKeyTolerance;
    }

    public static void setChromaKeyTolerance(float tol) {
        chromaKeyTolerance = Math.max(0.05f, Math.min(0.60f, tol));
    }

    public static float getChromaKeySmoothness() {
        return chromaKeySmoothness;
    }

    public static void setChromaKeySmoothness(float smooth) {
        chromaKeySmoothness = Math.max(0.00f, Math.min(0.25f, smooth));
    }
}
