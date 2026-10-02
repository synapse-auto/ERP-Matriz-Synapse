import type { RampaDeLimite, VariavelDaCampanha } from "./types";

export const RITMO_MINIMO = 1;
export const RITMO_MAXIMO = 600;
export const JANELA_PADRAO = { inicio: "09:00", fim: "18:00" } as const;
export const DIAS_UTEIS = [1, 2, 3, 4, 5] as const;
const ATALHOS_DE_LIMITE = [50, 100, 200, 500, 1000] as const;

export type ResultadoDoLimite = "ok" | "invalido" | "acima_do_teto";

/** O limite diário nunca passa do teto da instância: o backend recusa com 422, a tela recusa antes. */
export function validarLimiteDiario(valor: number | null, teto: number): ResultadoDoLimite {
  if (valor === null || !Number.isInteger(valor) || valor < 1) return "invalido";
  return valor > teto ? "acima_do_teto" : "ok";
}

export function limiteAcimaDaMeta(valor: number | null, limiteMeta: number): boolean {
  return valor !== null && limiteMeta > 0 && valor > limiteMeta;
}

export function validarRitmo(valor: number | null): boolean {
  return valor !== null && Number.isInteger(valor) && valor >= RITMO_MINIMO && valor <= RITMO_MAXIMO;
}

/** Atalhos do limite, sempre dentro do teto; o próprio teto fecha a lista. */
export function atalhosDeLimite(teto: number): number[] {
  const dentro = ATALHOS_DE_LIMITE.filter((atalho) => atalho < teto);
  return [...dentro, teto];
}

function minutosDoDia(horario: string): number {
  const [hora, minuto] = horario.split(":");
  return Number(hora) * 60 + Number(minuto);
}

/** Aceita `HH:mm` ou `HH:mm:ss`: o fim precisa ser depois do início. */
export function janelaValida(inicio: string, fim: string): boolean {
  if (!/^\d{2}:\d{2}/.test(inicio) || !/^\d{2}:\d{2}/.test(fim)) return false;
  return minutosDoDia(fim) > minutosDoDia(inicio);
}

export function validarRampa(rampa: RampaDeLimite | null, limiteDiario: number, teto: number): boolean {
  if (rampa === null) return true;
  return (
    Number.isInteger(rampa.incrementoPorDia) &&
    rampa.incrementoPorDia >= 1 &&
    Number.isInteger(rampa.teto) &&
    rampa.teto >= limiteDiario &&
    rampa.teto <= teto
  );
}

/** Só habilita "Iniciar" quando a pessoa digita exatamente o número de destinatários. */
export function confirmacaoConfere(digitado: string, destinatarios: number): boolean {
  return digitado.trim() === String(destinatarios);
}

export function percentual(parte: number, total: number): number {
  if (total <= 0) return 0;
  return Math.min(100, Math.round((parte / total) * 100));
}

export type PendenciaDeInicio = "consentimento" | "total" | "publico" | "permissao";

export interface EntradaDeInicio {
  ehAdministrador: boolean;
  consentimento: boolean;
  confirmacaoDigitada: string;
  destinatarios: number;
}

/** O que ainda impede "Iniciar", na ordem em que a tela mostra. Lista vazia = pode iniciar. */
export function pendenciasDeInicio(entrada: EntradaDeInicio): PendenciaDeInicio[] {
  const pendencias: PendenciaDeInicio[] = [];
  if (!entrada.ehAdministrador) pendencias.push("permissao");
  if (entrada.destinatarios <= 0) pendencias.push("publico");
  if (!entrada.consentimento) pendencias.push("consentimento");
  if (entrada.destinatarios > 0 && !confirmacaoConfere(entrada.confirmacaoDigitada, entrada.destinatarios)) {
    pendencias.push("total");
  }
  return pendencias;
}

export function horaCurta(horario: string): string {
  return horario.slice(0, 5);
}

export function alternarDia(dias: number[], dia: number): number[] {
  const novos = dias.includes(dia) ? dias.filter((atual) => atual !== dia) : [...dias, dia];
  return [...novos].sort((a, b) => a - b);
}

/** Ajusta as variáveis às posições `1..parametros`, preservando o que a pessoa já escolheu. */
export function variaveisParaOTemplate(atuais: VariavelDaCampanha[], parametros: number): VariavelDaCampanha[] {
  return Array.from({ length: parametros }, (_, indice) => {
    const posicao = indice + 1;
    return (
      atuais.find((variavel) => variavel.posicao === posicao) ?? {
        posicao,
        campo: "PRIMEIRO_NOME",
        reserva: "cliente",
      }
    );
  });
}

export function variaveisCompletas(variaveis: VariavelDaCampanha[]): boolean {
  return variaveis.every((variavel) => variavel.reserva.trim().length > 0);
}

/** Valor de `datetime-local` (`2026-10-05T09:00`) para o instante ISO que o backend espera. */
export function instanteDoAgendamento(valor: string): string | null {
  if (!valor) return null;
  const data = new Date(valor);
  return Number.isNaN(data.getTime()) ? null : data.toISOString();
}

// --- configuração da instância ---------------------------------------------------------------------

export interface FormularioDeConfiguracao {
  tetoDiarioDaInstancia: number | null;
  limiteDiarioPadrao: number | null;
  limiteMetaInformado: number | null;
  limiarDeFalhaPorCento: number | null;
  janelaDeEnvios: number | null;
  minimoDeAmostra: number | null;
}

export type CampoDeConfiguracao = keyof FormularioDeConfiguracao;

function inteiroEntre(valor: number | null, minimo: number, maximo = Number.MAX_SAFE_INTEGER): boolean {
  return valor !== null && Number.isInteger(valor) && valor >= minimo && valor <= maximo;
}

/** Faixas iguais às do backend (`ConfiguracaoRequisicao`); o limite padrão não passa do teto. */
export function errosDaConfiguracao(f: FormularioDeConfiguracao): CampoDeConfiguracao[] {
  const erros: CampoDeConfiguracao[] = [];
  if (!inteiroEntre(f.tetoDiarioDaInstancia, 1)) erros.push("tetoDiarioDaInstancia");
  const teto = f.tetoDiarioDaInstancia ?? Number.MAX_SAFE_INTEGER;
  if (!inteiroEntre(f.limiteDiarioPadrao, 1, teto)) erros.push("limiteDiarioPadrao");
  if (!inteiroEntre(f.limiteMetaInformado, 0)) erros.push("limiteMetaInformado");
  if (!inteiroEntre(f.limiarDeFalhaPorCento, 1, 100)) erros.push("limiarDeFalhaPorCento");
  if (!inteiroEntre(f.janelaDeEnvios, 10)) erros.push("janelaDeEnvios");
  if (!inteiroEntre(f.minimoDeAmostra, 5)) erros.push("minimoDeAmostra");
  return erros;
}
