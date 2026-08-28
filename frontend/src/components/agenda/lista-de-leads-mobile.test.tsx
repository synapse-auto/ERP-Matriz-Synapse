import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ListaDeLeadsMobile } from "./lista-de-leads-mobile";

const textos = {
  semResponsavel: "Sem responsável",
  indiceAlfabetico: "Índice alfabético",
  status: {
    ia: "Potencial (IA)",
    emAtendimento: "Em atendimento",
    finalizado: "Finalizado",
  },
} as never;

describe("ListaDeLeadsMobile", () => {
  it("agrupa contatos por letra e abre a ficha no clique", () => {
    const abrirFicha = vi.fn();
    render(
      <ListaDeLeadsMobile
        etapas={[{ id: "etapa-1", nome: "Orçamento", ordem: 1, corVisual: "var(--primary)" }]}
        equipe={[{ id: "user-1", nome: "Ana Beatriz", email: "ana@dev.local", papel: "ATENDENTE", statusPresenca: "ONLINE", ativo: true }]}
        textos={textos}
        onAbrirFicha={abrirFicha}
        leads={[
          {
            id: "lead-1",
            nome: "Marcos Vinícius",
            telefone: null,
            empresa: "Vidraçaria Cristal",
            localizacao: "Ceilândia · DF",
            status: "EM_ATENDIMENTO",
            etapaAtendimentoId: "etapa-1",
            atendenteResponsavelId: "user-1",
            numAtendimentos: 1,
            numMensagens: 2,
            criadoEm: "2026-01-01T00:00:00Z",
            ultimaInteracaoEm: null,
            tags: [],
          },
          {
            id: "lead-2",
            nome: "Camila Nunes",
            telefone: null,
            empresa: null,
            localizacao: null,
            status: "IA",
            etapaAtendimentoId: null,
            atendenteResponsavelId: null,
            numAtendimentos: 0,
            numMensagens: 0,
            criadoEm: "2026-01-01T00:00:00Z",
            ultimaInteracaoEm: null,
            tags: [],
          },
        ]}
      />,
    );

    expect(screen.getByRole("heading", { name: "M" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "C" })).toBeInTheDocument();
    expect(screen.getByText("Vidraçaria Cristal · Ceilândia · DF")).toBeInTheDocument();
    expect(screen.getByText("Orçamento")).toBeInTheDocument();
    expect(screen.getByText("Ana Beatriz")).toBeInTheDocument();
    expect(screen.getByLabelText("Índice alfabético")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Marcos Vinícius/ }));
    expect(abrirFicha).toHaveBeenCalledWith(expect.objectContaining({ id: "lead-1" }));
  });
});
