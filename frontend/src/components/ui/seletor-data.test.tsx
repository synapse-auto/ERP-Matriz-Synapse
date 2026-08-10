import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SeletorData } from "./seletor-data";

describe("SeletorData", () => {
  it("formata a data e localiza o calendário em pt-BR com domingo primeiro", async () => {
    render(
      <SeletorData
        valor="2026-08-10"
        placeholder="Data"
        onChange={vi.fn()}
      />,
    );

    const gatilho = screen.getByRole("button", { name: /10\/08\/2026/i });
    expect(gatilho).toBeInTheDocument();

    fireEvent.click(gatilho);

    expect(await screen.findByText(/agosto/i)).toBeInTheDocument();
    const cabecalhos = Array.from(document.querySelectorAll("thead th"));
    expect(cabecalhos).not.toHaveLength(0);
    expect(cabecalhos[0]?.getAttribute("aria-label")?.toLowerCase()).toContain("domingo");
  });
});
