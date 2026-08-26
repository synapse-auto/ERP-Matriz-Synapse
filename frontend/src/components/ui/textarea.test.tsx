import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Textarea } from "./textarea";

describe("Textarea", () => {
  it("ajusta valores controlados definidos por codigo e volta a altura minima ao limpar", () => {
    const { rerender } = render(<Textarea aria-label="mensagem" value="linha inicial" onChange={() => {}} />);
    const campo = screen.getByLabelText("mensagem") as HTMLTextAreaElement;
    Object.defineProperty(campo, "scrollHeight", { configurable: true, value: 120 });

    rerender(<Textarea aria-label="mensagem" value={`${"texto ".repeat(20)}\nsegunda linha`} onChange={() => {}} />);
    expect(campo.style.height).toBe("120px");

    Object.defineProperty(campo, "scrollHeight", { configurable: true, value: 64 });
    rerender(<Textarea aria-label="mensagem" value="" onChange={() => {}} />);
    expect(campo.style.height).toBe("64px");
  });

  it("mantem palavra longa dentro da largura e cresce somente na altura", () => {
    render(<Textarea aria-label="mensagem" rows={1} />);
    const campo = screen.getByLabelText("mensagem") as HTMLTextAreaElement;
    Object.defineProperty(campo, "scrollHeight", { configurable: true, value: 96 });

    fireEvent.change(campo, { target: { value: "x".repeat(300) } });

    expect(campo.className).toContain("w-full");
    expect(campo.className).toContain("min-w-0");
    expect(campo.className).toContain("break-words");
    expect(campo.style.height).toBe("96px");

    Object.defineProperty(campo, "scrollHeight", { configurable: true, value: 168 });
    fireEvent.change(campo, { target: { value: `${"x".repeat(300)}\nsegunda linha` } });

    expect(campo.style.height).toBe("168px");
  });
});
