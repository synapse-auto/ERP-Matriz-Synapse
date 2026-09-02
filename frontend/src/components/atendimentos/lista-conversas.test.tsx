import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ItemInbox } from "@/lib/atendimento/types";

const finalizarTodos = vi.fn();
const quantidadeFinalizavel = vi.hoisted(() => ({ valor: 2 }));
const authMock = vi.hoisted(() => ({ papel: "GESTOR" as string | null }));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: typeof authMock) => unknown) => seletor(authMock),
}));

vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useFinalizarAtendimentosVisiveis: () => ({ mutate: finalizarTodos, isPending: false, isError: false }),
  useQuantidadeAtendimentosFinalizaveis: () => ({ data: { quantidade: quantidadeFinalizavel.valor }, isLoading: false }),
}));

const cartoes: ItemInbox[] = [
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
    atendimentoAtivoId: "protocolo-001",
    atendenteId: "usuario-1",
    atendenteNome: "Jardel Lima",
    ultimaMensagemPreview: "Preciso do orçamento",
    ultimaMensagemRemetenteTipo: "LEAD",
    ultimaMensagemEm: "2026-08-16T12:30:00Z",
    ultimaMensagemDoLeadEm: "2026-08-16T12:30:00Z",
    naoLidas: 1,
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
    atendimentoAtivoId: "protocolo-002",
    atendenteId: null,
    atendenteNome: null,
    ultimaMensagemPreview: null,
    ultimaMensagemRemetenteTipo: null,
    ultimaMensagemEm: null,
    ultimaMensagemDoLeadEm: null,
    naoLidas: 0,
  },
  {
    tipo: "EQUIPE_INTERNA",
    atendimentoId: null,
    conversaId: "conversa-1",
    nome: "Equipe comercial",
    avatarUrl: null,
    identificadorVisual: "conversa-1",
    ultimaMensagemPreview: "Vamos revisar a proposta",
    ultimaMensagemEm: "2026-08-16T12:45:00Z",
    naoLidas: 2,
    participantes: "Ana, Bruno",
    tipoConversa: "GRUPO",
  },
  {
    atendimentoId: "protocolo-finalizado-com-ativo",
    atendimentoAtivoId: "protocolo-novo-ativo",
    leadId: "lead-3",
    leadNome: "Carla com histórico",
    leadFotoUrl: null,
    leadEmpresa: null,
    canalTipo: "WHATSAPP",
    etapaId: null,
    etapaNome: null,
    etapaCor: null,
    status: "FINALIZADO",
    atendenteId: "usuario-1",
    atendenteNome: "Jardel Lima",
    ultimaMensagemPreview: "Histórico antigo",
    ultimaMensagemRemetenteTipo: "LEAD",
    ultimaMensagemEm: "2026-08-16T12:00:00Z",
    ultimaMensagemDoLeadEm: "2026-08-16T12:00:00Z",
    naoLidas: 0,
  },
  {
    atendimentoId: "protocolo-finalizado",
    atendimentoAtivoId: null,
    leadId: "lead-4",
    leadNome: "Daniela finalizada",
    leadFotoUrl: null,
    leadEmpresa: null,
    canalTipo: "WHATSAPP",
    etapaId: null,
    etapaNome: null,
    etapaCor: null,
    status: "FINALIZADO",
    atendenteId: "usuario-1",
    atendenteNome: "Jardel Lima",
    ultimaMensagemPreview: "Conversa encerrada",
    ultimaMensagemRemetenteTipo: "ATENDENTE",
    ultimaMensagemEm: "2026-08-16T11:00:00Z",
    ultimaMensagemDoLeadEm: "2026-08-15T10:00:00Z",
    naoLidas: 0,
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
        carregarMais: "Carregar mais conversas",
        carregandoMais: "Carregando conversas...",
        finalizados: "Finalizados",
      },
      visoes: {
        todos: "Todos",
        ativos: "Ativos",
        pendentes: "Pendentes",
        potenciais: "Potenciais",
      },
      filtros: { etapa: "Etapa", atendente: "Atendente" },
      finalizar: {
        todosMenu: "Mais ações",
        todos: "Finalizar todos",
        todosTitulo: "Finalizar atendimentos",
        todosDescricao: "Encerrar {quantidade}",
        todosConfirmar: "Finalizar {quantidade}",
        todosCancelar: "Cancelar",
        todosResultado: "{finalizados} finalizados; {recusados} recusados",
        todosErro: "Erro",
      },
        cartao: {
        semAtendente: "Sem atendente",
        vazio: "Nenhuma conversa",
        naoLidas: "{quantidade} mensagens não lidas",
      },
      novoContato: { botao: "Novo atendimento" },
    },
    chatInterno: {
      titulo: "Equipe", novaConversa: "Nova conversa", selecionarPessoa: "Selecionar pessoa",
      selecionarPessoaDescricao: "Escolha uma pessoa", buscarPessoa: "Buscar por nome...",
      fecharSeletor: "Fechar", online: "Online", ausente: "Ausente", offline: "Offline",
      semPessoas: "Nenhuma pessoa", erroContatos: "Erro ao carregar", erroAbrirConversa: "Erro ao abrir",
      tentarNovamente: "Tentar novamente", carregando: "Carregando...",
    },
  }),
}));

