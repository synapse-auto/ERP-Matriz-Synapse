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

export type StatusMensagemProgramada = "AGENDADA" | "ENVIADA" | "CANCELADA";
export interface MensagemProgramada {
  id: string; leadId: string; leadNome: string; atendenteId: string; atendenteNome: string;
  conteudo: string; dataEnvio: string; status: StatusMensagemProgramada;
}
export interface PaginaMensagensProgramadas { mensagens: MensagemProgramada[]; pagina: number; temMais: boolean; }
export interface DadosMensagemProgramada { leadId: string; conteudo: string; dataEnvio: string; }

export interface MensagemRapida { id:string; atendenteId:string; atendenteNome:string; palavraChave:string; conteudo:string; tipoMidia:string|null; }
export interface DadosMensagemRapida { palavraChave:string; conteudo:string; }
