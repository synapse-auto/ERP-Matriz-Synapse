import { afterEach, describe, expect, it, vi } from "vitest";

import { baixarUrlAssinada } from "./baixar-url-assinada";

describe("baixarUrlAssinada", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("abre só http(s) absoluto e recusa caminho /api protegido", () => {
    const clicks: string[] = [];
    const original = document.createElement.bind(document);
    vi.spyOn(document, "createElement").mockImplementation((tag: string) => {
      const el = original(tag);
      if (tag === "a") {
        vi.spyOn(el, "click").mockImplementation(() => {
          clicks.push(el.getAttribute("href") ?? "");
        });
      }
      return el;
    });

    baixarUrlAssinada("https://fake-storage.local/foto.png?token=abc");
    baixarUrlAssinada("/api/v1/leads/x/midias/y/download");
    baixarUrlAssinada("javascript:alert(1)");

    expect(clicks).toEqual(["https://fake-storage.local/foto.png?token=abc"]);
  });
});
