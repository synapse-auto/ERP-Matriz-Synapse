/**
 * Espelha `PapelUsuario.recebeAtendimento()`: quem entra na fila da IA e
 * pode ser destino de transferência. Separada de "enxerga a base inteira".
 */
export function recebeAtendimento(papel: string | null | undefined): boolean {
  return papel === "ATENDENTE" || papel === "SUBGESTOR";
}

/**
 * Define quem aparece na grade de Equipe. Gestores fazem parte da equipe visível,
 * embora não recebam atendimentos da fila da IA; administradores ficam fora da grade.
 */
export function visivelNaEquipe(papel: string | null | undefined): boolean {
  return papel != null && papel !== "ADMINISTRADOR";
}
