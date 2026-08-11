import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SeletorDataHora } from "./seletor-data-hora";

describe("SeletorDataHora", () => {
  it("exibe data em pt-BR e hora sem input datetime-local", () => {
    const { container } = render(
      <SeletorDataHora
        valor="2026-08-11T14:35"
        placeholderData="Selecione a data"
        rotuloHora="Hora"
        rotuloMinuto="Minuto"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: /11\/08\/2026/i })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Hora" })).toHaveTextContent("14");
    expect(screen.getByRole("combobox", { name: "Minuto" })).toHaveTextContent("35");
    expect(container.querySelector('input[type="datetime-local"]')).toBeNull();
  });
});
