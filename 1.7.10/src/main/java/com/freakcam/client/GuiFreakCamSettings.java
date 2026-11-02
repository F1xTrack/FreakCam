package com.freakcam.client;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

public class GuiFreakCamSettings extends GuiScreen {

    private final GuiScreen parentScreen;
    private int currentTab = 0; // 0 = Camera & Crop, 1 = Position & 3D

    // Top Tabs
    private static final int BTN_TAB_CAM = 100;
    private static final int BTN_TAB_POS = 101;
    private static final int BTN_TAB_OFF = 102;
    private static final int BTN_TAB_FX = 103;

    // Tab 3: Visual Effects & Frames
    private static final int BTN_SHAPE_TOGGLE = 60;
    private static final int BTN_BORDER_TOGGLE = 61;
    private static final int BTN_BORDER_MODE_TOGGLE = 62;
    private static final int SLIDER_BORDER_WIDTH = 63;
    private static final int BTN_BORDER_COLOR_TOGGLE = 64;
    private static final int BTN_CHROMA_TOGGLE = 65;
    private static final int BTN_CHROMA_COLOR_TOGGLE = 66;
    private static final int SLIDER_CHROMA_TOL = 67;

    private GuiSlider borderWidthSlider;
    private GuiSlider chromaTolSlider;
    private net.minecraft.client.gui.GuiTextField borderWidthField;
    private net.minecraft.client.gui.GuiTextField chromaTolField;

    // Tab 0: Camera controls
    private static final int BTN_CAM_SELECT = 0;
    private static final int BTN_WEBCAM_TOGGLE = 1;
    private static final int BTN_RESOLUTION = 2;
    private static final int BTN_MIRROR_TOGGLE = 3;
    private static final int BTN_CROP_RESET = 4;

    // Tab 1: Position controls
    private static final int SLIDER_HEIGHT = 10;
    private static final int SLIDER_SCALE = 11;
    private static final int BTN_PRESET_FRONT = 20;
    private static final int BTN_PRESET_BACK = 21;
    private static final int BTN_PRESET_LEFT = 22;
    private static final int BTN_PRESET_RIGHT = 23;
    private static final int BTN_NAMETAG_TOGGLE = 30;
    private static final int BTN_RESET_POS = 31;

    // Tab 2: OFF Icon controls
    private static final int BTN_OFF_HUD_TOGGLE = 40;
    private static final int SLIDER_OFF_SCALE = 42;
    private static final int BTN_RESET_OFF_ICON = 43;
    private static final int BTN_OFF_SHADOW_TOGGLE = 44;
    private static final int BTN_OFF_STYLE_TOGGLE = 45;

    private static final int BTN_PRESET_TL = 50;
    private static final int BTN_PRESET_TC = 51;
    private static final int BTN_PRESET_TR = 52;
    private static final int BTN_PRESET_ML = 53;
    private static final int BTN_PRESET_MR = 54;
    private static final int BTN_PRESET_BL = 55;
    private static final int BTN_PRESET_BC = 56;
    private static final int BTN_PRESET_BR = 57;

    // Common
    private static final int BTN_DONE = 200;

    // Inline Camera Dropdown
    private boolean showCameraDropdown = false;
    private int selectedDropdownIndex = 0;
    private int cameraScrollOffset = 0;

    // Inline Resolution Dropdown
    private boolean showResolutionDropdown = false;
    private int resolutionScrollOffset = 0;

    // Text Fields for Tabs
    private net.minecraft.client.gui.GuiTextField fpsField;
    private net.minecraft.client.gui.GuiTextField heightField;
    private net.minecraft.client.gui.GuiTextField scaleField;
    private net.minecraft.client.gui.GuiTextField offScaleField;

    private GuiSlider heightSlider;
    private GuiSlider scaleSlider;
    private GuiSlider offScaleSlider;
    private boolean isDraggingOffIcon = false;

    // Interactive Crop Dragging State (8 Handles + Move)
    private enum DragMode {
        NONE,
        MOVE,
        TL,
        TR,
        BL,
        BR,
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    private DragMode currentDragMode = DragMode.NONE;
    private int dragStartX, dragStartY;
    private float startImgLeft, startImgTop, startImgRight, startImgBottom;

    // 3D Model Viewport State
    private float previewYaw = 0.0f;
    private float previewPitch = 10.0f;
    private float previewZoom = 1.0f;
    private boolean isDragging3DModel = false;
    private int lastMouseX, lastMouseY;

    // OpenGL texture for live preview
    private net.minecraft.client.renderer.texture.DynamicTexture rawDynamicTexture;
    private net.minecraft.client.renderer.texture.DynamicTexture liveDynamicTexture;

    private List<String> availableCameras = new ArrayList<>();
    private List<String> availableResolutions = new ArrayList<>();
    private int resolutionIndex = 0;

    public GuiFreakCamSettings(GuiScreen parent) {
        this.parentScreen = parent;
    }

    // ======== Image dimensions ========
    private int getImgW() {
        return ClientHandler.getLatestFrameWidth();
    }

    private int getImgH() {
        return ClientHandler.getLatestFrameHeight();
    }

    private int getMinDim() {
        return Math.min(getImgW(), getImgH());
    }

    // ======== Crop Tab Preview Box Geometry ========
    private int getPreviewBoxW() {
        int imgW = getImgW(), imgH = getImgH();
        int maxH = Math.max(60, this.height - 158);
        int maxW = Math.min(220, this.width - 20);
        return Math.min(maxW, Math.max(80, maxH * imgW / imgH));
    }

    private int getPreviewBoxH() {
        int imgW = getImgW(), imgH = getImgH();
        int pW = getPreviewBoxW();
        return pW * imgH / imgW;
    }

    private int getPreviewBoxX(int cx) {
        return cx - getPreviewBoxW() / 2;
    }

    private int getPreviewBoxY(int sy) {
        return 128;
    }

    // ======== 3D Viewport Box Geometry ========
    private int get3DPreviewBoxY() {
        return 48;
    }

    private int get3DPreviewBoxH() {
        int bottomReserve = 112;
        int topOffset = get3DPreviewBoxY();
        return Math.max(70, this.height - topOffset - bottomReserve);
    }

    private int get3DPreviewBoxW() {
        return (int) (get3DPreviewBoxH() * 0.76f);
    }

    private int get3DPreviewBoxX(int cx) {
        return cx - get3DPreviewBoxW() / 2;
    }

    // ======== Crop Coordinate Helpers ========
    private float[] getCropBoundsInImage() {
        int w = getImgW(), h = getImgH();
        int minDim = Math.min(w, h);
        float zoom = FreakCamConfig.getZoomLevel();
        float offX = FreakCamConfig.getCropOffsetX();
        float offY = FreakCamConfig.getCropOffsetY();

        float cropSize = minDim / zoom;
        float centerX = w * (0.5f + offX);
        float centerY = h * (0.5f + offY);

        float left = Math.max(0, Math.min(w - cropSize, centerX - cropSize / 2.0f));
        float top = Math.max(0, Math.min(h - cropSize, centerY - cropSize / 2.0f));

        return new float[] { left, top, left + cropSize, top + cropSize };
    }

    private void setCropBoundsFromImage(float left, float top, float right, float bottom) {
        int w = getImgW(), h = getImgH();
        int minDim = Math.min(w, h);
        float size = Math.max(8, Math.min(minDim, Math.max(right - left, bottom - top)));
        float centerX = Math.max(size / 2.0f, Math.min(w - size / 2.0f, left + size / 2.0f));
        float centerY = Math.max(size / 2.0f, Math.min(h - size / 2.0f, top + size / 2.0f));

        FreakCamConfig.setZoomLevel((float) minDim / size);
        FreakCamConfig.setCropOffsetX(centerX / w - 0.5f);
        FreakCamConfig.setCropOffsetY(centerY / h - 0.5f);
    }

    private int imgToPreviewX(float imgX, int pBoxX, int pBoxW) {
        return (int) (pBoxX + imgX / getImgW() * pBoxW);
    }

    private int imgToPreviewY(float imgY, int pBoxY, int pBoxH) {
        return (int) (pBoxY + imgY / getImgH() * pBoxH);
    }

    @Override
    public void initGui() {
        refreshDeviceLists();
        int cx = this.width / 2;
        int topY = 16;
        int sy = topY + 24;
        fpsField = new net.minecraft.client.gui.GuiTextField(this.fontRendererObj, cx - 120, sy + 66, 118, 18);
        fpsField.setText(String.valueOf(FreakCamConfig.getCaptureFps()));
        fpsField.setMaxStringLength(3);

        int posCtrlY = this.height - 106;
        int scaleY = posCtrlY + 20;

        heightField = new net.minecraft.client.gui.GuiTextField(this.fontRendererObj, cx + 45, posCtrlY, 65, 18);
        heightField.setText(String.format(java.util.Locale.US, "%.2f", FreakCamConfig.getHeightOffset()));
        heightField.setMaxStringLength(5);

        scaleField = new net.minecraft.client.gui.GuiTextField(this.fontRendererObj, cx + 45, scaleY, 65, 18);
        scaleField.setText(String.format(java.util.Locale.US, "%.2f", FreakCamConfig.getWebcamScale()));
        scaleField.setMaxStringLength(5);

        int offCtrlY = this.height - 70;
        offScaleField = new net.minecraft.client.gui.GuiTextField(this.fontRendererObj, cx + 50, offCtrlY, 70, 18);
        offScaleField.setText(String.format(java.util.Locale.US, "%.2f", FreakCamConfig.getOffIconScale()));
        offScaleField.setMaxStringLength(5);

        int syFx = topY + 24;
        int r3 = syFx + 40; // 80 - matches borderWidthSlider
        int r5 = syFx + 80; // 120 - matches chromaTolSlider

        borderWidthField = new net.minecraft.client.gui.GuiTextField(this.fontRendererObj, cx + 50, r3, 65, 18);
        borderWidthField.setText(String.format(java.util.Locale.US, "%.1f", FreakCamConfig.getWebcamBorderWidth()));
        borderWidthField.setMaxStringLength(4);

        chromaTolField = new net.minecraft.client.gui.GuiTextField(this.fontRendererObj, cx + 50, r5, 65, 18);
        chromaTolField.setText(String.format(java.util.Locale.US, "%.2f", FreakCamConfig.getChromaKeyTolerance()));
        chromaTolField.setMaxStringLength(4);

        rebuildButtons();
    }

