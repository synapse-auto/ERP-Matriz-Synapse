import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

const finalizar = vi.fn();
const finalizarTodos = vi.fn();
const conversa = vi.hoisted(
  () =>
    ({
      atendimentoId: "atendimento-1",
      leadId: "lead-1",
      leadNome: "Ana Vidros",
      leadFotoUrl: null,
      leadEmpresa: "Vidraçaria Central",
      canalTipo: "WHATSAPP",
      etapaId: null,
      etapaNome: null,
      etapaCor: null,
      status: "EM_ATENDIMENTO",
      atendenteId: "usuario-1",
      atendenteNome: "Jardel Lima",
      ultimaMensagemPreview: "Olá",
      ultimaMensagemRemetenteTipo: "LEAD",
      ultimaMensagemEm: "2026-08-16T12:30:00Z",
      ultimaMensagemDoLeadEm: "2026-08-16T12:30:00Z",
      naoLidas: 0,
    }) as const,
);

vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useFinalizarAtendimento: () => ({ mutate: finalizar, isPending: false }),
  useFinalizarAtendimentosVisiveis: () => ({ mutate: finalizarTodos, isPending: false, isError: false }),
  useQuantidadeAtendimentosFinalizaveis: () => ({ data: { quantidade: 2 }, isLoading: false }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useLead: () => ({ data: { telefone: "(61) 99999-0000", empresa: "Vidraçaria Central" } }),
}));

vi.mock("@/lib/atendimento/use-atendimentos", () => ({
  useAtendimentos: () => ({ data: [conversa], isLoading: false }),
  useContagemDeAtendimentos: () => ({ data: { TODOS: 1, ATIVOS: 1, PENDENTES: 0, POTENCIAIS: 0 } }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    menu: { itens: { atendimentos: "Atendimentos" } },
    atendimentos: {
      canais: { whatsapp: "WhatsApp" },
      lista: {
        busca: "Buscar cliente ou protocolo...",
        filtros: "Filtros da lista",
        carregarMais: "Carregar mais conversas",
        carregandoMais: "Carregando conversas...",
      },
      visoes: { todos: "Todos", ativos: "Ativos", pendentes: "Pendentes", potenciais: "Potenciais" },
      filtros: { etapa: "Etapa", atendente: "Atendente" },
      cartao: { semAtendente: "Sem atendente", vazio: "Nenhuma conversa", naoLidas: "{quantidade} mensagens não lidas" },
      novoContato: { botao: "Novo atendimento" },
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
      painel: { reabrir: "Reabrir detalhes do lead" },
    },
    painelLead: { dados: { telefone: "Telefone" } },
    chatInterno: { titulo: "Equipe", novaConversa: "Nova conversa", selecionarPessoa: "Selecionar pessoa" },
  }),
}));

vi.mock("./atalho-tags", () => ({
  AtalhoTags: () => <button type="button">Etiquetar</button>,
}));

vi.mock("./dialogo-transferir", () => ({
  DialogoTransferir: () => null,
}));

import { CabecalhoConversa } from "./cabecalho-conversa";
import { ListaConversas } from "./lista-conversas";

describe("menu global de finalização", () => {
  it("existe uma única entrada acessível na lista, e o cabeçalho da conversa não a duplica", () => {
    render(
      <>
        <ListaConversas selecionadoId="atendimento-1" onAbrirAtendimento={vi.fn()} />
        <CabecalhoConversa
          conversa={conversa as CartaoAtendimento}
          buscaAberta={false}
          onAlternarBusca={vi.fn()}
          painelDetalhesAberto
          onAlternarPainelDetalhes={vi.fn()}
        />
      </>,
    );

    const menu = screen.getAllByRole("button", { name: "Mais ações" });
    expect(menu).toHaveLength(1);
    expect(screen.getByRole("heading", { name: "Atendimentos" }).parentElement).toContainElement(menu[0]);
    expect(screen.getByRole("button", { name: "Finalizar" })).toBeInTheDocument();

    fireEvent.click(menu[0]);
    fireEvent.click(screen.getByRole("menuitem", { name: "Finalizar todos" }));
    expect(screen.getByText("Encerrar 2")).toBeInTheDocument();
  });
});
