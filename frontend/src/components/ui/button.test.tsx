import { describe, expect, it } from "vitest";

import { buttonVariants } from "@/components/ui/button";

describe("buttonVariants", () => {
  it("usa raios derivados do tema nas variantes principais", () => {
    expect(buttonVariants({ size: "default" })).toContain(
      "rounded-[var(--raio-botao-lg)]",
    );
    expect(buttonVariants({ size: "lg" })).toContain(
      "rounded-[var(--raio-botao-lg)]",
    );
    expect(buttonVariants({ size: "icon" })).toContain(
      "rounded-[var(--raio-botao-lg)]",
    );
    expect(buttonVariants({ size: "xs" })).toContain(
      "rounded-[var(--raio-botao-xs)]",
    );
    expect(buttonVariants({ size: "icon-xs" })).toContain(
      "rounded-[var(--raio-botao-xs)]",
    );
    expect(buttonVariants({ size: "sm" })).toContain(
      "rounded-[var(--raio-botao-sm)]",
    );
    expect(buttonVariants({ size: "icon-sm" })).toContain(
      "rounded-[var(--raio-botao-sm)]",
    );
  });

  it("preserva o raio pill fora da escala de botões", () => {
    expect(buttonVariants({ size: "default" })).not.toContain("rounded-full");
  });
});
