import { fireEvent, render, screen } from "@testing-library/react";
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
];

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: { papel: string }) => unknown) => seletor({ papel: "ATENDENTE" }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    agenda: {
      titulo: "Agenda de contatos",
      descricao: "Clique uma vez para consultar a ficha; clique duas vezes para abrir o atendimento.",
      vazia: "Nenhum contato encontrado com os filtros atuais.",
      carregando: "Carregando agenda...",
      erro: "Não foi possível carregar a agenda.",
      semResponsavel: "Sem responsável",
      contador: "Exibindo {exibindo} de {total}",
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
  }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useEtapas: () => ({ data: [{ id: "etapa-1", nome: "Orçamento", ordem: 1, corVisual: "#1F74E0" }] }),
  useCanais: () => ({ data: [] }),
  useTodasAsTags: () => ({ data: [{ id: "tag-1", nome: "Urgente", cor: "#F00", icone: null }] }),
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useEquipe: () => ({ data: [{ id: "user-1", nome: "Ana Beatriz", email: "ana@dev.local", papel: "ATENDENTE", statusPresenca: "ONLINE", ativo: true }] }),
}));

vi.mock("@/components/leads/painel-lateral-lead", () => ({
  PainelLateralLead: ({ leadId }: { leadId: string }) => <div data-testid="painel-lateral">{leadId}</div>,
}));

const useLeadsDaAgenda = vi.fn();
const useContagemDeLeads = vi.fn();
const useCamposFiltraveis = vi.fn();

vi.mock("@/lib/agenda/use-agenda", () => ({
  useCamposFiltraveis: () => useCamposFiltraveis(),
  useLeadsDaAgenda: () => useLeadsDaAgenda(),
  useContagemDeLeads: () => useContagemDeLeads(),
}));

import { PaginaAgenda } from "./pagina-agenda";

describe("pagina da agenda", () => {
  beforeEach(() => {
    useCamposFiltraveis.mockReturnValue({ data: CAMPOS, isLoading: false, isError: false });
    useLeadsDaAgenda.mockReturnValue({
      data: { leads: LEADS, pagina: 0, temMais: false },
      isLoading: false,
      isError: false,
    });
    useContagemDeLeads.mockReturnValue({ data: 1 });
  });

  it("mostra a tabela com as colunas do design e o contador vindo de /contagem", () => {
    render(<PaginaAgenda />);

    expect(screen.getByText("Marcos Vinícius")).toBeInTheDocument();
    expect(screen.getByText("Vidraçaria Cristal")).toBeInTheDocument();
    expect(screen.getByText("Taguatinga · DF")).toBeInTheDocument();
    expect(screen.getByText("Orçamento")).toBeInTheDocument();
    expect(screen.getByText("Urgente")).toBeInTheDocument();
    expect(screen.getByText("Ana Beatriz")).toBeInTheDocument();
    expect(screen.getByText("Exibindo 1 de 1")).toBeInTheDocument();
  });

  it("estado vazio real quando o filtro nao acha ninguem — sem mock, sem controle fantasma", () => {
    useLeadsDaAgenda.mockReturnValue({
      data: { leads: [], pagina: 0, temMais: false },
      isLoading: false,
      isError: false,
    });
    useContagemDeLeads.mockReturnValue({ data: 0 });

    render(<PaginaAgenda />);

    expect(screen.getByText("Nenhum contato encontrado com os filtros atuais.")).toBeInTheDocument();
  });

  it("adiciona um filtro pelo campo e operador escolhidos, sem lista hardcoded", async () => {
    render(<PaginaAgenda />);

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

  it("clique simples abre a ficha; a paginação respeita temMais", () => {
    render(<PaginaAgenda />);

    fireEvent.click(screen.getByText("Marcos Vinícius"));
    expect(screen.getByTestId("painel-lateral")).toHaveTextContent("lead-1");

    expect(screen.getByRole("button", { name: "Anterior" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Próxima" })).toBeDisabled();
  });

  it("clique duplo abre o atendimento na visão certa para o papel do usuário", () => {
    render(<PaginaAgenda />);

    fireEvent.doubleClick(screen.getByText("Marcos Vinícius"));

    expect(push).toHaveBeenCalledWith("/atendimentos?leadId=lead-1&visao=ATIVOS");
  });
});
