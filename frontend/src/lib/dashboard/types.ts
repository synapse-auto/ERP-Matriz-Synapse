export type UnidadeComparativo = "PERCENTUAL" | "PONTOS_PERCENTUAIS" | "PONTOS";

export interface Comparativo {
  valor: number;
  unidade: UnidadeComparativo;
}

export interface VisaoGeralDashboard {
  periodo: { ano: number; meses: number[]; inicio: string | null; fim: string | null };
  atendimentos: { noPeriodo: number; acumulado: number; comparativo: Comparativo | null };
  tempoMedioAtendimento: { segundos: number | null; comparativo: Comparativo | null };
  avaliacaoMedia: {
    media: number | null;
    escalaMaxima: number;
    quantidade: number;
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
  funil: Array<{
    id: string;
    nome: string;
    ordem: number;
    corVisual: string | null;
    quantidade: number;
    percentualDePassagem: number | null;
  }>;
  horarioDePico: Array<{ hora: number; quantidade: number }>;
  rankingDeVendas: {
    atendentes: Array<{ id: string; nome: string; vendas: number }>;
    semResponsavel: number;
  };
  rankingDeAvaliacoes: {
    atendentes: Array<{ id: string; nome: string; media: number; quantidade: number }>;
  };
}

export interface FiltroDashboard {
  ano: number;
  meses: number[];
  origemInicio: string;
  origemFim: string;
  inicio: string;
  fim: string;
}
