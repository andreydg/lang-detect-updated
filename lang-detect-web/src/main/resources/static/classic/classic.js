const MIN_LENGTH = 25;
const MAX_LENGTH = 100000;

function trim(str) {
  let s = str.replace(/^\s\s*/, "");
  const ws = /\s/;
  let i = s.length;
  while (ws.test(s.charAt(--i)));
  return s.slice(0, i + 1);
}

function updateCounter(counterElement, inputElement) {
  const charString = inputElement.value.length === 1 ? " character" : " characters";
  counterElement.innerHTML = inputElement.value.length + charString;
}

function updateAndClear(inputElement, errorId, counterId) {
  const errorElement = document.getElementById(errorId);
  errorElement.innerHTML = "";
  const counterElement = document.getElementById(counterId);
  updateCounter(counterElement, inputElement);
}

function checkLength(inputId, errorId, counterId, isMulti) {
  const inputElement = document.getElementById(inputId);
  const errorElement = document.getElementById(errorId);
  const resultsEl = document.getElementById("classic-results");
  resultsEl.innerHTML = "";

  if (inputElement) {
    inputElement.value = trim(inputElement.value);
    const counterElement = document.getElementById(counterId);
    updateCounter(counterElement, inputElement);
    const inputLength = inputElement.value.length;
    if (inputLength >= MIN_LENGTH && inputLength <= MAX_LENGTH) {
      runDetect(inputElement.value, isMulti ? "multi" : "single", errorElement, resultsEl);
    } else if (inputLength < MIN_LENGTH && inputLength > 0) {
      errorElement.innerHTML =
        "Minimum length for language detection is " + MIN_LENGTH + " characters";
    } else if (inputLength > MAX_LENGTH) {
      errorElement.innerHTML =
        "Maximum length for language detection is " + MAX_LENGTH + " characters";
    }
  }
}

async function runDetect(text, mode, errorElement, resultsEl) {
  errorElement.innerHTML = "";
  try {
    const res = await fetch("/api/detect", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text, mode }),
    });
    const data = await res.json();
    if (!res.ok || data.error) {
      errorElement.innerHTML = escapeHtml(data.error || "Request failed");
      return;
    }
    if (mode === "multi" && data.segments && data.segments.length) {
      let rows = "";
      for (let i = 0; i < data.segments.length; i++) {
        const s = data.segments[i];
        rows +=
          "<tr><td class='stringMark'>" +
          escapeHtml(s.text) +
          "</td><td class='languageMark'>" +
          escapeHtml(s.language) +
          "</td></tr>";
      }
      resultsEl.innerHTML = "<table class='output'>" + rows + "</table>";
    } else if (data.language) {
      resultsEl.innerHTML =
        '<div class="detectedLanguage"><span>' + escapeHtml(data.language) + "</span></div>";
    }
  } catch (e) {
    errorElement.innerHTML = escapeHtml(e instanceof Error ? e.message : "Request failed");
  }
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

document.addEventListener("DOMContentLoaded", function () {
  const inputArea = document.getElementById("inputArea");
  if (inputArea) {
    updateCounter(document.getElementById("inputCounter"), inputArea);
  }
});
