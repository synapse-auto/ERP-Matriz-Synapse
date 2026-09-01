/**
 * Espelha `PapelUsuario.recebeAtendimento()`: quem entra na fila da IA e
 * pode ser destino de transferência. Separada de "enxerga a base inteira".
 */
export function recebeAtendimento(papel: string | null | undefined): boolean {
  return papel === "ATENDENTE" || papel === "SUBGESTOR";
}
