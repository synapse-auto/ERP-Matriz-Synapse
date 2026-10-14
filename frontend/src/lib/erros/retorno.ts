/** Não manda uma entrada direta para about:blank ou para histórico externo desconhecido. */
export function temPaginaAnteriorDoCrm(historico: number, referrer: string, origem: string): boolean {
  if (historico <= 1 || !referrer) return false;
  try { return new URL(referrer).origin === origem; }
  catch { return false; }
}
