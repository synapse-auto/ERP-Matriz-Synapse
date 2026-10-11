export interface Cor { r: number; g: number; b: number; a: number }
export interface ParDeContraste {
  id: string; grupo: string; uso: string; texto: string; fundo: string[]; minimo: number;
}
export interface LinhaDeContraste extends ParDeContraste {
  razao: number | null; ok: boolean; motivo?: string;
}
export type Tema = Record<string, string | null>;
export const CHAVES_OPCIONAIS_ALEM_DO_PADRAO: string[];
export const SIDEBAR_TEXTO_FORTE_PADRAO: string;
export const PARES: ParDeContraste[];
export function parseCor(texto: unknown): Cor | null;
export function compor(frente: Cor, fundo: Cor): Cor;
export function luminancia(cor: Cor): number;
export function razao(a: Cor, b: Cor): number;
export function distanciaDeCor(a: Cor, b: Cor): number;
export function resolverFundo(tema: Tema, camadas: string[]): Cor | null;
export function calcularContraste(tema: Tema): LinhaDeContraste[];
export function distanciaPrimarioErro(tema: Tema): { deltaE: number; razaoDeLuminancia: number } | null;
export function validarEstrutura(tema: unknown, padrao: Tema): string[];
export function validarTema(tema: unknown, padrao: Tema): string[];
export function tabelaDeContraste(tema: Tema, referencia?: Tema): string;
