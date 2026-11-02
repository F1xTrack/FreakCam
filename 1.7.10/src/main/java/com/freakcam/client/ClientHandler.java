package com.freakcam.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.freakcam.FreakCam;
import com.freakcam.network.NetworkHandler;
import com.freakcam.network.VideoFrameMessage;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public class ClientHandler {

    private static final Map<Integer, byte[]> pendingNetworkFrames = new ConcurrentHashMap<>();
    private static final Map<Integer, BufferedImage> decodedFrames = new ConcurrentHashMap<>();
    private static final Map<Integer, net.minecraft.client.renderer.texture.DynamicTexture> playerTextures = new ConcurrentHashMap<>();

    static {
        Thread decoder = new Thread(() -> {
            while (true) {
                try {
                    for (Integer senderId : new java.util.ArrayList<>(pendingNetworkFrames.keySet())) {
                        byte[] frame = pendingNetworkFrames.remove(senderId);
                        if (frame != null) {
                            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame));
                            if (image != null) {
                                decodedFrames.put(senderId, image);
                            }
                        }
                    }
                    Thread.sleep(10);
                } catch (Exception e) {}
            }
        }, "FreakCam-Decoder");
        decoder.setDaemon(true);
        decoder.start();
    }
    private static volatile BufferedImage latestRawImage = null;
    // Store the dimensions of the latest captured frame for GUI to use
    private static volatile int latestFrameWidth = 192;
    private static volatile int latestFrameHeight = 144;

    private static WebcamCapture webcamCapture;
    private static Thread captureThread;
    private static volatile boolean capturing = false;

    private static int frameCounter = 0;
    private static final Map<Integer, Integer> latestFrameIds = new ConcurrentHashMap<>();
    private static final Map<Integer, byte[][]> frameAssembly = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> playerLastFrameTime = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> playerHeightOffsets = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> playerWebcamScales = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> playerFrameShapes = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> playerBorderEnabled = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> playerBorderWidths = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> playerBorderModes = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> playerBorderColors = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> playerChromaEnabled = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> playerChromaColors = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> playerChromaTolerances = new ConcurrentHashMap<>();

    public static KeyBinding keySettings;
    public static KeyBinding keyToggleWebcam;

    public static void init() {
        ClientHandler handler = new ClientHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);

        keySettings = new KeyBinding("key.freakcam.settings", Keyboard.KEY_B, "key.categories.freakcam");
        cpw.mods.fml.client.registry.ClientRegistry.registerKeyBinding(keySettings);

        keyToggleWebcam = new KeyBinding("key.freakcam.toggle", Keyboard.KEY_V, "key.categories.freakcam");
        cpw.mods.fml.client.registry.ClientRegistry.registerKeyBinding(keyToggleWebcam);
    }

    public static void onConfigSync(int port, UUID token) {
        if (FreakCamConfig.isWebcamEnabled()) {
            startCapture();
        }
    }

    public static void restartCapture() {
        stopCapture();
        if (FreakCamConfig.isWebcamEnabled()) {
            startCapture();
        }
    }

    public static Integer getPlayerTexture(int entityId) {
        net.minecraft.client.renderer.texture.DynamicTexture tex = playerTextures.get(entityId);
        return tex != null ? tex.getGlTextureId() : -1;
    }

    public static BufferedImage getLatestRawImage() {
        return latestRawImage;
    }

    public static int getLatestFrameWidth() {
        return latestFrameWidth;
    }

    public static int getLatestFrameHeight() {
        return latestFrameHeight;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (keySettings.isPressed()) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new GuiFreakCamSettings(null));
        }
        if (keyToggleWebcam.isPressed()) {
            boolean newState = !FreakCamConfig.isWebcamEnabled();
            FreakCamConfig.setWebcamEnabled(newState);
            FreakCamConfig.save();
            restartCapture();
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        stopCapture();
        decodedFrames.clear();
        playerTextures.clear();
        latestFrameIds.clear();
        frameAssembly.clear();
        latestRawImage = null;
    }

    private static void startCapture() {
        if (capturing) return;
        capturing = true;

        if (FreakCamConfig.isWebcamEnabled()) {
            NetworkHandler.INSTANCE.sendToServer(new com.freakcam.network.VideoToggleMessage(true));
            captureThread = new Thread(() -> {
                webcamCapture = new WebcamCapture();
                String camera = FreakCamConfig.getSelectedCamera();

                if (camera == null || camera.isEmpty()) {
                    List<String> devices = WebcamCapture.listDevices();
                    if (!devices.isEmpty()) {
                        camera = devices.get(0);
                        FreakCamConfig.setSelectedCamera(camera);
                        FreakCamConfig.save();
                        FreakCam.LOG.info("Auto-selected webcam: " + camera);
                    }
                }

                if (camera == null || camera.isEmpty()) {
                    FreakCam.LOG.warn("No webcam device available.");
                } else {
                    FreakCam.LOG.info("Pre-caching resolutions before starting capture...");
                    WebcamCapture.listResolutions(camera); // Pre-cache so GUI doesn't hang later

                    FreakCam.LOG.info("Starting capture thread for device: " + camera);
                    webcamCapture.start(
                        camera,
                        FreakCamConfig.getCaptureWidth(),
                        FreakCamConfig.getCaptureHeight(),
                        FreakCamConfig.getCaptureFps());

                    long lastProcessed = -1;
                    while (capturing && webcamCapture != null && webcamCapture.isRunning()) {
                        try {
                            BufferedImage rawImage = webcamCapture.readFrame();
                            long currentFrame = webcamCapture.getFrameCount();
                            if (rawImage != null && currentFrame != lastProcessed) {
                                lastProcessed = currentFrame;
                                // Real-time horizontal flip if mirror is active (instant, no restart needed)
                                if (FreakCamConfig.isMirrorWebcam()) {
                                    rawImage = flipHorizontally(rawImage);
                                }

                                // Store frame dimensions for GUI
                                latestFrameWidth = rawImage.getWidth();
                                latestFrameHeight = rawImage.getHeight();

                                // Store for GUI live preview
                                latestRawImage = rawImage;

                                // Crop & Zoom: extract square from rectangular image
                                BufferedImage croppedImg = applyCropAndZoom(rawImage);

                                EntityPlayer localPlayer = Minecraft.getMinecraft().thePlayer;
                                if (localPlayer != null) {
                                    decodedFrames.put(localPlayer.getEntityId(), croppedImg);
                                }

                                // Compress with explicit quality to avoid network floods
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpeg");
                                if (iter.hasNext()) {
                                    ImageWriter writer = iter.next();
                                    ImageWriteParam param = writer.getDefaultWriteParam();
                                    if (param.canWriteCompressed()) {
                                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                                        param.setCompressionQuality(0.35f); // High quality 480p streaming compression
                                    }
                                    ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                                    writer.setOutput(ios);
                                    writer.write(null, new IIOImage(croppedImg, null, null), param);
                                    ios.close();
                                    writer.dispose();
                                } else {
                                    ImageIO.write(croppedImg, "jpg", baos);
                                }
                                byte[] jpegData = baos.toByteArray();

                                // Send in chunks to bypass 32KB packet limit and Netty power-of-two backing array bug
                                int chunkSize = 14000;
                                int totalChunks = (jpegData.length + chunkSize - 1) / chunkSize;
                                for (int i = 0; i < totalChunks; i++) {
                                    int start = i * chunkSize;
                                    int len = Math.min(chunkSize, jpegData.length - start);
                                    byte[] chunk = new byte[len];
                                    System.arraycopy(jpegData, start, chunk, 0, len);
                                    NetworkHandler.INSTANCE.sendToServer(
                                        new VideoFrameMessage(
                                            frameCounter,
                                            i,
                                            totalChunks,
                                            chunk,
                                            FreakCamConfig.getHeightOffset(),
                                            FreakCamConfig.getWebcamScale(),
                                            FreakCamConfig.getWebcamFrameShape(),
                                            FreakCamConfig.isWebcamBorderEnabled(),
                                            FreakCamConfig.getWebcamBorderWidth(),
                                            FreakCamConfig.getWebcamBorderMode(),
                                            FreakCamConfig.getWebcamBorderColor(),
                                            FreakCamConfig.isChromaKeyEnabled(),
                                            FreakCamConfig.getChromaKeyColor(),
                                            FreakCamConfig.getChromaKeyTolerance()));
                                }
                                frameCounter++;
                            }
                            Thread.sleep(5);
                        } catch (Exception e) {
                            // Ignore individual frame errors
                        }
                    }

                    if (webcamCapture != null) {
                        webcamCapture.stop();
                    }
                }
            }, "FreakCam-Video");
            captureThread.setDaemon(true);
            captureThread.start();
        }
    }

    private static BufferedImage flipHorizontally(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage flipped = new BufferedImage(w, h, img.getType());
        Graphics2D g2d = flipped.createGraphics();
        g2d.drawImage(img, 0, 0, w, h, w, 0, 0, h, null);
        g2d.dispose();
        return flipped;
    }

    /**
     * Crops a square region from the (potentially rectangular) source image.
     * The crop square size is min(w,h) / zoom.
     * Offset moves the crop center.
     * Output is always square for the in-game circle mask.
     */
    static BufferedImage applyCropAndZoom(BufferedImage rawImage) {
        int w = rawImage.getWidth();
        int h = rawImage.getHeight();
        int minDim = Math.min(w, h);

        float zoom = FreakCamConfig.getZoomLevel();
        float offsetX = FreakCamConfig.getCropOffsetX();
        float offsetY = FreakCamConfig.getCropOffsetY();

        int cropSize = Math.max(1, Math.round(minDim / zoom));

        float centerX = w * (0.5f + offsetX);
        float centerY = h * (0.5f + offsetY);

        int cropX = Math.round(centerX - cropSize / 2.0f);
        int cropY = Math.round(centerY - cropSize / 2.0f);
        cropX = Math.max(0, Math.min(w - cropSize, cropX));
        cropY = Math.max(0, Math.min(h - cropSize, cropY));

        // If no crop needed (zoom=1, centered, and image is already square)
        if (zoom <= 1.0f && offsetX == 0.0f && offsetY == 0.0f && w == h) {
            return rawImage;
        }

        BufferedImage cropped = rawImage.getSubimage(cropX, cropY, cropSize, cropSize);

        // Ensure stream resolution is AT LEAST 480x480 as explicitly requested
        int targetDim = Math.max(480, cropSize > 480 ? 480 : cropSize);
        if (cropSize == targetDim && cropped.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return cropped;
        }

        BufferedImage scaled = new BufferedImage(targetDim, targetDim, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cropped, 0, 0, targetDim, targetDim, null);
        g.dispose();

        return scaled;
    }

    public static void onPlayerCamStatusChanged(int entityId, boolean enabled) {
        if (!enabled) {
            playerTextures.remove(entityId);
            decodedFrames.remove(entityId);
            playerLastFrameTime.remove(entityId);
            latestFrameIds.remove(entityId);
            frameAssembly.remove(entityId);
            pendingNetworkFrames.remove(entityId);
            playerHeightOffsets.remove(entityId);
            playerWebcamScales.remove(entityId);
        }
    }

    private static void stopCapture() {
        capturing = false;
        if (webcamCapture != null) {
            webcamCapture.stop();
            webcamCapture = null;
        }
        if (captureThread != null && captureThread.isAlive()) {
            try {
                captureThread.join(500);
            } catch (Exception e) {}
            captureThread = null;
        }
        latestRawImage = null;
        if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null) {
            int localId = Minecraft.getMinecraft().thePlayer.getEntityId();
            onPlayerCamStatusChanged(localId, false);
            NetworkHandler.INSTANCE.sendToServer(new com.freakcam.network.VideoToggleMessage(false));
        }
    }

    public static void onVideoChunk(int senderId, int frameId, int chunkIndex, int totalChunks, byte[] chunkData,
        float heightOffset, float webcamScale, int frameShape, boolean borderEnabled, float borderWidth, int borderMode,
        int borderColor, boolean chromaEnabled, int chromaColor, float chromaTolerance) {
        playerHeightOffsets.put(senderId, heightOffset);
        playerWebcamScales.put(senderId, webcamScale);
        playerFrameShapes.put(senderId, frameShape);
        playerBorderEnabled.put(senderId, borderEnabled);
        playerBorderWidths.put(senderId, borderWidth);
        playerBorderModes.put(senderId, borderMode);
        playerBorderColors.put(senderId, borderColor);
        playerChromaEnabled.put(senderId, chromaEnabled);
        playerChromaColors.put(senderId, chromaColor);
        playerChromaTolerances.put(senderId, chromaTolerance);
        Integer lastFrame = latestFrameIds.get(senderId);
        if (lastFrame != null && lastFrame != frameId) {
            frameAssembly.remove(senderId);
        }
        latestFrameIds.put(senderId, frameId);

        byte[][] chunks = frameAssembly.computeIfAbsent(senderId, k -> new byte[totalChunks][]);
        if (chunkIndex >= 0 && chunkIndex < totalChunks) {
            chunks[chunkIndex] = chunkData;
        }

        boolean complete = true;
        int totalSize = 0;
        for (byte[] c : chunks) {
            if (c == null) {
                complete = false;
                break;
            }
            totalSize += c.length;
        }

        if (complete) {
            byte[] fullFrame = new byte[totalSize];
            int offset = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, fullFrame, offset, c.length);
                offset += c.length;
            }
            frameAssembly.remove(senderId);
            pendingNetworkFrames.put(senderId, fullFrame);
            playerLastFrameTime.put(senderId, System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public void onRenderLivingSpecialsPost(RenderLivingEvent.Specials.Post event) {
        if (!(event.entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entity;

        if (player == Minecraft.getMinecraft().thePlayer
            && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0) {
            return;
        }

        int entityId = player.getEntityId();

        // If local player disabled webcam, remove texture & return completely (nothing drawn above head in 3D)
        if (player == Minecraft.getMinecraft().thePlayer && !FreakCamConfig.isWebcamEnabled()) {
            onPlayerCamStatusChanged(entityId, false);
            return;
        }

        // Purge remote player textures if no new frames received for 1 second (e.g. camera turned off or disconnected)
        Long lastTime = playerLastFrameTime.get(entityId);
        if (lastTime != null && System.currentTimeMillis() - lastTime > 1000) {
            onPlayerCamStatusChanged(entityId, false);
        }
        BufferedImage image = decodedFrames.remove(entityId);
        if (image != null) {
            try {
                int imgW = image.getWidth();
                int imgH = image.getHeight();

                net.minecraft.client.renderer.texture.DynamicTexture dynTex = playerTextures.get(entityId);
                if (dynTex == null || dynTex.getTextureData().length != imgW * imgH) {
                    dynTex = new net.minecraft.client.renderer.texture.DynamicTexture(imgW, imgH);
                    playerTextures.put(entityId, dynTex);
                }

                int[] pixels = dynTex.getTextureData();
                image.getRGB(0, 0, imgW, imgH, pixels, 0, imgW);

                applyPixelMaskAndChromaKey(entityId, pixels, imgW, imgH);
                dynTex.updateDynamicTexture();
            } catch (Exception e) {}
        }

        net.minecraft.client.renderer.texture.DynamicTexture dynTex = playerTextures.get(entityId);
        if (dynTex != null) {
            renderWebcamAbovePlayer(player, dynTex.getGlTextureId(), event.x, event.y, event.z);
        }
    }

    public static void applyPixelMaskAndChromaKey(int[] pixels, int imgW, int imgH) {
        int localId = (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null)
            ? Minecraft.getMinecraft().thePlayer.getEntityId()
            : 0;
        applyPixelMaskAndChromaKey(localId, pixels, imgW, imgH);
    }

    public static void applyPixelMaskAndChromaKey(int entityId, int[] pixels, int imgW, int imgH) {
        int shape = playerFrameShapes.getOrDefault(entityId, FreakCamConfig.getWebcamFrameShape());
        boolean chromaOn = playerChromaEnabled.getOrDefault(entityId, FreakCamConfig.isChromaKeyEnabled());
        int keyColor = playerChromaColors.getOrDefault(entityId, FreakCamConfig.getChromaKeyColor());
        int keyR = (keyColor >> 16) & 0xFF;
        int keyG = (keyColor >> 8) & 0xFF;
        int keyB = keyColor & 0xFF;

        float tol = playerChromaTolerances.getOrDefault(entityId, FreakCamConfig.getChromaKeyTolerance());
        float tolSq = (tol * 255.0f) * (tol * 255.0f) * 3.0f;

        float centerX = imgW / 2.0f;
        float centerY = imgH / 2.0f;
        float radius = Math.min(centerX, centerY);

        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                int idx = y * imgW + x;
                int pixel = pixels[idx];

                // 1. Chroma Key removal
                if (chromaOn) {
                    int pr = (pixel >> 16) & 0xFF;
                    int pg = (pixel >> 8) & 0xFF;
                    int pb = pixel & 0xFF;

                    int dr = pr - keyR;
                    int dg = pg - keyG;
                    int db = pb - keyB;
                    float distSq = dr * dr + dg * dg + db * db;
                    if (distSq <= tolSq) {
                        pixels[idx] = 0x00000000;
                        continue;
                    }
                }

                // 2. Frame Shape Masking
                int alpha = 0xFF;
                float nx = (x - centerX) / centerX; // -1.0 to 1.0
                float ny = (y - centerY) / centerY; // -1.0 to 1.0

                if (shape == 1) { // Circle
                    float dx = x - centerX;
                    float dy = y - centerY;
                    float distSq = dx * dx + dy * dy;
                    if (distSq > radius * radius) {
                        alpha = 0x00;
                    } else if (distSq > (radius - 2) * (radius - 2)) {
                        alpha = (int) (0xFF * (radius - Math.sqrt(distSq)) / 2.0f);
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

                if (alpha < 0xFF) {
                    int origAlpha = (pixel >> 24) & 0xFF;
                    int finalAlpha = Math.min(origAlpha, alpha);
                    pixels[idx] = (finalAlpha << 24) | (pixel & 0xFFFFFF);
                }
            }
        }
    }

    private void renderWebcamAbovePlayer(EntityPlayer player, int textureId, double viewX, double viewY, double viewZ) {
        RenderManager rm = RenderManager.instance;
        int entityId = player.getEntityId();
        float userScale = playerWebcamScales.getOrDefault(entityId, FreakCamConfig.getWebcamScale());
        float scale = (0.016666668F * 2.0F) * userScale;

        GL11.glPushMatrix();

        float heightOff = playerHeightOffsets.getOrDefault(entityId, FreakCamConfig.getHeightOffset());
        GL11.glTranslatef((float) viewX, (float) viewY + player.height + heightOff, (float) viewZ);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);

        GL11.glRotatef(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(rm.playerViewX, 1.0F, 0.0F, 0.0F);

        GL11.glScalef(-scale, -scale, scale);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();

        int size = 20;
        tess.addVertexWithUV(-size, -size, 0.0D, 0.0D, 0.0D);
        tess.addVertexWithUV(-size, size, 0.0D, 0.0D, 1.0D);
        tess.addVertexWithUV(size, size, 0.0D, 1.0D, 1.0D);
        tess.addVertexWithUV(size, -size, 0.0D, 1.0D, 0.0D);
        tess.draw();

        // 3D Frame Border & RGB Glow
        boolean borderOn = playerBorderEnabled.getOrDefault(entityId, FreakCamConfig.isWebcamBorderEnabled());
        if (borderOn) {
            draw3DFrameBorder(entityId, size, size);
        }

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    public static int getBorderRGB(int entityId) {
        int mode = playerBorderModes.getOrDefault(entityId, FreakCamConfig.getWebcamBorderMode());
        if (mode == 1) {
            float hue = (System.currentTimeMillis() % 3000L) / 3000.0f;
            return java.awt.Color.HSBtoRGB(hue, 0.9f, 1.0f) & 0xFFFFFF;
        } else {
            return playerBorderColors.getOrDefault(entityId, FreakCamConfig.getWebcamBorderColor());
        }
    }

    public static void draw3DFrameBorder(int entityId, float sizeW, float sizeH) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);

        float borderWidth = playerBorderWidths.getOrDefault(entityId, FreakCamConfig.getWebcamBorderWidth());
        GL11.glLineWidth(borderWidth);

        int mode = playerBorderModes.getOrDefault(entityId, FreakCamConfig.getWebcamBorderMode());
        int shape = playerFrameShapes.getOrDefault(entityId, FreakCamConfig.getWebcamFrameShape());
        int solidColor = getBorderRGB(entityId);

        java.util.List<float[]> vertices = new java.util.ArrayList<>();

        if (shape == 1) { // Circle
            int steps = 48;
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2.0 / steps;
                vertices.add(new float[] { (float) (Math.cos(a) * sizeW), (float) (Math.sin(a) * sizeH) });
            }
        } else if (shape == 2) { // Rounded Rectangle
            float r = 0.25f * Math.min(sizeW, sizeH);
            int arcSteps = 8;
            for (int i = 0; i <= arcSteps; i++) {
                double a = i * (Math.PI / 2.0) / arcSteps;
                vertices
                    .add(new float[] { (float) (sizeW - r + Math.cos(a) * r), (float) (-sizeH + r - Math.sin(a) * r) });
            }
            for (int i = 0; i <= arcSteps; i++) {
                double a = Math.PI / 2.0 + i * (Math.PI / 2.0) / arcSteps;
                vertices.add(
                    new float[] { (float) (-sizeW + r + Math.cos(a) * r), (float) (-sizeH + r - Math.sin(a) * r) });
            }
            for (int i = 0; i <= arcSteps; i++) {
                double a = Math.PI + i * (Math.PI / 2.0) / arcSteps;
                vertices
                    .add(new float[] { (float) (-sizeW + r + Math.cos(a) * r), (float) (sizeH - r - Math.sin(a) * r) });
            }
            for (int i = 0; i <= arcSteps; i++) {
                double a = 1.5 * Math.PI + i * (Math.PI / 2.0) / arcSteps;
                vertices
                    .add(new float[] { (float) (sizeW - r + Math.cos(a) * r), (float) (sizeH - r - Math.sin(a) * r) });
            }
        } else if (shape == 3) { // Diamond
            vertices.add(new float[] { 0, -sizeH });
            vertices.add(new float[] { sizeW, 0 });
            vertices.add(new float[] { 0, sizeH });
            vertices.add(new float[] { -sizeW, 0 });
        } else { // Rectangle
            vertices.add(new float[] { -sizeW, -sizeH });
            vertices.add(new float[] { sizeW, -sizeH });
            vertices.add(new float[] { sizeW, sizeH });
            vertices.add(new float[] { -sizeW, sizeH });
        }

        GL11.glBegin(GL11.GL_LINE_LOOP);
        int total = vertices.size();
        long time = System.currentTimeMillis();
        for (int i = 0; i < total; i++) {
            float[] v = vertices.get(i);
            if (mode == 2) { // Spectrum Rainbow - spatial angle sync!
                double angle = Math.atan2(v[1], v[0]);
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
            GL11.glVertex3f(v[0], v[1], -0.01f);
        }
        GL11.glEnd();

        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @SubscribeEvent
    public void onRenderGameOverlayPost(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if (event.type != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) return;
        if (!FreakCamConfig.isWebcamEnabled() && FreakCamConfig.isShowOffIconHud()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return;
            if (mc.currentScreen instanceof GuiFreakCamSettings) return;

            int sw = event.resolution.getScaledWidth();
            int sh = event.resolution.getScaledHeight();

            float scale = FreakCamConfig.getOffIconScale();
            float baseRadius = 14.0f * scale;

            float x = baseRadius + (sw - baseRadius * 2) * FreakCamConfig.getOffIconX();
            float y = baseRadius + (sh - baseRadius * 2) * FreakCamConfig.getOffIconY();

            GL11.glPushMatrix();
            drawOffWebcamIcon(x, y, baseRadius, 1.0f);
            GL11.glPopMatrix();
        }
    }

    public static void drawOffWebcamIcon(float centerX, float centerY, float radius, float alpha) {
        drawOffWebcamIcon(
            centerX,
            centerY,
            radius,
            alpha,
            FreakCamConfig.getOffIconStyle(),
            FreakCamConfig.isOffIconShadow());
    }

    public static void drawOffWebcamIcon(float centerX, float centerY, float radius, float alpha, int style,
        boolean hasShadow) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);

        // 0. Optional Shadow Backplate / Backdrop Card + Real Offset Drop Shadow
        if (hasShadow) {
            float offX = 3.5f * (radius / 14.0f);
            float offY = 3.5f * (radius / 14.0f);

            if (style == 0) {
                // A. Real Offset Drop Shadow (Circular)
                float shadowR = radius * 1.25f;
                GL11.glColor4f(0.0f, 0.0f, 0.0f, 0.85f * alpha);
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                GL11.glVertex2f(centerX + offX, centerY + offY);
                for (int i = 0; i <= 32; i++) {
                    double angle = i * Math.PI * 2.0 / 32;
                    GL11.glVertex2f(
                        (float) (centerX + offX + Math.cos(angle) * shadowR),
                        (float) (centerY + offY + Math.sin(angle) * shadowR));
                }
                GL11.glEnd();

                // B. Padded Dark Backplate Card (Circular)
                float backplateR = radius * 1.20f;
                GL11.glColor4f(0.05f, 0.05f, 0.09f, 0.92f * alpha);
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                GL11.glVertex2f(centerX, centerY);
                for (int i = 0; i <= 32; i++) {
                    double angle = i * Math.PI * 2.0 / 32;
                    GL11.glVertex2f(
                        (float) (centerX + Math.cos(angle) * backplateR),
                        (float) (centerY + Math.sin(angle) * backplateR));
                }
                GL11.glEnd();

                // C. Backplate Accent Border
                GL11.glColor4f(0.25f, 0.25f, 0.35f, 0.80f * alpha);
                GL11.glLineWidth(1.5f);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                for (int i = 0; i < 32; i++) {
                    double angle = i * Math.PI * 2.0 / 32;
                    GL11.glVertex2f(
                        (float) (centerX + Math.cos(angle) * backplateR),
                        (float) (centerY + Math.sin(angle) * backplateR));
                }
                GL11.glEnd();
            } else {
                // A. Real Offset Drop Shadow (Rectangular)
                float padW = radius * 1.25f;
                float padH = radius * 0.90f;
                GL11.glColor4f(0.0f, 0.0f, 0.0f, 0.85f * alpha);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(centerX + offX - padW, centerY + offY - padH);
                GL11.glVertex2f(centerX + offX + padW, centerY + offY - padH);
                GL11.glVertex2f(centerX + offX + padW, centerY + offY + padH);
                GL11.glVertex2f(centerX + offX - padW, centerY + offY + padH);
                GL11.glEnd();

                // B. Padded Dark Backplate Card (Rectangular)
                GL11.glColor4f(0.05f, 0.05f, 0.09f, 0.92f * alpha);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(centerX - padW, centerY - padH);
                GL11.glVertex2f(centerX + padW, centerY - padH);
                GL11.glVertex2f(centerX + padW, centerY + padH);
                GL11.glVertex2f(centerX - padW, centerY + padH);
                GL11.glEnd();

                // C. Backplate Accent Border
                GL11.glColor4f(0.25f, 0.25f, 0.35f, 0.80f * alpha);
                GL11.glLineWidth(1.5f);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex2f(centerX - padW, centerY - padH);
                GL11.glVertex2f(centerX + padW, centerY - padH);
                GL11.glVertex2f(centerX + padW, centerY + padH);
                GL11.glVertex2f(centerX - padW, centerY + padH);
                GL11.glEnd();
            }
        }

        if (style == 0) {
            // Style 0: Circular icon with diagonal red slash
            // 1. Dark Circular Background
            GL11.glColor4f(0.06f, 0.06f, 0.10f, 0.85f * alpha);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(centerX, centerY);
            for (int i = 0; i <= 32; i++) {
                double angle = i * Math.PI * 2.0 / 32;
                GL11.glVertex2f(
                    (float) (centerX + Math.cos(angle) * radius),
                    (float) (centerY + Math.sin(angle) * radius));
            }
            GL11.glEnd();

            // 2. Outer Red Ring
            GL11.glColor4f(0.85f, 0.2f, 0.2f, 0.9f * alpha);
            GL11.glLineWidth(2.0f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI * 2.0 / 32;
                GL11.glVertex2f(
                    (float) (centerX + Math.cos(angle) * radius),
                    (float) (centerY + Math.sin(angle) * radius));
            }
            GL11.glEnd();

            // 3. Camera Body Rectangle
            float camW = radius * 0.8f;
            float camH = radius * 0.55f;
            GL11.glColor4f(0.85f, 0.85f, 0.9f, 0.9f * alpha);
            GL11.glLineWidth(2.0f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(centerX - camW / 2, centerY - camH / 2);
            GL11.glVertex2f(centerX + camW / 2, centerY - camH / 2);
            GL11.glVertex2f(centerX + camW / 2, centerY + camH / 2);
            GL11.glVertex2f(centerX - camW / 2, centerY + camH / 2);
            GL11.glEnd();

            // Lens circle inside camera
            float lensR = radius * 0.22f;
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI * 2.0 / 24;
                GL11.glVertex2f(
                    (float) (centerX + Math.cos(angle) * lensR),
                    (float) (centerY + Math.sin(angle) * lensR));
            }
            GL11.glEnd();

            // 4. Red Diagonal Slash Line
            float slashOff = radius * 0.75f;
            GL11.glColor4f(1.0f, 0.15f, 0.15f, 1.0f * alpha);
            GL11.glLineWidth(3.0f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(centerX - slashOff, centerY - slashOff);
            GL11.glVertex2f(centerX + slashOff, centerY + slashOff);
            GL11.glEnd();
        } else {
            // Style 1: Video Camera silhouette with Red X
            float bodyW = radius * 0.9f;
            float bodyH = radius * 0.7f;
            float bodyLeft = centerX - radius * 0.65f;
            float bodyRight = bodyLeft + bodyW;
            float bodyTop = centerY - bodyH / 2.0f;
            float bodyBottom = centerY + bodyH / 2.0f;

            // Camera body fill (dark-translucent)
            GL11.glColor4f(0.08f, 0.08f, 0.12f, 0.85f * alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(bodyLeft, bodyTop);
            GL11.glVertex2f(bodyRight, bodyTop);
            GL11.glVertex2f(bodyRight, bodyBottom);
            GL11.glVertex2f(bodyLeft, bodyBottom);
            GL11.glEnd();

            // Camera body outline
            GL11.glColor4f(0.9f, 0.9f, 0.95f, 0.95f * alpha);
            GL11.glLineWidth(2.0f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(bodyLeft, bodyTop);
            GL11.glVertex2f(bodyRight, bodyTop);
            GL11.glVertex2f(bodyRight, bodyBottom);
            GL11.glVertex2f(bodyLeft, bodyBottom);
            GL11.glEnd();

            // Camera lens cone (pointing right)
            float coneRight = bodyRight + radius * 0.45f;
            float coneTop = centerY - radius * 0.45f;
            float coneBottom = centerY + radius * 0.45f;

            GL11.glColor4f(0.08f, 0.08f, 0.12f, 0.85f * alpha);
            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glVertex2f(bodyRight, centerY - radius * 0.2f);
            GL11.glVertex2f(coneRight, coneTop);
            GL11.glVertex2f(coneRight, coneBottom);
            GL11.glEnd();

            GL11.glColor4f(0.9f, 0.9f, 0.95f, 0.95f * alpha);
            GL11.glLineWidth(2.0f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(bodyRight, centerY - radius * 0.2f);
            GL11.glVertex2f(coneRight, coneTop);
            GL11.glVertex2f(coneRight, coneBottom);
            GL11.glVertex2f(bodyRight, centerY + radius * 0.2f);
            GL11.glEnd();

            // Prominent Red X Cross over camera body
            GL11.glColor4f(1.0f, 0.15f, 0.15f, 1.0f * alpha);
            GL11.glLineWidth(3.5f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(bodyLeft + 2.0f, bodyTop + 2.0f);
            GL11.glVertex2f(bodyRight - 2.0f, bodyBottom - 2.0f);
            GL11.glVertex2f(bodyLeft + 2.0f, bodyBottom - 2.0f);
            GL11.glVertex2f(bodyRight - 2.0f, bodyTop + 2.0f);
            GL11.glEnd();
        }

        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
