export type StatusBasicoLead = "IA" | "EM_ATENDIMENTO" | "FINALIZADO";

export interface LeadFicha {
  id: string;
  nome: string;
  fotoUrl: string | null;
  telefone: string | null;
  email: string | null;
  cpf: string | null;
  empresa: string | null;
  localizacao: string | null;
  canalOrigemId: string | null;
  status: StatusBasicoLead;
  etapaAtendimentoId: string | null;
  atendenteResponsavelId: string | null;
  notas: string | null;
  resumoIa: string | null;
  numAtendimentos: number;
  numMensagens: number;
  criadoEm: string;
  dadosCustomizados: Record<string, unknown>;
}

export interface AtualizacaoLead {
  notas: string;
  dadosCustomizados: Record<string, unknown>;
}

export interface TagDoLead {
  id: string;
  nome: string;
  cor: string;
  icone: string | null;
}

export interface EtapaAtendimento {
  id: string;
  nome: string;
  ordem: number;
  corVisual: string | null;
}

export type TipoCampoCustomizado = "TEXTO" | "NUMERO" | "DATA" | "BOOLEANO" | "LISTA";

export interface CampoCustomizado {
  chave: string;
  rotulo: string;
  tipo: TipoCampoCustomizado;
  opcoes: string[];
  obrigatorio: boolean;
  filtravel: boolean;
  ordem: number;
}

export interface CanalResumo {
  id: string;
  nome: string;
  tipo: string;
  ativo: boolean;
}

export type OrigemEvento = "SISTEMA" | "AUTOMACAO" | "USUARIO";

export interface EventoTimeline {
  id: string;
  atendimentoId: string | null;
  tipo: string;
  descricao: string;
  origem: OrigemEvento;
  criadoEm: string;
}

export interface PaginaTimeline {
  eventos: EventoTimeline[];
  pagina: number;
  temMais: boolean;
}

export interface MidiaDoLead {
  mensagemId: string;
  atendimentoId: string;
  tipo: "IMAGEM" | "AUDIO" | "DOCUMENTO";
  nome: string | null;
  mimetype: string | null;
  tamanho: number;
  legenda: string | null;
  urlDownload: string;
  enviadoEm: string;
  origem: string | null;
}
