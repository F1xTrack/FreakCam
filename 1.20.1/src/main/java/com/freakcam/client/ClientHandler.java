package com.freakcam.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.freakcam.network.PacketHandler;

import com.freakcam.network.VideoFrameClientMessage;
import com.freakcam.network.VideoFrameMessage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler {
    private static WebcamCapture capture;
    private static DynamicTexture localDynamicTexture;
    private static ResourceLocation localTextureLocation;
    private static int latestFrameWidth = 640;
    private static int latestFrameHeight = 480;

    private static final Map<Integer, DynamicTexture> remoteTextures = new ConcurrentHashMap<>();
    private static final Map<Integer, ResourceLocation> remoteLocations = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> playerFrameShapes = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> playerBorderEnabled = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> playerBorderWidths = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> playerBorderModes = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> playerBorderColors = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> playerChromaEnabled = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> playerChromaColors = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> playerChromaTolerances = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> playerWebcamScales = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> playerHeightOffsets = new ConcurrentHashMap<>();

    private static long lastFrameTime = 0;

    public static void init() {
        if (FreakCamConfig.isWebcamEnabled()) {
            startWebcam();
        }
    }

    public static void startWebcam() {
        if (capture == null) {
            capture = new WebcamCapture();
        }
        capture.start(FreakCamConfig.getSelectedCamera(), FreakCamConfig.getCaptureWidth(), FreakCamConfig.getCaptureHeight(), FreakCamConfig.getCaptureFps());
    }

    public static void stopWebcam() {
        if (capture != null) {
            capture.stop();
        }
    }

    public static void restartWebcam() {
        stopWebcam();
        startWebcam();
    }

    public static WebcamCapture getCapture() {
        return capture;
    }

    public static int getLatestFrameWidth() {
        return latestFrameWidth;
    }

    public static int getLatestFrameHeight() {
        return latestFrameHeight;
    }

    public static ResourceLocation getLocalTextureLocation() {
        return localTextureLocation;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (capture != null && capture.isRunning()) {
            BufferedImage rawImg = capture.getLatestFrame();
            long count = capture.getFrameCount();
            if (rawImg != null && count > lastFrameTime) {
                lastFrameTime = count;
                latestFrameWidth = rawImg.getWidth();
                latestFrameHeight = rawImg.getHeight();

                BufferedImage processed = processFrame(rawImg);
                updateLocalTexture(processed);

                // Send frame to server
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(processed, "JPG", baos);
                    byte[] jpegBytes = baos.toByteArray();

                    VideoFrameMessage msg = new VideoFrameMessage(
                        jpegBytes, processed.getWidth(), processed.getHeight(),
                        FreakCamConfig.getWebcamFrameShape(),
                        FreakCamConfig.isWebcamBorderEnabled(),
                        FreakCamConfig.getWebcamBorderWidth(),
                        FreakCamConfig.getWebcamBorderMode(),
                        FreakCamConfig.getWebcamBorderColor(),
                        FreakCamConfig.isChromaKeyEnabled(),
                        FreakCamConfig.getChromaKeyColor(),
                        FreakCamConfig.getChromaKeyTolerance(),
                        FreakCamConfig.getWebcamScale(),
                        FreakCamConfig.getHeightOffset()
                    );
                    PacketHandler.INSTANCE.sendToServer(msg);
                } catch (Exception e) {}
            }
        }
    }

    private static BufferedImage processFrame(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);

        applyPixelMaskAndChromaKey(pixels, w, h,
            FreakCamConfig.getWebcamFrameShape(),
            FreakCamConfig.isChromaKeyEnabled(),
            FreakCamConfig.getChromaKeyColor(),
            FreakCamConfig.getChromaKeyTolerance());

        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    public static void applyPixelMaskAndChromaKey(int[] pixels, int imgW, int imgH, int shape, boolean chromaOn, int chromaRGB, float chromaTol) {
        float centerX = imgW / 2.0f;
        float centerY = imgH / 2.0f;
        float cr = ((chromaRGB >> 16) & 0xFF) / 255.0f;
        float cg = ((chromaRGB >> 8) & 0xFF) / 255.0f;
        float cb = (chromaRGB & 0xFF) / 255.0f;

        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                int idx = y * imgW + x;
                int pixel = pixels[idx];
                float nx = (x - centerX) / centerX;
                float ny = (y - centerY) / centerY;
                int alpha = 0xFF;

                if (shape == 1) { // Circle
                    float distSq = nx * nx + ny * ny;
                    if (distSq > 1.0f) {
                        alpha = 0x00;
                    }
                } else if (shape == 2) { // Rounded Rectangle
                    float cornerR = 0.25f;
                    float ax = Math.abs(nx);
                    float ay = Math.abs(ny);
                    if (ax > (1.0f - cornerR) && ay > (1.0f - cornerR)) {
                        float cdx = ax - (1.0f - cornerR);
                        float cdy = ay - (1.0f - cornerR);
                        if (cdx * cdx + cdy * cdy > cornerR * cornerR) {
                            alpha = 0x00;
                        }
                    }
                } else if (shape == 3) { // Diamond
                    if (Math.abs(nx) + Math.abs(ny) > 1.0f) {
                        alpha = 0x00;
                    }
                }

                if (chromaOn && alpha > 0) {
                    float pr = ((pixel >> 16) & 0xFF) / 255.0f;
                    float pg = ((pixel >> 8) & 0xFF) / 255.0f;
                    float pb = (pixel & 0xFF) / 255.0f;
                    float diff = (float) Math.sqrt((pr - cr) * (pr - cr) + (pg - cg) * (pg - cg) + (pb - cb) * (pb - cb));
                    if (diff < chromaTol) {
                        alpha = 0x00;
                    }
                }

                pixels[idx] = (alpha << 24) | (pixel & 0xFFFFFF);
            }
        }
    }

    private static void updateLocalTexture(BufferedImage img) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (localDynamicTexture == null) {
                localDynamicTexture = new DynamicTexture(img.getWidth(), img.getHeight(), true);
                localTextureLocation = mc.getTextureManager().register("freakcam_local", localDynamicTexture);
            }
            copyImageToDynamicTexture(img, localDynamicTexture);
        });
    }

    public static void onRemoteVideoFrameReceived(VideoFrameClientMessage msg) {
        playerFrameShapes.put(msg.entityId, msg.frameShape);
        playerBorderEnabled.put(msg.entityId, msg.borderEnabled);
        playerBorderWidths.put(msg.entityId, msg.borderWidth);
        playerBorderModes.put(msg.entityId, msg.borderMode);
        playerBorderColors.put(msg.entityId, msg.borderColor);
        playerChromaEnabled.put(msg.entityId, msg.chromaEnabled);
        playerChromaColors.put(msg.entityId, msg.chromaColor);
        playerChromaTolerances.put(msg.entityId, msg.chromaTolerance);
        playerWebcamScales.put(msg.entityId, msg.webcamScale);
        playerHeightOffsets.put(msg.entityId, msg.heightOffset);

        if (msg.jpegData != null && msg.jpegData.length > 0) {
            try {
                BufferedImage rawImg = ImageIO.read(new ByteArrayInputStream(msg.jpegData));
                if (rawImg != null) {
                    Minecraft.getInstance().execute(() -> {
                        DynamicTexture tex = remoteTextures.computeIfAbsent(msg.entityId, id -> {
                            DynamicTexture newTex = new DynamicTexture(rawImg.getWidth(), rawImg.getHeight(), true);
                            ResourceLocation loc = Minecraft.getInstance().getTextureManager().register("freakcam_remote_" + id, newTex);
                            remoteLocations.put(id, loc);
                            return newTex;
                        });
                        copyImageToDynamicTexture(rawImg, tex);
                    });
                }
            } catch (Exception e) {}
        }
    }

    private static void copyImageToDynamicTexture(BufferedImage img, DynamicTexture tex) {
        var nativeImg = tex.getPixels();
        if (nativeImg != null) {
            int w = Math.min(img.getWidth(), nativeImg.getWidth());
            int h = Math.min(img.getHeight(), nativeImg.getHeight());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    // ABGR format for NativeImage
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    nativeImg.setPixelRGBA(x, y, abgr);
                }
            }
            tex.upload();
        }
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (player == null) return;
        int entityId = player.getId();

        ResourceLocation loc = (player == Minecraft.getInstance().player)
            ? localTextureLocation
            : remoteLocations.get(entityId);

        if (loc == null && player == Minecraft.getInstance().player && !FreakCamConfig.isShowOffIcon3D()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        float userScale = playerWebcamScales.getOrDefault(entityId, FreakCamConfig.getWebcamScale());
        float heightOff = playerHeightOffsets.getOrDefault(entityId, FreakCamConfig.getHeightOffset());

        poseStack.pushPose();
        poseStack.translate(0.0D, player.getBbHeight() + heightOff, 0.0D);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float scale = 0.033333f * userScale;
        poseStack.scale(-scale, -scale, scale);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (loc != null) {
            RenderSystem.setShaderTexture(0, loc);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            float size = 20.0f;
            builder.vertex(poseStack.last().pose(), -size, -size, 0).uv(0.0f, 0.0f).endVertex();
            builder.vertex(poseStack.last().pose(), -size, size, 0).uv(0.0f, 1.0f).endVertex();
            builder.vertex(poseStack.last().pose(), size, size, 0).uv(1.0f, 1.0f).endVertex();
            builder.vertex(poseStack.last().pose(), size, -size, 0).uv(1.0f, 0.0f).endVertex();
            tesselator.end();
        }

        poseStack.popPose();
    }
}
