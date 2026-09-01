export const FORMATOS_ACEITOS_PELA_META = [
  "audio/ogg;codecs=opus",
  "audio/ogg",
  "audio/mp4;codecs=mp4a.40.2",
  "audio/mp4",
] as const;

export function tipoPrincipalDoMime(mime: string): string {
  return mime.split(";")[0]?.trim().toLowerCase() ?? "";
}

export function arquivoDaGravacao(blob: Blob, mimeDeclarado: string, agora = Date.now()): File {
  const tipo = tipoPrincipalDoMime(blob.type || mimeDeclarado) || "audio/mp4";
  const ogg = tipo === "audio/ogg" || tipo === "audio/opus";
  const tipoArquivo = tipo === "audio/opus" ? "audio/ogg" : tipo;
  const extensao = ogg ? "ogg" : tipoArquivo === "audio/aac" ? "aac" : "m4a";
  return new File([blob], `gravacao-${agora}.${extensao}`, { type: tipoArquivo });
}
