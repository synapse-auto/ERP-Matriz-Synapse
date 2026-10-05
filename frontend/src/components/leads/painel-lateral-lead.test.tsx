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
      dados: { nome: "Nome", nomeInvalido: "Informe o nome do cliente.", telefone: "Telefone", editarTelefone: "Editar telefone", novoTelefone: "Novo número de telefone", avisoAlteracaoTelefone: "O número será alterado. As próximas mensagens serão enviadas para o novo número.", telefoneInvalido: "Informe um número válido com pelo menos 10 dígitos.", erroAlteracaoTelefone: "Não foi possível alterar o telefone.", confirmarAlteracaoTelefone: "Confirmar alteração", cancelarAlteracaoTelefone: "Cancelar", email: "Email", cpf: "CPF", empresa: "Empresa", codigo: "Código", localizacao: "Local", naoInformado: "Não", canalOrigem: "Canal" },
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
const camposState = vi.hoisted(() => ({ data: [] as CampoCustomizado[] }));
const permissoesState = vi.hoisted(() => ({ editar: true, liberadas: new Set<string>() }));
const salvarFichaState = vi.hoisted(() => ({
  mutate: vi.fn(),
  isPending: false,
}));
const salvarTelefoneState = vi.hoisted(() => ({
  mutate: vi.fn(),
  isPending: false,
}));
vi.mock("@/lib/lead/use-painel-lead", () => ({
  useEtapas: () => ({ data: [] }),
  useCamposCustomizados: () => camposState,
  useCanais: () => ({ data: [] }),
  useTodasAsTags: () => ({ data: [] }),
  useTagsDoLead: () => ({ data: [] }),
  useTimelineDoLead: () => ({ data: null }),
  useSalvarFicha: () => salvarFichaState,
  useSalvarTelefoneLead: () => salvarTelefoneState,
  useVincularTag: () => ({ mutate: vi.fn(), isPending: false }),
  useDesvincularTag: () => ({ mutate: vi.fn(), isPending: false }),
  useLead: () => ({ data: mockLeadData, isLoading: false, isError: false }),
}));
vi.mock("@/lib/gestao/use-capacidades", () => ({
  useCapacidades: () => ({
    pode: (chave: string) =>
      (chave === "contatos.editar" && permissoesState.editar) || permissoesState.liberadas.has(chave),
  }),
}));
vi.mock("@/components/ui/seletor-data", () => ({
  SeletorData: ({ id, valor, onChange }: { id: string; valor: string; onChange: (valor: string) => void }) => (
    <input id={id} type="text" value={valor} onChange={(evento) => onChange(evento.target.value)} />
  ),
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

  it("permite editar o telefone e apresenta o aviso antes de confirmar", () => {
    salvarTelefoneState.mutate.mockClear();
    mockLeadData = { id: "1", nome: "Lead 1", telefone: "11999999999" };
    renderComProvider(<PainelLateralLead leadId="1" contexto="agenda" onFechar={() => {}} />);

    fireEvent.click(screen.getByRole("button", { name: "Editar telefone" }));
    expect(screen.getByText(/próximas mensagens serão enviadas para o novo número/i)).toBeInTheDocument();
    const campo = screen.getByRole("textbox", { name: "Novo número de telefone" });
    fireEvent.change(campo, { target: { value: "+55 (11) 98888-7777" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar alteração" }));

    expect(salvarTelefoneState.mutate).toHaveBeenCalledWith("+55 (11) 98888-7777", expect.any(Object));
  });

  it("não exibe a edição de telefone sem a permissão de editar contatos", () => {
    permissoesState.editar = false;
    mockLeadData = { id: "1", nome: "Lead 1", telefone: "11999999999" };
    renderComProvider(<PainelLateralLead leadId="1" onFechar={() => {}} />);
    expect(screen.queryByRole("button", { name: "Editar telefone" })).not.toBeInTheDocument();
    permissoesState.editar = true;
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

describe("PainelLateralLead resumo por IA", () => {
  const renderFicha = () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={queryClient}>
        <PainelLateralLead leadId="1" onFechar={() => {}} />
      </QueryClientProvider>,
    );
  };

  it("com resumo_ia.ver mostra a seção e o texto", () => {
    permissoesState.liberadas = new Set(["resumo_ia.ver"]);
    mockLeadData = { id: "1", nome: "Lead 1", resumoIa: "Cliente pediu orçamento de box." };

    renderFicha();

    expect(screen.getByText("IA", { exact: true })).toBeInTheDocument();
    expect(screen.getByText("Cliente pediu orçamento de box.")).toBeInTheDocument();
    permissoesState.liberadas = new Set();
  });

  it("sem resumo_ia.ver não mostra a seção nem um texto que tenha ficado em cache", () => {
    permissoesState.liberadas = new Set();
    mockLeadData = { id: "1", nome: "Lead 1", resumoIa: "Cliente pediu orçamento de box." };

    renderFicha();

    expect(screen.queryByText("IA", { exact: true })).not.toBeInTheDocument();
    expect(screen.queryByText("Cliente pediu orçamento de box.")).not.toBeInTheDocument();
  });
});

describe("PainelLateralLead data de nascimento por instancia", () => {
  const renderFicha = () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={queryClient}>
        <PainelLateralLead leadId="1" onFechar={() => {}} />
      </QueryClientProvider>,
    );
  };

  const nascimento: CampoCustomizado = {
    chave: "data_nascimento",
    rotulo: "Data de nascimento",
    tipo: "DATA",
    opcoes: [],
    obrigatorio: false,
    filtravel: false,
    ordem: 2,
  };

  it("sem metadado nao inventa o campo na ficha", () => {
    camposState.data = [];
    permissoesState.editar = true;
    mockLeadData = { id: "1", nome: "Lead 1", dadosCustomizados: {} };

    renderFicha();

    expect(screen.queryByLabelText(/Data de nascimento/)).not.toBeInTheDocument();
  });

  it("com metadado mostra DATA e envia aniversario junto dos demais campos", () => {
    camposState.data = [
      { ...campoObrigatorio, obrigatorio: false, ordem: 1 },
      nascimento,
    ];
    permissoesState.editar = true;
    mockLeadData = { id: "1", nome: "Lead 1", dadosCustomizados: { codigo_obra: "OBRA-12" } };
    salvarFichaState.mutate.mockClear();

    renderFicha();
    fireEvent.change(screen.getByLabelText(/Data de nascimento/), { target: { value: "1990-05-21" } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));

    expect(salvarFichaState.mutate).toHaveBeenCalledWith(
      { notas: "", dadosCustomizados: { codigo_obra: "OBRA-12", data_nascimento: "1990-05-21" } },
      expect.any(Object),
    );
  });

  it("sem permissao de edicao exibe o valor mas nao permite salvar", () => {
    camposState.data = [nascimento];
    permissoesState.editar = false;
    mockLeadData = {
      id: "1",
      nome: "Lead 1",
      dadosCustomizados: { data_nascimento: "1990-05-21T00:00:00Z" },
    };

    renderFicha();

    expect(screen.getByLabelText(/Data de nascimento/)).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Salvar" })).not.toBeInTheDocument();
    permissoesState.editar = true;
  });
});
