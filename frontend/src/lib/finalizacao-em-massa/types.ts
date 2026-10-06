/**
 * Contratos de `/api/v1/atendimentos/finalizacoes-em-massa`. Espelham `FinalizacaoEmMassaDtos.java`; o dominio nunca
 * sai pela API e a visibilidade de cada usuario e decidida no servidor.
 */

export type StatusDaFinalizacaoEmMassa = "PENDENTE" | "EM_ANDAMENTO" | "CONCLUIDA";

export type StatusDoItemDeFinalizacao = "PENDENTE" | "FINALIZADO" | "IGNORADO" | "FALHA";

export type MotivoDoItemDeFinalizacao =
  | "JA_FINALIZADO"
  | "TRANSFERIDO"
  | "ATIVIDADE_POSTERIOR"
  | "INDISPONIVEL"
  | "ERRO_INESPERADO";

/** Datas `yyyy-MM-dd` (inclusivas) e horas `HH:mm` opcionais, sempre no fuso da instancia. */
export interface FiltroDeFinalizacao {
  atendenteIds: string[];
  de: string;
  ate: string;
  horaInicio: string | null;
  horaFim: string | null;
}

export interface AtendenteContado {
  atendenteId: string;
  nome: string;
  quantidade: number;
}

export interface PreviaDeFinalizacao {
  total: number;
  porAtendente: AtendenteContado[];
  periodoInicio: string;
  periodoFim: string;
  fuso: string;
  limite: number;
  excedeLimite: boolean;
}

export interface PeriodoDaOperacao {
  inicio: string;
  fim: string;
  fuso: string;
  de: string;
  ate: string;
  horaInicio: string | null;
  horaFim: string | null;
}

export interface OperacaoDeFinalizacao {
  id: string;
  solicitanteId: string;
  atendenteIds: string[];
  periodo: PeriodoDaOperacao;
  status: StatusDaFinalizacaoEmMassa;
  encontrados: number;
  pendentes: number;
  processados: number;
  finalizados: number;
  ignorados: number;
  falhas: number;
  percentual: number;
  criadaEm: string;
  iniciadaEm: string | null;
  concluidaEm: string | null;
  repetida: boolean;
}

export interface ItemDeFinalizacao {
  atendimentoId: string;
  atendenteId: string;
  atendenteNome: string;
  leadNome: string | null;
  status: StatusDoItemDeFinalizacao;
  motivo: MotivoDoItemDeFinalizacao | null;
  processadoEm: string | null;
}

export interface PaginaDeItens {
  itens: ItemDeFinalizacao[];
  pagina: number;
  tamanho: number;
}

export function operacaoEstaAtiva(operacao: Pick<OperacaoDeFinalizacao, "status">): boolean {
  return operacao.status !== "CONCLUIDA";
}
