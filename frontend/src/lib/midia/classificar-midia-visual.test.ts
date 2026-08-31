import { describe, expect, it } from "vitest";

import { classificarMidiaVisual } from "./classificar-midia-visual";
import { classificarOrigemDeRecursoVisual } from "./origem-de-recurso-visual";

describe("classificarMidiaVisual", () => {
  it("separa imagem, vídeo, áudio, PDF e o resto", () => {
    expect(classificarMidiaVisual("IMAGEM", "image/jpeg", "a.jpg")).toBe("imagem");
    expect(classificarMidiaVisual("DOCUMENTO", "video/mp4", "clip.mp4")).toBe("video");
    expect(classificarMidiaVisual("AUDIO", "audio/ogg", "voz.ogg")).toBe("audio");
    expect(classificarMidiaVisual("DOCUMENTO", "application/pdf", "x.pdf")).toBe("pdf");
    expect(classificarMidiaVisual("DOCUMENTO", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "a.docx")).toBe("documento");
  });
});

describe("classificarOrigemDeRecursoVisual", () => {
  it("trata caminho relativo como autenticado e http(s) como absoluto", () => {
    expect(classificarOrigemDeRecursoVisual("/api/v1/leads/1/foto")).toEqual({
      tipo: "autenticada",
      caminho: "/api/v1/leads/1/foto",
    });
    expect(classificarOrigemDeRecursoVisual("https://cdn.example/f.webp")).toEqual({
      tipo: "absoluta",
      url: "https://cdn.example/f.webp",
    });
    expect(classificarOrigemDeRecursoVisual("javascript:alert(1)")).toEqual({ tipo: "invalida" });
  });
});
