export type TipoConfiguracaoAutomacao = "INT" | "DECIMAL" | "BOOLEAN" | "TEXT";

export interface ParametroAutomacao {
  chave: string;
  valor: string;
  unidade: string | null;
  tipo: TipoConfiguracaoAutomacao;
  valorMin: number | null;
  valorMax: number | null;
  descricao: string | null;
  atualizadoEm: string;
}

/** Espelha StatusAutomacaoTelemetriaController.StatusAutomacaoTelemetriaResposta — GET /api/v1/automacao/telemetria. */
export interface StatusAutomacaoTelemetria {
  mensagensEnviadas: number;
  clientesTransferidos: number;
  conexaoAutomacaoAtiva: boolean;
  crmOnline: boolean;
  atualizadoEm: string;
}

export interface RegraFollowUp { id: string; nome: string; tempoMinutos: number; texto: string; ativo: boolean }
export interface RegraFidelizacao { id: string; diasSemContato: number; mensagem: string; ativo: boolean }
export type FollowUpPayload = Omit<RegraFollowUp, "id" | "nome">;
export type FidelizacaoPayload = Omit<RegraFidelizacao, "id">;
