/** Contratos de `/api/v1/campanhas` (E220). Espelham `CampanhaDtos.java`; o domínio nunca sai pela API. */

export type StatusDaCampanha =
  | "RASCUNHO"
  | "AGENDADA"
  | "EM_ANDAMENTO"
  | "PAUSADA"
  | "CONCLUIDA"
  | "CANCELADA"
  | "PAUSADA_AUTOMATICAMENTE";

export type StatusDoDestinatario =
  | "PENDENTE"
  | "ENFILEIRADO"
  | "ENVIADO"
  | "ENTREGUE"
  | "LIDO"
  | "FALHA"
  | "IGNORADO";

export type MotivoDoDestinatario =
  | "TELEFONE_INVALIDO"
  | "SEM_NOME_UTILIZAVEL"
  | "OPT_OUT"
  | "JA_RECEBEU"
  | "ATENDIMENTO_ATIVO"
  | "RECEBEU_PROATIVA_RECENTE"
  | "COOLDOWN"
  | "TETO_DIARIO_POR_LEAD"
  | "TIPO_PROATIVO_DESLIGADO"
  | "AUTOMACAO_PROATIVA_DESLIGADA"
  | "OCORRENCIA_JA_REGISTRADA"
  | "LEAD_INDISPONIVEL"
  | "ATENDIMENTO_ABERTO_NO_ENVIO"
  | "OPT_OUT_NO_ENVIO"
  | "ENVIO_NAO_CONFIRMADO"
  | "FALHA_NO_PROVEDOR"
  | "ENFILEIRADO_SEM_CONFIRMACAO";

export type CampoDoLead = "PRIMEIRO_NOME" | "NOME_COMPLETO" | "EMPRESA" | "LOCALIZACAO";

export interface VariavelDaCampanha {
  posicao: number;
  campo: CampoDoLead;
  reserva: string;
}

export interface FiltroDePublico {
  tagIds: string[] | null;
  etapaId: string | null;
  atendenteId: string | null;
  cadastroDesde: string | null;
  cadastroAte: string | null;
  nuncaConversou: boolean | null;
  busca: string | null;
}

/** Dias ISO: 1 = segunda ... 7 = domingo. Horários no formato `HH:mm:ss` do backend. */
export interface JanelaDeEnvio {
  inicio: string;
  fim: string;
  dias: number[];
}

export interface RampaDeLimite {
  incrementoPorDia: number;
  teto: number;
}

export interface ContadoresDaCampanha {
  total: number;
  pendentes: number;
  enfileirados: number;
  enviados: number;
  entregues: number;
  lidos: number;
  respondidos: number;
  falhas: number;
  ignorados: number;
  conferencia: number;
}

export interface TemplateDaCampanha {
  id: string;
  nome: string;
  idioma: string;
  categoria: string;
  corpo: string;
  parametros: number;
}

export interface Campanha {
  id: string;
  nome: string;
  status: StatusDaCampanha;
  desligada: boolean;
  template: TemplateDaCampanha;
  variaveis: VariavelDaCampanha[];
  filtro: FiltroDePublico;
  limiteDiario: number;
  janela: JanelaDeEnvio;
  ritmoPorMinuto: number;
  rampa: RampaDeLimite | null;
  agendadaPara: string | null;
  contadores: ContadoresDaCampanha;
  motivoDePausa: string | null;
  pausadaEm: string | null;
  iniciadaEm: string | null;
  concluidaEm: string | null;
  criadaPor: string;
  criadaEm: string;
}

export interface Indicadores {
  enviadas: number;
  entregues: number;
  lidas: number;
  respondidas: number;
  falhas: number;
}

export interface ListaDeCampanhas {
  itens: Campanha[];
  pagina: number;
  tamanho: number;
  total: number;
  indicadores: Indicadores;
  enfileiradasHoje: number;
  tetoDiario: number;
  limiteMetaInformado: number;
}

export interface DiaEnfileirado {
  dia: string;
  enfileiradas: number;
}

export interface DetalheDaCampanha {
  campanha: Campanha;
  porDia: DiaEnfileirado[];
  limiteEfetivoHoje: number;
  tetoDaInstancia: number;
  limiteMetaInformado: number;
  enfileiradasHojeNaInstancia: number;
}

export interface TemplateParaCampanha {
  id: string;
  nome: string;
  idioma: string;
  categoria: string;
  status: string;
  corpo: string;
  parametros: number;
  elegivel: boolean;
  restricoes: string[];
}

export interface PreviaDoPublico {
  total: number;
  elegiveis: number;
  excluidos: number;
  excluidosPorMotivo: Partial<Record<MotivoDoDestinatario, number>>;
}

export interface ContatoExcluido {
  leadId: string;
  nome: string | null;
  telefone: string | null;
  motivo: MotivoDoDestinatario;
}

export interface DiaProjetado {
  dia: string;
  mensagens: number;
  limiteDoDia: number;
}

export interface ProjecaoDeEnvio {
  destinatarios: number;
  dias: DiaProjetado[];
  terminoEstimado: string | null;
  completa: boolean;
  tetoDaInstancia: number;
  limiteMetaInformado: number;
  limiteEfetivoNoPrimeiroDia: number;
}

export interface Destinatario {
  id: string;
  leadId: string;
  nome: string | null;
  telefone: string | null;
  status: StatusDoDestinatario;
  motivo: MotivoDoDestinatario | null;
  codigoDeErro: number | null;
  enviadoEm: string | null;
  entregueEm: string | null;
  lidoEm: string | null;
  respondeuEm: string | null;
  conferenciaEm: string | null;
}

export interface PaginaDe<T> {
  itens: T[];
  pagina: number;
  tamanho: number;
  total: number;
}

export interface ConfiguracaoDeCampanhas {
  envioHabilitado: boolean;
  tetoDiarioDaInstancia: number;
  limiteDiarioPadrao: number;
  limiteMetaInformado: number;
  limiarDeFalhaPorCento: number;
  janelaDeEnvios: number;
  minimoDeAmostra: number;
  conferenciaAposMinutos: number;
  respondeuJanelaDias: number;
}

export type AtualizacaoDeConfiguracao = Partial<
  Pick<
    ConfiguracaoDeCampanhas,
    | "envioHabilitado"
    | "tetoDiarioDaInstancia"
    | "limiteDiarioPadrao"
    | "limiteMetaInformado"
    | "limiarDeFalhaPorCento"
    | "janelaDeEnvios"
    | "minimoDeAmostra"
  >
>;

/** Corpo de POST/PUT de campanha. */
export interface PedidoDeCampanha {
  nome: string;
  templateNome: string;
  templateIdioma: string;
  variaveis: VariavelDaCampanha[];
  filtro: Partial<FiltroDePublico>;
  limiteDiario: number | null;
  janela: JanelaDeEnvio | null;
  ritmoPorMinuto: number | null;
  rampa: RampaDeLimite | null;
  agendadaPara: string | null;
}

export interface PedidoDeProjecao {
  filtro: Partial<FiltroDePublico>;
  limiteDiario: number;
  janela: JanelaDeEnvio;
  ritmoPorMinuto: number;
  rampa: RampaDeLimite | null;
  primeiroDia: string | null;
}

export interface ResultadoDoTeste {
  leadId: string;
  mensagemId: string;
  enviadoEm: string;
  corpoRenderizado: string;
}
