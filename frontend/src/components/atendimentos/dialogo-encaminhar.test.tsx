import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento, MensagemResposta } from "@/lib/atendimento/types";

vi.mock("@/lib/atendimento/api", () => ({
  listarAtendimentos: vi.fn(),
  encaminharMensagem: vi.fn(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      encaminhar: {
        titulo: "Encaminhar mensagem",
        busca: "Buscar conversa",
        vazio: "Nenhuma conversa encontrada.",
        confirmar: "Encaminhar",
        cancelar: "Cancelar",
        origem: "De",
        destino: "Para",
        erro: "Não foi possível encaminhar a mensagem.",
        incompativel: "Este tipo de mensagem não pode ser encaminhado.",
      },
    },
  }),
}));

import * as api from "@/lib/atendimento/api";
import { DialogoEncaminhar } from "./dialogo-encaminhar";

const origem: MensagemResposta = {
  id: "msg-1",
  remetenteTipo: "LEAD",
  remetenteId: null,
  remetenteNome: "Maria",
  tipo: "TEXTO",
  conteudo: "orçamento",
  midiaUrl: null,
  midiaMetadados: null,
  opcoes: null,
  statusEntrega: "ENTREGUE",
  enviadoEm: "2026-08-29T12:00:00Z",
};

function cartao(parcial: Partial<CartaoAtendimento> & Pick<CartaoAtendimento, "atendimentoId" | "leadId" | "leadNome">): CartaoAtendimento {
  return {
    leadFotoUrl: null,
    leadEmpresa: null,
    canalTipo: "WHATSAPP",
    etapaId: null,
    etapaNome: null,
    etapaCor: null,
    status: "EM_ATENDIMENTO",
    atendenteId: "ana",
    atendenteNome: "Ana",
    ultimaMensagemPreview: null,
    ultimaMensagemRemetenteTipo: null,
    ultimaMensagemEm: null,
    ultimaMensagemDoLeadEm: null,
    naoLidas: 0,
    ...parcial,
  };
}

function renderizar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <DialogoEncaminhar
        origemAtendimentoId="at-origem"
        origemLeadId="lead-origem"
        mensagem={origem}
        aberto
        onFechar={vi.fn()}
      />
    </QueryClientProvider>,
  );
}

describe("DialogoEncaminhar", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("lista só conversas visíveis, exclui a origem e exige confirmação", async () => {
    vi.mocked(api.listarAtendimentos).mockResolvedValue([
      cartao({ atendimentoId: "at-origem", leadId: "lead-origem", leadNome: "Mesma conversa" }),
      cartao({ atendimentoId: "at-mesmo-lead", leadId: "lead-origem", leadNome: "Mesmo lead" }),
      cartao({ atendimentoId: "at-dest", leadId: "lead-dest", leadNome: "Cliente destino" }),
    ]);
    const encaminhar = vi.mocked(api.encaminharMensagem).mockResolvedValue({
      atendimentoId: "at-dest",
      mensagemId: "nova",
      statusEntrega: "PENDENTE",
      enviadoEm: "2026-08-29T13:00:00Z",
      transferiuOLead: false,
    });

    renderizar();

    expect(await screen.findByRole("button", { name: "Cliente destino" })).toBeInTheDocument();
    expect(screen.queryByText("Mesma conversa")).not.toBeInTheDocument();
    expect(screen.queryByText("Mesmo lead")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Encaminhar" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Cliente destino" }));
    fireEvent.click(screen.getByRole("button", { name: "Encaminhar" }));

    await waitFor(() =>
      expect(encaminhar).toHaveBeenCalledWith("at-origem", "msg-1", "2026-08-29T12:00:00Z", "at-dest"),
    );
  });

  it("filtra pela busca e não chama o envio só de selecionar", async () => {
    vi.mocked(api.listarAtendimentos).mockResolvedValue([
      cartao({ atendimentoId: "at-a", leadId: "lead-a", leadNome: "Ana Vidros" }),
      cartao({ atendimentoId: "at-b", leadId: "lead-b", leadNome: "Bruno Alumínio" }),
    ]);
    renderizar();
    expect(await screen.findByText("Ana Vidros")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Buscar conversa"), { target: { value: "bruno" } });
    expect(screen.queryByText("Ana Vidros")).not.toBeInTheDocument();
    expect(screen.getByText("Bruno Alumínio")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Bruno Alumínio" }));
    expect(api.encaminharMensagem).not.toHaveBeenCalled();
  });
});
