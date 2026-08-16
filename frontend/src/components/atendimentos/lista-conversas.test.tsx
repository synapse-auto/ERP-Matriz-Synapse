import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

const cartoes: CartaoAtendimento[] = [
  {
    atendimentoId: "protocolo-001",
    leadId: "lead-1",
    leadNome: "Ana Vidros",
    leadFotoUrl: null,
    leadEmpresa: "Vidraçaria Central",
    canalTipo: "WHATSAPP",
    etapaId: "etapa-1",
    etapaNome: "Negociação",
    etapaCor: "#2563eb",
    status: "EM_ATENDIMENTO",
    atendenteId: "usuario-1",
    atendenteNome: "Jardel Lima",
    ultimaMensagemPreview: "Preciso do orçamento",
    ultimaMensagemRemetenteTipo: "LEAD",
    ultimaMensagemEm: "2026-08-16T12:30:00Z",
    ultimaMensagemDoLeadEm: "2026-08-16T12:30:00Z",
  },
  {
    atendimentoId: "protocolo-002",
    leadId: "lead-2",
    leadNome: "Bruno Almeida",
    leadFotoUrl: null,
    leadEmpresa: null,
    canalTipo: "WHATSAPP",
    etapaId: null,
    etapaNome: null,
    etapaCor: null,
    status: "EM_IA",
    atendenteId: null,
    atendenteNome: null,
    ultimaMensagemPreview: null,
    ultimaMensagemRemetenteTipo: null,
    ultimaMensagemEm: null,
    ultimaMensagemDoLeadEm: null,
  },
];

vi.mock("@/lib/atendimento/use-atendimentos", () => ({
  useAtendimentos: () => ({ data: cartoes, isLoading: false }),
  useContagemDeAtendimentos: () => ({
    data: { TODOS: 2, ATIVOS: 1, PENDENTES: 1, POTENCIAIS: 1 },
  }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    menu: { itens: { atendimentos: "Atendimentos" } },
    atendimentos: {
      canais: { whatsapp: "WhatsApp" },
      lista: {
        busca: "Buscar cliente ou protocolo...",
        filtros: "Filtros da lista",
      },
      visoes: {
        todos: "Todos",
        ativos: "Ativos",
        pendentes: "Pendentes",
        potenciais: "Potenciais",
      },
      filtros: { etapa: "Etapa", atendente: "Atendente" },
      cartao: { semAtendente: "Sem atendente", vazio: "Nenhuma conversa" },
    },
  }),
}));

import { ListaConversas } from "./lista-conversas";

describe("ListaConversas", () => {
  it("mostra as quatro visões e busca por cliente, empresa ou protocolo", () => {
    const abrir = vi.fn();
    render(
      <ListaConversas
        selecionadoId={null}
        visaoInicial={null}
        onAbrirAtendimento={abrir}
      />,
    );

    expect(
      screen.getByRole("heading", { name: "Atendimentos" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("tab")).toHaveLength(4);
    expect(screen.getByRole("tab", { name: /Todos/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Ativos/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Pendentes/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Potenciais/ })).toBeInTheDocument();

    fireEvent.change(
      screen.getByPlaceholderText("Buscar cliente ou protocolo..."),
      {
        target: { value: "protocolo-002" },
      },
    );

    expect(screen.queryByText("Ana Vidros")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Bruno Almeida/ }));
    expect(abrir).toHaveBeenCalledWith(cartoes[1]);
  });
});
