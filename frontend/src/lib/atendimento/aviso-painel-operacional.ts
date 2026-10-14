export interface AvisoPainelOperacional {
  tipo: "PAINEL_OPERACIONAL_ALTERADO";
  eventoId: string;
  dados: Record<string, never>;
}

export function ehAvisoPainelOperacional(valor: unknown): valor is AvisoPainelOperacional {
  if (!valor || typeof valor !== "object") return false;
  const candidato = valor as Partial<AvisoPainelOperacional>;
  return candidato.tipo === "PAINEL_OPERACIONAL_ALTERADO" && typeof candidato.eventoId === "string";
}
