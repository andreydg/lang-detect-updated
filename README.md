lang-detect
===========

Language detection with language boundary recognition.

## Build

Requires **Java 25** (see parent `pom.xml` `java.version`). The repo includes a **Maven Wrapper** (`./mvnw`), so you do not need a global Maven install.

```bash
./mvnw verify
```

Modules:

* **lang-detect-core** — detection library (`language.model`, classifiers, utilities).
* **lang-detect-utils** — optional helpers (e.g. `NgramLanguageDetectorWithUtils`).
* **lang-detect-web** — Spring Boot 4 app: JSON API + static UI.
* **lang-detect-test** — unit tests (training data under `lang-detect/war/`).

Language models are still stored under `lang-detect/war/languagemodels/` and are copied into the web module at build time.

## Run the web app

From the repository root:

```bash
./mvnw -pl lang-detect-web -am spring-boot:run
```

(`-am` builds `lang-detect-core` first; the parent POM skips the Spring Boot plugin so only `lang-detect-web` runs.)

Then open `http://localhost:8080/`. The UI calls `POST /api/detect` with JSON `{ "text": "...", "mode": "single" | "multi" }`.

Other endpoints:

* `GET /api/config` — returns the server-side validation limits (`{ "minLength", "maxLength" }`) so the UI stays in sync with the server.
* `GET /actuator/health` — health probe (with `liveness`/`readiness` groups) for Cloud Run / uptime checks.

### Configuration

Tunable via `langdetect.*` properties (see `DetectionProperties`); all have defaults:

| Property | Default | Purpose |
|---|---|---|
| `langdetect.min-length` | `25` | Minimum input length (characters). |
| `langdetect.max-length` | `100000` | Maximum input length (characters). |
| `langdetect.max-payload-bytes` | `1048576` | Hard cap on request body size (413 above it). |
| `langdetect.warmup` | `true` | Warm the detector at startup so the first request is fast. |
| `langdetect.cache-max-entries` | `500` | Max entries in the in-memory result cache. |
| `langdetect.cache-max-text-length` | `4096` | Only cache results for inputs at or below this length. |
| `langdetect.allowed-origins` | _(empty)_ | CORS origins for `/api/**`; empty means same-origin only. |

### Model path

The app looks for a `languagemodels` directory in this order:

1. Environment variable **`LANG_DETECT_BASE_PATH`** — directory that **contains** `languagemodels/` (not the folder itself).
2. Current working directory (if `./languagemodels` exists).
3. `lang-detect-web/target/classes` during development (after a build).
4. Otherwise models are unpacked from the classpath into a temp directory (fat JAR).

### JAR

Build the web module and its dependencies:

```bash
./mvnw -pl lang-detect-web -am package
java -jar lang-detect-web/target/lang-detect-web-1.0.0-SNAPSHOT.jar
```

### Tests

`LogisticRegressionSingleLangTest` is excluded from the default Maven test run because logistic-regression scores are numerically sensitive on modern JDKs; other tests still run.

Override test data location:

```bash
./mvnw -pl lang-detect-test test -Dlang.detect.test.data=/path/to/war
```

(`war` must contain a `languagemodels` directory.)

### UI tests

The front-end (`lang-detect-web/src/main/resources/static/app.js`) has unit tests
run with [Vitest](https://vitest.dev) + jsdom:

```bash
cd lang-detect-web
npm install
npm test
```

Both the Maven and UI test suites run in CI (`.github/workflows/ci.yml`).

## Data directory

Training and n-gram assets live under **`lang-detect/war/languagemodels/`** (historical path; only this tree remains under `lang-detect/`). The Maven web module copies `languagemodels/**` at build time.

### Utility scripts

Shell scripts in **`lang-detect-utils/`** run `LanguageDetectorTester` via **`exec-maven-plugin`** from the repo root (they call `./mvnw`). Example: `./lang-detect-utils/detectLang.sh`.

## License

Apache License 2.0 — see `LICENSE.md`.
