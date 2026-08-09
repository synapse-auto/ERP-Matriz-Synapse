export type StatusBasicoLead = "IA" | "EM_ATENDIMENTO" | "FINALIZADO";

/** Espelha `TipoDeCampo`/`TipoCampoCustomizado` do backend — o widget certo por campo. */
export type TipoDeCampoFiltravel = "TEXTO" | "NUMERO" | "DATA" | "STATUS" | "REFERENCIA" | "BOOLEANO" | "LISTA";

export type OperadorDeFiltro =
  | "IGUAL"
  | "DIFERENTE"
  | "CONTEM"
  | "COMECA_COM"
  | "MAIOR_QUE"
  | "MENOR_QUE"
  | "ENTRE"
  | "EM"
  | "PREENCHIDO"
  | "VAZIO";

/** GET /api/v1/leads/filtrar/campos — a barra de filtros se monta inteira a partir disto. */
export interface CampoFiltravel {
  apelido: string;
  rotulo: string;
  tipo: TipoDeCampoFiltravel;
  operadores: OperadorDeFiltro[];
  opcoes: string[];
}

export interface TagDaLista {
  tagId: string;
  nome: string;
  cor: string;
  icone: string | null;
}

export interface LeadDaAgenda {
  id: string;
  nome: string;
  telefone: string | null;
  empresa: string | null;
  localizacao: string | null;
  status: StatusBasicoLead;
  etapaAtendimentoId: string | null;
  atendenteResponsavelId: string | null;
  numAtendimentos: number;
  numMensagens: number;
  criadoEm: string;
  ultimaInteracaoEm: string | null;
  tags: TagDaLista[];
}

export interface PaginaDeLeads {
  leads: LeadDaAgenda[];
  pagina: number;
  temMais: boolean;
}

/** Um nó folha da árvore de critérios, como o backend espera. */
export interface CriterioSimplesRequisicao {
  tipo: "SIMPLES";
  campo: string;
  operador: OperadorDeFiltro;
  valor?: string;
  valores?: string[];
}

export interface CriterioCompostoRequisicao {
  tipo: "COMPOSTO";
  conector: "AND" | "OR";
  criterios: CriterioRequisicao[];
}

export type CriterioRequisicao = CriterioSimplesRequisicao | CriterioCompostoRequisicao;

/**
 * Um filtro ativo na tela: sempre um {@link CriterioSimplesRequisicao} — a agenda só compõe
 * filtros com E (AND) entre chips, nunca árvore aninhada. `rotuloValor` é o texto já resolvido
 * para o chip (nome da etapa, do atendente etc.), calculado no momento em que o filtro é criado.
 */
export interface FiltroAtivo {
  id: string;
  campo: CampoFiltravel;
  operador: OperadorDeFiltro;
  valor?: string;
  valores?: string[];
  rotuloValor: string;
}
