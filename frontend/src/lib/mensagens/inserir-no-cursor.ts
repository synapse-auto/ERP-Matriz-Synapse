/** Insere texto na seleção do campo; sem campo, acrescenta no fim. */
export function inserirNoCursor(
  atual: string,
  insercao: string,
  campo: Pick<HTMLTextAreaElement, "selectionStart" | "selectionEnd"> | null,
): { texto: string; cursor: number } {
  const inicio = campo?.selectionStart ?? atual.length;
  const fim = campo?.selectionEnd ?? atual.length;
  return {
    texto: `${atual.slice(0, inicio)}${insercao}${atual.slice(fim)}`,
    cursor: inicio + insercao.length,
  };
}

export function posicionarCursor(campo: HTMLTextAreaElement | null, cursor: number) {
  if (!campo) return;
  campo.focus();
  campo.setSelectionRange(cursor, cursor);
}
