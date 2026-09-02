import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const useDashboardMock = vi.fn();
const telaEstreita = { atual: false };

vi.mock("@/lib/dashboard/use-dashboard", () => ({
  useVisaoGeralDashboard: (filtro: unknown) => useDashboardMock(filtro),
}));

vi.mock("@/lib/navegacao/tela-estreita", () => ({
  useTelaEstreita: () => telaEstreita.atual,
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    dashboard: {
      titulo: "Dashboard", descricao: "Visão consolidada", carregando: "Carregando", erro: "Erro", semDado: "Sem dados",
      abas: { rotulo: "Áreas", visaoGeral: "Visão Geral", operacional: "Operacional", comercial: "Comercial", iaAutomacao: "IA e Automação", depois: "Em breve" },
      periodos: { rotulo: "Período", hoje: "Hoje", seteDias: "7 dias", mes: "Mês", ano: "Ano" },
      somenteComputador: "Filtros avançados no computador",
      avisoComputador: "Relatórios completos ficam no computador.",
      funilApoio: "Etapas do pipeline",
      filtros: { rotulo: "Filtros", ano: "Ano", meses: "Meses", anoInteiro: "Ano inteiro", originacao: "Originação", intervalo: "{inicio} até {fim}", de: "De", ate: "Até", limpar: "Limpar", selecioneMes: "Selecione", origemCompleta: "Complete" },
      meses: ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"],
      kpis: { rotulo: "Indicadores", atendimentos: "Atendimentos", atendimentosApoio: "{total} acumulados", conversao: "Conversão", conversaoApoio: "{vendas} vendas / {leads} leads", tempoMedio: "Tempo médio", tempoMedioApoio: "Atendimentos finalizados", vendas: "Vendas fechadas", vendasApoio: "{total} acumuladas", csat: "Avaliação", csatApoio: "{total} avaliações", resolucaoIa: "Resolução por IA", resolucaoIaApoio: "Sem transferência humana", periodoAnterior: "vs. período anterior" },
      secoes: { ranking: "Top atendentes · avaliação", funil: "Funil de conversão", horarioPico: "Horário de pico · mensagens por hora" },
      ranking: { vazio: "Sem avaliações", media: "{media}", quantidadeSingular: "{total} avaliação", quantidadePlural: "{total} avaliações", semResponsavelSingular: "{total} venda sem responsável atribuído", semResponsavelPlural: "{total} vendas sem responsável atribuído" },
      funil: { vazio: "Sem etapas", semPassagem: "—" }, horario: { vazio: "Sem mensagens", hora: "{hora}h" },
      tempo: { minutos: "{minutos} min", horasMinutos: "{horas}h {minutos}min" },
    },
  }),
}));

import { PaginaDashboard } from "./pagina-dashboard";

const PAYLOAD_COM_DADOS = {
  periodo: { ano: 2040, meses: [8] },
  atendimentos: { noPeriodo: 12, acumulado: 40, comparativo: { valor: 20, unidade: "PERCENTUAL" } },
  tempoMedioAtendimento: { segundos: 5400, comparativo: { valor: -10, unidade: "PERCENTUAL" } },
  avaliacaoMedia: { media: 4.5, escalaMaxima: 10, quantidade: 8, comparativo: null },
  resolucaoPorIa: { percentual: 75, resolvidosSemTransferencia: 9, atendimentosFinalizados: 12, comparativo: { valor: 5, unidade: "PONTOS_PERCENTUAIS" } },
  vendasFechadas: { noPeriodo: 3, acumulado: 9, comparativo: null },
  taxaConversao: { percentual: 50, vendas: 3, leadsRecebidos: 6, comparativo: { valor: 25, unidade: "PONTOS_PERCENTUAIS" } },
  funil: [{ id: "e1", nome: "Negociação", ordem: 1, corVisual: null, quantidade: 6, percentualDePassagem: 50 }],
  horarioDePico: [{ hora: 10, quantidade: 7 }],
  rankingDeVendas: { atendentes: [{ id: "u1", nome: "Ana Silva", vendas: 2 }], semResponsavel: 1 },
  rankingDeAvaliacoes: {
    atendentes: [
      { id: "u1", nome: "Ana Silva", media: 4.5, quantidade: 8 },
      { id: "u2", nome: "Bruno Costa", media: 4.2, quantidade: 5 },
      { id: "u3", nome: "Carla Dias", media: 4, quantidade: 3 },
      { id: "u4", nome: "Diego Elias", media: 3.9, quantidade: 2 },
    ],
  },
};

