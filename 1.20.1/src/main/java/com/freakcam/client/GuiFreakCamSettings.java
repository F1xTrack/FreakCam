package com.freakcam.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GuiFreakCamSettings extends Screen {
    private final Screen parentScreen;
    private int currentTab = 0; // 0=Crop, 1=Position, 2=OffIcon, 3=FX

    private Button btnTabCrop, btnTabPos, btnTabOff, btnTabFX;
    private Button btnCamSelect, btnWebcamToggle, btnResolution, btnMirrorToggle, btnCropReset;
    private Button btnNameTagToggle, btnOffIconHud, btnOffIcon3D, btnOffIconStyle, btnOffIconShadow, btnResetIcon;
    private Button btnShapeToggle, btnBorderToggle, btnBorderColorToggle, btnChromaToggle, btnChromaColorToggle;

    private List<String> availableCameras = new ArrayList<>();
    private List<String> availableResolutions = new ArrayList<>();
    private int resolutionIndex = 0;

    public GuiFreakCamSettings(Screen parent) {
        super(Component.literal("FreakCam Settings"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        availableCameras = WebcamCapture.listDevices();
        availableResolutions = WebcamCapture.listResolutions(FreakCamConfig.getSelectedCamera());

        int cx = this.width / 2;
        int tabW = 100;
        int tabY = 10;

        btnTabCrop = addRenderableWidget(Button.builder(Component.translatable("gui.freakcam.tab_crop"), btn -> { currentTab = 0; rebuildButtons(); }).bounds(cx - 205, tabY, tabW, 20).build());
        btnTabPos = addRenderableWidget(Button.builder(Component.translatable("gui.freakcam.tab_pos"), btn -> { currentTab = 1; rebuildButtons(); }).bounds(cx - 102, tabY, tabW, 20).build());
        btnTabOff = addRenderableWidget(Button.builder(Component.translatable("gui.freakcam.tab_off"), btn -> { currentTab = 2; rebuildButtons(); }).bounds(cx + 2, tabY, tabW, 20).build());
        btnTabFX = addRenderableWidget(Button.builder(Component.translatable("gui.freakcam.tab_fx"), btn -> { currentTab = 3; rebuildButtons(); }).bounds(cx + 105, tabY, tabW, 20).build());

        // Done button
        addRenderableWidget(Button.builder(Component.translatable("gui.freakcam.done"), btn -> this.minecraft.setScreen(this.parentScreen)).bounds(cx - 75, this.height - 25, 150, 20).build());

        rebuildButtons();
    }

    private void rebuildButtons() {
        int cx = this.width / 2;
        boolean isCrop = (currentTab == 0);
        boolean isPos = (currentTab == 1);
        boolean isOff = (currentTab == 2);
        boolean isFx = (currentTab == 3);

        if (isCrop) {
            btnCamSelect = addRenderableWidget(Button.builder(Component.literal(getCamSelectLabel()), btn -> cycleCamera()).bounds(cx - 150, 40, 148, 20).build());
            btnWebcamToggle = addRenderableWidget(Button.builder(Component.literal(getWebcamToggleLabel()), btn -> { FreakCamConfig.setWebcamEnabled(!FreakCamConfig.isWebcamEnabled()); if (FreakCamConfig.isWebcamEnabled()) ClientHandler.startWebcam(); else ClientHandler.stopWebcam(); rebuildButtons(); }).bounds(cx + 2, 40, 148, 20).build());
            btnResolution = addRenderableWidget(Button.builder(Component.literal(getResolutionLabel()), btn -> cycleResolution()).bounds(cx - 150, 64, 148, 20).build());
            btnMirrorToggle = addRenderableWidget(Button.builder(Component.literal(getMirrorToggleLabel()), btn -> { FreakCamConfig.setMirrorWebcam(!FreakCamConfig.isMirrorWebcam()); rebuildButtons(); }).bounds(cx + 2, 64, 148, 20).build());
            btnCropReset = addRenderableWidget(Button.builder(Component.translatable("gui.freakcam.crop_reset"), btn -> { FreakCamConfig.resetCrop(); rebuildButtons(); }).bounds(cx - 150, 88, 300, 20).build());
        } else if (isFx) {
            btnShapeToggle = addRenderableWidget(Button.builder(Component.literal(getShapeLabel()), btn -> { FreakCamConfig.setWebcamFrameShape((FreakCamConfig.getWebcamFrameShape() + 1) % 4); rebuildButtons(); }).bounds(cx - 150, 40, 300, 20).build());
            btnBorderToggle = addRenderableWidget(Button.builder(Component.literal(getBorderToggleLabel()), btn -> { FreakCamConfig.setWebcamBorderEnabled(!FreakCamConfig.isWebcamBorderEnabled()); rebuildButtons(); }).bounds(cx - 150, 64, 148, 20).build());
            btnBorderColorToggle = addRenderableWidget(Button.builder(Component.literal(getBorderColorLabel()), btn -> cycleBorderColor()).bounds(cx + 2, 64, 148, 20).build());
            btnChromaToggle = addRenderableWidget(Button.builder(Component.literal(getChromaToggleLabel()), btn -> { FreakCamConfig.setChromaKeyEnabled(!FreakCamConfig.isChromaKeyEnabled()); rebuildButtons(); }).bounds(cx - 150, 88, 148, 20).build());
            btnChromaColorToggle = addRenderableWidget(Button.builder(Component.literal(getChromaColorLabel()), btn -> cycleChromaColor()).bounds(cx + 2, 88, 148, 20).build());
        }
    }

    private void cycleCamera() {
        if (availableCameras.isEmpty()) return;
        int idx = availableCameras.indexOf(FreakCamConfig.getSelectedCamera());
        idx = (idx + 1) % availableCameras.size();
        FreakCamConfig.setSelectedCamera(availableCameras.get(idx));
        ClientHandler.restartWebcam();
        rebuildButtons();
    }

    private void cycleResolution() {
        if (availableResolutions.isEmpty()) return;
        resolutionIndex = (resolutionIndex + 1) % availableResolutions.size();
        String[] parts = availableResolutions.get(resolutionIndex).split("x");
        if (parts.length == 2) {
            try {
                FreakCamConfig.setCaptureResolution(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                ClientHandler.restartWebcam();
            } catch (Exception e) {}
        }
        rebuildButtons();
    }

    private void cycleBorderColor() {
        int mode = FreakCamConfig.getWebcamBorderMode();
        if (mode == 1) FreakCamConfig.setWebcamBorderMode(2);
        else if (mode == 2) { FreakCamConfig.setWebcamBorderMode(0); FreakCamConfig.setWebcamBorderColor(0x00FFCC); }
        else {
            int color = FreakCamConfig.getWebcamBorderColor();
            if (color == 0x00FFCC) FreakCamConfig.setWebcamBorderColor(0x00FF00);
            else if (color == 0x00FF00) FreakCamConfig.setWebcamBorderColor(0xFF0000);
            else if (color == 0xFF0000) FreakCamConfig.setWebcamBorderColor(0xFFD700);
            else if (color == 0xFFD700) FreakCamConfig.setWebcamBorderColor(0xFFFFFF);
            else if (color == 0xFFFFFF) FreakCamConfig.setWebcamBorderColor(0xAA00FF);
            else { FreakCamConfig.setWebcamBorderMode(1); }
        }
        rebuildButtons();
    }

    private void cycleChromaColor() {
        int col = FreakCamConfig.getChromaKeyColor();
        if (col == 0x00FF00) FreakCamConfig.setChromaKeyColor(0x0000FF);
        else if (col == 0x0000FF) FreakCamConfig.setChromaKeyColor(0x000000);
        else FreakCamConfig.setChromaKeyColor(0x00FF00);
        rebuildButtons();
    }

    private String getCamSelectLabel() {
        String name = FreakCamConfig.getSelectedCamera();
        if (name.length() > 14) name = name.substring(0, 11) + "...";
        return Component.translatable("gui.freakcam.select_camera").getString() + (name.isEmpty() ? "Default" : name);
    }

    private String getWebcamToggleLabel() {
        return Component.translatable("gui.freakcam.webcam").getString() + (FreakCamConfig.isWebcamEnabled() ? "§aВКЛ" : "§cВЫКЛ");
    }

    private String getResolutionLabel() {
        return Component.translatable("gui.freakcam.resolution").getString() + FreakCamConfig.getCaptureWidth() + "x" + FreakCamConfig.getCaptureHeight();
    }

    private String getMirrorToggleLabel() {
        return Component.translatable("gui.freakcam.mirror").getString() + (FreakCamConfig.isMirrorWebcam() ? "§aВКЛ" : "§cВЫКЛ");
    }

    private String getShapeLabel() {
        int shape = FreakCamConfig.getWebcamFrameShape();
        String shapeName = Component.translatable("gui.freakcam.shape." + shape).getString();
        return Component.translatable("gui.freakcam.shape").getString() + "§e" + shapeName;
    }

    private String getBorderToggleLabel() {
        return Component.translatable("gui.freakcam.border").getString() + (FreakCamConfig.isWebcamBorderEnabled() ? "§aВКЛ" : "§cВЫКЛ");
    }

    private String getBorderColorLabel() {
        int mode = FreakCamConfig.getWebcamBorderMode();
        if (mode == 1) return Component.translatable("gui.freakcam.border_color").getString() + "§dПульсация";
        if (mode == 2) return Component.translatable("gui.freakcam.border_color").getString() + "§bСпектр";
        return Component.translatable("gui.freakcam.border_color").getString() + "§eColor";
    }

    private String getChromaToggleLabel() {
        return Component.translatable("gui.freakcam.chroma").getString() + (FreakCamConfig.isChromaKeyEnabled() ? "§aВКЛ" : "§cВЫКЛ");
    }

    private String getChromaColorLabel() {
        int col = FreakCamConfig.getChromaKeyColor();
        if (col == 0x00FF00) return Component.translatable("gui.freakcam.chroma_color").getString() + "§aЗеленый";
        if (col == 0x0000FF) return Component.translatable("gui.freakcam.chroma_color").getString() + "§9Синий";
        return Component.translatable("gui.freakcam.chroma_color").getString() + "§8Черный";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        guiGraphics.drawCenteredString(this.font, Component.literal("Настройки FreakCam (1.20.1 Forge)"), cx, 1, 0xFFFFFF);
    }
}
