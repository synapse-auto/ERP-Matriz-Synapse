const FORMATO_DATA = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });
const FORMATO_DATA_HORA = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});
const FORMATO_DIA_CURTO = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit" });
const FORMATO_NUMERO = new Intl.NumberFormat("pt-BR");

export function formatarNumero(valor: number): string {
  return FORMATO_NUMERO.format(valor);
}

/** Instante ISO (com fuso) para `dd/mm/aaaa hh:mm`; vazio vira o traço informado. */
export function formatarDataHora(iso: string | null, semValor = "-"): string {
  if (!iso) return semValor;
  const data = new Date(iso);
  return Number.isNaN(data.getTime()) ? semValor : FORMATO_DATA_HORA.format(data);
}

function partesDoDia(dia: string): [number, number, number] {
  const [ano, mes, diaDoMes] = dia.split("-").map(Number);
  return [ano, mes, diaDoMes];
}

/** Dia civil `aaaa-mm-dd` (sem fuso) para `dd/mm/aaaa`, sem deslocar o dia por causa do fuso. */
export function formatarDia(dia: string | null, semValor = "-"): string {
  if (!dia) return semValor;
  const [ano, mes, diaDoMes] = partesDoDia(dia);
  if (!ano || !mes || !diaDoMes) return semValor;
  return FORMATO_DATA.format(new Date(ano, mes - 1, diaDoMes));
}

export function formatarDiaCurto(dia: string): string {
  const [ano, mes, diaDoMes] = partesDoDia(dia);
  return FORMATO_DIA_CURTO.format(new Date(ano, mes - 1, diaDoMes));
}

/** Dia da semana ISO (1 = segunda) de um dia civil `aaaa-mm-dd`. */
export function diaDaSemanaIso(dia: string): number {
  const [ano, mes, diaDoMes] = partesDoDia(dia);
  const js = new Date(ano, mes - 1, diaDoMes).getDay();
  return js === 0 ? 7 : js;
}
