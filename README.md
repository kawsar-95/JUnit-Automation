# JUnit-Automation

JUnit + Selenium WebDriver automation for two assignments:

1. **Guest Registration Form** — fill out a web form, submit, and assert success.
2. **Dhaka Stock Exchange Share Price Scraper** — scrape a 395-row data table from a live
   financial site, print every cell, and persist the result to a text file.

Both automations are written in Java with JUnit 4 and Selenium WebDriver, built and run via
Gradle, and produce HTML test reports, per-test screenshots, and per-test screen-recordings
every time they execute.

---

## Repository layout

```
JUnit-Automation/
├── .gitignore                      # ignores .gradle, build, logs, etc.
├── build.gradle                    # Gradle 4.4 build: JUnit 4 + Selenium 3 + WebDriverManager
├── settings.gradle
├── README.md                       # ← you are here
├── videos/                         # MP4 recordings of each test run (also see build/reports/videos)
│   ├── dse-share-price-scrape.mp4
│   └── guest-registration-form.mp4
├── scripts/
│   └── record-test.sh              # convenience wrapper that records an MP4 of a test run
└── src/
    └── test/
        ├── java/com/assignment/tests/
        │   ├── BaseSeleniumTest.java        # Chrome lifecycle, per-test screenshots
        │   ├── VideoRecorder.java           # @ClassRule that captures MP4s via Selenium's screenshot API
        │   ├── DseSharePriceScrapeTest.java # Assignment #2
        │   └── GuestRegistrationFormTest.java # Assignment #1
        └── resources/
            └── guest-registration-form.html # local fixture (real form markup)
```

---

## How to run

> Requires JDK 8+ (tested on OpenJDK 25), Gradle 4+, Google Chrome, and `ffmpeg` (only
> needed if you want to run `scripts/record-test.sh` from scratch — otherwise the
> `VideoRecorder` JUnit rule uses `ffmpeg` automatically).

### Run both tests with reports, screenshots, and videos

```bash
xvfb-run -a gradle clean test
```

Outputs land under `build/reports/`:

| Path | What it is |
|---|---|
| `build/reports/tests/test/index.html` | Gradle HTML test report (open in a browser) |
| `build/reports/screenshots/<testName>.png` | Screenshot captured after every test |
| `build/reports/screenshots/dse_share_prices.txt` | The scraped share-price table (step 2 of the assignment) |
| `build/reports/videos/<className>.mp4` | MP4 recording of the test, written by the `VideoRecorder` rule |
| `videos/*.mp4` | Mirror copies named for the README |

### Run one test

```bash
xvfb-run -a gradle test --tests 'com.assignment.tests.DseSharePriceScrapeTest'
xvfb-run -a gradle test --tests 'com.assignment.tests.GuestRegistrationFormTest'
```

### Re-record the videos from scratch (optional)

```bash
bash scripts/record-test.sh DseSharePriceScrapeTest      videos/dse-share-price-scrape.mp4
bash scripts/record-test.sh GuestRegistrationFormTest   videos/guest-registration-form.mp4
```

(The `VideoRecorder` rule already produces an MP4 on every `gradle test` invocation, so
this manual step is only useful if you want a different filename or a different recording
window.)

---

## Assignment #1 — Guest Registration Form

### Assignment text

> Automate this webform: `https://demo.wpeverest.com/user-registration/guest-registration-form/`
>
> Steps for Automation:
> 1. Input following fields: Firstname, Lastname, UserEmail, Gender, Date of Birth,
>    Nationality, Phone, Country (Bangladesh), Terms & Conditions
> 2. Then click on submit button
> 3. Finally, Assert that registration is successful

### Network status of the live URL

The assignment's URL has been returning **HTTP 526 — Invalid SSL certificate** from
Cloudflare's edge every time we probe it during development, because the live origin's
SSL certificate is currently invalid:

```bash
$ curl -sI https://demo.wpeverest.com/user-registration/guest-registration-form/ | head -1
HTTP/2 526
```

Both curl and a real headless Chrome see the same Cloudflare interstitial page
("wpeverest.com | 526: Invalid SSL certificate"), not the real form. The Internet
Archive's Wayback Machine has cached the form (last good capture 2025-06-21), but
Wayback is read-only — submitting the form via Wayback would not POST to the real
backend.

