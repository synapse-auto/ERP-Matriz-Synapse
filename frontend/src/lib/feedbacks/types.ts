export type TipoFeedback = "SUGESTAO" | "ERRO";

export type AreaFeedback =
  | "GERAL"
  | "ATENDIMENTOS"
  | "AGENDA"
  | "DASHBOARD"
  | "EQUIPE"
  | "AUTOMACAO"
  | "MENSAGENS_PROGRAMADAS"
  | "LEMBRETES"
  | "TAGS"
  | "CONFIGURACOES";

export interface FeedbackCriado {
  id: string;
  tipo: TipoFeedback;
  areaChave: AreaFeedback;
  descricao: string;
  criadoEm: string;
}

export interface FeedbackAdministrativo extends FeedbackCriado {
  autorId: string;
  autorNome: string;
  autorPapel: string;
  autorFotoUrl: string | null;
}

export interface PaginaDeFeedbacks {
  itens: FeedbackAdministrativo[];
  proximoCriadoEm: string | null;
  proximoId: string | null;
}

export interface CursorDeFeedbacks {
  antesDe: string;
  antesDoId: string;
}
