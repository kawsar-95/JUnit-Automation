package com.assignment.tests;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

/** Shared base for all Selenium JUnit tests.
 *  - Boots a single Chrome browser for the whole suite (@BeforeClass)
 *  - Reuses it across tests
 *  - Takes a screenshot into build/reports/screenshots on every test method
 *    (whether it passes or fails), ready to attach to the README */
public abstract class BaseSeleniumTest {

    /** Selenium contract: a Test instance owns a WebDriver. We share one for speed,
     *  and we put it on the class so every @Test can reach it without re-wiring. */
    protected static WebDriver driver;

    /** System property "screenshot.dir" set in build.gradle -> build/reports/screenshots */
    private static final Path SCREENSHOT_DIR =
            Paths.get(System.getProperty("screenshot.dir",
                    System.getProperty("user.dir") + "/build/reports/screenshots"));

    /** Lets each test method know its own name so the screenshot file is descriptive. */
    @Rule
    public TestName testName = new TestName();

    @BeforeClass
    public static void startBrowser() throws IOException {
        WebDriverManager.chromedriver().setup();

        ChromeOptions opts = new ChromeOptions();
        opts.addArguments(
                "--headless",                  // old headless so Chrome actually uses X11 (so we can record it)
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1400,900",
                "--disable-gpu",
                "--remote-allow-origins=*",
                "--disable-blink-features=AutomationControlled");

        // Capture browser console logs (handy for debugging flaky network issues)
        LoggingPreferences logs = new LoggingPreferences();
        logs.enable(LogType.BROWSER, Level.WARNING);
        opts.setCapability(CapabilityType.LOGGING_PREFS, logs);

        driver = new ChromeDriver(opts);

        Files.createDirectories(SCREENSHOT_DIR);
        System.out.println("[BaseSeleniumTest] Screenshots will be written to: " + SCREENSHOT_DIR);
    }

    @After
    public void captureScreenshot() {
        if (driver == null) return;
        File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        String safeName = testName.getMethodName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = SCREENSHOT_DIR.resolve(safeName + ".png");
        try {
            Files.copy(shot.toPath(), target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[screenshot] " + target);
        } catch (IOException e) {
            System.err.println("Could not save screenshot for " + safeName + ": " + e.getMessage());
        }
    }

    @AfterClass
    public static void stopBrowser() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    /** Convenience helper for tests that want to dump the current page HTML. */
    protected String currentPageHtml() {
        return driver.getPageSource();
    }
}
