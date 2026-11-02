package com.freakcam.client;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.freakcam.FreakCam;

public class WebcamCapture {

    private static String ffmpegPath;

    private Process captureProcess;
    private InputStream videoStream;
    private volatile boolean running = false;
    private String deviceName;
    private volatile BufferedImage latestCapturedFrame = null;
    private volatile long frameCount = 0;
    private Thread internalCaptureThread = null;

    // Output dimensions computed at start() time preserving source aspect ratio
    private int outputWidth = 128;
    private int outputHeight = 128;

    static {
        ffmpegPath = findFFmpeg();
        if (ffmpegPath != null) {
            FreakCam.LOG.info("Found FFmpeg at: " + ffmpegPath);
        } else {
            FreakCam.LOG.warn("FFmpeg not found! Webcam capture will be unavailable.");
        }
    }

    private static String findFFmpeg() {
        String[] paths = { "ffmpeg", "C:\\Program Files\\FFmpeg\\bin\\ffmpeg.exe",
            "C:\\Program Files (x86)\\FFmpeg\\bin\\ffmpeg.exe", "C:\\ffmpeg\\bin\\ffmpeg.exe" };
        for (String path : paths) {
            try {
                ProcessBuilder pb = new ProcessBuilder(path, "-version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                byte[] buf = new byte[4096];
                InputStream is = p.getInputStream();
                while (is.read(buf) != -1) {}
                int exit = p.waitFor();
                if (exit == 0) return path;
            } catch (Exception e) {}
        }
        return null;
    }

    private static List<String> cachedDevices = null;
    private static java.util.Map<String, List<String>> cachedResolutions = new java.util.HashMap<>();

    public static List<String> listDevices() {
        if (cachedDevices != null) return cachedDevices;
        List<String> devices = new ArrayList<>();
        if (ffmpegPath == null) return devices;

        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-list_devices", "true", "-f", "dshow", "-i", "dummy");
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            Pattern pattern = Pattern.compile("\"(.+?)\"\\s+\\(video\\)");
            while ((line = reader.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    devices.add(m.group(1));
                }
            }
            p.waitFor();
            cachedDevices = devices;
        } catch (Exception e) {
            FreakCam.LOG.error("Failed to list webcam devices", e);
        }
        return devices;
    }