### How this repo handles it

1. The **exact markup** of the live registration form (every `<input id="...">`,
   `<input data-id="...">`, `<select>`, checkbox, and submit button) is mirrored
   from the Wayback 2025-06-21 capture into
   `src/test/resources/guest-registration-form.html`. The selectors in the test
   are the real ones from the live page.
2. The form is loaded from a `file://` URL, so the test runs offline and is fully
   reproducible.
3. Because there is no working backend to POST to, the fixture's submit handler
   performs the same client-side validation a browser would (required-field
   checking) and surfaces a `#ur-success-banner` element on success. The
   assertion in `GuestRegistrationFormTest.java` checks exactly that banner —
   so when the assignment's real backend comes back online, swapping the
   `driver.get(...)` line to the live URL will make the same code assert on
   whatever the live plugin shows.

### What the test does

`GuestRegistrationFormTest.fillForm_andSubmit_registrationIsSuccessful()`

1. Opens `file:.../src/test/resources/guest-registration-form.html`.
2. Fills the form:
   - First Name = `Md. Nuruddin`
   - Last Name = `Kawsar`
   - User Email = `kawsar.nuruddin@example.com`
   - User Password = `Test12345!StrongPass`
   - Gender = `Male` (radio `#radio_1665627729_Male`)
   - Date of Birth = `1995-08-15`
   - Nationality = `Bangladeshi`
   - Phone = `+8801711002233`
   - Country = `Bangladesh` (`#country_1665629257` → value `BD`)
   - Terms & Conditions checkbox (`#privacy_policy_1665633140`) = checked
3. Clicks the `button.ur-submit-button` (selector matches the live form).
4. Asserts the success banner is visible, contains "Registration successful", and
   no `.ur-error` elements remain.

### Recording

[`videos/guest-registration-form.mp4`](videos/guest-registration-form.mp4)

<video src="videos/guest-registration-form.mp4" controls="controls" width="100%"></video>

### Test report (Gradle HTML)

[`build/reports/tests/test/index.html`](build/reports/tests/test/index.html) —
click into `com.assignment.tests.GuestRegistrationFormTest` to see the full PASSED record.

### Test report screenshot

This is the post-submit screenshot taken by `BaseSeleniumTest` after the form has
been filled and the submit button has been clicked. The green banner at the bottom
is the success assertion target:

![Form screenshot — all fields filled, Terms & Conditions checked, "Registration successful" banner](build/reports/screenshots/fillForm_andSubmit_registrationIsSuccessful.png)

### Console output (excerpt)

```
[BaseSeleniumTest] Screenshots will be written to: .../build/reports/screenshots
[Form] Saved post-submit HTML to .../build/reports/screenshots/guest-registration-success.html
[screenshot] .../build/reports/screenshots/fillForm_andSubmit_registrationIsSuccessful.png
[VideoRecorder] wrote .../build/reports/videos/com.assignment.tests.GuestRegistrationFormTest.mp4

com.assignment.tests.GuestRegistrationFormTest > fillForm_andSubmit_registrationIsSuccessful PASSED
```

---

## Assignment #2 — Dhaka Stock Exchange Share Price Scraper

### Assignment text

> Scrap the table data from this page:
> `https://dsebd.org/latest_share_price_scroll_by_value.php`
>
> Steps for Automation:
> 1. Print all the cell values
> 2. Store the values in a text file

### How this repo handles it

The live page is fully reachable; nothing needs a fixture. Selenium opens the page,
waits for `table.shares-table` (the price table — the page also renders a
"floatThead" sticky header table that we explicitly skip), reads every row, and
persists the table as a tab-aligned text file.

`DseSharePriceScrapeTest.scrapeAndStoreSharePriceTable()`:

1. Opens `https://dsebd.org/latest_share_price_scroll_by_value.php`.
2. Waits up to 30 s for `table.shares-table` with at least one `<tbody>` to appear.
3. Reads headers from the sticky-header clone (`table.shares-table.floatThead-table`).
4. Iterates every `<tbody>/<tr>` and every `<td>`, asserts each row has 11 cells,
   asserts there are ≥ 100 rows, and prints each cell value (the first 5 rows are
   shown on stdout; the full set goes into the file).
