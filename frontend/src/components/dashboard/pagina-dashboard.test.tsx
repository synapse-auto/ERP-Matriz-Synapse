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
      modos: { rotulo: "Modo de visualização", compacta: "Compacta", expandida: "Expandida", serieIndisponivel: "Série mensal indisponível" },
      periodos: { rotulo: "Período", hoje: "Hoje", seteDias: "7 dias", mes: "Mês", ano: "Ano" },
      somenteComputador: "Filtros avançados no computador",
      avisoComputador: "Relatórios completos ficam no computador.",
      funilApoio: "Etapas do pipeline",
      filtros: { rotulo: "Filtros", ano: "Ano", meses: "Meses", anoInteiro: "Ano inteiro", originacao: "Originação", intervalo: "{inicio} até {fim}", de: "De", ate: "Até", limpar: "Limpar", selecioneMes: "Selecione", origemCompleta: "Complete" },
      meses: ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"],
      kpis: { rotulo: "Indicadores", atendimentos: "Atendimentos", atendimentosApoio: "{total} acumulados", conversao: "Conversão", conversaoApoio: "{vendas} vendas / {leads} leads", tempoMedio: "Tempo médio", tempoMedioApoio: "Atendimentos finalizados", vendas: "Vendas fechadas", vendasApoio: "{total} acumuladas", csat: "Avaliação", csatApoio: "{total} avaliações", resolucaoIa: "Resolução por IA", resolucaoIaApoio: "Sem transferência humana", novosLeads: "Novos leads", novosLeadsApoio: "Criados no período", resumoSerie: "mín {minMes} {min} · máx {maxMes} {max} · média {media}", periodoAnterior: "vs. período anterior" },
      agora: { rotulo: "Agora", emIa: "Em IA", emAtendimento: "Em atendimento humano", leadsNovosHoje: "Leads novos hoje", vendasHoje: "Vendas hoje", atendentesOnline: "Atendentes online", atualizadoAgora: "atualizado agora", atualizadoSegundos: "atualizado há {segundos}s", atualizadoMinutos: "atualizado há {minutos}min" },
      secoes: { ranking: "Top atendentes · avaliação", equipe: "Equipe · desempenho", equipeApoio: "Atendimentos, vendas e nota média no período", equipeOrdenadoPor: "ordenado por vendas fechadas", funil: "Funil de conversão", horarioPico: "Horário de pico · mensagens por hora" },
      ranking: { vazio: "Sem avaliações", media: "{media}", quantidadeSingular: "{total} avaliação", quantidadePlural: "{total} avaliações", semResponsavelSingular: "{total} venda sem responsável atribuído", semResponsavelPlural: "{total} vendas sem responsável atribuído" },
      equipe: { vazio: "Sem atendimentos no período", colunaAtendente: "Atendente", colunaAtendimentos: "Atend.", colunaVendas: "Vendas", colunaNota: "Nota", semNota: "—" },
      funil: { vazio: "Sem etapas", semPassagem: "—", perdido: "Perdido", colunaEtapa: "Etapa", colunaPassa: "Passa" }, horario: { vazio: "Sem mensagens", hora: "{hora}h", apoio: "Mensagens trocadas em cada hora do dia", picoUnico: "pico às {hora}", picos: "picos às {manha} e {tarde}", picosEValeUnico: "picos às {manha} e {tarde} · vale às {vale}", picosEVale: "picos às {manha} e {tarde} · vale entre {inicio} e {fim}" },
      tempo: { minutos: "{minutos} min", horasMinutos: "{horas}h {minutos}min" },
      satisfacao: { titulo: "Satisfação no período", apoio: "Escala 0 a 10 · {total} avaliações", media: "média", otimo: "Ótimo 9–10", bom: "Bom 7–8", ruim: "Ruim 0–6", vazio: "Nenhuma avaliação no período." },
    },
  }),
}));

import { PaginaDashboard } from "./pagina-dashboard";

const PAYLOAD_COM_DADOS = {
  periodo: { ano: 2040, meses: [8] },
  atendimentos: { noPeriodo: 12, acumulado: 40, comparativo: { valor: 20, unidade: "PERCENTUAL" } },
  novosLeads: { noPeriodo: 30, comparativo: { valor: 14, unidade: "PERCENTUAL" } },
  tempoMedioAtendimento: { segundos: 5400, comparativo: { valor: -10, unidade: "PERCENTUAL" } },
  avaliacaoMedia: { media: 4.5, escalaMaxima: 10, quantidade: 8, distribuicao: { otimo: 2, bom: 3, ruim: 3 }, comparativo: null },
  resolucaoPorIa: { percentual: 75, resolvidosSemTransferencia: 9, atendimentosFinalizados: 12, comparativo: { valor: 5, unidade: "PONTOS_PERCENTUAIS" } },
  vendasFechadas: { noPeriodo: 3, acumulado: 9, comparativo: null },
  taxaConversao: { percentual: 50, vendas: 3, leadsRecebidos: 6, comparativo: { valor: 25, unidade: "PONTOS_PERCENTUAIS" } },
  statusAoVivo: { emIa: 6, emAtendimento: 4, leadsNovosHoje: 2, vendasHoje: 1, atendentesOnline: { online: 5, total: 8 } },
  funil: [{ id: "e1", nome: "Negociação", ordem: 1, corVisual: null, quantidade: 6, percentualDePassagem: 50 }],
  leadsPerdidos: 5,
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
  equipeDesempenho: [
    { id: "u1", nome: "Ana Silva", atendimentos: 18, vendas: 2, nota: 4.5, avaliacoes: 8 },
    { id: "u2", nome: "Bruno Costa", atendimentos: 9, vendas: 1, nota: 4.2, avaliacoes: 5 },
    { id: "u3", nome: "Carla Dias", atendimentos: 7, vendas: 0, nota: null, avaliacoes: 0 },
  ],
  seriesMensais: [{ mes: "2040-08", parcial: false, disponivel: true,
    atendimentos: 12, novosLeads: 30, tempoMedioSegundos: 5400,
    vendasFechadas: 3, taxaConversao: 10, avaliacaoMedia: 4.5, resolucaoPorIa: 75 }],
};

