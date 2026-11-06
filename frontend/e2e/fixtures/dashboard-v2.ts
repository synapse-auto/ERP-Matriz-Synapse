import type { VisaoGeralDashboard } from "../../src/lib/dashboard/types";

// Dados exclusivos de teste: não copiam os números, pessoas nem mensagens do protótipo.
const ano = new Date().getFullYear();
export const dashboardFixture: VisaoGeralDashboard = {
  periodo: { ano, meses: [1, 2, 3], inicio: null, fim: null },
  atendimentos: { noPeriodo: 84, acumulado: 126, comparativo: { valor: 12, unidade: "PERCENTUAL" } },
  novosLeads: { noPeriodo: 112, comparativo: null },
  tempoMedioAtendimento: { segundos: 4200, comparativo: { valor: -8, unidade: "PERCENTUAL" } },
  avaliacaoMedia: { media: 8.2, escalaMaxima: 10, quantidade: 16, distribuicao: { otimo: 7, bom: 6, ruim: 3 }, comparativo: null },
  resolucaoPorIa: { percentual: 50, resolvidosSemTransferencia: 14, atendimentosFinalizados: 28, comparativo: null },
  vendasFechadas: { noPeriodo: 24, acumulado: 39, comparativo: null },
  taxaConversao: { percentual: 21.4, vendas: 24, leadsRecebidos: 112, comparativo: null },
  statusAoVivo: { emIa: 11, emAtendimento: 18, leadsNovosHoje: 4, vendasHoje: 2, atendentesOnline: { online: 3, total: 5 } },
  funil: [
    { id: "fixture-inicio", nome: "Etapa inicial de teste", ordem: 1, corVisual: null, quantidade: 32, percentualDePassagem: 100 },
    { id: "fixture-analise", nome: "Etapa de análise de teste", ordem: 2, corVisual: null, quantidade: 18, percentualDePassagem: 56.25 },
    { id: "fixture-conclusao", nome: "Etapa final de teste", ordem: 3, corVisual: null, quantidade: 9, percentualDePassagem: 50 },
  ],
  leadsPerdidos: 3,
  horarioDePico: Array.from({ length: 24 }, (_, hora) => ({ hora, quantidade: hora >= 8 && hora < 18 ? (hora * 3) % 13 + 3 : 0 })),
  rankingDeVendas: { atendentes: [], semResponsavel: 0 },
  rankingDeAvaliacoes: { atendentes: [] },
  equipeDesempenho: [
    { id: "fixture-pessoa-1", nome: "Pessoa de verificação 1", atendimentos: 32, vendas: 12, nota: 8.4, avaliacoes: 7 },
    { id: "fixture-pessoa-2", nome: "Pessoa de verificação 2", atendimentos: 28, vendas: 8, nota: 8, avaliacoes: 5 },
    { id: "fixture-pessoa-3", nome: "Pessoa de verificação 3", atendimentos: 24, vendas: 4, nota: null, avaliacoes: 0 },
  ],
  seriesMensais: Array.from({ length: 12 }, (_, indice) => ({
    mes: `${ano}-${String(indice + 1).padStart(2, "0")}`,
    parcial: false, disponivel: true, atendimentos: 12 + indice * 3,
    novosLeads: 18 + indice * 4, tempoMedioSegundos: 3600 + indice * 60,
    vendasFechadas: 2 + indice, taxaConversao: 14 + indice, avaliacaoMedia: 7 + indice / 10,
    resolucaoPorIa: 40 + indice,
  })),
};

export const dashboardVazioFixture: VisaoGeralDashboard = {
  ...dashboardFixture,
  atendimentos: { noPeriodo: 0, acumulado: 0, comparativo: null },
  novosLeads: { noPeriodo: 0, comparativo: null },
  tempoMedioAtendimento: { segundos: null, comparativo: null },
  avaliacaoMedia: { media: null, escalaMaxima: 10, quantidade: 0, distribuicao: { otimo: 0, bom: 0, ruim: 0 }, comparativo: null },
  resolucaoPorIa: { percentual: null, resolvidosSemTransferencia: 0, atendimentosFinalizados: 0, comparativo: null },
  vendasFechadas: { noPeriodo: 0, acumulado: 0, comparativo: null },
  taxaConversao: { percentual: null, vendas: 0, leadsRecebidos: 0, comparativo: null },
  funil: [], leadsPerdidos: 0, horarioDePico: [], equipeDesempenho: [], seriesMensais: [],
};
