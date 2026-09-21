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
   * "AGORA" do mockup (aguardando 1ª resposta, esquecidos, atendentes online) não têm critério
   * definido ainda — por isso não têm campo aqui, em vez de vir zerado por engano.
   */
  statusAoVivo: { emIa: number; emAtendimento: number; leadsNovosHoje: number; vendasHoje: number };
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
}

export interface FiltroDashboard {
  ano: number;
  meses: number[];
  origemInicio: string;
  origemFim: string;
  inicio: string;
  fim: string;
}
