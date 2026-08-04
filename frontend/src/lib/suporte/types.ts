export type StatusLembrete = "PENDENTE" | "CONCLUIDO";

export interface Lembrete {
  id: string;
  leadId: string;
  leadNome: string | null;
  atendenteId: string;
  atendenteNome: string;
  texto: string;
  dataHora: string;
  origemAutomatica: boolean;
  status: StatusLembrete;
}

export interface PaginaLembretes {
  lembretes: Lembrete[];
  pagina: number;
  temMais: boolean;
}

export interface DadosLembrete {
  leadId: string;
  texto: string;
  dataHora: string;
}
