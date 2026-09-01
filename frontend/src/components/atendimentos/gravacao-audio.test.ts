import { describe, expect, it } from "vitest";

import { arquivoDaGravacao, tipoPrincipalDoMime } from "./gravacao-audio";

describe("arquivoDaGravacao", () => {
  it("remove codecs do MIME e usa extensao m4a", () => {
    const blob = new Blob([new Uint8Array([1, 2, 3])], {
      type: "audio/mp4;codecs=mp4a.40.2",
    });

    const arquivo = arquivoDaGravacao(blob, "audio/mp4;codecs=mp4a.40.2", 42);

    expect(arquivo.type).toBe("audio/mp4");
    expect(arquivo.name).toBe("gravacao-42.m4a");
  });

  it("grava ogg com extensao e MIME que a Meta aceita", () => {
    const blob = new Blob([new Uint8Array([1])], {
      type: "audio/ogg;codecs=opus",
    });

    const arquivo = arquivoDaGravacao(blob, "audio/ogg;codecs=opus", 7);

    expect(arquivo.type).toBe("audio/ogg");
    expect(arquivo.name).toBe("gravacao-7.ogg");
  });

  it("tipoPrincipalDoMime ignora parametros", () => {
    expect(tipoPrincipalDoMime("audio/mp4;codecs=mp4a.40.2")).toBe("audio/mp4");
    expect(tipoPrincipalDoMime("audio/ogg")).toBe("audio/ogg");
  });
});
