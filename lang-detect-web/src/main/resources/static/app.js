// Fallback limits; the authoritative values are fetched from /api/config so the
// client stays in sync with the server's validation rules (single source: Java).
const DEFAULTS = { min: 25, max: 100000 };

// ---------------------------------------------------------------------------
// Pure helpers (exported for unit tests; no DOM access)
// ---------------------------------------------------------------------------

export function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export function counterText(n) {
  return `${n} ${n === 1 ? "character" : "characters"}`;
}

export function hintText(min, max) {
  return `Use ${min} to ${max.toLocaleString()} characters. Longer samples usually improve confidence.`;
}

/** Returns a validation error string, or null when the length is acceptable. */
export function lengthError(len, min, max) {
  if (len > 0 && len < min) {
    return `Minimum length for language detection is ${min} characters`;
  }
  if (len > max) {
    return `Maximum length for language detection is ${max} characters`;
  }
  return null;
}

/** Builds the result markup for a detection response, or null when empty. */
export function resultHtml(data) {
  if (!data || (!data.language && !data.segments?.length)) {
    return null;
  }
  if (data.mode === "multi" && data.segments?.length) {
    const rows = data.segments
      .map(
        (s) =>
          `<tr><th scope="row">${escapeHtml(s.language)}</th><td>${escapeHtml(s.text)}</td></tr>`,
      )
      .join("");
    return `
      <p class="result-label">Segments</p>
      <table class="segments" role="grid">
        <thead>
          <tr><th scope="col">Language</th><th scope="col">Text</th></tr>
        </thead>
        <tbody>
          ${rows}
        </tbody>
      </table>
    `;
  }
  if (data.language) {
    const tag = data.languageTag
      ? `<p class="lang-meta">${escapeHtml(data.languageTag)}</p>`
      : "";
    return `
      <div class="lang-showcase">
        <p class="result-label">Most likely language</p>
        <p class="lang-name">${escapeHtml(data.language)}</p>
        ${tag}
      </div>
    `;
  }
  return null;
}

/** Extracts {min, max} overrides from a /api/config payload, ignoring junk. */
export function parseConfig(cfg) {
  const out = {};
  if (cfg && Number.isFinite(cfg.minLength)) out.min = cfg.minLength;
  if (cfg && Number.isFinite(cfg.maxLength)) out.max = cfg.maxLength;
  return out;
}

// ---------------------------------------------------------------------------
// DOM wiring (browser only). Exported so tests can drive it explicitly.
// ---------------------------------------------------------------------------

export function init() {
  const input = document.getElementById("input");
  if (!input) {
    return; // not on the detection page (e.g. imported in a unit test)
  }
  const counter = document.getElementById("counter");
  const errorEl = document.getElementById("error");
  const resultEl = document.getElementById("result");
  const btnSingle = document.getElementById("btn-single");
  const btnMulti = document.getElementById("btn-multi");
  const hintEl = document.getElementById("hint");

  const limits = { ...DEFAULTS };

  function setError(msg) {
    if (!msg) {
      errorEl.hidden = true;
      errorEl.textContent = "";
      return;
    }
    errorEl.hidden = false;
    errorEl.textContent = msg;
  }

  function updateCounter() {
    counter.textContent = counterText(input.value.length);
  }

  function updateHint() {
    if (hintEl) {
      hintEl.textContent = hintText(limits.min, limits.max);
    }
  }

  function renderResult(data) {
    const html = resultHtml(data);
    if (html === null) {
      resultEl.hidden = true;
      resultEl.innerHTML = "";
      return;
    }
    resultEl.hidden = false;
    resultEl.innerHTML = html;
  }

  async function run(mode) {
    setError("");
    resultEl.hidden = true;
    resultEl.innerHTML = "";

    const text = input.value;
    const err = lengthError(text.length, limits.min, limits.max);
    if (err) {
      setError(err);
      return;
    }

    btnSingle.disabled = true;
    btnMulti.disabled = true;
    try {
      const res = await fetch("/api/detect", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, mode }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || `Request failed (${res.status})`);
        return;
      }
      if (data.error) {
        setError(data.error);
        return;
      }
      renderResult(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed");
    } finally {
      btnSingle.disabled = false;
      btnMulti.disabled = false;
    }
  }

  async function loadConfig() {
    try {
      const res = await fetch("/api/config");
      if (!res.ok) return;
      Object.assign(limits, parseConfig(await res.json()));
    } catch {
      // keep fallback defaults if the config endpoint is unreachable
    } finally {
      updateHint();
    }
  }

  input.addEventListener("input", () => {
    updateCounter();
    setError("");
  });
  btnSingle.addEventListener("click", () => run("single"));
  btnMulti.addEventListener("click", () => run("multi"));

  updateCounter();
  updateHint();
  loadConfig();
}

// Auto-initialize in the browser. Module scripts are deferred, so the DOM is
// already parsed. In a unit-test import there is no #input, so init() no-ops.
if (typeof document !== "undefined") {
  init();
}