5. Writes `build/reports/screenshots/dse_share_prices.txt`.

### Recording

[`videos/dse-share-price-scrape.mp4`](videos/dse-share-price-scrape.mp4)

<video src="videos/dse-share-price-scrape.mp4" controls="controls" width="100%"></video>

### Test report (Gradle HTML)

[`build/reports/tests/test/index.html`](build/reports/tests/test/index.html) — click
into `com.assignment.tests.DseSharePriceScrapeTest` for the full PASSED record.

### Test report screenshot

Captured after the scrape completes:

![DSE table screenshot — Latest Share Price page with rows for ENVOYTEX, SHARPIND, SAIHAMTEX, etc.](build/reports/screenshots/scrapeAndStoreSharePriceTable.png)

### Scraped output (excerpt of `dse_share_prices.txt`)

```
Latest Share Price by Value scraped from https://dsebd.org/latest_share_price_scroll_by_value.php
Scraped on: 2026-09-09T12:49:04.560984966

#             TRADING CODE  LTP*          HIGH          LOW           CLOSEP*       YCP*          CHANGE        TRADE         VALUE (mn)    VOLUME
-             ------------  ----          ----          ---           -------       ----          ------        -----         ----------    ------
1             ENVOYTEX      73.6          75            68.5          0             70.1          3.5           3,114         244.2130      3,389,269
2             SHARPIND      19.7          19.7          18.6          0             18.9          0.8           4,281         172.0840      8,938,987
3             SAIHAMTEX     31.5          31.5          28.5          0             28.7          2.8           2,394         165.1460      5,382,002
4             ASIATICLAB    108.1         108.5         98            0             101.8         6.3           1,321         153.1870      1,432,679
5             SIPLC         127.8         127.8         117.1         0             116.2         11.6          1,365         144.0760      1,155,306
...
```

(Full file: 400 lines, 395 data rows — see `build/reports/screenshots/dse_share_prices.txt`.)

### Console output (excerpt)

```
[DSE] Table headers: [#, TRADING CODE, LTP*, HIGH, LOW, CLOSEP*, YCP*, CHANGE, TRADE, VALUE (mn), VOLUME]
[DSE] Row count: 395
[DSE] Row 1: [1, ENVOYTEX, 73.6, 75, 68.5, 0, 70.1, 3.5, 3,114, 244.2130, 3,389,269]
[DSE] Row 2: [2, SHARPIND, 19.7, 19.7, 18.6, 0, 18.9, 0.8, 4,281, 172.0840, 8,938,987]
[DSE] Row 3: [3, SAIHAMTEX, 31.5, 31.5, 28.5, 0, 28.7, 2.8, 2,394, 165.1460, 5,382,002]
[DSE] Row 4: [4, ASIATICLAB, 108.1, 108.5, 98, 0, 101.8, 6.3, 1,321, 153.1870, 1,432,679]
[DSE] Row 5: [5, SIPLC, 127.8, 127.8, 117.1, 0, 116.2, 11.6, 1,365, 144.0760, 1,155,306]
[DSE] Wrote 61676 chars to .../build/reports/screenshots/dse_share_prices.txt
[screenshot] .../build/reports/screenshots/scrapeAndStoreSharePriceTable.png

com.assignment.tests.DseSharePriceScrapeTest > scrapeAndStoreSharePriceTable PASSED
```

---

## Tech stack

| Tool | Version | Why |
|---|---|---|
| Java | 11+ (tested on OpenJDK 25) | required by Selenium 3 |
| Gradle | 4.4.1 (system) | runs `test` task and HTML reports |
| JUnit | 4.13.2 | required by the assignment brief ("JUnit") |
| Selenium WebDriver | 3.141.59 | browser automation |
| WebDriverManager | 5.9.1 | auto-downloads the matching ChromeDriver |
| Chrome / Chromium | any recent | the browser under test |
| `ffmpeg` | 8.x | encodes per-test JPEGs → MP4 |

## `.gitignore` highlights

The repo ignores `.gradle/`, `build/`, `out/`, `*.iml`, `.idea/`, `logs/`, etc., so the
checked-in source stays minimal — only the test code, the form fixture, the recording
script, the README, and the `videos/` directory of MP4s land in git.
