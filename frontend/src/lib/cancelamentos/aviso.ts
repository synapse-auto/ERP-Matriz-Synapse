export function ehAvisoDeCancelamentos(valor: unknown): valor is { tipo: "CANCELAMENTOS_ALTERADOS"; eventoId: string } {
  if (!valor || typeof valor !== "object") return false;
  const aviso = valor as Record<string, unknown>;
  return aviso.tipo === "CANCELAMENTOS_ALTERADOS" && typeof aviso.eventoId === "string";
}