    private void refreshDeviceLists() {
        availableCameras = WebcamCapture.listDevices();
        String device = FreakCamConfig.getSelectedCamera();
        if ((device == null || device.isEmpty()) && !availableCameras.isEmpty()) {
            device = availableCameras.get(0);
            FreakCamConfig.setSelectedCamera(device);
        }
        availableResolutions = WebcamCapture.listResolutions(device);
        if (availableResolutions.isEmpty()) {
            availableResolutions.add("640x480");
        }
        String currentRes = FreakCamConfig.getCaptureWidth() + "x" + FreakCamConfig.getCaptureHeight();
        resolutionIndex = 0;
        for (int i = 0; i < availableResolutions.size(); i++) {
            if (availableResolutions.get(i)
                .equals(currentRes)) {
                resolutionIndex = i;
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuildButtons() {
        this.buttonList.clear();
        int cx = this.width / 2;
        int topY = 16;

        String tabCamText = StatCollector.translateToLocal("gui.freakcam.tab.camera");
        String tabPosText = StatCollector.translateToLocal("gui.freakcam.tab.position");
        String tabOffText = StatCollector.translateToLocal("gui.freakcam.tab.off_icon");
        String tabFxText = StatCollector.translateToLocal("gui.freakcam.tab.fx");

        String tabCamStyle = (currentTab == 0) ? "\u00a7a\u00a7l" + tabCamText : "\u00a77" + tabCamText;
        String tabPosStyle = (currentTab == 1) ? "\u00a7a\u00a7l" + tabPosText : "\u00a77" + tabPosText;
        String tabOffStyle = (currentTab == 2) ? "\u00a7a\u00a7l" + tabOffText : "\u00a77" + tabOffText;
        String tabFxStyle = (currentTab == 3) ? "\u00a7a\u00a7l" + tabFxText : "\u00a77" + tabFxText;

        this.buttonList.add(new GuiButton(BTN_TAB_CAM, cx - 156, topY, 75, 20, tabCamStyle));
        this.buttonList.add(new GuiButton(BTN_TAB_POS, cx - 78, topY, 75, 20, tabPosStyle));
        this.buttonList.add(new GuiButton(BTN_TAB_OFF, cx + 3, topY, 75, 20, tabOffStyle));
        this.buttonList.add(new GuiButton(BTN_TAB_FX, cx + 81, topY, 75, 20, tabFxStyle));

        int sy = topY + 24;
        if (currentTab == 0) {
            this.buttonList.add(new GuiButton(BTN_CAM_SELECT, cx - 120, sy, 240, 20, getCameraLabel()));
            this.buttonList.add(new GuiButton(BTN_WEBCAM_TOGGLE, cx - 120, sy + 22, 118, 20, getWebcamLabel()));
            this.buttonList.add(new GuiButton(BTN_MIRROR_TOGGLE, cx + 2, sy + 22, 118, 20, getMirrorLabel()));
            this.buttonList.add(new GuiButton(BTN_RESOLUTION, cx - 120, sy + 44, 118, 20, getResolutionLabel()));
            this.buttonList.add(
                new GuiButton(
                    BTN_CROP_RESET,
                    cx + 2,
                    sy + 44,
                    118,
                    20,
                    StatCollector.translateToLocal("gui.freakcam.reset_crop")));
        } else if (currentTab == 1) {
            int posCtrlY = this.height - 106;
            heightSlider = new GuiSlider(
                SLIDER_HEIGHT,
                cx - 115,
                posCtrlY,
                155,
                18,
                StatCollector.translateToLocal("gui.freakcam.height_offset"),
                StatCollector.translateToLocal("gui.freakcam.meter_unit"),
                0.20f,
                3.00f,
                FreakCamConfig.getHeightOffset(),
                (slider, val) -> {
                    FreakCamConfig.setHeightOffset(val);
                    if (heightField != null && !heightField.isFocused()) {
                        heightField.setText(String.format(java.util.Locale.US, "%.2f", val));
                    }
                });
            this.buttonList.add(heightSlider);

            int scaleY = posCtrlY + 20;
            scaleSlider = new GuiSlider(
                SLIDER_SCALE,
                cx - 115,
                scaleY,
                155,
                18,
                StatCollector.translateToLocal("gui.freakcam.scale"),
                StatCollector.translateToLocal("gui.freakcam.scale_unit"),
                0.20f,
                3.00f,
                FreakCamConfig.getWebcamScale(),
                (slider, val) -> {
                    FreakCamConfig.setWebcamScale(val);
                    if (scaleField != null && !scaleField.isFocused()) {
                        scaleField.setText(String.format(java.util.Locale.US, "%.2f", val));
                    }
                });
            this.buttonList.add(scaleSlider);

            int presetY = scaleY + 20;
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_FRONT,
                    cx - 115,
                    presetY,
                    53,
                    18,
                    StatCollector.translateToLocal("gui.freakcam.front")));
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_BACK,
                    cx - 58,
                    presetY,
                    53,
                    18,
                    StatCollector.translateToLocal("gui.freakcam.back")));
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_LEFT,
                    cx - 1,
                    presetY,
                    53,
                    18,
                    StatCollector.translateToLocal("gui.freakcam.left")));
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_RIGHT,
                    cx + 56,
                    presetY,
                    53,
                    18,
                    StatCollector.translateToLocal("gui.freakcam.right")));

            int row4Y = presetY + 20;
            this.buttonList.add(new GuiButton(BTN_NAMETAG_TOGGLE, cx - 115, row4Y, 112, 18, getNameTagLabel()));
            this.buttonList.add(
                new GuiButton(
                    BTN_RESET_POS,
                    cx + 3,
                    row4Y,
                    112,
                    18,
                    StatCollector.translateToLocal("gui.freakcam.reset_view")));
        } else if (currentTab == 2) {
            int pBoxY = 44;
            int pBoxH = getOffIconPreviewBoxH();
            int pBoxW = getOffIconPreviewBoxW();
            int pBoxX = cx - pBoxW / 2;

            // 8 preset buttons around preview box edges & corners
            int cornerW = 22;
            int cornerH = 20;
            int midW = 32;

            // Top row
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_TL,
                    pBoxX - 24,
                    pBoxY - 2,
                    cornerW,
                    cornerH,
                    StatCollector.translateToLocal("gui.freakcam.preset_tl")));
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_TC,
                    cx - midW / 2,
                    pBoxY - 22,
                    midW,
                    cornerH,
                    StatCollector.translateToLocal("gui.freakcam.preset_tc")));
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_TR,
                    pBoxX + pBoxW + 2,
                    pBoxY - 2,
                    cornerW,
                    cornerH,
                    StatCollector.translateToLocal("gui.freakcam.preset_tr")));

            // Middle side buttons
            int midY = pBoxY + pBoxH / 2 - cornerH / 2;
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_ML,
                    pBoxX - 24,
                    midY,
                    cornerW,
                    cornerH,
                    StatCollector.translateToLocal("gui.freakcam.preset_ml")));
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_MR,
                    pBoxX + pBoxW + 2,
                    midY,
                    cornerW,
                    cornerH,
                    StatCollector.translateToLocal("gui.freakcam.preset_mr")));

            // Bottom row around preview box
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_BL,
                    pBoxX - 24,
                    pBoxY + pBoxH - 18,
                    cornerW,
                    cornerH,
                    StatCollector.translateToLocal("gui.freakcam.preset_bl")));
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_BC,
                    cx - midW / 2,
                    pBoxY + pBoxH + 2,
                    midW,
                    cornerH,
                    StatCollector.translateToLocal("gui.freakcam.preset_bc")));
            this.buttonList.add(
                new GuiButton(
                    BTN_PRESET_BR,
                    pBoxX + pBoxW + 2,
                    pBoxY + pBoxH - 18,
                    cornerW,
                    cornerH,
                    StatCollector.translateToLocal("gui.freakcam.preset_br")));

            // Controls below preview box
            int ctrlY1 = this.height - 110;
            this.buttonList.add(new GuiButton(BTN_OFF_HUD_TOGGLE, cx - 120, ctrlY1, 118, 18, getOffIconHudLabel()));
            this.buttonList.add(new GuiButton(BTN_OFF_SHADOW_TOGGLE, cx + 2, ctrlY1, 118, 18, getOffIconShadowLabel()));

            int ctrlY2 = ctrlY1 + 20;
            this.buttonList.add(new GuiButton(BTN_OFF_STYLE_TOGGLE, cx - 120, ctrlY2, 240, 18, getOffIconStyleLabel()));

            int scaleY = ctrlY2 + 20;
            offScaleSlider = new GuiSlider(
                SLIDER_OFF_SCALE,
                cx - 120,
                scaleY,
                165,
                18,
                StatCollector.translateToLocal("gui.freakcam.off_icon_scale"),
                StatCollector.translateToLocal("gui.freakcam.scale_unit"),
                0.20f,
                3.00f,
                FreakCamConfig.getOffIconScale(),
                (slider, val) -> {
                    FreakCamConfig.setOffIconScale(val);
                    if (offScaleField != null && !offScaleField.isFocused()) {
                        offScaleField.setText(String.format(java.util.Locale.US, "%.2f", val));
                    }
                });
            this.buttonList.add(offScaleSlider);

            int resetY = scaleY + 20;
            this.buttonList.add(
                new GuiButton(
                    BTN_RESET_OFF_ICON,
                    cx - 120,
                    resetY,
                    240,
                    18,
                    StatCollector.translateToLocal("gui.freakcam.reset_icon")));
        } else if (currentTab == 3) {
            int syFx = topY + 24;
            this.buttonList.add(new GuiButton(BTN_SHAPE_TOGGLE, cx - 120, syFx, 240, 18, getShapeLabel()));

            int r2 = syFx + 20;
            this.buttonList.add(new GuiButton(BTN_BORDER_TOGGLE, cx - 120, r2, 118, 18, getBorderToggleLabel()));
            this.buttonList.add(new GuiButton(BTN_BORDER_COLOR_TOGGLE, cx + 2, r2, 118, 18, getBorderColorLabel()));

            int r3 = r2 + 20;
            borderWidthSlider = new GuiSlider(
                SLIDER_BORDER_WIDTH,
                cx - 120,
                r3,
                165,
                18,
                StatCollector.translateToLocal("gui.freakcam.border_width"),
                "px",
                1.0f,
                6.0f,
                FreakCamConfig.getWebcamBorderWidth(),
                (slider, val) -> {
                    FreakCamConfig.setWebcamBorderWidth(val);
                    if (borderWidthField != null && !borderWidthField.isFocused()) {
                        borderWidthField.setText(String.format(java.util.Locale.US, "%.1f", val));
                    }
                });
            this.buttonList.add(borderWidthSlider);

            int r4 = r3 + 20;
            this.buttonList.add(new GuiButton(BTN_CHROMA_TOGGLE, cx - 120, r4, 118, 18, getChromaToggleLabel()));
            this.buttonList.add(new GuiButton(BTN_CHROMA_COLOR_TOGGLE, cx + 2, r4, 118, 18, getChromaColorLabel()));

            int r5 = r4 + 20;
            chromaTolSlider = new GuiSlider(
                SLIDER_CHROMA_TOL,
                cx - 120,
                r5,
                165,
                18,
                StatCollector.translateToLocal("gui.freakcam.chroma_tol"),
                "",
                0.05f,
                0.60f,
                FreakCamConfig.getChromaKeyTolerance(),
                (slider, val) -> {
                    FreakCamConfig.setChromaKeyTolerance(val);
                    if (chromaTolField != null && !chromaTolField.isFocused()) {
                        chromaTolField.setText(String.format(java.util.Locale.US, "%.2f", val));
                    }
                });
            this.buttonList.add(chromaTolSlider);
        }

        this.buttonList.add(
            new GuiButton(
                BTN_DONE,
                cx - 100,
                this.height - 24,
                200,
                20,
                "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.done")));
    }

    // ======== Actions ========
    @Override
    protected void actionPerformed(GuiButton button) {
        if (showCameraDropdown) return;
        switch (button.id) {
            case BTN_TAB_CAM:
                currentTab = 0;
                rebuildButtons();
                break;
            case BTN_TAB_POS:
                currentTab = 1;
                rebuildButtons();
                break;
            case BTN_CAM_SELECT:
                showCameraDropdown = true;
                String currentCam = FreakCamConfig.getSelectedCamera();
                selectedDropdownIndex = 0;
                if (currentCam != null) {
                    for (int i = 0; i < availableCameras.size(); i++) {
                        if (availableCameras.get(i)
                            .equals(currentCam)) {
                            selectedDropdownIndex = i;
                            break;
                        }
                    }
                }
                break;
            case BTN_WEBCAM_TOGGLE:
                FreakCamConfig.setWebcamEnabled(!FreakCamConfig.isWebcamEnabled());
                FreakCamConfig.save();
                ClientHandler.restartCapture();
                rebuildButtons();
                break;
            case BTN_RESOLUTION:
                showResolutionDropdown = true;
                break;
            case BTN_MIRROR_TOGGLE:
                FreakCamConfig.setMirrorWebcam(!FreakCamConfig.isMirrorWebcam());
                rebuildButtons();
                break;
            case BTN_CROP_RESET:
                FreakCamConfig.setZoomLevel(1.0f);
                FreakCamConfig.setCropOffsetX(0.0f);
                FreakCamConfig.setCropOffsetY(0.0f);
                break;
            case BTN_PRESET_FRONT:
                previewYaw = 0.0f;
                previewPitch = 10.0f;
                break;
            case BTN_PRESET_BACK:
                previewYaw = 180.0f;
                previewPitch = 10.0f;
                break;
            case BTN_PRESET_LEFT:
                previewYaw = 90.0f;
                previewPitch = 10.0f;
                break;
            case BTN_PRESET_RIGHT:
                previewYaw = -90.0f;
                previewPitch = 10.0f;
                break;
            case BTN_NAMETAG_TOGGLE:
                FreakCamConfig.setShowNameTagInPreview(!FreakCamConfig.isShowNameTagInPreview());
                rebuildButtons();
                break;
            case BTN_TAB_OFF:
                currentTab = 2;
                rebuildButtons();
                break;
            case BTN_TAB_FX:
                currentTab = 3;
                rebuildButtons();
                break;
            case BTN_SHAPE_TOGGLE:
                FreakCamConfig.setWebcamFrameShape((FreakCamConfig.getWebcamFrameShape() + 1) % 4);
                rebuildButtons();
                break;
            case BTN_BORDER_TOGGLE:
                FreakCamConfig.setWebcamBorderEnabled(!FreakCamConfig.isWebcamBorderEnabled());
                rebuildButtons();
                break;
            case BTN_BORDER_MODE_TOGGLE:
                FreakCamConfig.setWebcamBorderMode(FreakCamConfig.getWebcamBorderMode() == 0 ? 1 : 0);
                rebuildButtons();
                break;
            case BTN_BORDER_COLOR_TOGGLE:
                int currentMode = FreakCamConfig.getWebcamBorderMode();
                if (currentMode == 1) { // Pulse -> Spectrum
                    FreakCamConfig.setWebcamBorderMode(2);
                } else if (currentMode == 2) { // Spectrum -> Solid Aqua
                    FreakCamConfig.setWebcamBorderMode(0);
                    FreakCamConfig.setWebcamBorderColor(0x00FFCC);
                } else {
                    int curColor = FreakCamConfig.getWebcamBorderColor();
                    if (curColor == 0x00FFCC) FreakCamConfig.setWebcamBorderColor(0x00FF00); // Green
                    else if (curColor == 0x00FF00) FreakCamConfig.setWebcamBorderColor(0xFF2222); // Red
                    else if (curColor == 0xFF2222) FreakCamConfig.setWebcamBorderColor(0xFFD700); // Gold
                    else if (curColor == 0xFFD700) FreakCamConfig.setWebcamBorderColor(0xAA00FF); // Purple
                    else if (curColor == 0xAA00FF) FreakCamConfig.setWebcamBorderColor(0xFFFFFF); // White
                    else {
                        FreakCamConfig.setWebcamBorderMode(1); // Switch to Pulse Rainbow!
                    }
                }
                rebuildButtons();
                break;
            case BTN_CHROMA_TOGGLE:
                FreakCamConfig.setChromaKeyEnabled(!FreakCamConfig.isChromaKeyEnabled());
                rebuildButtons();
                break;
            case BTN_CHROMA_COLOR_TOGGLE:
                int curChroma = FreakCamConfig.getChromaKeyColor();
                if (curChroma == 0x00FF00) FreakCamConfig.setChromaKeyColor(0x0000FF); // Blue
                else if (curChroma == 0x0000FF) FreakCamConfig.setChromaKeyColor(0x000000); // Black
                else FreakCamConfig.setChromaKeyColor(0x00FF00); // Green
                rebuildButtons();
                break;
            case BTN_OFF_HUD_TOGGLE:
                FreakCamConfig.setShowOffIconHud(!FreakCamConfig.isShowOffIconHud());
                rebuildButtons();
                break;
            case BTN_OFF_SHADOW_TOGGLE:
                FreakCamConfig.setOffIconShadow(!FreakCamConfig.isOffIconShadow());
                rebuildButtons();
                break;
            case BTN_OFF_STYLE_TOGGLE:
                FreakCamConfig.setOffIconStyle(FreakCamConfig.getOffIconStyle() == 0 ? 1 : 0);
                rebuildButtons();
                break;
            case BTN_PRESET_TL:
                FreakCamConfig.setOffIconX(0.02f);
                FreakCamConfig.setOffIconY(0.02f);
                break;
            case BTN_PRESET_TC:
                FreakCamConfig.setOffIconX(0.50f);
                FreakCamConfig.setOffIconY(0.02f);
                break;
            case BTN_PRESET_TR:
                FreakCamConfig.setOffIconX(0.98f);
                FreakCamConfig.setOffIconY(0.02f);
                break;
            case BTN_PRESET_ML:
                FreakCamConfig.setOffIconX(0.02f);
                FreakCamConfig.setOffIconY(0.50f);
                break;
            case BTN_PRESET_MR:
                FreakCamConfig.setOffIconX(0.98f);
                FreakCamConfig.setOffIconY(0.50f);
                break;
            case BTN_PRESET_BL:
                FreakCamConfig.setOffIconX(0.02f);
                FreakCamConfig.setOffIconY(0.98f);
                break;
            case BTN_PRESET_BC:
                FreakCamConfig.setOffIconX(0.50f);
                FreakCamConfig.setOffIconY(0.98f);
                break;
            case BTN_PRESET_BR:
                FreakCamConfig.setOffIconX(0.98f);
                FreakCamConfig.setOffIconY(0.98f);
                break;
            case BTN_RESET_OFF_ICON:
                FreakCamConfig.setOffIconX(0.02f);
                FreakCamConfig.setOffIconY(0.02f);
                FreakCamConfig.setOffIconScale(1.00f);
                if (offScaleField != null) offScaleField.setText("1.00");
                rebuildButtons();
                break;
            case BTN_RESET_POS:
                FreakCamConfig.setHeightOffset(1.40f);
                FreakCamConfig.setWebcamScale(1.00f);
                previewYaw = 0.0f;
                previewPitch = 10.0f;
                if (heightField != null) heightField.setText("1.40");
                if (scaleField != null) scaleField.setText("1.00");
                rebuildButtons();
                break;
            case BTN_DONE:
                try {
                    int fps = Integer.parseInt(fpsField.getText());
                    if (fps > 0 && fps != FreakCamConfig.getCaptureFps()) {
                        FreakCamConfig.setCaptureFps(fps);
                        ClientHandler.restartCapture();
                    }
                } catch (NumberFormatException e) {}
                FreakCamConfig.save();
                this.mc.displayGuiScreen(this.parentScreen);
                break;
        }
    }

    private void saveCurrentResolution() {
        if (availableResolutions.isEmpty()) return;
        String[] parts = availableResolutions.get(resolutionIndex)
            .split("x");
        if (parts.length == 2) {
            try {
                FreakCamConfig.setCaptureResolution(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } catch (Exception e) {}
        }
    }

    // ======== Mouse Input Handling ========
    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel != 0) {
            int dir = dWheel > 0 ? -1 : 1;
            if (showCameraDropdown) {
                int maxScroll = Math.max(0, availableCameras.size() - 5);
                cameraScrollOffset = Math.max(0, Math.min(maxScroll, cameraScrollOffset + dir));
            } else if (showResolutionDropdown) {
                int maxScroll = Math.max(0, availableResolutions.size() - 5);
                resolutionScrollOffset = Math.max(0, Math.min(maxScroll, resolutionScrollOffset + dir));
            } else if (currentTab == 1 || currentTab == 3) {
                float zoomChange = (dWheel > 0) ? 0.10f : -0.10f;
                previewZoom = Math.max(0.40f, Math.min(3.00f, previewZoom + zoomChange));
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (showCameraDropdown) {
            handleDropdownClick(mouseX, mouseY);
            return;
        }
        if (showResolutionDropdown) {
            handleResDropdownClick(mouseX, mouseY);
            return;
        }

        if (currentTab == 0 && fpsField != null) {
            fpsField.mouseClicked(mouseX, mouseY, button);
        }
        if (currentTab == 1) {
            if (heightField != null) heightField.mouseClicked(mouseX, mouseY, button);
            if (scaleField != null) scaleField.mouseClicked(mouseX, mouseY, button);
        }
        if (currentTab == 2 && offScaleField != null) {
            offScaleField.mouseClicked(mouseX, mouseY, button);
        }
        if (currentTab == 3) {
            if (borderWidthField != null) borderWidthField.mouseClicked(mouseX, mouseY, button);
            if (chromaTolField != null) chromaTolField.mouseClicked(mouseX, mouseY, button);
        }

        super.mouseClicked(mouseX, mouseY, button);

        int cx = this.width / 2;
        int sy = 40;

        if (currentTab == 0) {
            int pBoxW = getPreviewBoxW();
            int pBoxH = getPreviewBoxH();
            int pBoxX = getPreviewBoxX(cx);
            int pBoxY = getPreviewBoxY(sy);

            if (mouseX >= pBoxX && mouseX <= pBoxX + pBoxW && mouseY >= pBoxY && mouseY <= pBoxY + pBoxH) {
                float[] bounds = getCropBoundsInImage();
                int sqLeft = imgToPreviewX(bounds[0], pBoxX, pBoxW);
                int sqTop = imgToPreviewY(bounds[1], pBoxY, pBoxH);
                int sqRight = imgToPreviewX(bounds[2], pBoxX, pBoxW);
                int sqBottom = imgToPreviewY(bounds[3], pBoxY, pBoxH);
                int sqMidX = (sqLeft + sqRight) / 2;
                int sqMidY = (sqTop + sqBottom) / 2;

                int handleR = 6;
                if (Math.abs(mouseX - sqLeft) <= handleR && Math.abs(mouseY - sqTop) <= handleR)
                    currentDragMode = DragMode.TL;
                else if (Math.abs(mouseX - sqRight) <= handleR && Math.abs(mouseY - sqTop) <= handleR)
                    currentDragMode = DragMode.TR;
                else if (Math.abs(mouseX - sqLeft) <= handleR && Math.abs(mouseY - sqBottom) <= handleR)
                    currentDragMode = DragMode.BL;
                else if (Math.abs(mouseX - sqRight) <= handleR && Math.abs(mouseY - sqBottom) <= handleR)
                    currentDragMode = DragMode.BR;
                else if (Math.abs(mouseX - sqMidX) <= handleR && Math.abs(mouseY - sqTop) <= handleR)
                    currentDragMode = DragMode.TOP;
                else if (Math.abs(mouseX - sqMidX) <= handleR && Math.abs(mouseY - sqBottom) <= handleR)
                    currentDragMode = DragMode.BOTTOM;
                else if (Math.abs(mouseX - sqLeft) <= handleR && Math.abs(mouseY - sqMidY) <= handleR)
                    currentDragMode = DragMode.LEFT;
                else if (Math.abs(mouseX - sqRight) <= handleR && Math.abs(mouseY - sqMidY) <= handleR)
                    currentDragMode = DragMode.RIGHT;
                else if (mouseX >= sqLeft && mouseX <= sqRight && mouseY >= sqTop && mouseY <= sqBottom) {
                    currentDragMode = DragMode.MOVE;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    startImgLeft = bounds[0];
                    startImgTop = bounds[1];
                    startImgRight = bounds[2];
                    startImgBottom = bounds[3];
                }
            }
        } else if (currentTab == 1) {
            int previewBoxW = get3DPreviewBoxW();
            int previewBoxH = get3DPreviewBoxH();
            int previewBoxX = get3DPreviewBoxX(cx);
            int previewBoxY = get3DPreviewBoxY();
            if (mouseX >= previewBoxX && mouseX <= previewBoxX + previewBoxW
                && mouseY >= previewBoxY
                && mouseY <= previewBoxY + previewBoxH) {
                isDragging3DModel = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
        } else if (currentTab == 3) {
            int cardW = Math.min(this.width - 20, 500);
            int cardX = cx - cardW / 2;
            int halfW = cardW / 2 - 6;
            int leftX = cardX + 4;
            int cardY = 144;
            int cardH = Math.max(80, this.height - 144 - 30);
            if (mouseX >= leftX && mouseX <= leftX + halfW && mouseY >= cardY && mouseY <= cardY + cardH) {
                isDragging3DModel = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
        } else if (currentTab == 2) {
            int previewBoxW = getOffIconPreviewBoxW();
            int previewBoxH = getOffIconPreviewBoxH();
            int previewBoxX = getOffIconPreviewBoxX(cx);
            int previewBoxY = getOffIconPreviewBoxY();
            if (mouseX >= previewBoxX && mouseX <= previewBoxX + previewBoxW
                && mouseY >= previewBoxY
                && mouseY <= previewBoxY + previewBoxH) {
                isDraggingOffIcon = true;
            }
            if (offScaleField != null) offScaleField.mouseClicked(mouseX, mouseY, button);
        }
    }

    private void handleDropdownClick(int mouseX, int mouseY) {
        int cx = this.width / 2;
        int boxX = cx - 110, boxY = 50, boxWidth = 220, boxHeight = 150;
        int itemY = boxY + 22, itemHeight = 18;
        for (int i = cameraScrollOffset; i < availableCameras.size(); i++) {
            if (itemY + itemHeight > boxY + boxHeight - 28) break;
            if (mouseX >= boxX + 6 && mouseX <= boxX + boxWidth - 6 && mouseY >= itemY && mouseY < itemY + itemHeight) {
                selectedDropdownIndex = i;
                break;
            }
            itemY += itemHeight;
        }
        int btnY = boxY + boxHeight - 24;
        int btnW = (boxWidth - 16) / 2;
        if (mouseY >= btnY && mouseY <= btnY + 20) {
            if (mouseX >= boxX + 6 && mouseX <= boxX + 6 + btnW) {
                if (!availableCameras.isEmpty() && selectedDropdownIndex >= 0
                    && selectedDropdownIndex < availableCameras.size()) {
                    FreakCamConfig.setSelectedCamera(availableCameras.get(selectedDropdownIndex));
                    FreakCamConfig.save();
                    ClientHandler.restartCapture();
                    refreshDeviceLists();
                    rebuildButtons();
                }
                showCameraDropdown = false;
            } else if (mouseX >= boxX + 10 + btnW && mouseX <= boxX + 10 + 2 * btnW) {
                showCameraDropdown = false;
            }
        }
    }

    private void handleResDropdownClick(int mouseX, int mouseY) {
        int cx = this.width / 2;
        int sy = 40 + 48; // Position of the resolution button
        int boxX = cx - 110, boxY = sy + 22, boxWidth = 220, boxHeight = 150;
        int itemY = boxY + 22, itemHeight = 18;

        int tempSel = -1;
        for (int i = resolutionScrollOffset; i < availableResolutions.size(); i++) {
            if (itemY + itemHeight > boxY + boxHeight - 28) break;
            if (mouseX >= boxX + 6 && mouseX <= boxX + boxWidth - 6 && mouseY >= itemY && mouseY < itemY + itemHeight) {
                tempSel = i;
                break;
            }
            itemY += itemHeight;
        }
        int btnY = boxY + boxHeight - 24;
        int btnW = (boxWidth - 16) / 2;
        if (mouseY >= btnY && mouseY <= btnY + 20) {
            if (mouseX >= boxX + 6 && mouseX <= boxX + 6 + btnW) {
                if (tempSel != -1) {
                    resolutionIndex = tempSel;
                    saveCurrentResolution();
                    ClientHandler.restartCapture();
                    rebuildButtons();
                }
                showResolutionDropdown = false;
            } else if (mouseX >= boxX + 10 + btnW && mouseX <= boxX + 10 + 2 * btnW) {
                showResolutionDropdown = false;
            }
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (!showCameraDropdown && !showResolutionDropdown) {
            super.mouseMovedOrUp(mouseX, mouseY, button);
        }
        if (button == 0) {
            currentDragMode = DragMode.NONE;
            isDragging3DModel = false;
            isDraggingOffIcon = false;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (showCameraDropdown || showResolutionDropdown) return;
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        int cx = this.width / 2;
        int sy = 40;

        if (currentTab == 0 && currentDragMode != DragMode.NONE) {
            int pBoxW = getPreviewBoxW(), pBoxH = getPreviewBoxH();
            float dxImg = (mouseX - dragStartX) / (float) pBoxW * getImgW();
            float dyImg = (mouseY - dragStartY) / (float) pBoxH * getImgH();

            float newLeft = startImgLeft, newTop = startImgTop;
            float newRight = startImgRight, newBottom = startImgBottom;

            if (currentDragMode == DragMode.MOVE) {
                float size = startImgRight - startImgLeft;
                newLeft = Math.max(0, Math.min(getImgW() - size, startImgLeft + dxImg));
                newTop = Math.max(0, Math.min(getImgH() - size, startImgTop + dyImg));
                newRight = newLeft + size;
                newBottom = newTop + size;
            } else {
                switch (currentDragMode) {
                    case RIGHT:
                        newRight = startImgRight + dxImg;
                        newLeft = startImgLeft;
                        break;
                    case LEFT:
                        newLeft = startImgLeft + dxImg;
                        newRight = startImgRight;
                        break;
                    case BOTTOM:
                        newBottom = startImgBottom + dyImg;
                        newTop = startImgTop;
                        break;
                    case TOP:
                        newTop = startImgTop + dyImg;
                        newBottom = startImgBottom;
                        break;
                    case BR: {
                        float d = (dxImg + dyImg) / 2;
                        newRight = startImgRight + d;
                        newBottom = startImgBottom + d;
                    }
                        break;
                    case TL: {
                        float d = (dxImg + dyImg) / 2;
                        newLeft = startImgLeft + d;
                        newTop = startImgTop + d;
                    }
                        break;
                    case TR: {
                        float d = (dxImg - dyImg) / 2;
                        newRight = startImgRight + d;
                        newTop = startImgTop - d;
                    }
                        break;
                    case BL: {
                        float d = (-dxImg + dyImg) / 2;
                        newLeft = startImgLeft - d;
                        newBottom = startImgBottom + d;
                    }
                        break;
                    default:
                        break;
                }
                float sizeH = newRight - newLeft, sizeV = newBottom - newTop;
                float newSize;
                if (currentDragMode == DragMode.LEFT || currentDragMode == DragMode.RIGHT) {
                    newSize = sizeH;
                    float vc = (startImgTop + startImgBottom) / 2;
                    newTop = vc - newSize / 2;
                    newBottom = vc + newSize / 2;
                } else if (currentDragMode == DragMode.TOP || currentDragMode == DragMode.BOTTOM) {
                    newSize = sizeV;
                    float hc = (startImgLeft + startImgRight) / 2;
                    newLeft = hc - newSize / 2;
                    newRight = hc + newSize / 2;
                } else {
                    newSize = Math.max(sizeH, sizeV);
                }
                if (newSize < 8 || newSize > getMinDim()) return;
            }

            float size = newRight - newLeft;
            if (size < 8) return;
            size = Math.min(size, getMinDim());
            newLeft = Math.max(0, Math.min(getImgW() - size, newLeft));
            newTop = Math.max(0, Math.min(getImgH() - size, newTop));
            newRight = newLeft + size;
            newBottom = newTop + size;
            setCropBoundsFromImage(newLeft, newTop, newRight, newBottom);
        } else if ((currentTab == 1 || currentTab == 3) && isDragging3DModel) {
            if (org.lwjgl.input.Mouse.isButtonDown(1)) { // RMB drag = Zoom!
                float dy = (mouseY - lastMouseY) * 0.02f;
                previewZoom = Math.max(0.40f, Math.min(3.00f, previewZoom - dy));
            } else { // LMB drag = Rotate!
                previewYaw += (mouseX - lastMouseX) * 1.5f;
                previewPitch = Math.max(-60.0f, Math.min(60.0f, previewPitch + (mouseY - lastMouseY) * 1.5f));
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    // ======== Texture Update (With CIRCULAR ALPHA MASK matching ClientHandler) ========
    private void updateLivePreviewTexture() {
        BufferedImage rawImg = ClientHandler.getLatestRawImage();
        if (rawImg != null) {
            try {
                // 1. UPDATE RAW TEXTURE for Camera & Crop tab
                int rw = rawImg.getWidth(), rh = rawImg.getHeight();
                if (rawDynamicTexture == null || rawDynamicTexture.getTextureData().length != rw * rh) {
                    rawDynamicTexture = new net.minecraft.client.renderer.texture.DynamicTexture(rw, rh);
                }
                int[] rPixels = rawDynamicTexture.getTextureData();
                rawImg.getRGB(0, 0, rw, rh, rPixels, 0, rw);
                // Ensure full alpha
                for (int i = 0; i < rPixels.length; i++) {
                    rPixels[i] = 0xFF000000 | (rPixels[i] & 0xFFFFFF);
                }
                rawDynamicTexture.updateDynamicTexture();

                // 2. UPDATE CROPPED TEXTURE WITH ALPHA MASK for Position & 3D tab
                BufferedImage croppedImg = ClientHandler.applyCropAndZoom(rawImg);

                int w = croppedImg.getWidth(), h = croppedImg.getHeight();
                if (liveDynamicTexture == null || liveDynamicTexture.getTextureData().length != w * h) {
                    liveDynamicTexture = new net.minecraft.client.renderer.texture.DynamicTexture(w, h);
                }
                int[] pixels = liveDynamicTexture.getTextureData();
                croppedImg.getRGB(0, 0, w, h, pixels, 0, w);

                ClientHandler.applyPixelMaskAndChromaKey(pixels, w, h);
                liveDynamicTexture.updateDynamicTexture();
            } catch (Exception e) {}
        }
    }

    // ======== Main Screen Draw ========
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateLivePreviewTexture();
        drawRect(0, 0, this.width, this.height, 0xEE101010);
        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a7l\u00a76" + StatCollector.translateToLocal("gui.freakcam.title"),
            this.width / 2,
            4,
            0xFFFFFF);

        int cx = this.width / 2;
        int sy = 40;

        if (currentTab == 0) drawCameraTabContent(cx, sy, mouseX, mouseY);
        else if (currentTab == 1) drawPositionTabContent(cx, sy, mouseX, mouseY);
        else if (currentTab == 2) drawOffIconTabContent(cx, sy, mouseX, mouseY);
        else if (currentTab == 3) drawFxTabContent(cx, sy, mouseX, mouseY);

        if (!showCameraDropdown && !showResolutionDropdown) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            if (currentTab == 1) {
                if (heightField != null) heightField.drawTextBox();
                if (scaleField != null) scaleField.drawTextBox();
            } else if (currentTab == 2) {
                if (offScaleField != null) offScaleField.drawTextBox();
            } else if (currentTab == 3) {
                if (borderWidthField != null) borderWidthField.drawTextBox();
                if (chromaTolField != null) chromaTolField.drawTextBox();
            }
        } else if (showCameraDropdown) {
            super.drawScreen(-1, -1, partialTicks);
            drawInlineCameraDropdown(cx, mouseX, mouseY);
        } else if (showResolutionDropdown) {
            super.drawScreen(-1, -1, partialTicks);
            drawInlineResolutionDropdown(cx, mouseX, mouseY);
        }
    }

    // ======== Camera & Crop Tab Content ========
    private void drawCameraTabContent(int cx, int sy, int mouseX, int mouseY) {
        int pBoxW = getPreviewBoxW();
        int pBoxH = getPreviewBoxH();
        int pBoxX = getPreviewBoxX(cx);
        int pBoxY = getPreviewBoxY(sy);

        drawRect(pBoxX - 2, pBoxY - 2, pBoxX + pBoxW + 2, pBoxY + pBoxH + 2, 0xFF444444);
        drawRect(pBoxX, pBoxY, pBoxX + pBoxW, pBoxY + pBoxH, 0xFF000000);

        if (rawDynamicTexture != null) {
            GL11.glPushMatrix();
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, rawDynamicTexture.getGlTextureId());
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(pBoxX, pBoxY, 0, 0, 0);
            tess.addVertexWithUV(pBoxX, pBoxY + pBoxH, 0, 0, 1);
            tess.addVertexWithUV(pBoxX + pBoxW, pBoxY + pBoxH, 0, 1, 1);
            tess.addVertexWithUV(pBoxX + pBoxW, pBoxY, 0, 1, 0);
            tess.draw();
            GL11.glPopMatrix();
        } else {
            this.drawCenteredString(
                this.fontRendererObj,
                StatCollector.translateToLocal("gui.freakcam.no_signal"),
                pBoxX + pBoxW / 2,
                pBoxY + pBoxH / 2 - 4,
                0xFF5555);
        }

        if (fpsField != null) {
            String fpsLabel = "\u00a77" + StatCollector.translateToLocal("gui.freakcam.target_fps");
            this.drawString(
                this.fontRendererObj,
                fpsLabel,
                fpsField.xPosition - this.fontRendererObj.getStringWidth(fpsLabel) - 6,
                fpsField.yPosition + 6,
                0xFFFFFF);
            fpsField.drawTextBox();
        }

        float[] bounds = getCropBoundsInImage();
        int sqLeft = imgToPreviewX(bounds[0], pBoxX, pBoxW);
        int sqTop = imgToPreviewY(bounds[1], pBoxY, pBoxH);
        int sqRight = imgToPreviewX(bounds[2], pBoxX, pBoxW);
        int sqBottom = imgToPreviewY(bounds[3], pBoxY, pBoxH);

        int dL = Math.max(pBoxX, sqLeft), dT = Math.max(pBoxY, sqTop);
        int dR = Math.min(pBoxX + pBoxW, sqRight), dB = Math.min(pBoxY + pBoxH, sqBottom);

        drawRect(dL, dT, dR, dT + 1, 0xFF00FF00);
        drawRect(dL, dB - 1, dR, dB, 0xFF00FF00);
        drawRect(dL, dT, dL + 1, dB, 0xFF00FF00);
        drawRect(dR - 1, dT, dR, dB, 0xFF00FF00);

        int sqCX = (sqLeft + sqRight) / 2, sqCY = (sqTop + sqBottom) / 2;
        float circleRx = (sqRight - sqLeft) / 2.0f;
        float circleRy = (sqBottom - sqTop) / 2.0f;
        drawEllipse(sqCX, sqCY, circleRx, circleRy, 0xFFFFFF00);

        int hr = 3, mX = sqCX, mY = sqCY;
        drawHandle(sqLeft, sqTop, hr);
        drawHandle(sqRight, sqTop, hr);
        drawHandle(sqLeft, sqBottom, hr);
        drawHandle(sqRight, sqBottom, hr);
        drawHandle(mX, sqTop, hr);
        drawHandle(mX, sqBottom, hr);
        drawHandle(sqLeft, mY, hr);
        drawHandle(sqRight, mY, hr);

        float zoom = FreakCamConfig.getZoomLevel();
        float offX = FreakCamConfig.getCropOffsetX();
        float offY = FreakCamConfig.getCropOffsetY();
        String zoomCropFmt = StatCollector.translateToLocal("gui.freakcam.zoom_crop_fmt");
        this.drawString(
            this.fontRendererObj,
            String.format(zoomCropFmt, zoom, offX, offY),
            pBoxX,
            pBoxY + pBoxH + 4,
            0xFFFFFF);
    }

    private void drawHandle(int x, int y, int hr) {
        drawRect(x - hr, y - hr, x + hr, y + hr, 0xFFFFFF00);
    }

    private void drawEllipse(float cx, float cy, float rx, float ry, int color) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        float a = (color >> 24 & 255) / 255.0F;
        float rC = (color >> 16 & 255) / 255.0F;
        float gC = (color >> 8 & 255) / 255.0F;
        float bC = (color & 255) / 255.0F;
        GL11.glColor4f(rC, gC, bC, a == 0 ? 1.0f : a);
        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI * 2.0 / 48;
            GL11.glVertex2d(cx + Math.cos(angle) * rx, cy + Math.sin(angle) * ry);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    // ======== POSITION & 3D TAB CONTENT ========
    private void drawPositionTabContent(int cx, int sy, int mouseX, int mouseY) {
        int previewBoxW = get3DPreviewBoxW();
        int previewBoxH = get3DPreviewBoxH();
        int previewBoxX = get3DPreviewBoxX(cx);
        int previewBoxY = get3DPreviewBoxY();

        // Viewport Frame & Background
        drawRect(
            previewBoxX - 2,
            previewBoxY - 2,
            previewBoxX + previewBoxW + 2,
            previewBoxY + previewBoxH + 2,
            0xFF444455);
        drawRect(previewBoxX, previewBoxY, previewBoxX + previewBoxW, previewBoxY + previewBoxH, 0xFF0A0A14);

        // Sub-caption hint
        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a77" + StatCollector.translateToLocal("gui.freakcam.drag_hint"),
            previewBoxX + previewBoxW / 2,
            previewBoxY + 4,
            0x666688);

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            ScaledResolution sr = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
            int sf = sr.getScaleFactor();

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                previewBoxX * sf,
                (this.height - (previewBoxY + previewBoxH)) * sf,
                previewBoxW * sf,
                previewBoxH * sf);

            int playerScale = Math.max(25, (int) (previewBoxH * 0.22f));
            draw3DPlayerViewport(
                previewBoxX + previewBoxW / 2,
                previewBoxY + previewBoxH - 16,
                playerScale,
                previewYaw,
                previewPitch,
                player);

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /**
     * 3D Player Viewport Renderer with Perfect Circle Texture and Depth Fix.
     */
    private void draw3DPlayerViewport(int posX, int posY, int scale, float yaw, float pitch, EntityPlayer player) {
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glPushMatrix();

        // Root translation in 2D GUI space - push Z far back so we don't clip against GUI planes
        GL11.glTranslatef(posX, posY, 250.0F);

        // Exact vanilla GUI scaling & rotation logic to match renderEntityWithPosYaw perfectly
        float totalScale = scale * previewZoom;
        GL11.glScalef(-totalScale, totalScale, totalScale);
        GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);

        // Model view rotation
        GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(yaw, 0.0F, 1.0F, 0.0F);

        RenderHelper.enableStandardItemLighting();

        // 1. RENDER PLAYER MODEL WITH MATRIX ISOLATION
        GL11.glPushMatrix();
        float oldRenderYaw = player.renderYawOffset;
        float oldRotYaw = player.rotationYaw;
        float oldRotPitch = player.rotationPitch;
        float oldPrevYaw = player.prevRotationYawHead;
        float oldYawHead = player.rotationYawHead;

        player.renderYawOffset = 0.0f;
        player.rotationYaw = 0.0f;
        player.rotationPitch = 0.0f;
        player.rotationYawHead = 0.0f;
        player.prevRotationYawHead = 0.0f;

        // Apply yOffset so feet are at exactly 0,0,0 (Fixes nametag and webcam being too high)
        GL11.glTranslatef(0.0F, player.yOffset, 0.0F);

        RenderManager.instance.playerViewY = 180.0F;
        RenderManager.instance.renderEntityWithPosYaw(player, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);

        player.renderYawOffset = oldRenderYaw;
        player.rotationYaw = oldRotYaw;
        player.rotationPitch = oldRotPitch;
        player.prevRotationYawHead = oldPrevYaw;
        player.rotationYawHead = oldYawHead;
        GL11.glPopMatrix(); // CLEAN MATRIX AT PLAYER ROOT

        // Disable GL Depth Test and Cull Face for floating billboards
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);

        // 2. RENDER NAME TAG (At Y = player.height + 0.5f, matching vanilla RenderLiving)
        if (FreakCamConfig.isShowNameTagInPreview()) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0.0F, player.height + 0.5F, 0.0F);
            GL11.glRotatef(-yaw, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-pitch, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
            GL11.glScalef(0.02F, 0.02F, 0.02F);

            String name = player.getCommandSenderName();
            int nw = this.fontRendererObj.getStringWidth(name) / 2;
            drawRect(-nw - 2, -2, nw + 2, 9, 0x77000000);
            this.fontRendererObj.drawString(name, -nw, 0, 0xFFFFFF);
            GL11.glPopMatrix();
        }

        // 3. RENDER WEBCAM CIRCLE (At Y = player.height + heightOffset)
        float heightOff = FreakCamConfig.getHeightOffset();
        float webcamWorldY = player.height + heightOff;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, webcamWorldY, 0.0F);

        // Billboard rotation
        GL11.glRotatef(-yaw, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-pitch, 1.0F, 0.0F, 0.0F);

        // Exact in-game scale factor: 20 * (0.016666668F * 2.0F) * userScale
        float quadRadiusInBlocks = 0.6666667F * FreakCamConfig.getWebcamScale();

        if (liveDynamicTexture != null) {
            // Live circular-masked webcam texture quad
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, liveDynamicTexture.getGlTextureId());
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(-quadRadiusInBlocks, -quadRadiusInBlocks, 0.0D, 0.0D, 1.0D);
            tess.addVertexWithUV(-quadRadiusInBlocks, quadRadiusInBlocks, 0.0D, 0.0D, 0.0D);
            tess.addVertexWithUV(quadRadiusInBlocks, quadRadiusInBlocks, 0.0D, 1.0D, 0.0D);
            tess.addVertexWithUV(quadRadiusInBlocks, -quadRadiusInBlocks, 0.0D, 1.0D, 1.0D);
            tess.draw();

            if (FreakCamConfig.isWebcamBorderEnabled()) {
                int localId = player.getEntityId();
                ClientHandler.draw3DFrameBorder(localId, quadRadiusInBlocks, quadRadiusInBlocks);
            }
        } else {
            // Fallback Blue Disc if camera is idle
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(0.1F, 0.5F, 1.0F, 0.9F);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3f(0.0F, 0.0F, 0.0F);
            for (int i = 0; i <= 32; i++) {
                double a = i * Math.PI * 2.0 / 32;
                GL11.glVertex3f(
                    (float) (Math.cos(a) * quadRadiusInBlocks),
                    (float) (Math.sin(a) * quadRadiusInBlocks),
                    0.0F);
            }
            GL11.glEnd();

            GL11.glColor4f(0.4F, 0.8F, 1.0F, 1.0F);
            GL11.glLineWidth(2.0F);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 32; i++) {
                double a = i * Math.PI * 2.0 / 32;
                GL11.glVertex3f(
                    (float) (Math.cos(a) * quadRadiusInBlocks),
                    (float) (Math.sin(a) * quadRadiusInBlocks),
                    0.0F);
            }
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        GL11.glPopMatrix(); // RESTORE WEBCAM MATRIX
        GL11.glPopMatrix(); // RESTORE ROOT MATRIX

        GL11.glEnable(GL11.GL_CULL_FACE);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // ======== Dropdown Popup Render ========
    private void drawInlineCameraDropdown(int cx, int mouseX, int mouseY) {
        int boxX = cx - 110, boxY = 50, boxWidth = 220, boxHeight = 150;

        drawRect(0, 0, this.width, this.height, 0xFF000000);
        drawRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF101010);
        drawRect(boxX + 1, boxY + 1, boxX + boxWidth - 1, boxY + boxHeight - 1, 0xFF303030);
        drawRect(boxX + 3, boxY + 3, boxX + boxWidth - 3, boxY + 20, 0xFF181818);
        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a7l\u00a76" + StatCollector.translateToLocal("gui.freakcam.select_webcam"),
            boxX + boxWidth / 2,
            boxY + 6,
            0xFFFFFF);

        String currentCam = FreakCamConfig.getSelectedCamera();
        int itemY = boxY + 22, itemHeight = 18;
        for (int i = 0; i < availableCameras.size(); i++) {
            if (itemY + itemHeight > boxY + boxHeight - 28) break;
            boolean isHover = mouseX >= boxX + 6 && mouseX <= boxX + boxWidth - 6
                && mouseY >= itemY
                && mouseY < itemY + itemHeight;
            boolean isSel = (i == selectedDropdownIndex);
            boolean isCurrent = availableCameras.get(i)
                .equals(currentCam);

            int bgCol = isSel ? 0xFF006600 : (isHover ? 0xFF3D3D3D : 0xFF222222);
            if (isCurrent && !isSel) bgCol = 0xFF333300;
            drawRect(boxX + 6, itemY, boxX + boxWidth - 6, itemY + itemHeight - 2, bgCol);

            String name = availableCameras.get(i);
            if (name.length() > 26) name = name.substring(0, 23) + "...";
            String prefix = isSel ? "\u00a7a\u00a7l> " : (isCurrent ? "\u00a7e" : "\u00a7f");
            String activeStr = StatCollector.translateToLocal("gui.freakcam.active");
            String suffix = isCurrent ? " \u00a76[" + activeStr + "\u00a76]" : "";
            this.drawString(this.fontRendererObj, prefix + name + suffix, boxX + 10, itemY + 3, 0xFFFFFF);
            itemY += itemHeight;
        }

        int btnY = boxY + boxHeight - 24, btnW = (boxWidth - 16) / 2;
        boolean selHover = mouseX >= boxX + 6 && mouseX <= boxX + 6 + btnW && mouseY >= btnY && mouseY <= btnY + 20;
        boolean canHover = mouseX >= boxX + 10 + btnW && mouseX <= boxX + 10 + 2 * btnW
            && mouseY >= btnY
            && mouseY <= btnY + 20;
        drawRect(boxX + 6, btnY, boxX + 6 + btnW, btnY + 20, selHover ? 0xFF008800 : 0xFF005500);
        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.select"),
            boxX + 6 + btnW / 2,
            btnY + 6,
            0xFFFFFF);
        drawRect(boxX + 10 + btnW, btnY, boxX + 10 + 2 * btnW, btnY + 20, canHover ? 0xFF880000 : 0xFF550000);
        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.cancel"),
            boxX + 10 + btnW + btnW / 2,
            btnY + 6,
            0xFFFFFF);
    }

    private void drawInlineResolutionDropdown(int cx, int mouseX, int mouseY) {
        int sy = 40 + 48; // Position of the resolution button
        int boxX = cx - 110, boxY = sy + 22, boxWidth = 220, boxHeight = 150;

        drawRect(0, 0, this.width, this.height, 0xFF000000);
        drawRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF101010);
        drawRect(boxX + 1, boxY + 1, boxX + boxWidth - 1, boxY + boxHeight - 1, 0xFF303030);
        drawRect(boxX + 3, boxY + 3, boxX + boxWidth - 3, boxY + 20, 0xFF181818);
        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a7l\u00a76" + StatCollector.translateToLocal("gui.freakcam.select_res"),
            boxX + boxWidth / 2,
            boxY + 6,
            0xFFFFFF);

        int itemY = boxY + 22, itemHeight = 18;
        for (int i = resolutionScrollOffset; i < availableResolutions.size(); i++) {
            if (itemY + itemHeight > boxY + boxHeight - 28) break;
            boolean isHover = mouseX >= boxX + 6 && mouseX <= boxX + boxWidth - 6
                && mouseY >= itemY
                && mouseY < itemY + itemHeight;
            boolean isSel = (i == resolutionIndex);

            int bgCol = isSel ? 0xFF006600 : (isHover ? 0xFF3D3D3D : 0xFF222222);
            drawRect(boxX + 6, itemY, boxX + boxWidth - 6, itemY + itemHeight - 2, bgCol);

            String name = availableResolutions.get(i);
            String prefix = isSel ? "\u00a7a\u00a7l> " : "\u00a7f";
            this.drawString(this.fontRendererObj, prefix + name, boxX + 10, itemY + 3, 0xFFFFFF);
            itemY += itemHeight;
        }

        int btnY = boxY + boxHeight - 24, btnW = (boxWidth - 16) / 2;
        boolean canHover = mouseX >= boxX + 10 + btnW && mouseX <= boxX + 10 + 2 * btnW
            && mouseY >= btnY
            && mouseY <= btnY + 20;
        drawRect(boxX + 10 + btnW, btnY, boxX + 10 + 2 * btnW, btnY + 20, canHover ? 0xFF880000 : 0xFF550000);
        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.cancel"),
            boxX + 10 + btnW + btnW / 2,
            btnY + 6,
            0xFFFFFF);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (currentTab == 0 && fpsField != null && fpsField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        if (currentTab == 1) {
            if (heightField != null && heightField.textboxKeyTyped(typedChar, keyCode)) {
                try {
                    float val = Float.parseFloat(
                        heightField.getText()
                            .replace(',', '.'));
                    FreakCamConfig.setHeightOffset(val);
                    if (heightSlider != null) heightSlider.setValue(val);
                } catch (Exception e) {}
                return;
            }
            if (scaleField != null && scaleField.textboxKeyTyped(typedChar, keyCode)) {
                try {
                    float val = Float.parseFloat(
                        scaleField.getText()
                            .replace(',', '.'));
                    FreakCamConfig.setWebcamScale(val);
                    if (scaleSlider != null) scaleSlider.setValue(val);
                } catch (Exception e) {}
                return;
            }
        }
        if (currentTab == 2 && offScaleField != null && offScaleField.textboxKeyTyped(typedChar, keyCode)) {
            try {
                float val = Float.parseFloat(
                    offScaleField.getText()
                        .replace(',', '.'));
                FreakCamConfig.setOffIconScale(val);
                if (offScaleSlider != null) offScaleSlider.setValue(val);
            } catch (Exception e) {}
            return;
        }
        if (currentTab == 3) {
            if (borderWidthField != null && borderWidthField.textboxKeyTyped(typedChar, keyCode)) {
                try {
                    float val = Float.parseFloat(
                        borderWidthField.getText()
                            .replace(',', '.'));
                    FreakCamConfig.setWebcamBorderWidth(val);
                    if (borderWidthSlider != null) borderWidthSlider.setValue(val);
                } catch (Exception e) {}
                return;
            }
            if (chromaTolField != null && chromaTolField.textboxKeyTyped(typedChar, keyCode)) {
                try {
                    float val = Float.parseFloat(
                        chromaTolField.getText()
                            .replace(',', '.'));
                    FreakCamConfig.setChromaKeyTolerance(val);
                    if (chromaTolSlider != null) chromaTolSlider.setValue(val);
                } catch (Exception e) {}
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    // ======== OFF ICON TAB CONTENT ========
    private int getOffIconPreviewBoxY() {
        return 48;
    }

    private int getOffIconPreviewBoxH() {
        int bottomReserve = 118;
        int topOffset = getOffIconPreviewBoxY();
        return Math.max(60, this.height - topOffset - bottomReserve);
    }

    private int getOffIconPreviewBoxW() {
        return (int) (getOffIconPreviewBoxH() * 1.7777778f);
    }

    private int getOffIconPreviewBoxX(int cx) {
        return cx - getOffIconPreviewBoxW() / 2;
    }

    private void drawOffIconTabContent(int cx, int sy, int mouseX, int mouseY) {
        int pBoxW = getOffIconPreviewBoxW();
        int pBoxH = getOffIconPreviewBoxH();
        int pBoxX = getOffIconPreviewBoxX(cx);
        int pBoxY = getOffIconPreviewBoxY();

        drawRect(pBoxX - 2, pBoxY - 2, pBoxX + pBoxW + 2, pBoxY + pBoxH + 2, 0xFF444455);
        drawRect(pBoxX, pBoxY, pBoxX + pBoxW, pBoxY + pBoxH, 0xFF141820);

        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a77" + StatCollector.translateToLocal("gui.freakcam.off_icon_hint"),
            pBoxX + pBoxW / 2,
            pBoxY + 4,
            0x666688);

        float scale = FreakCamConfig.getOffIconScale();
        float baseIconR = 12.0f * scale;

        float iconXInBox = pBoxX + baseIconR + (pBoxW - baseIconR * 2) * FreakCamConfig.getOffIconX();
        float iconYInBox = pBoxY + baseIconR + (pBoxH - baseIconR * 2) * FreakCamConfig.getOffIconY();

        if (isDraggingOffIcon) {
            float newNormX = (mouseX - pBoxX - baseIconR) / Math.max(1.0f, (pBoxW - baseIconR * 2));
            float newNormY = (mouseY - pBoxY - baseIconR) / Math.max(1.0f, (pBoxH - baseIconR * 2));
            FreakCamConfig.setOffIconX(newNormX);
            FreakCamConfig.setOffIconY(newNormY);
            iconXInBox = pBoxX + baseIconR + (pBoxW - baseIconR * 2) * FreakCamConfig.getOffIconX();
            iconYInBox = pBoxY + baseIconR + (pBoxH - baseIconR * 2) * FreakCamConfig.getOffIconY();
        }

        if (FreakCamConfig.isShowOffIconHud()) {
            ClientHandler.drawOffWebcamIcon(
                iconXInBox,
                iconYInBox,
                baseIconR,
                1.0f,
                FreakCamConfig.getOffIconStyle(),
                FreakCamConfig.isOffIconShadow());
        } else {
            this.drawCenteredString(
                this.fontRendererObj,
                "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off"),
                pBoxX + pBoxW / 2,
                pBoxY + pBoxH / 2 - 4,
                0xFF5555);
        }
    }

    // ======== Labels ========
    private String getCameraLabel() {
        String name = FreakCamConfig.getSelectedCamera();
        if (name == null || name.isEmpty()) name = StatCollector.translateToLocal("gui.freakcam.camera.none");
        if (name.length() > 24) name = name.substring(0, 21) + "...";
        return StatCollector.translateToLocal("gui.freakcam.camera") + "\u00a7a" + name;
    }

    private String getWebcamLabel() {
        String status = FreakCamConfig.isWebcamEnabled() ? "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.on")
            : "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off");
        return StatCollector.translateToLocal("gui.freakcam.webcam") + status;
    }

    private String getResolutionLabel() {
        if (availableResolutions.isEmpty()) return StatCollector.translateToLocal("gui.freakcam.res") + "\u00a7c"
            + StatCollector.translateToLocal("gui.freakcam.unknown");
        return StatCollector.translateToLocal("gui.freakcam.res") + "\u00a7b"
            + availableResolutions.get(resolutionIndex);
    }

    private String getMirrorLabel() {
        String status = FreakCamConfig.isMirrorWebcam() ? "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.on")
            : "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off");
        return StatCollector.translateToLocal("gui.freakcam.mirror") + status;
    }

    private String getNameTagLabel() {
        String status = FreakCamConfig.isShowNameTagInPreview()
            ? "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.on")
            : "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off");
        return StatCollector.translateToLocal("gui.freakcam.nametag") + status;
    }

    private String getOffIconHudLabel() {
        String status = FreakCamConfig.isShowOffIconHud()
            ? "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.on")
            : "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off");
        return StatCollector.translateToLocal("gui.freakcam.off_icon_hud") + status;
    }

    private String getOffIcon3DLabel() {
        String status = FreakCamConfig.isShowOffIcon3D() ? "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.on")
            : "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off");
        return StatCollector.translateToLocal("gui.freakcam.off_icon_3d") + status;
    }

    private String getOffIconShadowLabel() {
        String status = FreakCamConfig.isOffIconShadow() ? "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.on")
            : "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off");
        return StatCollector.translateToLocal("gui.freakcam.off_icon_shadow") + status;
    }

    private String getOffIconStyleLabel() {
        String style = (FreakCamConfig.getOffIconStyle() == 0)
            ? "\u00a7e" + StatCollector.translateToLocal("gui.freakcam.style_circle")
            : "\u00a7b" + StatCollector.translateToLocal("gui.freakcam.style_camera");
        return StatCollector.translateToLocal("gui.freakcam.off_icon_style") + style;
    }

    private String getShapeLabel() {
        int shape = FreakCamConfig.getWebcamFrameShape();
        String shapeName = StatCollector.translateToLocal("gui.freakcam.shape." + shape);
        return StatCollector.translateToLocal("gui.freakcam.shape") + "\u00a7e" + shapeName;
    }

    private String getBorderToggleLabel() {
        String status = FreakCamConfig.isWebcamBorderEnabled()
            ? "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.on")
            : "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off");
        return StatCollector.translateToLocal("gui.freakcam.border") + status;
    }

    private String getBorderModeLabel() {
        int mode = FreakCamConfig.getWebcamBorderMode();
        String modeName = StatCollector.translateToLocal("gui.freakcam.border_mode." + mode);
        return StatCollector.translateToLocal("gui.freakcam.border_mode") + (mode == 1 ? "\u00a7d" : "\u00a7b")
            + modeName;
    }

    private String getBorderColorLabel() {
        int mode = FreakCamConfig.getWebcamBorderMode();
        if (mode == 1) {
            return StatCollector.translateToLocal("gui.freakcam.border_color") + "\u00a7dПульсация";
        } else if (mode == 2) {
            return StatCollector.translateToLocal("gui.freakcam.border_color") + "\u00a7bСпектр";
        }
        int color = FreakCamConfig.getWebcamBorderColor();
        String colorName = "Custom";
        if (color == 0x00FFCC) colorName = "Aqua";
        else if (color == 0x00FF00) colorName = "Green";
        else if (color == 0xFF2222) colorName = "Red";
        else if (color == 0xFFD700) colorName = "Gold";
        else if (color == 0xFFFFFF) colorName = "White";
        else if (color == 0xAA00FF) colorName = "Purple";
        return StatCollector.translateToLocal("gui.freakcam.border_color") + "\u00a7e" + colorName;
    }

    private String getChromaToggleLabel() {
        String status = FreakCamConfig.isChromaKeyEnabled()
            ? "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.on")
            : "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off");
        return StatCollector.translateToLocal("gui.freakcam.chroma") + status;
    }

    private String getChromaColorLabel() {
        int color = FreakCamConfig.getChromaKeyColor();
        String colorName = StatCollector.translateToLocal("gui.freakcam.chroma_color.green");
        if (color == 0x0000FF) colorName = StatCollector.translateToLocal("gui.freakcam.chroma_color.blue");
        else if (color == 0x000000) colorName = StatCollector.translateToLocal("gui.freakcam.chroma_color.black");
        return StatCollector.translateToLocal("gui.freakcam.chroma_color") + "\u00a7b" + colorName;
    }

    private void drawFxTabContent(int cx, int sy, int mouseX, int mouseY) {
        int cardY = 144;
        int cardH = Math.max(80, this.height - cardY - 30);
        int cardW = Math.min(this.width - 20, 500);
        int cardX = cx - cardW / 2;

        drawRect(cardX - 2, cardY - 2, cardX + cardW + 2, cardY + cardH + 2, 0xFF444455);
        drawRect(cardX, cardY, cardX + cardW, cardY + cardH, 0xFF141820);

        int halfW = cardW / 2 - 6;

        // 1. LEFT VIEWPORT: 3D Interactive Player Viewport
        int leftX = cardX + 4;
        int leftY = cardY + 4;
        int leftW = halfW;
        int leftH = cardH - 8;

        drawRect(leftX, leftY, leftX + leftW, leftY + leftH, 0xFF0D1016);
        this.drawCenteredString(this.fontRendererObj, "\u00a7b3D Игрок", leftX + leftW / 2, leftY + 4, 0xFFFFFF);

        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(
            this.mc,
            this.mc.displayWidth,
            this.mc.displayHeight);
        int sf = sr.getScaleFactor();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(leftX * sf, (this.height - (leftY + leftH)) * sf, leftW * sf, leftH * sf);

        EntityPlayer player = this.mc.thePlayer;
        if (player != null) {
            float playerScale = (leftH / 2.0f) * 0.90f;
            draw3DPlayerViewport(
                leftX + leftW / 2,
                leftY + leftH - 12,
                (int) playerScale,
                previewYaw,
                previewPitch,
                player);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // 2. RIGHT VIEWPORT: Pure 2D Large Camera Viewport ("чисто большая камера, но ПО ФОРМЕ!")
        int rightX = cardX + cardW / 2 + 2;
        int rightY = cardY + 4;
        int rightW = halfW;
        int rightH = cardH - 8;

        drawRect(rightX, rightY, rightX + rightW, rightY + rightH, 0xFF0D1016);
        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a7eКамера (По форме)",
            rightX + rightW / 2,
            rightY + 4,
            0xFFFFFF);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(rightX * sf, (this.height - (rightY + rightH)) * sf, rightW * sf, rightH * sf);

        int camSize = Math.min(rightW - 16, rightH - 24) / 2;
        int camCenterX = rightX + rightW / 2;
        int camCenterY = rightY + 16 + (rightH - 16) / 2;

        if (liveDynamicTexture != null && FreakCamConfig.isWebcamEnabled()) {
            GL11.glPushMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, liveDynamicTexture.getGlTextureId());

            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(camCenterX - camSize, camCenterY - camSize, 0, 0, 0);
            tess.addVertexWithUV(camCenterX - camSize, camCenterY + camSize, 0, 0, 1);
            tess.addVertexWithUV(camCenterX + camSize, camCenterY + camSize, 0, 1, 1);
            tess.addVertexWithUV(camCenterX + camSize, camCenterY - camSize, 0, 1, 0);
            tess.draw();
            GL11.glPopMatrix();

            if (FreakCamConfig.isWebcamBorderEnabled()) {
                draw2DFrameBorder(camCenterX, camCenterY, camSize, camSize);
            }
        } else {
            this.drawCenteredString(
                this.fontRendererObj,
                "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.off"),
                camCenterX,
                camCenterY - 4,
                0xFF5555);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void draw2DFrameBorder(float centerX, float centerY, float sizeW, float sizeH) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);

        float borderWidth = FreakCamConfig.getWebcamBorderWidth();
        GL11.glLineWidth(borderWidth);

        int mode = FreakCamConfig.getWebcamBorderMode();
        int shape = FreakCamConfig.getWebcamFrameShape();
        int solidColor = ClientHandler.getBorderRGB(0);

        java.util.List<float[]> vertices = new java.util.ArrayList<>();

        if (shape == 1) { // Circle
            int steps = 48;
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2.0 / steps;
                vertices.add(
                    new float[] { (float) (centerX + Math.cos(a) * sizeW), (float) (centerY + Math.sin(a) * sizeH) });
            }
        } else if (shape == 2) { // Rounded Rectangle
            float r = 0.25f * Math.min(sizeW, sizeH);
            int arcSteps = 8;
            for (int i = 0; i <= arcSteps; i++) {
                double a = i * (Math.PI / 2.0) / arcSteps;
                vertices.add(
                    new float[] { (float) (centerX + sizeW - r + Math.cos(a) * r),
                        (float) (centerY - sizeH + r - Math.sin(a) * r) });
            }
            for (int i = 0; i <= arcSteps; i++) {
                double a = Math.PI / 2.0 + i * (Math.PI / 2.0) / arcSteps;
                vertices.add(
                    new float[] { (float) (centerX - sizeW + r + Math.cos(a) * r),
                        (float) (centerY - sizeH + r - Math.sin(a) * r) });
            }
            for (int i = 0; i <= arcSteps; i++) {
                double a = Math.PI + i * (Math.PI / 2.0) / arcSteps;
                vertices.add(
                    new float[] { (float) (centerX - sizeW + r + Math.cos(a) * r),
                        (float) (centerY + sizeH - r - Math.sin(a) * r) });
            }
            for (int i = 0; i <= arcSteps; i++) {
                double a = 1.5 * Math.PI + i * (Math.PI / 2.0) / arcSteps;
                vertices.add(
                    new float[] { (float) (centerX + sizeW - r + Math.cos(a) * r),
                        (float) (centerY + sizeH - r - Math.sin(a) * r) });
            }
        } else if (shape == 3) { // Diamond
            vertices.add(new float[] { centerX, centerY - sizeH });
            vertices.add(new float[] { centerX + sizeW, centerY });
            vertices.add(new float[] { centerX, centerY + sizeH });
            vertices.add(new float[] { centerX - sizeW, centerY });
        } else { // Rectangle
            vertices.add(new float[] { centerX - sizeW, centerY - sizeH });
            vertices.add(new float[] { centerX + sizeW, centerY - sizeH });
            vertices.add(new float[] { centerX + sizeW, centerY + sizeH });
            vertices.add(new float[] { centerX - sizeW, centerY + sizeH });
        }

        GL11.glBegin(GL11.GL_LINE_LOOP);
        int total = vertices.size();
        long time = System.currentTimeMillis();
        for (int i = 0; i < total; i++) {
            float[] v = vertices.get(i);
            if (mode == 2) { // Spectrum Rainbow - spatial angle sync!
                double angle = Math.atan2(v[1] - centerY, v[0] - centerX);
                float hue = (float) ((((angle + Math.PI) / (2.0 * Math.PI)) + (time % 3000L) / 3000.0f) % 1.0f);
                int c = java.awt.Color.HSBtoRGB(hue, 0.95f, 1.0f);
                float cr = ((c >> 16) & 0xFF) / 255.0f;
                float cg = ((c >> 8) & 0xFF) / 255.0f;
                float cb = (c & 0xFF) / 255.0f;
                GL11.glColor4f(cr, cg, cb, 0.95f);
            } else {
                float cr = ((solidColor >> 16) & 0xFF) / 255.0f;
                float cg = ((solidColor >> 8) & 0xFF) / 255.0f;
                float cb = (solidColor & 0xFF) / 255.0f;
                GL11.glColor4f(cr, cg, cb, 0.95f);
            }
            GL11.glVertex2f(v[0], v[1]);
        }
        GL11.glEnd();

        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