import { ListaConversas } from "./lista-conversas";

describe("ListaConversas", () => {
  beforeEach(() => {
    authMock.papel = "GESTOR";
  });

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
    const todos = screen.getByRole("tab", { name: /Todos/ });
    expect(todos).toHaveAttribute("data-active");
    expect(todos.className).not.toContain("border-b-2");
    expect(todos).toHaveTextContent("2");

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

  it("oferece somente as visões permitidas e ignora Todos na URL para atendente", () => {
    authMock.papel = "ATENDENTE";
    render(
      <ListaConversas
        selecionadoId={null}
        visaoInicial="TODOS"
        onAbrirAtendimento={vi.fn()}
      />,
    );

    expect(screen.getAllByRole("tab")).toHaveLength(3);
    expect(screen.queryByRole("tab", { name: /Todos/ })).not.toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Ativos/ })).toHaveAttribute("data-active");
    expect(screen.getAllByRole("tab").map((tab) => tab.textContent?.replace(/\d+$/, ""))).toEqual([
      "Ativos",
      "Pendentes",
      "Potenciais",
    ]);
    expect(screen.getByRole("tab", { name: /Ativos/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Potenciais/ })).toBeInTheDocument();
  });

  it("abre a finalização global na barra da lista com a quantidade real", () => {
    render(<ListaConversas selecionadoId={null} onAbrirAtendimento={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Mais ações" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Finalizar todos" }));

    expect(screen.getByText("Encerrar 2")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Finalizar 2" }));
    expect(finalizarTodos).toHaveBeenCalledTimes(1);
  });

  it("coloca o menu global ao lado do título da lista", () => {
    render(<ListaConversas selecionadoId={null} onAbrirAtendimento={vi.fn()} />);

    const titulo = screen.getByRole("heading", { name: "Atendimentos" });
    const menu = screen.getByRole("button", { name: "Mais ações" });
    expect(titulo.parentElement).toContainElement(menu);
    expect(screen.getAllByRole("button", { name: "Mais ações" })).toHaveLength(1);
  });

  it("seleciona conversa interna pelo tipo e conversaId", () => {
    const abrir = vi.fn();
    render(<ListaConversas selecionadoId={null} onAbrirAtendimento={abrir} chatInternoHabilitado />);
    fireEvent.click(screen.getByRole("button", { name: /Equipe comercial/ }));
    expect(abrir).toHaveBeenCalledWith(cartoes[2]);
  });

  it("abre o seletor de equipe com o ícone de usuários e mantém o diálogo após pointerup", () => {
    render(
      <ListaConversas
        selecionadoId={null}
        onAbrirAtendimento={vi.fn()}
        chatInternoHabilitado
        contatosInternos={[{ id: "usuario-2", nome: "Bruno Almeida", presenca: "ONLINE" }]}
      />,
    );

    const botaoNova = screen.getByRole("button", { name: "Nova conversa" });
    expect(botaoNova).toBeInTheDocument();
    fireEvent.pointerDown(botaoNova);
    fireEvent.pointerUp(botaoNova);
    fireEvent.click(botaoNova);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Buscar por nome..." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bruno Almeida, Online" })).toBeInTheDocument();
    expect(botaoNova.querySelector(".lucide-users-round")).toBeInTheDocument();
    expect(botaoNova.querySelector(".lucide-plus")).not.toBeInTheDocument();
  });

  it("abre a conversa ao selecionar uma pessoa e fecha somente após sucesso", async () => {
    const criar = vi.fn();
    render(
      <ListaConversas
        selecionadoId={null}
        onAbrirAtendimento={vi.fn()}
        chatInternoHabilitado
        contatosInternos={[{ id: "usuario-2", nome: "Bruno Almeida", presenca: "ONLINE" }]}
        onCriarConversaInterna={criar}
      />,
    );

    const botaoNova = screen.getByRole("button", { name: "Nova conversa" });
    fireEvent.click(botaoNova);
    fireEvent.click(screen.getByRole("button", { name: "Bruno Almeida, Online" }));
    expect(criar).toHaveBeenCalledTimes(1);
    expect(criar).toHaveBeenCalledWith("usuario-2");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    fireEvent.click(botaoNova);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("não renderiza controles quando o chat interno está desligado e preserva abertura em rerender", () => {
    const { rerender } = render(
      <ListaConversas selecionadoId={null} onAbrirAtendimento={vi.fn()} chatInternoHabilitado />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Nova conversa" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    rerender(<ListaConversas selecionadoId={null} onAbrirAtendimento={vi.fn()} chatInternoHabilitado />);
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    rerender(<ListaConversas selecionadoId={null} onAbrirAtendimento={vi.fn()} />);
    expect(screen.queryByRole("button", { name: "Nova conversa" })).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Novo atendimento" })).toBeInTheDocument();
  });

  it("dispara novo contato WhatsApp por um botão separado do + do chat interno", () => {
    const novo = vi.fn();
    render(
      <ListaConversas
        selecionadoId={null}
        onAbrirAtendimento={vi.fn()}
        chatInternoHabilitado
        onNovoContato={novo}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Novo atendimento" }));
    expect(novo).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Nova conversa" })).toBeInTheDocument();
  });

  it("separa apenas em Todos os leads sem atendimento aberto e suaviza somente o nome deles", () => {
    render(<ListaConversas selecionadoId={null} onAbrirAtendimento={vi.fn()} />);

    expect(screen.getAllByRole("separator", { name: "Finalizados" })).toHaveLength(1);
    expect(screen.getByText("Daniela finalizada")).toHaveClass("text-muted-foreground");
    expect(screen.getByText("Carla com histórico")).toHaveClass("text-foreground");

    fireEvent.click(screen.getByRole("tab", { name: /Ativos/ }));
    expect(screen.queryByRole("separator", { name: "Finalizados" })).not.toBeInTheDocument();
  });

  it("não mostra cabeçalho solto quando a busca remove todos os finalizados", () => {
    render(<ListaConversas selecionadoId={null} onAbrirAtendimento={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText("Buscar cliente ou protocolo..."), {
      target: { value: "Ana Vidros" },
    });

    expect(screen.getByText("Ana Vidros")).toBeInTheDocument();
    expect(screen.queryByRole("separator", { name: "Finalizados" })).not.toBeInTheDocument();
  });
});
