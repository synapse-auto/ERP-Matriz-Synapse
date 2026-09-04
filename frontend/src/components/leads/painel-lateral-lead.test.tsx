import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { vi } from "vitest";

// Mocks para PainelLateralLead
vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    estados: { erroGenerico: "Erro", indisponivel: "Indisponível" },
    lembretes: { formulario: { tituloEdicao: "Editar", titulo: "Novo", erroValidacao: "Erro", salvar: "Salvar", erroReversao: "Erro" } },
    mensagensProgramadas: { formulario: {} },
painelLead: {
      titulo: "Ficha",
      fechar: "Fechar",
      dados: { nome: "Nome", nomeInvalido: "Informe o nome do cliente.", telefone: "Telefone", email: "Email", cpf: "CPF", empresa: "Empresa", codigo: "Código", localizacao: "Local", naoInformado: "Não", canalOrigem: "Canal" },
      acoes: {
        ligar: "Ligar para lead",
        lembrete: "L",
        mensagemProgramada: "M",
        abrirAtendimento: "Abrir atendimento",
        abrindoAtendimento: "Abrindo atendimento...",
        erroAbrirAtendimento: "Não foi possível abrir o atendimento.",
      },
      etapa: { titulo: "Etapa", semEtapa: "Sem", posicao: "{atual} de {total}" },
      contadores: { atendimentos: "At", mensagens: "Msg" },
      tags: { titulo: "Tags", remover: "Remover", adicionar: "Add", selecionar: "Selecione", erroReversao: "Erro" },
      resumoIa: { titulo: "IA", vazio: "Sem resumo" },
      edicao: {
        notas: "Notas",
        notasPlaceholder: "N",
        camposCustomizados: "Campos",
        obrigatorio: "Obrigatorio",
        opcional: "Opcional",
        salvar: "Salvar",
        salvando: "Salvando",
        salvo: "Salvo",
        campoObrigatorio: "Obrigatorio",
        erroReversao: "Erro"
      },
      timeline: { titulo: "Timeline", erro: "Erro", vazia: "Vazia", origens: { sistema: "S", automacao: "A", usuario: "U" }, carregandoMais: "Carregando", carregarMais: "Carregar mais" },
    }
  })
}));

let mockLeadData: Record<string, unknown> = { id: "1", nome: "Lead 1", telefone: "11999999999" };
const salvarFichaState = vi.hoisted(() => ({
  mutate: vi.fn(),
  isPending: false,
}));
vi.mock("@/lib/lead/use-painel-lead", () => ({
  useEtapas: () => ({ data: [] }),
  useCamposCustomizados: () => ({ data: [] }),
  useCanais: () => ({ data: [] }),
  useTodasAsTags: () => ({ data: [] }),
  useTagsDoLead: () => ({ data: [] }),
  useTimelineDoLead: () => ({ data: null }),
  useSalvarFicha: () => salvarFichaState,
  useVincularTag: () => ({ mutate: vi.fn(), isPending: false }),
  useDesvincularTag: () => ({ mutate: vi.fn(), isPending: false }),
  useLead: () => ({ data: mockLeadData, isLoading: false, isError: false }),
}));

import { PainelLateralLead } from "./painel-lateral-lead";
import { describe, expect, it } from "vitest";
import type { CampoCustomizado } from "@/lib/lead/types";
import { primeiroCampoObrigatorioAusente } from "./painel-lateral-lead";

const campoObrigatorio: CampoCustomizado = {
  chave: "codigo_obra",
  rotulo: "Código da obra",
  tipo: "TEXTO",
  opcoes: [],
  obrigatorio: true,
  filtravel: false,
  ordem: 1,
};

describe("campos customizados da ficha", () => {
  it("exige um campo obrigatório ausente antes de salvar", () => {
    expect(primeiroCampoObrigatorioAusente([campoObrigatorio], {})).toBe(campoObrigatorio);
    expect(primeiroCampoObrigatorioAusente([campoObrigatorio], { codigo_obra: "   " })).toBe(
      campoObrigatorio,
    );
  });

  it("aceita o campo obrigatório preenchido", () => {
    expect(primeiroCampoObrigatorioAusente([campoObrigatorio], { codigo_obra: "OBRA-12" })).toBeUndefined();
  });
});

describe("PainelLateralLead telefones", () => {
  const renderComProvider = (ui: React.ReactElement) => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
  };

  it("renderiza botao de telefone com href tel: se tiver telefone", () => {
    mockLeadData = { id: "1", nome: "Lead 1", telefone: "11999999999" };
    renderComProvider(<PainelLateralLead leadId="1" onFechar={() => {}} />);
    // O @base-ui/react força o render a ser button, então procuramos por button e depois vemos se no DOM é uma ancora
    const btn = screen.getByRole("link", { name: "Ligar para lead" });
    expect(btn).toHaveAttribute("href", "tel:11999999999");
  });

  it("nao renderiza botao de telefone se nao tiver", () => {
    mockLeadData = { id: "1", nome: "Lead 1", telefone: null };
    renderComProvider(<PainelLateralLead leadId="1" onFechar={() => {}} />);
    const btn = screen.queryByRole("link", { name: "Ligar para lead" });
    expect(btn).not.toBeInTheDocument();
  });

  it("mostra o código numérico nas informações da ficha", () => {
    mockLeadData = { id: "1", nome: "Lead 1", telefone: null, codigo: "00421" };
    renderComProvider(<PainelLateralLead leadId="1" onFechar={() => {}} />);
    expect(screen.getByText("Código")).toBeInTheDocument();
    expect(screen.getByText("00421")).toBeInTheDocument();
  });

  it("grava o nome do cliente ao sair do campo no overlay", () => {
    salvarFichaState.mutate.mockClear();
    mockLeadData = { id: "1", nome: "Lead 1", telefone: null };
    renderComProvider(<PainelLateralLead leadId="1" onFechar={() => {}} />);
    const campo = screen.getByLabelText("Nome");
    fireEvent.change(campo, { target: { value: "Maria Silva" } });
    fireEvent.blur(campo);
    expect(salvarFichaState.mutate).toHaveBeenCalledWith(
      { nome: "Maria Silva" },
      expect.any(Object),
    );
  });
});