/** Estado real desta instância hoje: quase tudo zerado. Uma tela desenhada só para o mock quebra aqui. */
const PAYLOAD_ZERADO = {
  periodo: { ano: 2040, meses: [8] },
  atendimentos: { noPeriodo: 0, acumulado: 0, comparativo: null },
  tempoMedioAtendimento: { segundos: null, comparativo: null },
  avaliacaoMedia: { media: null, escalaMaxima: 10, quantidade: 0, comparativo: null },
  resolucaoPorIa: { percentual: null, resolvidosSemTransferencia: 0, atendimentosFinalizados: 0, comparativo: null },
  vendasFechadas: { noPeriodo: 0, acumulado: 0, comparativo: null },
  taxaConversao: { percentual: 0, vendas: 0, leadsRecebidos: 0, comparativo: null },
  funil: [
    { id: "e1", nome: "Novo contato", ordem: 1, corVisual: null, quantidade: 0, percentualDePassagem: null },
    { id: "e2", nome: "Qualificação", ordem: 2, corVisual: null, quantidade: 0, percentualDePassagem: null },
  ],
  horarioDePico: [{ hora: 10, quantidade: 0 }],
  rankingDeVendas: { atendentes: [], semResponsavel: 0 },
  rankingDeAvaliacoes: { atendentes: [] },
};

