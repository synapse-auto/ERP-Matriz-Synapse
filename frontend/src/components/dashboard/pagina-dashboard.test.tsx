import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const useDashboardMock = vi.fn();

vi.mock("@/lib/dashboard/use-dashboard", () => ({
  useVisaoGeralDashboard: (filtro: unknown) => useDashboardMock(filtro),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    dashboard: {
      titulo: "Dashboard", descricao: "Visão consolidada", carregando: "Carregando", erro: "Erro", semDado: "Sem dados",
      abas: { rotulo: "Áreas", visaoGeral: "Visão Geral", operacional: "Operacional", comercial: "Comercial", iaAutomacao: "IA e Automação", depois: "Em breve" },
      filtros: { rotulo: "Filtros", ano: "Ano", meses: "Meses", originacao: "Originação", de: "De", ate: "Até", limpar: "Limpar", selecioneMes: "Selecione", origemCompleta: "Complete" },
      meses: ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"],
      kpis: { rotulo: "Indicadores", atendimentos: "Atendimentos", atendimentosApoio: "{total} acumulados", conversao: "Conversão", conversaoApoio: "{vendas} vendas / {leads} leads", tempoMedio: "Tempo médio", tempoMedioApoio: "Atendimentos finalizados", vendas: "Vendas fechadas", vendasApoio: "{total} acumuladas", csat: "CSAT", csatApoio: "{total} avaliações", resolucaoIa: "Resolução por IA", resolucaoIaApoio: "Sem transferência humana", periodoAnterior: "vs. período anterior" },
      secoes: { ranking: "Top atendentes · vendas fechadas", funil: "Funil de conversão", horarioPico: "Horário de pico · mensagens por hora" },
      ranking: { vazio: "Sem vendas", semResponsavelSingular: "{total} venda sem responsável atribuído", semResponsavelPlural: "{total} vendas sem responsável atribuído" },
      funil: { vazio: "Sem etapas" }, horario: { vazio: "Sem mensagens", hora: "{hora}h" },
      tempo: { minutos: "{minutos} min", horasMinutos: "{horas}h {minutos}min" },
    },
  }),
}));

import { PaginaDashboard } from "./pagina-dashboard";

describe("PaginaDashboard", () => {
  it("renderiza dados reais do payload, abas futuras desabilitadas e gráficos em CSS", () => {
    useDashboardMock.mockReturnValue({
      data: {
        periodo: { ano: 2040, meses: [8] },
        atendimentos: { noPeriodo: 12, acumulado: 40, comparativo: { valor: 20, unidade: "PERCENTUAL" } },
        tempoMedioAtendimento: { segundos: 5400, comparativo: { valor: -10, unidade: "PERCENTUAL" } },
        avaliacaoMedia: { media: 4.5, escalaMaxima: 5, quantidade: 8, comparativo: null },
        resolucaoPorIa: { percentual: 75, resolvidosSemTransferencia: 9, atendimentosFinalizados: 12, comparativo: { valor: 5, unidade: "PONTOS_PERCENTUAIS" } },
        vendasFechadas: { noPeriodo: 3, acumulado: 9, comparativo: null },
        taxaConversao: { percentual: 50, vendas: 3, leadsRecebidos: 6, comparativo: { valor: 25, unidade: "PONTOS_PERCENTUAIS" } },
        funil: [{ id: "e1", nome: "Negociação", ordem: 1, corVisual: null, quantidade: 6, percentualDePassagem: 50 }],
        horarioDePico: [{ hora: 10, quantidade: 7 }],
        rankingDeVendas: { atendentes: [{ id: "u1", nome: "Ana Silva", vendas: 2 }], semResponsavel: 1 },
      },
      isLoading: false,
      isError: false,
    });

    render(<PaginaDashboard />);

    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("50,0%")).toBeInTheDocument();
    expect(screen.getByText("1h 30min")).toBeInTheDocument();
    expect(screen.getByText("4,5/5")).toBeInTheDocument();
    expect(screen.getByText("Ana Silva")).toBeInTheDocument();
    expect(screen.getByText("1 venda sem responsável atribuído")).toBeInTheDocument();
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
});
