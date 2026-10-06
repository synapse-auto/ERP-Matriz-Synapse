import type { CitacaoMensagem } from "@/lib/atendimento/types";

export type StatusPresencaChat = "ONLINE" | "AUSENTE" | "OFFLINE";
export interface ChatContato {
  id: string;
  nome: string;
  fotoUrl?: string | null;
  presenca: StatusPresencaChat;
}
export interface ChatParticipante {
  id: string;
  nome: string;
}
export interface ChatConversa {
  id: string; tipo: "DIRETA" | "GRUPO"; participantes: string;
  ultimaMensagem: string | null; ultimaMensagemEm: string | null; naoLidas: number;
  fotoUrl?: string | null;
  /** O usuário autenticado criou o grupo e pode trocar ou remover a foto (decidido no backend). */
  podeAlterarFoto?: boolean;
}
export interface FotoDoGrupo {
  /** URL autenticada e versionada; nula quando o grupo ficou sem foto. */
  fotoUrl: string | null;
}
export interface ChatMensagem {
  id: string; conversaId: string; remetenteId: string; remetenteNome: string;
  tipo?: string; conteudo: string | null; midiaUrl?: string | null; midiaMetadados?: unknown; enviadoEm: string;
  reacoes?: { emoji: string; quantidade: number; reagi: boolean }[];
  removida?: boolean;
  editadoEm?: string | null;
  citacao?: (CitacaoMensagem & { origemRemovida?: boolean }) | null;
}
export interface EventoSistemaChat {
  evento: string;
  nome?: string;
  nomeAnterior?: string;
  alvoId?: string;
  alvoNome?: string;
}
export interface PaginaChatMensagens { mensagens: ChatMensagem[]; proximoCursor: string | null }

export type TipoMidiaChatInterno = "IMAGEM" | "AUDIO" | "DOCUMENTO" | "VIDEO";

export interface MidiaDoGrupo {
  mensagemId: string;
  tipo: TipoMidiaChatInterno;
  nome: string | null;
  mimetype: string | null;
  tamanho: number;
  legenda: string | null;
  enviadoEm: string;
}

/** Espelha PreviaDoEncaminhamento.java: o que a tela mostra antes de confirmar o encaminhamento ao cliente. */
export type EfeitoDoEncaminhamento =
  | "ASSUME_O_LEAD"
  | "MANTEM_RESPONSAVEL_E_CONVIDA"
  | "MANTEM_RESPONSAVEL"
  | "VOCE_E_RESPONSAVEL";

export type MotivoDeBloqueioDoEncaminhamento =
  | "ATENDIMENTO_FINALIZADO"
  | "FORA_DA_JANELA"
  | "TIPO_NAO_SUPORTADO"
  | "ARQUIVO_ACIMA_DO_LIMITE"
  | "ARQUIVO_SEM_TAMANHO";

export interface PreviaDoEncaminhamentoAoCliente {
  atendimentoId: string;
  clienteNome: string;
  /** Sempre mascarado pelo backend; o número inteiro nunca chega a esta tela. */
  telefoneMascarado: string;
  statusAtendimento: string;
  responsavelNome: string | null;
  efeito: EfeitoDoEncaminhamento;
  tipo: string;
  texto: string | null;
  legenda: string | null;
  nomeArquivo: string | null;
  mimetype: string | null;
  tamanhoBytes: number | null;
  podeEnviar: boolean;
  bloqueio: MotivoDeBloqueioDoEncaminhamento | null;
}

export type StatusDaEntregaAoCliente = "PENDENTE" | "ENVIADO" | "ENTREGUE" | "LIDO" | "FALHOU";

/** Espelha EncaminhamentoDoChatInternoController.EncaminhamentoResposta. */
export interface EncaminhamentoAoCliente {
  id: string;
  atendimentoId: string;
  mensagemInternaId: string;
  mensagemExternaId: string;
  tipo: string;
  statusEntrega: StatusDaEntregaAoCliente;
  erroEntrega: string | null;
  transferiuOLead: boolean;
  conviteCriado: boolean;
  reutilizado: boolean;
}

/** Espelha DestinoDoEncaminhamento.java: um atendimento aberto que o usuário pode escolher. Telefone sempre mascarado. */
export interface DestinoDoEncaminhamento {
  atendimentoId: string;
  clienteNome: string;
  telefoneMascarado: string;
  statusAtendimento: string;
  responsavelNome: string | null;
}