/** Estado real desta instância hoje: quase tudo zerado. Uma tela desenhada só para o mock quebra aqui. */
const PAYLOAD_ZERADO = {
  periodo: { ano: 2040, meses: [8] },
  atendimentos: { noPeriodo: 0, acumulado: 0, comparativo: null },
  novosLeads: { noPeriodo: 0, comparativo: null },
  tempoMedioAtendimento: { segundos: null, comparativo: null },
  avaliacaoMedia: { media: null, escalaMaxima: 10, quantidade: 0, distribuicao: { otimo: 0, bom: 0, ruim: 0 }, comparativo: null },
  resolucaoPorIa: { percentual: null, resolvidosSemTransferencia: 0, atendimentosFinalizados: 0, comparativo: null },
  vendasFechadas: { noPeriodo: 0, acumulado: 0, comparativo: null },
  taxaConversao: { percentual: 0, vendas: 0, leadsRecebidos: 0, comparativo: null },
  statusAoVivo: { emIa: 0, emAtendimento: 0, leadsNovosHoje: 0, vendasHoje: 0, atendentesOnline: { online: 0, total: 0 } },
  funil: [
    { id: "e1", nome: "Novo contato", ordem: 1, corVisual: null, quantidade: 0, percentualDePassagem: null },
    { id: "e2", nome: "Qualificação", ordem: 2, corVisual: null, quantidade: 0, percentualDePassagem: null },
  ],
  leadsPerdidos: 0,
  horarioDePico: [{ hora: 10, quantidade: 0 }],
  rankingDeVendas: { atendentes: [], semResponsavel: 0 },
  rankingDeAvaliacoes: { atendentes: [] },
  equipeDesempenho: [],
  seriesMensais: [{ mes: "2040-08", parcial: false, disponivel: true,
    atendimentos: 0, novosLeads: 0, tempoMedioSegundos: null,
    vendasFechadas: 0, taxaConversao: null, avaliacaoMedia: null, resolucaoPorIa: null }],
};

/**
 * O selo mostra só a variação ("+14,0%"), como no mockup; o "vs. período anterior" continua no
 * texto acessível do mesmo elemento. Procura pelo selo e confere as duas partes juntas.
 */
function seloComTexto(variacao: string): HTMLElement {
  const selo = screen
    .getAllByTestId("selo-tendencia")
    .find((elemento) => elemento.textContent?.startsWith(variacao));
  if (!selo) throw new Error(`selo de tendencia ${variacao} nao encontrado`);
  return selo;
}

