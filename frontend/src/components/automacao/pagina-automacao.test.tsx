import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const atualizarMutate = vi.fn();
const navigation = vi.hoisted(() => ({
  replace: vi.fn((href: string) => window.history.replaceState(null, "", href)),
}));

const PARAMETROS = [
  {
    chave: "followup.primeiro.minutos",
    valor: "15",
    unidade: "minutos",
    tipo: "INT",
    valorMin: 1,
    valorMax: 1440,
    descricao: "Tempo sem resposta do lead até o primeiro follow-up automático.",
    atualizadoEm: "2026-01-01T00:00:00Z",
  },
  {
    chave: "automacao.habilitada",
    valor: "true",
    unidade: null,
    tipo: "BOOLEAN",
    valorMin: null,
    valorMax: null,
    descricao: "Chave geral da automação.",
    atualizadoEm: "2026-01-01T00:00:00Z",
  },
];

const TELEMETRIA = {
  mensagensEnviadas: 1284,
  clientesTransferidos: 342,
  conexaoAutomacaoAtiva: true,
  crmOnline: false,
  atualizadoEm: "2026-01-01T00:00:00Z",
};

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    automacao: {
      titulo: "Automação",
      descricao: "Parâmetros gerais que controlam o comportamento da automação.",
      carregando: "Carregando parâmetros...",
      vazio: "Nenhum parâmetro cadastrado.",
      erro: "Não foi possível carregar os parâmetros da automação.",
      erroSalvar: "Não foi possível salvar o parâmetro.",
      erroFaixa: "Valor fora da faixa permitida.",
      faixaLabel: "Faixa",
      ativado: "Ativado",
      desativado: "Desativado",
      salvar: "Salvar",
      salvando: "Salvando...",
      abas: { geral: "Geral", followUp: "Follow-up", fidelizacao: "Fidelização" },
      regras: { novo: "Nova regra", editar: "Editar", excluir: "Excluir", confirmarExclusao: "Excluir esta regra?", cancelar: "Cancelar", ativo: "Ativa", inativo: "Inativa", vazio: "Nenhuma regra cadastrada.", erro: "Erro", unidadeHoras: "Horas", unidadeDias: "Dias", tempo: "Tempo", dias: "Dias", mensagem: "Mensagem", preview: "Prévia", previewNome: "Marcos", placeholderAjuda: "Use {nome}" },
      telemetria: {
        mensagensEnviadas: "Mensagens Enviadas",
        clientesTransferidos: "Clientes Transferidos",
        conexaoAutomacao: "Conexão Automação",
        statusDoCrm: "Status do CRM",
        conectado: "Conectado",
        desconectado: "Desconectado",
        online: "Online",
        offline: "Offline",
        erro: "Não foi possível carregar a telemetria.",
      },
    },
  }),
}));

vi.mock("@/lib/automacao/use-automacao", () => ({
  useConfiguracaoAutomacao: () => ({ data: PARAMETROS, isLoading: false, isError: false }),
  useAtualizarParametroAutomacao: () => ({
    mutate: atualizarMutate,
    isPending: false,
    isError: false,
  }),
  useTelemetriaAutomacao: () => ({ data: TELEMETRIA, isLoading: false, isError: false }),
  useRegrasFollowUp: () => ({ data: [], isLoading: false, isError: false, refetch: vi.fn() }),
  useRegrasFidelizacao: () => ({ data: [], isLoading: false, isError: false, refetch: vi.fn() }),
  useMutacaoRegraFollowUp: () => ({ mutate: vi.fn(), isPending: false }),
  useMutacaoRegraFidelizacao: () => ({ mutate: vi.fn(), isPending: false }),
  useAlternarRegraFollowUp: () => ({ mutate: vi.fn(), isPending: false }),
  useAlternarRegraFidelizacao: () => ({ mutate: vi.fn(), isPending: false }),
  useExcluirRegraFollowUp: () => ({ mutate: vi.fn(), isPending: false }),
  useExcluirRegraFidelizacao: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => navigation,
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

import { PaginaAutomacao } from "./pagina-automacao";

describe("pagina de automacao", () => {
  it("mostra os quatro cards de telemetria com os valores do backend", () => {
    render(<PaginaAutomacao />);

    expect(screen.getByText("Mensagens Enviadas")).toBeInTheDocument();
    expect(screen.getByText("1.284")).toBeInTheDocument();
    expect(screen.getByText("Clientes Transferidos")).toBeInTheDocument();
    expect(screen.getByText("342")).toBeInTheDocument();
    expect(screen.getByText("Conexão Automação")).toBeInTheDocument();
    expect(screen.getByText("Conectado")).toBeInTheDocument();
    expect(screen.getByText("Status do CRM")).toBeInTheDocument();
    expect(screen.getByText("Offline")).toBeInTheDocument();
  });

  it("mostra a faixa valida vinda do backend, nao um limite fixo no componente", () => {
    render(<PaginaAutomacao />);

    expect(screen.getByText("Faixa: 1–1440 minutos")).toBeInTheDocument();
  });

  it("desabilita salvar quando o valor digitado esta fora da faixa", () => {
    render(<PaginaAutomacao />);

    const campo = screen.getByDisplayValue("15");
    fireEvent.change(campo, { target: { value: "9999" } });

    expect(screen.getByText("Valor fora da faixa permitida.")).toBeInTheDocument();
    const botoesSalvar = screen.getAllByRole("button", { name: "Salvar" });
    expect(botoesSalvar[0]).toBeDisabled();
    expect(atualizarMutate).not.toHaveBeenCalled();
  });

  it("salva um valor dentro da faixa", () => {
    render(<PaginaAutomacao />);

    const campo = screen.getByDisplayValue("15");
    fireEvent.change(campo, { target: { value: "20" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Salvar" })[0]);

    expect(atualizarMutate).toHaveBeenCalledWith({
      chave: "followup.primeiro.minutos",
      valor: "20",
    });
  });

  it("alterna um parametro booleano sem faixa numerica", () => {
    render(<PaginaAutomacao />);

    fireEvent.click(screen.getByLabelText("Ativado"));

    expect(screen.getByLabelText("Desativado")).toBeInTheDocument();
  });

  it("persiste a aba selecionada na URL e a recupera depois da recarga", () => {
    window.history.replaceState(null, "", "/automacao");
    const primeiraRenderizacao = render(<PaginaAutomacao />);

    fireEvent.click(screen.getByRole("tab", { name: "Follow-up" }));
    expect(navigation.replace).toHaveBeenCalledWith("/automacao?aba=followUp", { scroll: false });

    primeiraRenderizacao.unmount();
    render(<PaginaAutomacao />);
    expect(screen.getByRole("tab", { name: "Follow-up" })).toHaveAttribute("aria-selected", "true");

    window.history.replaceState(null, "", "/automacao");
    navigation.replace.mockClear();
  });
});
