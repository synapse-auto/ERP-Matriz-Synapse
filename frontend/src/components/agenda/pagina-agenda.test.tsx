import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

const CAMPOS = [
  { apelido: "nome", rotulo: "Nome", tipo: "TEXTO", operadores: ["IGUAL", "CONTEM"], opcoes: [] },
  {
    apelido: "atendenteResponsavel",
    rotulo: "Atendente responsável",
    tipo: "REFERENCIA",
    operadores: ["IGUAL", "DIFERENTE", "EM"],
    opcoes: [],
  },
];

const LEADS = [
  {
    id: "lead-1",
    nome: "Marcos Vinícius",
    telefone: "(61) 99999-0000",
    empresa: "Vidraçaria Cristal",
    localizacao: "Taguatinga · DF",
    status: "EM_ATENDIMENTO",
    etapaAtendimentoId: "etapa-1",
    atendenteResponsavelId: "user-1",
    numAtendimentos: 3,
    numMensagens: 20,
    criadoEm: "2026-01-01T00:00:00Z",
    ultimaInteracaoEm: "2026-01-05T12:00:00Z",
    tags: [{ tagId: "tag-1", nome: "Urgente", cor: "#F00", icone: null }],
  },
  {
    id: "lead-finalizado",
    nome: "Cliente Finalizado",
    telefone: "(61) 98888-0000",
    empresa: null,
    localizacao: null,
    status: "FINALIZADO",
    etapaAtendimentoId: null,
    atendenteResponsavelId: "user-1",
    numAtendimentos: 1,
    numMensagens: 4,
    criadoEm: "2026-01-01T00:00:00Z",
    ultimaInteracaoEm: "2026-01-02T12:00:00Z",
    tags: [],
  },
];

const push = vi.fn();
const abrirAtendimentoApi = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/lib/atendimento/api", () => ({
  abrirAtendimentoParaLead: (...args: unknown[]) => abrirAtendimentoApi(...args),
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: { papel: string }) => unknown) => seletor({ papel: "ATENDENTE" }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    agenda: {
      titulo: "Agenda de contatos",
      descricao: "Clique no contato para consultar a ficha; use Abrir atendimento para ir ao chat.",
      vazia: "Nenhum contato encontrado com os filtros atuais.",
      carregando: "Carregando agenda...",
      erro: "Não foi possível carregar a agenda.",
      semResponsavel: "Sem responsável",
      contador: "Exibindo {exibindo} de {total}",
      indiceAlfabetico: "Índice alfabético",
      abrirAtendimento: "Abrir atendimento",
      abrindoAtendimento: "Abrindo atendimento...",
      erroAbrirAtendimento: "Não foi possível abrir o atendimento.",
      colunas: {
        lead: "Lead",
        telefone: "Telefone",
        cidade: "Cidade",
        etapa: "Etapa",
        tags: "Tags",
        responsavel: "Responsável",
        ultimoContato: "Último contato",
      },
      status: { ia: "Potencial (IA)", emAtendimento: "Em atendimento", finalizado: "Finalizado" },
      filtros: {
        titulo: "Filtros",
        busca: "Busca",
        buscaPlaceholder: "Buscar por nome, telefone, CNPJ/CPF ou tag...",
        avancados: "Filtros avançados",
        etapa: "Etapa",
        atendente: "Atendente",
        cidade: "Cidade",
        tag: "Tag",
        adicionar: "Adicionar filtro",
        campo: "Campo",
        operador: "Operador",
        valor: "Valor",
        valorInicial: "De",
        valorFinal: "Até",
        selecionarCampo: "Selecione o campo",
        selecionarOperador: "Selecione o operador",
        selecionarValor: "Selecione",
        limparTudo: "Limpar tudo",
        carregandoCampos: "Carregando campos...",
        erroCampos: "Não foi possível carregar os campos filtráveis.",
        operadores: {
          igual: "é",
          diferente: "não é",
          contem: "contém",
          comecaCom: "começa com",
          maiorQue: "maior que",
          menorQue: "menor que",
          entre: "entre",
          em: "está em",
          preenchido: "preenchido",
          vazio: "vazio",
        },
      },
      paginacao: { anterior: "Anterior", proxima: "Próxima" },
    },
    painelLead: {
      acoes: {
        abrirAtendimento: "Abrir atendimento",
        abrindoAtendimento: "Abrindo atendimento...",
        erroAbrirAtendimento: "Não foi possível abrir o atendimento.",
        ligar: "Ligar",
      },
    },
  }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useEtapas: () => ({ data: [{ id: "etapa-1", nome: "Orçamento", ordem: 1, corVisual: "#1F74E0" }] }),
  useCanais: () => ({ data: [] }),
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useEquipe: () => ({ data: [{ id: "user-1", nome: "Ana Beatriz", email: "ana@dev.local", papel: "ATENDENTE", statusPresenca: "ONLINE", ativo: true }] }),
}));

