import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SeletorData } from "./seletor-data";

describe("SeletorData", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("formata a data e localiza o calendário em pt-BR com domingo primeiro", async () => {
    // O calendário tem de abrir no mês do valor, não no de hoje. Sem isso o teste
    // passa em agosto e quebra no CI no dia 1º de setembro (UTC).
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 8, 1));

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
