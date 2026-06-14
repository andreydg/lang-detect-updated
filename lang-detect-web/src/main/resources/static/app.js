// Fallback limits; the authoritative values are fetched from /api/config so the
// client stays in sync with the server's validation rules (single source: Java).
let MIN = 25;
let MAX = 100000;

const input = document.getElementById("input");
const counter = document.getElementById("counter");
const errorEl = document.getElementById("error");
const resultEl = document.getElementById("result");
const btnSingle = document.getElementById("btn-single");
const btnMulti = document.getElementById("btn-multi");
const hintEl = document.getElementById("hint");

function updateHint() {
  if (hintEl) {
    hintEl.textContent = `Use ${MIN} to ${MAX.toLocaleString()} characters. Longer samples usually improve confidence.`;
  }
}

async function loadConfig() {
  try {
    const res = await fetch("/api/config");
    if (!res.ok) return;
    const cfg = await res.json();
    if (Number.isFinite(cfg.minLength)) MIN = cfg.minLength;
    if (Number.isFinite(cfg.maxLength)) MAX = cfg.maxLength;
  } catch {
    // keep fallback defaults if the config endpoint is unreachable
  } finally {
    updateHint();
  }
}

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
  const n = input.value.length;
  const word = n === 1 ? "character" : "characters";
  counter.textContent = `${n} ${word}`;
}

async function run(mode) {
  setError("");
  resultEl.hidden = true;
  resultEl.innerHTML = "";

  const text = input.value;
  if (text.length > 0 && text.length < MIN) {
    setError(`Minimum length for language detection is ${MIN} characters`);
    return;
  }
  if (text.length > MAX) {
    setError(`Maximum length for language detection is ${MAX} characters`);
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

function renderResult(data) {
  if (!data.language && !data.segments?.length) {
    resultEl.hidden = true;
    return;
  }
  resultEl.hidden = false;
  if (data.mode === "multi" && data.segments?.length) {
    resultEl.innerHTML = `
      <p class="result-label">Segments</p>
      <table class="segments" role="grid">
        <thead>
          <tr><th scope="col">Language</th><th scope="col">Text</th></tr>
        </thead>
        <tbody>
          ${data.segments
            .map(
              (s) =>
                `<tr><th scope="row">${escapeHtml(s.language)}</th><td>${escapeHtml(s.text)}</td></tr>`,
            )
            .join("")}
        </tbody>
      </table>
    `;
  } else if (data.language) {
    const tag = data.languageTag
      ? `<p class="lang-meta">${escapeHtml(data.languageTag)}</p>`
      : "";
    resultEl.innerHTML = `
      <div class="lang-showcase">
        <p class="result-label">Most likely language</p>
        <p class="lang-name">${escapeHtml(data.language)}</p>
        ${tag}
      </div>
    `;
  }
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
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
