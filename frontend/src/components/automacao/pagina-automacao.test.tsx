import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const atualizarParametro = vi.fn();
const atualizarDisponibilidade = vi.fn();
const atualizarFollowUp = vi.fn();
const atualizarFidelizacao = vi.fn();
const authMock = vi.hoisted(() => ({ papel: "GESTOR" }));
const navigation = vi.hoisted(() => ({ replace: vi.fn((href: string) => window.history.replaceState(null, "", href)) }));

const PARAMETROS = [
  { chave: "followup.primeiro.minutos", valor: "15", unidade: "minutos", tipo: "INT", valorMin: 1, valorMax: 1440, descricao: "Tempo sem resposta do lead.", atualizadoEm: "2026-01-01T00:00:00Z" },
  { chave: "automacao.habilitada", valor: "true", unidade: null, tipo: "BOOLEAN", valorMin: null, valorMax: null, descricao: "Chave geral da automação.", atualizadoEm: "2026-01-01T00:00:00Z" },
];

const TELEMETRIA = { mensagensEnviadas: 1284, clientesTransferidos: 342, conexaoAutomacaoAtiva: true, crmOnline: false, atualizadoEm: "2026-01-01T00:00:00Z" };
const EQUIPE = [
  { id: "ana", nome: "Ana Atendente", email: "ana@teste.local", papel: "ATENDENTE", statusPresenca: "ONLINE", ativo: true, disponivelParaIa: true, cargo: "Consultora", fotoUrl: null },
  { id: "bia", nome: "Bia Inativa", email: "bia@teste.local", papel: "ATENDENTE", statusPresenca: "OFFLINE", ativo: false, disponivelParaIa: true, cargo: null, fotoUrl: null },
  { id: "gestor", nome: "Gil Gestor", email: "gil@teste.local", papel: "GESTOR", statusPresenca: "ONLINE", ativo: true, disponivelParaIa: false, cargo: "Gerente", fotoUrl: null },
  { id: "sub", nome: "Sara Subgestora", email: "sara@teste.local", papel: "SUBGESTOR", statusPresenca: "AUSENTE", ativo: true, disponivelParaIa: false, cargo: null, fotoUrl: null },
];
const FOLLOW_UPS = [{ id: "fu-1", nome: "Após 2 horas", tempoMinutos: 120, texto: "Olá, {nome}!", ativo: true }];

const TEXTOS = {
  app: { nome: "Synapse CRM", marca: "Instância Teste" },
  equipe: {
    papeis: { atendente: "Atendente", subgestor: "Subgestor" },
    presenca: { online: "Online", ausente: "Ausente", offline: "Offline" },
    disponibilidadeIa: { rotulo: "Disponibilidade IA", disponivel: "Disponível", indisponivel: "Fora", naoAplicavel: "N/A" },
  },
  automacao: {
    titulo: "Automação", descricao: "Descrição", descricaoPorAba: { geral: "Configurações gerais do assistente de IA.", followUp: "Mensagens automáticas enviadas quando o cliente demora para responder.", fidelizacao: "Mensagens automáticas para reativar clientes sem contato recente." },
    semPermissao: "Você não tem permissão para acessar a Automação.", carregando: "Carregando...", vazio: "Nenhum parâmetro.", erro: "Erro ao carregar.", erroSalvar: "Erro ao salvar.", erroFaixa: "Valor fora da faixa permitida.", faixaLabel: "Faixa", ativado: "Ativado", desativado: "Desativado", salvar: "Salvar", salvando: "Salvando...",
    abas: { geral: "Geral", followUp: "Follow-up", fidelizacao: "Fidelização" },
    disponibilidade: { titulo: "Atendentes Disponíveis", descricao: "Defina quais atendentes estão disponíveis agora para a IA direcionar clientes.", rotuloAtual: "DISPONIBILIDADE ATUAL", contagem: "{disponiveis} de {total} disponíveis", vazio: "Nenhum atendente ativo cadastrado.", erro: "Erro equipe" },
    recursosIa: { titulo: "Recursos de IA", resumo: "Resumo automático por IA", resumoDescricao: "Gera um resumo do atendimento ao finalizar.", preenchimento: "Preenchimento automático", preenchimentoDescricao: "Preenche dados do cliente a partir da conversa.", ativado: "Ativado", desativado: "Desativado", gatilho: "Gatilho", quantidade: "Mensagens" },
    avancado: { titulo: "Parâmetros avançados", descricao: "Valores operacionais lidos diretamente pela Automação.", abrir: "Abrir parâmetros avançados", fechar: "Fechar parâmetros avançados" },
    regras: {
      novo: "Nova regra", novoFollowUp: "Novo follow-up", novaMensagem: "Nova mensagem", followUpContagemSingular: "{quantidade} follow-up", followUpsContagem: "{quantidade} follow-ups", mensagemContagemSingular: "{quantidade} mensagem", mensagensContagem: "{quantidade} mensagens", editar: "Editar", ativar: "Ativar regra", desativar: "Desativar regra", excluir: "Excluir", confirmarExclusao: "Excluir esta regra?", cancelar: "Cancelar", ativo: "Ativo", inativo: "Inativo", vazio: "Vazio", vazioFollowUp: "Nenhum follow-up cadastrado.", vazioFidelizacao: "Nenhuma mensagem cadastrada.", erro: "Erro regras", erroSalvar: "Não foi possível salvar. O valor anterior foi restaurado.", unidadeHora: "hora", unidadeHoras: "Horas", unidadeDia: "dia", unidadeDias: "Dias", tempo: "TEMPO SEM RESPOSTA PARA ENVIAR", dias: "DIAS SEM ENTRAR EM CONTATO", diasSemContato: "dias sem contato", mensagem: "MENSAGEM", preview: "Prévia", previewNome: "Marcos", placeholderAjuda: "Use {nome}", mensagemNovaFollowUp: "Olá, {nome}!", mensagemNovaFidelizacao: "Olá novamente, {nome}!", visualizacaoWhatsapp: "VISUALIZAÇÃO NO WHATSAPP", online: "online", hoje: "HOJE", horario: "09:14", composer: "Mensagem", previewVazio: "Sua mensagem aparecerá aqui…", badgeFollowUp: "{tempo} sem resposta", badgeFidelizacao: "{dias} sem contato", gatilhoFollowUp: "Enviado após {tempo} sem resposta", gatilhoFidelizacao: "Enviado após {dias} sem contato",
    },
    telemetria: { mensagensEnviadas: "Mensagens Enviadas", clientesTransferidos: "Clientes Transferidos", conexaoAutomacao: "Conexão Automação", statusDoCrm: "Status do CRM", conectado: "Conectado", desconectado: "Desconectado", online: "Online", offline: "Offline", erro: "Erro telemetria" },
  },
};

vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => TEXTOS }));
vi.mock("@/lib/auth/auth-store", () => ({ useAuthStore: (seletor: (estado: { papel: string }) => unknown) => seletor(authMock) }));
vi.mock("@/lib/equipe/use-equipe", () => ({
  useEquipe: () => ({ data: EQUIPE, isLoading: false, isError: false, refetch: vi.fn() }),
  useAtualizarDisponibilidadeParaIa: () => ({ mutate: atualizarDisponibilidade, isPending: false }),
}));
vi.mock("@/lib/automacao/use-automacao", () => ({
  useConfiguracaoAutomacao: () => ({ data: PARAMETROS, isLoading: false, isError: false, refetch: vi.fn() }),
  useAtualizarParametroAutomacao: () => ({ mutate: atualizarParametro, isPending: false, isError: false }),
  useTelemetriaAutomacao: () => ({ data: TELEMETRIA, isLoading: false, isError: false, refetch: vi.fn() }),
  useRecursosIa: () => ({ data: { resumo: { ativo: true, gatilho: "AMBOS", quantidadeMensagens: 20 }, preenchimentoAutomatico: false }, isLoading: false, isError: false, refetch: vi.fn() }),
  useAtualizarResumoIa: () => ({ mutate: vi.fn(), isPending: false }),
  useRegrasFollowUp: () => ({ data: FOLLOW_UPS, isLoading: false, isError: false, refetch: vi.fn() }),
  useRegrasFidelizacao: () => ({ data: [], isLoading: false, isError: false, refetch: vi.fn() }),
  useMutacaoRegraFollowUp: () => ({ mutate: atualizarFollowUp, isPending: false }),
  useMutacaoRegraFidelizacao: () => ({ mutate: atualizarFidelizacao, isPending: false }),
  useAlternarRegraFollowUp: () => ({ mutate: vi.fn(), isPending: false }),
  useAlternarRegraFidelizacao: () => ({ mutate: vi.fn(), isPending: false }),
  useExcluirRegraFollowUp: () => ({ mutate: vi.fn(), isPending: false }),
  useExcluirRegraFidelizacao: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock("next/navigation", () => ({ useRouter: () => navigation, useSearchParams: () => new URLSearchParams(window.location.search) }));

import { PaginaAutomacao } from "./pagina-automacao";

describe("pagina de automacao", () => {
  beforeEach(() => {
    authMock.papel = "GESTOR";
    window.history.replaceState(null, "", "/automacao");
    vi.clearAllMocks();
  });

  it("nega a rota diretamente para ATENDENTE", () => {
    authMock.papel = "ATENDENTE";
    render(<PaginaAutomacao />);
    expect(screen.getByRole("alert")).toHaveTextContent("Você não tem permissão");
    expect(screen.queryByText("Mensagens Enviadas")).not.toBeInTheDocument();
  });

  it("mostra telemetria real sem inventar valores", () => {
    render(<PaginaAutomacao />);
    expect(screen.getByText("1.284")).toBeInTheDocument();
    expect(screen.getByText("342")).toBeInTheDocument();
    expect(screen.getByText("Conectado")).toBeInTheDocument();
    expect(screen.getByText("Offline")).toBeInTheDocument();
  });

  it("lista atendentes e subgestores ativos e altera a disponibilidade pela mutacao existente", () => {
    render(<PaginaAutomacao />);
    expect(screen.getByText("Ana Atendente")).toBeInTheDocument();
    expect(screen.getByText("Sara Subgestora")).toBeInTheDocument();
    expect(screen.getByText("Consultora")).toBeInTheDocument();
    expect(screen.getByText("1 de 2 disponíveis")).toBeInTheDocument();
    expect(screen.queryByText("Bia Inativa")).not.toBeInTheDocument();
    expect(screen.queryByText("Gil Gestor")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("switch", { name: "Disponibilidade IA: Sara Subgestora" }));
    expect(atualizarDisponibilidade).toHaveBeenCalledWith({ id: "sub", disponivelParaIa: true });
    fireEvent.click(screen.getByRole("switch", { name: "Disponibilidade IA: Ana Atendente" }));
    expect(atualizarDisponibilidade).toHaveBeenCalledWith({ id: "ana", disponivelParaIa: false });
  });

  it("mantem parametros avancados fechados e ainda salva automacao.habilitada via Switch", () => {
    const { container } = render(<PaginaAutomacao />);
    const detalhes = container.querySelector("details");
    expect(detalhes).not.toHaveAttribute("open");
    fireEvent.click(screen.getByText("Parâmetros avançados"));
    expect(detalhes).toHaveAttribute("open");
    const interruptor = screen.getByRole("switch", { name: "Chave geral da automação." });
    expect(interruptor).toBeChecked();
    fireEvent.click(interruptor);
    expect(interruptor).not.toBeChecked();
    const salvar = screen.getAllByRole("button", { name: "Salvar" }).find((botao) => !botao.hasAttribute("disabled"));
    expect(salvar).toBeDefined();
    fireEvent.click(salvar!);
    expect(atualizarParametro).toHaveBeenCalledWith({ chave: "automacao.habilitada", valor: "false" });
  });

  it("envia uma unica atualizacao do texto no blur e nenhuma enquanto digita", () => {
    window.history.replaceState(null, "", "/automacao?aba=followUp");
    render(<PaginaAutomacao />);
    const mensagem = screen.getByRole("textbox", { name: "MENSAGEM" });
    fireEvent.change(mensagem, { target: { value: "Novo texto para {nome}" } });
    expect(atualizarFollowUp).not.toHaveBeenCalled();
    fireEvent.blur(mensagem);
    expect(atualizarFollowUp).toHaveBeenCalledTimes(1);
    expect(atualizarFollowUp).toHaveBeenCalledWith(
      { id: "fu-1", dados: { tempoMinutos: 120, texto: "Novo texto para {nome}", ativo: true } },
      expect.objectContaining({ onError: expect.any(Function) }),
    );
  });

  it("cria regras de follow-up e fidelizacao inativas para revisao", () => {
    window.history.replaceState(null, "", "/automacao?aba=followUp");
    const { unmount } = render(<PaginaAutomacao />);
    fireEvent.click(screen.getByRole("button", { name: "Novo follow-up" }));
    expect(atualizarFollowUp).toHaveBeenCalledWith(
      { dados: { tempoMinutos: 60, texto: "Olá, {nome}!", ativo: false } },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );

    unmount();
    window.history.replaceState(null, "", "/automacao?aba=fidelizacao");
    render(<PaginaAutomacao />);
    fireEvent.click(screen.getByRole("button", { name: "Nova mensagem" }));
    expect(atualizarFidelizacao).toHaveBeenCalledWith(
      { dados: { diasSemContato: 30, mensagem: "Olá novamente, {nome}!", ativo: false } },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("persiste a aba escolhida na URL", () => {
    render(<PaginaAutomacao />);
    fireEvent.click(screen.getByRole("tab", { name: "Fidelização" }));
    expect(navigation.replace).toHaveBeenCalledWith("/automacao?aba=fidelizacao", { scroll: false });
    expect(screen.getByText("Mensagens automáticas para reativar clientes sem contato recente.")).toBeInTheDocument();
  });

  it("mantem a faixa numerica vinda do backend", () => {
    render(<PaginaAutomacao />);
    fireEvent.click(screen.getByText("Parâmetros avançados"));
    expect(screen.getByText("Faixa: 1–1440 minutos")).toBeInTheDocument();
    const linha = screen.getByText("followup.primeiro.minutos").parentElement?.parentElement?.parentElement;
    expect(linha).toBeTruthy();
    fireEvent.change(within(linha!).getByDisplayValue("15"), { target: { value: "9999" } });
    expect(within(linha!).getByRole("alert")).toHaveTextContent("Valor fora da faixa");
  });
});
