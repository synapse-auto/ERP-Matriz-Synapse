export type UnidadeComparativo = "PERCENTUAL" | "PONTOS_PERCENTUAIS" | "PONTOS";

export interface Comparativo {
  valor: number;
  unidade: UnidadeComparativo;
}

export interface VisaoGeralDashboard {
  periodo: { ano: number; meses: number[]; inicio: string | null; fim: string | null };
  atendimentos: { noPeriodo: number; acumulado: number; comparativo: Comparativo | null };
  novosLeads: { noPeriodo: number; comparativo: Comparativo | null };
  tempoMedioAtendimento: { segundos: number | null; comparativo: Comparativo | null };
  avaliacaoMedia: {
    media: number | null;
    escalaMaxima: number;
    quantidade: number;
    distribuicao: { otimo: number; bom: number; ruim: number };
    comparativo: Comparativo | null;
  };
  resolucaoPorIa: {
    percentual: number | null;
    resolvidosSemTransferencia: number;
    atendimentosFinalizados: number;
    comparativo: Comparativo | null;
  };
  vendasFechadas: { noPeriodo: number; acumulado: number; comparativo: Comparativo | null };
  taxaConversao: {
    percentual: number | null;
    vendas: number;
    leadsRecebidos: number;
    comparativo: Comparativo | null;
  };
  /**
   * Contadores ao vivo (sem recorte de período): emIa/emAtendimento refletem o instante da
   * consulta; leadsNovosHoje/vendasHoje, o dia corrente no fuso do tenant. Os demais itens do
   * "AGORA" do mockup (aguardando 1ª resposta e esquecidos) ainda não têm critério definido —
   * por isso não têm campo aqui, em vez de vir zerados por engano.
   */
  statusAoVivo: {
    emIa: number;
    emAtendimento: number;
    leadsNovosHoje: number;
    vendasHoje: number;
    atendentesOnline: { online: number; total: number };
  };
  funil: Array<{
    id: string;
    nome: string;
    ordem: number;
    corVisual: string | null;
    quantidade: number;
    percentualDePassagem: number | null;
  }>;
  leadsPerdidos: number;
  horarioDePico: Array<{ hora: number; quantidade: number }>;
  rankingDeVendas: {
    atendentes: Array<{ id: string; nome: string; vendas: number }>;
    semResponsavel: number;
  };
  rankingDeAvaliacoes: {
    atendentes: Array<{ id: string; nome: string; media: number; quantidade: number }>;
  };
  /** Tabela "Equipe · desempenho"; sem colunas de conversão e 1ª resposta (ver relatório da E197). */
  equipeDesempenho: Array<{
    id: string;
    nome: string;
    atendimentos: number;
    vendas: number;
    nota: number | null;
    avaliacoes: number;
  }>;
  /** Null representa ausencia de amostra ou mes futuro; zero representa contagem real. */
  seriesMensais?: Array<{
    mes: string;
    parcial: boolean;
    disponivel: boolean;
    atendimentos: number | null;
    novosLeads: number | null;
    tempoMedioSegundos: number | null;
    vendasFechadas: number | null;
    taxaConversao: number | null;
    avaliacaoMedia: number | null;
    resolucaoPorIa: number | null;
  }>;
}

export interface FiltroDashboard {
  ano: number;
  meses: number[];
  origemInicio: string;
  origemFim: string;
  inicio: string;
  fim: string;
}