describe("PaginaDashboard", () => {
  it("renderiza dados reais do payload, abas futuras desabilitadas e gráficos em CSS", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });

    render(<PaginaDashboard />);

    expect(screen.getByTestId("dashboard-conteudo")).toHaveClass("bg-background");
    expect(screen.getByTestId("dashboard-conteudo")).toHaveClass("min-h-full");
    expect(screen.getByLabelText("Filtros")).toHaveClass("bg-card");
    expect(screen.getAllByText("Atendimentos")[0].closest('[data-slot="card"]')).toHaveClass("bg-card");
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("1h 30min")).toBeInTheDocument();
    expect(screen.getByText("Avaliação")).toBeInTheDocument();
    expect(screen.getByTestId("kpi-Avaliação")).toHaveTextContent("4,5/10");
    expect(screen.getByText("Satisfação no período")).toBeInTheDocument();
    expect(screen.getByTestId("grafico-distribuicao-avaliacoes")).toBeInTheDocument();
    expect(screen.getByText("Ótimo 9–10")).toBeInTheDocument();
    // 50,0% aparece duas vezes: taxa de conversão (KPI) e passagem da etapa (funil).
    expect(screen.getAllByText("50,0%")).toHaveLength(2);
    expect(screen.getAllByText("Vendas fechadas").length).toBeGreaterThan(0);
    expect(screen.getByText("Equipe · desempenho")).toBeInTheDocument();
    expect(screen.getAllByText("Ana Silva").length).toBeGreaterThan(0);
    expect(seloComTexto("+25,0pp")).toHaveTextContent("+25,0pp vs. período anterior");
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

  it("alterna Compacta e Expandida com a série recebida, sem refazer a consulta", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    const compacta = screen.getByRole("button", { name: "Compacta" });
    const expandida = screen.getByRole("button", { name: "Expandida" });
    expect(compacta).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("serie-atendimentos")).toBeInTheDocument();

    fireEvent.click(expandida);
    expect(expandida).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("serie-atendimentos")).toBeInTheDocument();
    expect(screen.getByTestId("kpi-Atendimentos")).toHaveTextContent("12");
    expect(screen.getByText("mín Ago 12 · máx Ago 12 · média 12")).toBeInTheDocument();
    expect(useDashboardMock).toHaveBeenLastCalledWith(expect.objectContaining({ meses: expect.any(Array) }));

    fireEvent.click(compacta);
    expect(compacta).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("serie-atendimentos")).toBeInTheDocument();
  });

  it("faixa AGORA mostra os contadores ao vivo e o KPI de novos leads, sem inventar os itens sem critério definido", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    const faixa = screen.getByLabelText("Agora");
    expect(within(faixa).getByText("Em IA")).toBeInTheDocument();
    expect(within(faixa).getByText("6")).toBeInTheDocument();
    expect(within(faixa).getByText("Em atendimento humano")).toBeInTheDocument();
    expect(within(faixa).getByText("4")).toBeInTheDocument();
    expect(within(faixa).getByText("Leads novos hoje")).toBeInTheDocument();
    expect(within(faixa).getByText("Vendas hoje")).toBeInTheDocument();
    expect(within(faixa).getByText("Atendentes online")).toBeInTheDocument();
    expect(within(faixa).getByText("5/8")).toBeInTheDocument();
    expect(within(faixa).getByText("atualizado agora")).toBeInTheDocument();
    // Itens do mockup sem critério definido (aguardando 1ª resposta e esquecidos) não são inventados.
    expect(within(faixa).queryByText(/Aguardando/)).not.toBeInTheDocument();
    expect(within(faixa).queryByText(/Esquecid/)).not.toBeInTheDocument();

    expect(screen.getByText("Novos leads")).toBeInTheDocument();
    expect(screen.getByText("30")).toBeInTheDocument();
    expect(seloComTexto("+14,0%")).toHaveTextContent("+14,0% vs. período anterior");
  });

  it("funil mostra a linha Perdido como agregado à parte, sem entrar na sequência ordenada", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    expect(screen.getByText("Perdido")).toBeInTheDocument();
    expect(screen.getByTestId("quantidade-perdidos")).toHaveTextContent("5");
  });

  it("tabela Equipe · desempenho junta atendimentos, vendas e nota; sem nota mostra o marcador do catálogo", () => {
    useDashboardMock.mockReturnValue({ data: PAYLOAD_COM_DADOS, isLoading: false, isError: false });
    render(<PaginaDashboard />);

    const tabela = screen.getByText("Equipe · desempenho").closest('[data-slot="card"]') as HTMLElement;
    expect(within(tabela).getByText("Atend.")).toBeInTheDocument();
    expect(within(tabela).getByText("Vendas")).toBeInTheDocument();
    expect(within(tabela).getByText("Nota")).toBeInTheDocument();
    expect(within(tabela).getByText("ordenado por vendas fechadas")).toBeInTheDocument();
    expect(within(tabela).getByText("Carla Dias")).toBeInTheDocument();
    // Carla não recebeu avaliação no período: nota vem null, não zero inventado.
    const linhaCarla = within(tabela).getByText("Carla Dias").closest("tr") as HTMLElement;
    expect(within(linhaCarla).getByText("—")).toBeInTheDocument();
    expect(within(tabela).getByText("1 venda sem responsável atribuído")).toBeInTheDocument();
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
    expect(janeiro).toHaveClass("bg-primary/10", "border-transparent");

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

  it("podio da Equipe colore 1o, 2o e 3o e deixa o 4o neutro", () => {
    const comQuartaLinha = {
      ...PAYLOAD_COM_DADOS,
      equipeDesempenho: [
        ...PAYLOAD_COM_DADOS.equipeDesempenho,
        { id: "u4", nome: "Diego Elias", atendimentos: 3, vendas: 0, nota: 3.9, avaliacoes: 2 },
      ],
    };
    useDashboardMock.mockReturnValue({ data: comQuartaLinha, isLoading: false, isError: false });
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
    expect(screen.getByText("Sem atendimentos no período")).toBeInTheDocument();
    expect(screen.getByTestId("quantidade-perdidos")).toHaveTextContent("0");

    const barras = screen.getAllByTestId("barra-funil");
    expect(barras).toHaveLength(2);
    for (const barra of barras) {
      expect(barra).toHaveStyle({ width: "0%" });
      // O trilho é o pai da barra: some a barra preenchida, nunca a linha.
      expect(barra.parentElement).toHaveClass("bg-muted", "h-7");
    }
    // Etapa sem taxa de passagem mostra o marcador do catálogo, não espaço em branco.
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(2);

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
