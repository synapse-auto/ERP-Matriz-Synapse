import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      painel: {
        titulo: "Detalhes do lead",
        notasInternas: "Notas internas",
        secoes: { resumo: "Resumo por IA e notas", programadas: "Mensagens programadas", lembretes: "Lembretes" },
        vazioProgramadas: "Nenhuma mensagem programada",
        vazioLembretes: "Nenhum lembrete",
      },
    },
    painelLead: {
      dados: { telefone: "Telefone", email: "E-mail", localizacao: "Localização" },
      etapa: { titulo: "Etapa" },
      contadores: { atendimentos: "Atendimentos", mensagens: "Mensagens" },
      tags: { titulo: "Etiquetas" },
      resumoIa: { vazio: "Sem notas." },
    },
  }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useLead: () => ({
    data: {
      id: "lead-1",
      nome: "Marcos Vinícius",
      fotoUrl: null,
      empresa: "Vidraçaria Cristal",
      telefone: "(61) 99999-0000",
      email: "marcos@cliente.com",
      localizacao: "Taguatinga · DF",
      etapaAtendimentoId: "etapa-1",
      numAtendimentos: 3,
      numMensagens: 20,
      resumoIa: "Cliente pediu orçamento de box.",
      notas: "",
    },
  }),
  useEtapas: () => ({ data: [{ id: "etapa-1", nome: "Orçamento", ordem: 1, corVisual: "#1F74E0" }] }),
  useTagsDoLead: () => ({ data: [] }),
  useTodasAsTags: () => ({ data: [] }),
  useVincularTag: () => ({ mutate: vi.fn() }),
  useDesvincularTag: () => ({ mutate: vi.fn() }),
}));

vi.mock("@/lib/suporte/use-suporte", () => ({
  useMensagensProgramadasDoLead: () => ({ data: { mensagens: [], pagina: 0, temMais: false } }),
  useLembretesDoLead: () => ({ data: { lembretes: [], pagina: 0, temMais: false } }),
}));

import { PainelDaConversa } from "./painel-da-conversa";

describe("painel da conversa", () => {
  it("mostra contadores, etapa e resumo por IA aberto por padrão — sem seção de arquivos", () => {
    render(<PainelDaConversa leadId="lead-1" />);

    expect(screen.getByText("Marcos Vinícius")).toBeInTheDocument();
    expect(screen.getByText("Orçamento")).toBeInTheDocument();
    expect(screen.getByText("Cliente pediu orçamento de box.")).toBeInTheDocument();
    expect(screen.queryByText(/arquivos/i)).not.toBeInTheDocument();
  });

  it("mensagens programadas e lembretes começam fechados e abrem com o estado vazio real", () => {
    render(<PainelDaConversa leadId="lead-1" />);

    expect(screen.queryByText("Nenhuma mensagem programada")).not.toBeInTheDocument();
    fireEvent.click(screen.getByText("Mensagens programadas"));
    expect(screen.getByText("Nenhuma mensagem programada")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Lembretes"));
    expect(screen.getByText("Nenhum lembrete")).toBeInTheDocument();
  });
});
