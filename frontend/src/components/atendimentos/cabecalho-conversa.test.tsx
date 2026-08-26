import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

const finalizar = vi.fn();
const finalizarTodos = vi.fn();
const quantidadeFinalizavel = vi.hoisted(() => ({ valor: 2 }));

vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useFinalizarAtendimento: () => ({ mutate: finalizar, isPending: false }),
  useFinalizarAtendimentosVisiveis: () => ({ mutate: finalizarTodos, isPending: false, isError: false }),
  useQuantidadeAtendimentosFinalizaveis: () => ({ data: { quantidade: quantidadeFinalizavel.valor }, isLoading: false }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useLead: () => ({
    data: { telefone: "(61) 99999-0000", empresa: "Vidraçaria Central" },
  }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      canais: { whatsapp: "WhatsApp" },
      cabecalho: {
        atendidoPor: "Atendido por",
        semAtendente: "Sem atendente",
        transferir: "Transferir",
        finalizar: "Finalizar",
        buscar: "Buscar na conversa",
      },
      finalizar: {
        titulo: "Finalizar atendimento",
        descricao: "",
        confirmar: "Finalizar",
        cancelar: "Cancelar",
        sucesso: "Finalizado",
        erro: "Erro",
        todosMenu: "Mais ações",
        todos: "Finalizar todos",
        todosTitulo: "Finalizar atendimentos",
        todosDescricao: "Encerrar {quantidade}",
        todosConfirmar: "Finalizar {quantidade}",
        todosCancelar: "Cancelar",
        todosResultado: "{finalizados} finalizados; {recusados} recusados",
        todosErro: "Erro",
      },
    },
    painelLead: { dados: { telefone: "Telefone" } },
  }),
}));

vi.mock("./atalho-tags", () => ({
  AtalhoTags: () => <button type="button">Etiquetar</button>,
}));

vi.mock("./dialogo-transferir", () => ({
  DialogoTransferir: () => null,
}));

import { CabecalhoConversa } from "./cabecalho-conversa";

const conversa: CartaoAtendimento = {
  atendimentoId: "atendimento-1",
  leadId: "lead-1",
  leadNome: "Ana Vidros",
  leadFotoUrl: null,
  leadEmpresa: "Empresa antiga",
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: "usuario-1",
  atendenteNome: "Jardel Lima",
  ultimaMensagemPreview: null,
  ultimaMensagemRemetenteTipo: null,
  ultimaMensagemEm: null,
  ultimaMensagemDoLeadEm: null,
  naoLidas: 0,
};

describe("CabecalhoConversa", () => {
  it("mostra contexto real e oferece ações funcionais do protótipo", () => {
    const alternarBusca = vi.fn();
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={alternarBusca}
      />,
    );

    expect(screen.getByText("WhatsApp")).toBeInTheDocument();
    expect(
      screen.getByText(
        /\(61\) 99999-0000 · Vidraçaria Central · Atendido por Jardel Lima/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Telefone/ })).toHaveAttribute(
      "href",
      "tel:61999990000",
    );

    fireEvent.click(screen.getByRole("button", { name: "Buscar na conversa" }));
    expect(alternarBusca).toHaveBeenCalledOnce();

    fireEvent.click(screen.getByRole("button", { name: "Finalizar" }));
    expect(finalizar).toHaveBeenCalledWith("atendimento-1");
  });

  it("pede confirmação com a quantidade de atendimentos visíveis", () => {
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Mais ações" }));
    fireEvent.click(screen.getByText("Finalizar todos"));

    expect(screen.getByText("Encerrar 2")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Finalizar 2" }));
    expect(finalizarTodos).toHaveBeenCalledWith(undefined, expect.anything());
  });
});