describe("PaginaDashboard", () => {
  it("renderiza dados reais do payload, abas futuras desabilitadas e gráficos em CSS", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });

    render(<PaginaDashboard />);

    expect(screen.getByTestId("dashboard-conteudo")).toHaveClass("bg-background");
    expect(screen.getByTestId("dashboard-conteudo")).toHaveClass("min-h-full");
    expect(screen.getByLabelText("Filtros")).toHaveClass("bg-card/75");
    expect(screen.getAllByText("Atendimentos")[0].closest('[data-slot="card"]')).toHaveClass("bg-card");
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("1h 30min")).toBeInTheDocument();
    expect(screen.getByText("Avaliação")).toBeInTheDocument();
    expect(screen.getByText("4,5/10")).toBeInTheDocument();
    // 50,0% aparece duas vezes: taxa de conversão (KPI) e passagem da etapa (funil).
    expect(screen.getAllByText("50,0%")).toHaveLength(2);
    expect(screen.queryByText("Vendas fechadas")).not.toBeInTheDocument();
    expect(screen.getByText("Top atendentes · avaliação")).toBeInTheDocument();
    expect(screen.getByText("Ana Silva")).toBeInTheDocument();
    expect(screen.getAllByText("8 avaliações").length).toBeGreaterThan(0);
    expect(screen.getByText("+25,0pp vs. período anterior")).toBeInTheDocument();
    expect(screen.getAllByTestId("barra-funil")).toHaveLength(1);
    expect(screen.getAllByTestId("barra-horario")).toHaveLength(24);
    expect(screen.getByRole("button", { name: /Operacional/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Comercial/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /IA e Automação/ })).toBeDisabled();
    expect(screen.getByText("Resolução por IA")).toBeInTheDocument();
    expect(screen.getByText("75,0%")).toBeInTheDocument();
    expect(screen.getByText("Sem transferência humana")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Jan" }));
    expect(useDashboardMock).toHaveBeenLastCalledWith(expect.objectContaining({ meses: [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] }));
  });

  it("abas usam sublinhado, e a futura continua com o sufixo 'Em breve' e inativa", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    const ativa = screen.getByRole("button", { name: "Visão Geral" });
    expect(ativa).toHaveAttribute("aria-current", "page");
    expect(ativa).toHaveClass("border-b-2", "border-primary");
    expect(ativa).not.toHaveClass("bg-primary");

    const futura = screen.getByRole("button", { name: /Operacional/ });
    expect(futura).toHaveTextContent("Operacional · Em breve");
    expect(futura).toBeDisabled();
  });

  it("'Ano inteiro' marca os doze meses e desmarca todos ao repetir o clique", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    const anoInteiro = screen.getByRole("button", { name: "Ano inteiro" });
    expect(anoInteiro).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(anoInteiro);
    expect(useDashboardMock).toHaveBeenLastCalledWith(expect.objectContaining({ meses: [] }));
    expect(screen.getByRole("alert")).toHaveTextContent("Selecione");

    fireEvent.click(screen.getByRole("button", { name: "Ano inteiro" }));
    expect(useDashboardMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ meses: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] }),
    );
  });

  it("meses nao selecionados nao ficam preenchidos de azul — so a selecao destaca", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    const janeiro = screen.getByRole("button", { name: "Jan" });
    expect(janeiro).toHaveClass("bg-primary/10", "border-primary");

    fireEvent.click(janeiro);
    const desmarcado = screen.getByRole("button", { name: "Jan" });
    expect(desmarcado).toHaveAttribute("aria-pressed", "false");
    expect(desmarcado).not.toHaveClass("bg-primary/10");
  });

  it("originacao vira um botao unico, sem os campos De/Ate expostos", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    expect(screen.getByRole("button", { name: /Originação/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "De" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Até" })).not.toBeInTheDocument();
  });

  it("podio colore 1o, 2o e 3o e deixa o 4o neutro", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    expect(screen.getByTestId("posicao-1").getAttribute("style")).toContain("--medalha: var(--cor-atencao)");
    expect(screen.getByTestId("posicao-2").getAttribute("style")).toContain("--medalha: var(--texto-fraco)");
    expect(screen.getByTestId("posicao-3").getAttribute("style")).toContain("--medalha: var(--cor-atencao-escura)");
    expect(screen.getByTestId("posicao-4")).toHaveClass("bg-muted");
    expect(screen.getByTestId("posicao-4")).not.toHaveAttribute("style");
  });

  it("nao inventa selo de tendencia quando a API nao devolve comparativo", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    // avaliacaoMedia.comparativo é null no payload: o cartão de Avaliação fica sem selo.
    const cartaoAvaliacao = screen.getByText("Avaliação").closest('[data-slot="card"]');
    expect(within(cartaoAvaliacao as HTMLElement).queryByText(/período anterior/)).toBeNull();
  });

  it("com tudo zerado: sem NaN, trilho do funil visivel e nenhum selo inventado", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_ZERADO, isLoading: false, isError: false });
    const { container } = render(<PaginaDashboard />);

    expect(container.innerHTML).not.toMatch(/NaN|Infinity/);
    expect(screen.queryByText(/período anterior/)).toBeNull();
    expect(screen.getByText("0,0%")).toBeInTheDocument();
    expect(screen.getAllByText("Sem dados").length).toBeGreaterThan(0);

    const barras = screen.getAllByTestId("barra-funil");
    expect(barras).toHaveLength(2);
    for (const barra of barras) {
      expect(barra).toHaveStyle({ width: "0%" });
      // O trilho é o pai da barra: some a barra preenchida, nunca a linha.
      expect(barra.parentElement).toHaveClass("bg-muted", "h-7");
    }
    // Etapa sem taxa de passagem mostra o marcador do catálogo, não espaço em branco.
    expect(screen.getAllByText("—")).toHaveLength(2);

    for (const barra of screen.getAllByTestId("barra-horario")) {
      expect(barra).toHaveStyle({ height: "0%" });
    }
  });

  it("chip Hoje envia recorte diário, não o mês corrente", () => {
    telaEstreita.atual = true;
    useDashboardMock.mockReturnValue({ data: undefined, isLoading: false, isError: false, refetch: vi.fn() });
    render(<PaginaDashboard />);
    fireEvent.click(screen.getByRole("button", { name: "Hoje" }));
    const agora = new Date();
    const iso = `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, "0")}-${String(agora.getDate()).padStart(2, "0")}`;
    expect(useDashboardMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ inicio: iso, fim: iso, meses: [] }),
    );
    telaEstreita.atual = false;
  });
});
