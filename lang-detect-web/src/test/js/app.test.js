import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import {
  counterText,
  escapeHtml,
  hintText,
  init,
  lengthError,
  parseConfig,
  resultHtml,
} from "../../main/resources/static/app.js";

describe("pure helpers", () => {
  test("escapeHtml neutralizes markup characters", () => {
    expect(escapeHtml('<b>"a&b"</b>')).toBe("&lt;b&gt;&quot;a&amp;b&quot;&lt;/b&gt;");
  });

  test("counterText pluralizes correctly", () => {
    expect(counterText(0)).toBe("0 characters");
    expect(counterText(1)).toBe("1 character");
    expect(counterText(2)).toBe("2 characters");
  });

  test("hintText includes the bounds and guidance", () => {
    const h = hintText(25, 100000);
    expect(h).toContain("25");
    expect(h).toContain("characters");
    expect(h).toContain("Longer samples");
  });

  test("lengthError enforces min/max with empty allowed", () => {
    expect(lengthError(0, 25, 100000)).toBeNull(); // empty is allowed
    expect(lengthError(10, 25, 100000)).toBe(
      "Minimum length for language detection is 25 characters",
    );
    expect(lengthError(25, 25, 100000)).toBeNull(); // at the boundary
    expect(lengthError(100001, 25, 100000)).toBe(
      "Maximum length for language detection is 100000 characters",
    );
  });

  test("parseConfig keeps only finite numbers", () => {
    expect(parseConfig({ minLength: 30, maxLength: 5000 })).toEqual({ min: 30, max: 5000 });
    expect(parseConfig({ minLength: "x" })).toEqual({});
    expect(parseConfig(null)).toEqual({});
  });
});

describe("resultHtml", () => {
  test("returns null for empty payloads", () => {
    expect(resultHtml(null)).toBeNull();
    expect(resultHtml({ mode: "single" })).toBeNull();
    expect(resultHtml({ mode: "multi", segments: [] })).toBeNull();
  });

  test("renders a single-language result and escapes values", () => {
    const html = resultHtml({ mode: "single", language: "English", languageTag: "en" });
    expect(html).toContain("Most likely language");
    expect(html).toContain("English");
    expect(html).toContain("en");
  });

  test("renders multi-language segments and escapes user text", () => {
    const html = resultHtml({
      mode: "multi",
      segments: [{ text: "<script>x", language: "English", languageTag: "en" }],
    });
    expect(html).toContain("Segments");
    expect(html).toContain("&lt;script&gt;x");
    expect(html).not.toContain("<script>x");
  });
});

describe("init() DOM wiring", () => {
  function setupDom() {
    document.body.innerHTML = `
      <p id="hint"></p>
      <textarea id="input"></textarea>
      <span id="counter"></span>
      <div id="error" hidden></div>
      <section id="result" hidden></section>
      <button id="btn-single"></button>
      <button id="btn-multi"></button>
    `;
  }

  beforeEach(() => {
    setupDom();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  test("counter updates on input", () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("no network")));
    init();
    const input = document.getElementById("input");
    input.value = "hello";
    input.dispatchEvent(new Event("input"));
    expect(document.getElementById("counter").textContent).toBe("5 characters");
  });

  test("short input shows a validation error without calling detect", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) });
    vi.stubGlobal("fetch", fetchMock);
    init();
    // settle the loadConfig() call fired during init
    await Promise.resolve();

    const input = document.getElementById("input");
    input.value = "too short";
    document.getElementById("btn-single").click();
    await Promise.resolve();

    const error = document.getElementById("error");
    expect(error.hidden).toBe(false);
    expect(error.textContent).toContain("Minimum length");
    // /api/detect must not have been called for invalid input
    expect(fetchMock).not.toHaveBeenCalledWith("/api/detect", expect.anything());
  });

  test("loadConfig overrides limits and refreshes the hint", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url) => {
        if (url === "/api/config") {
          return { ok: true, json: async () => ({ minLength: 40, maxLength: 9000 }) };
        }
        throw new Error("unexpected url " + url);
      }),
    );
    init();
    await Promise.resolve();
    await Promise.resolve();

    expect(document.getElementById("hint").textContent).toContain("40");
  });

  test("a successful detect renders the result", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url) => {
        if (url === "/api/config") {
          return { ok: true, json: async () => ({}) };
        }
        return {
          ok: true,
          json: async () => ({ mode: "single", language: "Spanish", languageTag: "es" }),
        };
      }),
    );
    init();
    const input = document.getElementById("input");
    input.value = "x".repeat(50);
    document.getElementById("btn-single").click();
    // allow fetch + json microtasks to settle
    await new Promise((r) => setTimeout(r, 0));

    const result = document.getElementById("result");
    expect(result.hidden).toBe(false);
    expect(result.innerHTML).toContain("Spanish");
  });

  test("a server error is surfaced to the user", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url) => {
        if (url === "/api/config") {
          return { ok: true, json: async () => ({}) };
        }
        return { ok: false, status: 500, json: async () => ({ error: "Internal server error" }) };
      }),
    );
    init();
    const input = document.getElementById("input");
    input.value = "x".repeat(50);
    document.getElementById("btn-single").click();
    await new Promise((r) => setTimeout(r, 0));

    expect(document.getElementById("error").textContent).toBe("Internal server error");
  });
});