    public static synchronized List<String> listResolutions(String device) {
        if (cachedResolutions.containsKey(device)) return cachedResolutions.get(device);
        List<String> resList = new ArrayList<>();
        if (ffmpegPath == null || device == null || device.isEmpty()) return resList;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-f",
                "dshow",
                "-list_options",
                "true",
                "-i",
                "video=" + device);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            Pattern pattern = Pattern.compile("s=(\\d+x\\d+)");
            while ((line = reader.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    String res = m.group(1);
                    if (!resList.contains(res)) resList.add(res);
                }
            }
            p.waitFor();
            cachedResolutions.put(device, resList);
        } catch (Exception e) {
            FreakCam.LOG.error("Failed to list resolutions for " + device, e);
        }
        return resList;
    }

    public void start(String device, int captureWidth, int captureHeight, int fps) {
        if (running) stop();
        if (ffmpegPath == null) {
            FreakCam.LOG.error("FFmpeg not found, cannot start webcam capture");
            return;
        }
        this.deviceName = device;
        running = true;

        if (captureWidth <= 0 || captureHeight <= 0) {
            captureWidth = 640;
            captureHeight = 480;
        }
        outputWidth = captureWidth;
        outputHeight = captureHeight;

        // Ensure even dimensions for FFmpeg
        outputWidth = (outputWidth + 1) & ~1;
        outputHeight = (outputHeight + 1) & ~1;

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegPath);
            cmd.add("-fflags");
            cmd.add("nobuffer");
            cmd.add("-flags");
            cmd.add("low_delay");
            cmd.add("-probesize");
            cmd.add("32");
            cmd.add("-analyzeduration");
            cmd.add("0");
            cmd.add("-f");
            cmd.add("dshow");

            if (fps > 0) {
                cmd.add("-framerate");
                cmd.add(String.valueOf(fps));
            }
            if (captureWidth > 0 && captureHeight > 0) {
                cmd.add("-video_size");
                cmd.add(captureWidth + "x" + captureHeight);
            }
            cmd.add("-i");
            cmd.add("video=" + device);

            // No hflip here — mirroring is done in Java for instant toggling
            cmd.add("-vf");
            cmd.add("scale=" + outputWidth + ":" + outputHeight);

            cmd.add("-r");
            cmd.add(String.valueOf(fps));
            cmd.add("-f");
            cmd.add("rawvideo");
            cmd.add("-pix_fmt");
            cmd.add("bgr24");
            cmd.add("-an");
            cmd.add("-");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            captureProcess = pb.start();
            videoStream = captureProcess.getInputStream();

            Thread errDrain = new Thread(() -> {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(captureProcess.getErrorStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.contains("Error") || line.contains("failed") || line.contains("Could not")) {
                            FreakCam.LOG.warn("FFmpeg: " + line);
                        }
                    }
                    br.close();
                } catch (IOException e) {}
            }, "FFmpeg-stderr");
            errDrain.setDaemon(true);
            errDrain.start();

            FreakCam.LOG.info("Started FFmpeg webcam capture: " + device + " at " + outputWidth + "x" + outputHeight);

            internalCaptureThread = new Thread(() -> {
                int frameSize = outputWidth * outputHeight * 3;
                byte[] frameData = new byte[frameSize];
                try {
                    BufferedImage sharedImage = new BufferedImage(
                        outputWidth,
                        outputHeight,
                        BufferedImage.TYPE_3BYTE_BGR);
                    byte[] targetPixels = ((java.awt.image.DataBufferByte) sharedImage.getRaster()
                        .getDataBuffer()).getData();

                    while (running && videoStream != null) {
                        // Drain stale frames from OS pipe buffer to eliminate latency
                        int avail = videoStream.available();
                        while (avail >= frameSize * 2) {
                            int toSkip = frameSize;
                            while (toSkip > 0) {
                                int read = videoStream.read(frameData, 0, Math.min(toSkip, frameData.length));
                                if (read == -1) break;
                                toSkip -= read;
                            }
                            avail = videoStream.available();
                        }

                        int totalRead = 0;
                        while (totalRead < frameSize) {
                            int read = videoStream.read(frameData, totalRead, frameSize - totalRead);
                            if (read == -1) {
                                running = false;
                                break;
                            }
                            totalRead += read;
                        }
                        if (!running) break;

                        System.arraycopy(frameData, 0, targetPixels, 0, frameSize);
                        latestCapturedFrame = sharedImage;
                        frameCount++;
                    }
                } catch (Exception e) {
                    running = false;
                }
            }, "FFmpeg-Capture-Reader");
            internalCaptureThread.setDaemon(true);
            internalCaptureThread.start();

        } catch (Exception e) {
            FreakCam.LOG.error("Failed to start FFmpeg capture", e);
            running = false;
        }
    }

    public BufferedImage readFrame() {
        return running ? latestCapturedFrame : null;
    }

    public long getFrameCount() {
        return frameCount;
    }

    public void stop() {
        running = false;
        if (captureProcess != null) {
            try {
                captureProcess.destroy();
                // Wait for the process to release the webcam lock
                for (int i = 0; i < 20; i++) {
                    try {
                        captureProcess.exitValue();
                        break;
                    } catch (IllegalThreadStateException e) {
                        Thread.sleep(100); // Poll every 100ms
                    }
                }
                if (captureProcess.isAlive()) {
                    captureProcess.destroyForcibly();
                }
            } catch (Exception e) {}
            captureProcess = null;
        }
        videoStream = null;
    }

    public boolean isRunning() {
        return running;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public int getOutputWidth() {
        return outputWidth;
    }

    public int getOutputHeight() {
        return outputHeight;
    }

    public static boolean isFFmpegAvailable() {
        return ffmpegPath != null;
    }

    public BufferedImage getLatestFrame() {
        return latestCapturedFrame;
    }
}
