import { describe, expect, it } from "vitest";

import { modoDaGrade } from "./modo-da-grade";

describe("modoDaGrade", () => {
  it("mantem as tres colunas quando conversa, lista e ficha cabem", () => {
    expect(modoDaGrade(1010)).toBe("ampla");
    expect(modoDaGrade(1400)).toBe("ampla");
  });

  it("retira uma coluna no limite medido, inclusive quando a sidebar cresce", () => {
    expect(modoDaGrade(1009)).toBe("dupla");
    expect(modoDaGrade(724)).toBe("dupla");
    expect(modoDaGrade(666)).toBe("dupla");
  });

  it("usa uma unica coluna quando nem lista e conversa mantem largura util", () => {
    expect(modoDaGrade(665)).toBe("unica");
    expect(modoDaGrade(390)).toBe("unica");
  });
});