vi.mock("@/components/leads/painel-lateral-lead", () => ({
  PainelLateralLead: ({
    leadId,
    onAbrirAtendimento,
    abrindoAtendimento,
    erroAbrirAtendimento,
  }: {
    leadId: string;
    onAbrirAtendimento?: () => void;
    abrindoAtendimento?: boolean;
    erroAbrirAtendimento?: string | null;
  }) => (
    <div data-testid="painel-lateral">
      <span>{leadId}</span>
      {onAbrirAtendimento && (
        <button type="button" onClick={onAbrirAtendimento} disabled={abrindoAtendimento}>
          {abrindoAtendimento ? "Abrindo atendimento..." : "Abrir atendimento"}
        </button>
      )}
      {erroAbrirAtendimento && <span role="alert">{erroAbrirAtendimento}</span>}
    </div>
  ),
}));

const useLeadsDaAgenda = vi.fn();
const useContagemDeLeads = vi.fn();
const useCamposFiltraveis = vi.fn();
const useCatalogosDeFiltro = vi.fn();

vi.mock("@/lib/agenda/use-agenda", () => ({
  useCamposFiltraveis: () => useCamposFiltraveis(),
  useCatalogosDeFiltro: () => useCatalogosDeFiltro(),
  useLeadsDaAgenda: (...args: unknown[]) => useLeadsDaAgenda(...args),
  useContagemDeLeads: (...args: unknown[]) => useContagemDeLeads(...args),
  SEM_RESPONSAVEL: "__sem_responsavel__",
}));

import { PaginaAgenda } from "./pagina-agenda";

function renderAgenda() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PaginaAgenda />
    </QueryClientProvider>,
  );
}

