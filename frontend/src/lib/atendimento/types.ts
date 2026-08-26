/** Espelha VisaoAtendimento.java. */
export type VisaoAtendimento = "ATIVOS" | "PENDENTES" | "POTENCIAIS" | "TODOS";

/** Espelha StatusAtendimento.java. */
export type StatusAtendimento = "EM_IA" | "EM_ATENDIMENTO" | "FINALIZADO";

/** Espelha StatusEntrega.java — o ciclo PENDENTE → ENVIADO → ENTREGUE → LIDO, ou PENDENTE → FALHOU. */
export type StatusEntrega =
  "PENDENTE" | "ENVIADO" | "ENTREGUE" | "LIDO" | "FALHOU";

/** Espelha TipoMensagem.java. */
export type TipoMensagem = "TEXTO" | "AUDIO" | "IMAGEM" | "DOCUMENTO" | "BOTOES" | "LISTA";

export interface ConfiguracaoComposer {
  tamanhoMaximoAudioBytes: number;
  duracaoMaximaAudioSegundos: number;
  tempoNotificacaoSegundos: number;
}

/** Espelha RemetenteTipo.java. */
export type RemetenteTipo = "LEAD" | "ATENDENTE" | "SISTEMA" | "IA";

/** Espelha PainelDeAtendimentosController.contagem() — GET /api/v1/atendimentos/contagem. */
export type ContagemPorVisao = Record<VisaoAtendimento, number>;

/** Espelha PainelDeAtendimentosController.CartaoAtendimento — GET /api/v1/atendimentos?visao=. */
export interface CartaoAtendimento {
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  leadFotoUrl: string | null;
  leadEmpresa: string | null;
  canalTipo: string | null;
  etapaId: string | null;
  etapaNome: string | null;
  etapaCor: string | null;
  status: StatusAtendimento;
  atendenteId: string | null;
  atendenteNome: string | null;
  /** Atendimento aberto do lead; nulo quando todo o histórico está finalizado. */
  atendimentoAtivoId?: string | null;
  ultimaMensagemPreview: string | null;
  ultimaMensagemRemetenteTipo: RemetenteTipo | null;
  ultimaMensagemEm: string | null;
  /** Base para a estimativa client-side da janela de 24h — ver janela-24h.ts. */
  ultimaMensagemDoLeadEm: string | null;
  naoLidas: number;
}

/** Espelha AtendimentoMensagensController.MensagemResposta — GET /api/v1/atendimentos/{id}/mensagens. */
export interface MensagemResposta {
  id: string;
  /** Atendimento de origem; permite desenhar marcos ao atravessar o histórico do lead. */
  atendimentoId?: string;
  atendimentoIniciadoEm?: string | null;
  atendimentoFinalizadoEm?: string | null;
  atendimentoResponsavelNome?: string | null;
  remetenteTipo: RemetenteTipo;
  remetenteId: string | null;
  remetenteNome: string | null;
  tipo: TipoMensagem;
  conteudo: string | null;
  midiaUrl: string | null;
  midiaMetadados: string | null;
  opcoes: string | null;
  statusEntrega: StatusEntrega;
  enviadoEm: string;
}

export interface PaginaMensagens {
  mensagens: MensagemResposta[];
  proximoCursor: string | null;
}

/** Espelha AtendimentoAcoesController.EnvioResposta — POST /api/v1/atendimentos/mensagens. */
export interface EnvioResposta {
  atendimentoId: string;
  mensagemId: string;
  statusEntrega: StatusEntrega;
  enviadoEm: string;
  transferiuOLead: boolean;
}

/** Espelha AtendimentoAcoesController.AtendimentoResumo — resposta de /transferir e /finalizar. */
export interface AtendimentoResumo {
  id: string;
  status: StatusAtendimento;
  atendenteId: string | null;
}

export interface FinalizacaoEmLotePrevia {
  quantidade: number;
}

export interface FinalizacaoEmLoteResposta {
  solicitados: number;
  finalizados: number;
  recusados: number;
}

export interface ParticipanteAtendimento {
  usuarioId: string;
  nome: string;
  entrouEm: string;
}

export type StatusPedidoEntrada = "PENDENTE" | "APROVADO" | "RECUSADO" | "EXPIRADO";
export interface PedidoEntradaAtendimento {
  id: string;
  atendimentoId: string;
  solicitanteId: string;
  solicitanteNome: string;
  status: StatusPedidoEntrada;
  solicitadoEm: string;
}

/** Espelha UsuarioController.UsuarioResposta — GET /api/v1/usuarios. */
export interface UsuarioResposta {
  id: string;
  nome: string;
  email: string;
  papel: "ATENDENTE" | "SUBGESTOR" | "GESTOR" | "ADMINISTRADOR";
  ativo: boolean;
  statusPresenca: "ONLINE" | "AUSENTE" | "OFFLINE";
}

/** Espelha TagController.TagResposta — GET /api/v1/tags. */
export interface TagResposta {
  id: string;
  nome: string;
  cor: string;
  icone: string | null;
}

// --- Tempo real (STOMP) ----------------------------------------------------
// Envelope e payloads espelham RelayDeTempoRealListener.java / RedisSubscriberDeAtendimento.java.

export interface MensagemTempoReal {
  atendimentoId: string;
  leadId: string;
  mensagemId: string;
  remetenteTipo: RemetenteTipo;
  remetenteId: string | null;
  tipo: TipoMensagem;
  conteudo: string | null;
  midiaUrl: string | null;
  midiaMetadados: string | null;
  opcoes: string | null;
  statusEntrega: StatusEntrega;
  enviadoEm: string;
}

export interface StatusTempoReal {
  atendimentoId: string;
  leadId: string;
  mensagemId: string;
  statusEntrega: StatusEntrega;
  ocorridoEm: string;
}

export interface TransferenciaTempoReal {
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  deAtendenteId: string | null;
  paraAtendenteId: string | null;
  quemTransferiu: string | null;
  atorTipo: "USUARIO" | "AUTOMACAO" | "SISTEMA";
  ocorridoEm: string;
}

export interface TransferenciaRecebidaTempoReal {
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  quemTransferiu: string | null;
  atorTipo: "USUARIO" | "AUTOMACAO" | "SISTEMA";
  ocorridoEm: string;
}

export interface AtendimentoDevolvidoParaIaTempoReal {
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  ocorridoEm: string;
}

export interface FinalizacaoTempoReal {
  atendimentoId: string;
  leadId: string;
  quemFinalizou: string;
  ocorridoEm: string;
}

export type EventoTempoReal =
  | { tipo: "MENSAGEM"; dados: MensagemTempoReal }
  | { tipo: "STATUS"; dados: StatusTempoReal }
  | { tipo: "TRANSFERENCIA"; dados: TransferenciaTempoReal }
  | { tipo: "FINALIZACAO"; dados: FinalizacaoTempoReal };

export type NotificacaoTempoReal = {
  tipo: "TRANSFERENCIA_RECEBIDA";
  dados: TransferenciaRecebidaTempoReal;
} | {
  tipo: "ATENDIMENTO_DEVOLVIDO_PARA_IA";
  dados: AtendimentoDevolvidoParaIaTempoReal;
} | {
  tipo: "CHAT_INTERNO_MENSAGEM";
  dados: ChatInternoMensagemTempoReal;
};

export interface ChatInternoMensagemTempoReal {
  conversaId: string;
  mensagemId: string;
  remetenteId: string;
  conteudo: string;
  enviadoEm: string;
}

/** Payload de /user/queue/revogacoes. */
export interface RevogacaoTempoReal {
  atendimentoId: string;
}
