import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

const finalizar = vi.fn();

vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useFinalizarAtendimento: () => ({ mutate: finalizar, isPending: false }),
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
      avaliacao: {
        titulo: "Avaliação",
        descricao: "Nota 1 a 5",
        registrar: "Avaliar",
        confirmar: "Salvar",
        cancelar: "Agora não",
        jaRegistrada: "Nota {nota}",
        sucesso: "Ok",
        erro: "Erro",
        nota: "Nota {nota}",
      },
      painel: {
        reabrir: "Reabrir detalhes do lead",
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

vi.mock("./dialogo-avaliacao", () => ({
  DialogoAvaliacao: () => null,
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
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
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
    expect(finalizar).toHaveBeenCalledWith("atendimento-1", expect.any(Object));
  });

  it("mantém a ação individual e não oferece o menu global de finalização", () => {
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
    expect(screen.queryByText("Finalizar todos")).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Finalizar" })).toBeEnabled();
    expect(screen.getAllByRole("button", { name: "Transferir" }).length).toBeGreaterThan(0);
  });

  it("oferece reabrir os detalhes somente quando o painel está retraído", () => {
    const onAlternar = vi.fn();
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto={false}
        onAlternarPainelDetalhes={onAlternar}
      />,
    );

    const controle = screen.getByRole("button", { name: "Reabrir detalhes do lead" });
    expect(controle).toHaveAttribute("aria-expanded", "false");
    expect(controle).toHaveAttribute("aria-controls", "painel-detalhes-lead");
    fireEvent.click(controle);
    expect(onAlternar).toHaveBeenCalledOnce();
  });
});
