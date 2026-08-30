import { describe, expect, it } from "vitest";

import { inserirNoCursor } from "./inserir-no-cursor";

describe("inserirNoCursor", () => {
  it("acrescenta no fim quando não há campo", () => {
    expect(inserirNoCursor("oi", "🎉", null)).toEqual({ texto: "oi🎉", cursor: 4 });
  });

  it("substitui a seleção e posiciona o cursor depois do inserido", () => {
    const campo = { selectionStart: 1, selectionEnd: 3 };
    expect(inserirNoCursor("abcd", "👍🏽", campo)).toEqual({
      texto: "a👍🏽d",
      cursor: 1 + "👍🏽".length,
    });
  });
});