describe("pagina da agenda", () => {
  beforeEach(() => {
    push.mockReset();
    abrirAtendimentoApi.mockReset();
    abrirAtendimentoApi.mockResolvedValue({
      leadId: "lead-1",
      atendimentoId: "atendimento-1",
      mensagemId: null,
      leadCriado: false,
    });
    useCamposFiltraveis.mockReturnValue({ data: CAMPOS, isLoading: false, isError: false });
    useCatalogosDeFiltro.mockReturnValue({
      data: {
        cidades: ["Taguatinga · DF", "Cidade fora da pagina"],
        tags: [{ id: "tag-1", nome: "Urgente", cor: "#F00", icone: null }],
      },
      isLoading: false,
      isError: false,
    });
    useLeadsDaAgenda.mockReturnValue({
      data: { leads: LEADS, pagina: 0, temMais: false },
      isLoading: false,
      isError: false,
    });
    useContagemDeLeads.mockReturnValue({ data: 2 });
  });

  it("mostra a tabela com as colunas do design e o contador vindo de /contagem", () => {
    renderAgenda();

    expect(screen.getByText("Marcos Vinícius")).toBeInTheDocument();
    expect(screen.getByText("Vidraçaria Cristal")).toBeInTheDocument();
    expect(screen.getByText("Taguatinga · DF")).toBeInTheDocument();
    expect(screen.getByText("Orçamento")).toBeInTheDocument();
    expect(screen.getByText("Urgente")).toBeInTheDocument();
    expect(screen.getAllByText("Ana Beatriz").length).toBeGreaterThan(0);
    expect(screen.getByText("Exibindo 2 de 2")).toBeInTheDocument();
  });

  it("estado vazio real quando o filtro nao acha ninguem — sem mock, sem controle fantasma", () => {
    useLeadsDaAgenda.mockReturnValue({
      data: { leads: [], pagina: 0, temMais: false },
      isLoading: false,
      isError: false,
    });
    useContagemDeLeads.mockReturnValue({ data: 0 });

    renderAgenda();

    expect(screen.getByText("Nenhum contato encontrado com os filtros atuais.")).toBeInTheDocument();
  });

  it("adiciona um filtro pelo campo e operador escolhidos, sem lista hardcoded", async () => {
    renderAgenda();

    expect(screen.queryByRole("combobox", { name: "Campo" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Filtros avançados/i }));
    fireEvent.click(screen.getByRole("combobox", { name: "Campo" }));
    const campoNome = await screen.findByRole("option", { name: "Nome" });
    fireEvent.pointerDown(campoNome, { pointerType: "mouse", button: 0 });
    fireEvent.click(campoNome);
    fireEvent.click(screen.getByRole("combobox", { name: "Operador" }));
    const operadorContem = await screen.findByRole("option", { name: /cont.m/i });
    fireEvent.pointerDown(operadorContem, { pointerType: "mouse", button: 0 });
    fireEvent.click(operadorContem);
    fireEvent.change(screen.getByLabelText("Valor"), { target: { value: "Marcos" } });
    fireEvent.click(screen.getByRole("button", { name: "Adicionar filtro" }));

    expect(screen.getByText("Nome contém Marcos")).toBeInTheDocument();
  });

  it("expõe busca, quatro filtros prontos e contador na barra principal", () => {
    renderAgenda();

    expect(screen.getByPlaceholderText("Buscar por nome, telefone, CNPJ/CPF ou tag...")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Etapa" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Atendente" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Cidade" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Tag" })).toBeInTheDocument();
    expect(screen.getByText("Exibindo 2 de 2")).toBeInTheDocument();
  });

  it("envia a busca livre ao mesmo fluxo de filtro e cria chip removível", () => {
    renderAgenda();

    fireEvent.change(screen.getByRole("textbox", { name: "Busca" }), {
      target: { value: "Marcos" },
    });

    expect(useLeadsDaAgenda).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: "Marcos" }),
      expect.any(Array),
      expect.any(Array),
      0,
    );
    expect(screen.getByText("Busca: Marcos")).toBeInTheDocument();
  });

  it("clique simples abre a ficha; a paginação respeita temMais", () => {
    renderAgenda();

    fireEvent.click(screen.getByText("Marcos Vinícius"));
    expect(screen.getByTestId("painel-lateral")).toHaveTextContent("lead-1");

    expect(screen.getByRole("button", { name: "Anterior" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Próxima" })).toBeDisabled();
  });

  it("botão Abrir atendimento chama a API e navega para Ativos com o lead", async () => {
    renderAgenda();

    fireEvent.click(screen.getAllByRole("button", { name: "Abrir atendimento" })[0]!);

    await waitFor(() => expect(abrirAtendimentoApi).toHaveBeenCalledWith("lead-1"));
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith("/atendimentos?leadId=lead-1&visao=ATIVOS"),
    );
  });

  it("iniciar atendimento de lead finalizado pela Agenda reusa a API e abre em Ativos", async () => {
    abrirAtendimentoApi.mockResolvedValueOnce({
      leadId: "lead-finalizado",
      atendimentoId: "atendimento-novo",
      mensagemId: null,
      leadCriado: false,
    });
    renderAgenda();

    fireEvent.click(screen.getByText("Cliente Finalizado"));
    fireEvent.click(
      within(screen.getByTestId("painel-lateral")).getByRole("button", {
        name: "Abrir atendimento",
      }),
    );

    await waitFor(() => expect(abrirAtendimentoApi).toHaveBeenCalledWith("lead-finalizado"));
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith("/atendimentos?leadId=lead-finalizado&visao=ATIVOS"),
    );
    expect(abrirAtendimentoApi).toHaveBeenCalledTimes(1);
  });

  it("falha da API mantém a ficha aberta e exibe o erro", async () => {
    abrirAtendimentoApi.mockRejectedValueOnce(
      new Error("Numero indisponivel para iniciar atendimento. Procure a gestao."),
    );
    renderAgenda();

    fireEvent.click(screen.getByText("Marcos Vinícius"));
    fireEvent.click(
      within(screen.getByTestId("painel-lateral")).getByRole("button", {
        name: "Abrir atendimento",
      }),
    );

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Numero indisponivel para iniciar atendimento. Procure a gestao.",
      ),
    );
    expect(screen.getByTestId("painel-lateral")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
