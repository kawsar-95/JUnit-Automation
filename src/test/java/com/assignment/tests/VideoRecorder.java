package com.assignment.tests;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import javax.imageio.ImageIO;

/**
 * JUnit rule that records each test as an MP4 video. We don't use ffmpeg
 * / x11grab because Chrome's headless mode renders to an offscreen
 * compositor and never appears on the X11 display — so external screen
 * capture sees a blank frame.
 *
 * Instead, we tap Selenium's own screenshot API, which captures the actual
 * rendered pixels, and write the frames directly into an MP4 container.
 *
 * Pipeline:
 *   1. Start a daemon thread that wakes every 500ms during the test,
 *      calls TakesScreenshot, writes a JPEG to a temp dir, and records
 *      the wall-clock time of each frame.
 *   2. After the test method finishes, ffmpeg encodes the JPEG sequence
 *      into an MP4 (libx264, yuv420p, 2 fps). 2 fps is plenty for a
 *      JUnit run (typically 5-60 s) and keeps the file small.
 *
 * The output file lands in {@code build/reports/videos/<TestName>.mp4}
 * and is symlinked to {@code videos/<TestName>.mp4} for the README.
 */
public final class VideoRecorder implements TestRule {

    private static final long INTERVAL_MS = 500;
    private static final int  OUT_FPS     = 2;
    static final Path VIDEO_DIR = computeVideoDir();
    static final Path FRAME_ROOT =
            Paths.get(System.getProperty("java.io.tmpdir"), "test-frames");

    private static Path computeVideoDir() {
        Path screenshots = Paths.get(System.getProperty("screenshot.dir",
                System.getProperty("user.dir") + "/build/reports/screenshots"));
        // Screenshots dir is build/reports/screenshots; videos go in build/reports/videos
        Path parent = screenshots.getParent();
        if (parent == null) parent = screenshots.toAbsolutePath().getParent();
        return (parent != null ? parent : Paths.get("build/reports"))
                .resolve("videos");
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // For a @ClassRule, description.getMethodName() may be null;
                // fall back to the class name so the recorder still works.
                String key = description.getMethodName();
                if (key == null || key.isEmpty()) key = description.getClassName();
                Path frameDir = FRAME_ROOT.resolve(key.replaceAll("[^a-zA-Z0-9._-]", "_"));
                deleteRecursively(frameDir);
                Files.createDirectories(frameDir);

                Recorder recorder = new Recorder(frameDir);
                recorder.start();
                Throwable failure = null;
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    failure = t;
                } finally {
                    recorder.stopAndFlush();
                }

                Path outMp4 = VIDEO_DIR.resolve(key.replaceAll("[^a-zA-Z0-9._-]", "_") + ".mp4");
                Files.createDirectories(outMp4.getParent());
                encodeMp4(frameDir, outMp4);

                if (failure != null) throw failure;
            }
        };
    }

    /** background frame capture loop */
    private static final class Recorder implements Runnable {
        private final Path frameDir;
        private Thread thread;
        private volatile boolean running;
        private final long startNs;
        Recorder(Path frameDir) { this.frameDir = frameDir; this.startNs = System.nanoTime(); }
        void start() {
            running = true;
            thread = new Thread(this, "video-recorder");
            thread.setDaemon(true);
            thread.start();
        }
        void stopAndFlush() {
            running = false;
            try { if (thread != null) thread.join(2000); } catch (InterruptedException ignored) {}
        }
        @Override public void run() {
            long frameIdx = 0;
            while (running) {
                WebDriver d = BaseSeleniumTest.driver;
                if (d != null) {
                    try {
                        File shot = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
                        // write as JPEG to keep disk usage low
                        BufferedImage img = ImageIO.read(shot);
                        if (img != null) {
                            Path target = frameDir.resolve(String.format("%05d.jpg", frameIdx));
                            try (OutputStream os = Files.newOutputStream(target,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING)) {
                                ImageIO.write(img, "jpg", os);
                            }
                        }
                    } catch (Throwable ignored) {
                        // test is between pages / browser closed / window lost focus
                    }
                }
                frameIdx++;
                try { Thread.sleep(INTERVAL_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private static void encodeMp4(Path frameDir, Path outMp4) throws IOException, InterruptedException {
        if (!Files.exists(frameDir) || !Files.isDirectory(frameDir)) return;
        String[] frames = frameDir.toFile().list((d, n) -> n.endsWith(".jpg"));
        if (frames == null || frames.length == 0) return;

        // Sort by file name (they're zero-padded)
        java.util.Arrays.sort(frames);

        // Run ffmpeg to glue the JPEGs into an MP4
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-y");
        cmd.add("-framerate"); cmd.add(String.valueOf(OUT_FPS));
        cmd.add("-i"); cmd.add(frameDir.resolve("%05d.jpg").toString());
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add("ultrafast");
        cmd.add("-pix_fmt"); cmd.add("yuv420p");
        cmd.add("-vf"); cmd.add("pad=ceil(iw/2)*2:ceil(ih/2)*2"); // libx264 needs even dims
        cmd.add("-movflags"); cmd.add("+faststart");
        cmd.add(outMp4.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(new File("/tmp/ffmpeg-encode.log")));
        int code = pb.start().waitFor();
        if (code != 0) {
            System.err.println("[VideoRecorder] ffmpeg encode failed (code=" + code + "). See /tmp/ffmpeg-encode.log");
        } else {
            System.out.println("[VideoRecorder] wrote " + outMp4);
        }

        // Best-effort: clean up the JPEG frames to avoid filling /tmp
        deleteRecursively(frameDir);
    }

    private static void deleteRecursively(Path p) {
        if (!Files.exists(p)) return;
        try (var walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(child -> { try { Files.deleteIfExists(child); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
