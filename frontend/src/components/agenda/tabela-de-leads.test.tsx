import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { EtapaAtendimento } from "@/lib/lead/types";

import { TabelaDeLeads } from "./tabela-de-leads";

const textos = {
  titulo: "Agenda",
  descricao: "",
  vazia: "",
  carregando: "",
  erro: "",
  semResponsavel: "Sem responsável",
  contador: "",
  colunas: {
    lead: "Lead",
    telefone: "Telefone",
    cidade: "Cidade",
    etapa: "Etapa",
    tags: "Tags",
    responsavel: "Responsável",
    ultimoContato: "Último contato",
  },
  status: {
    ia: "Potencial (IA)",
    emAtendimento: "Em atendimento",
    finalizado: "Finalizado",
  },
  filtros: {} as never,
  paginacao: { anterior: "Anterior", proxima: "Próxima" },
} as never;

const etapa: EtapaAtendimento = {
  id: "etapa-1",
  nome: "Negociação",
  ordem: 1,
  corVisual: "var(--cor-primaria)",
};

describe("TabelaDeLeads", () => {
  it("mantém dados reais, avatar do responsável e compacta tags excedentes", () => {
    const abrirFicha = vi.fn();
    const abrirAtendimento = vi.fn();

    render(
      <TabelaDeLeads
        etapas={[etapa]}
        equipe={[
          {
            id: "user-1",
            nome: "Ana Beatriz",
            email: "ana@dev.local",
            papel: "ATENDENTE",
            statusPresenca: "ONLINE",
            ativo: true,
          },
        ]}
        textos={textos}
        leads={[
          {
            id: "lead-1",
            nome: "Marcos Vinícius",
            telefone: "5511999999999",
            empresa: "Vidraçaria Cristal",
            localizacao: "Taguatinga · DF",
            status: "EM_ATENDIMENTO",
            etapaAtendimentoId: "etapa-1",
            atendenteResponsavelId: "user-1",
            numAtendimentos: 1,
            numMensagens: 2,
            criadoEm: "2026-01-01T00:00:00Z",
            ultimaInteracaoEm: "2026-01-05T12:00:00Z",
            tags: [
              {
                tagId: "tag-1",
                nome: "Urgente",
                cor: "var(--cor-erro)",
                icone: null,
              },
              {
                tagId: "tag-2",
                nome: "Recorrente",
                cor: "var(--cor-info)",
                icone: null,
              },
              {
                tagId: "tag-3",
                nome: "Orçamento",
                cor: "var(--cor-atencao)",
                icone: null,
              },
            ],
          },
          {
            id: "lead-2",
            nome: "Cliente sem dono",
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
        onAbrirFicha={abrirFicha}
        onAbrirAtendimento={abrirAtendimento}
      />,
    );

    expect(screen.getByText("5511999999999")).toHaveClass("font-mono");
    expect(screen.getByTitle("Ana Beatriz")).toBeInTheDocument();
    expect(screen.getByText("Urgente")).toBeInTheDocument();
    expect(screen.getByText("Recorrente")).toBeInTheDocument();
    expect(screen.getByText("+1")).toBeInTheDocument();
    expect(screen.getByText("Sem responsável")).toHaveClass("italic");
    expect(screen.getByText("Negociação")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Marcos Vinícius"));
    expect(abrirFicha).toHaveBeenCalledWith(
      expect.objectContaining({ id: "lead-1" }),
    );
  });
});
