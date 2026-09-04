export type RamoDoVisualizador = "imagem" | "video" | "audio" | "pdf" | "documento";

export function classificarMidiaVisual(
  tipoMensagem: string | null | undefined,
  mimetype: string | null | undefined,
  nome: string | null | undefined,
): RamoDoVisualizador {
  const mime = (mimetype ?? "").toLowerCase();
  const arquivo = (nome ?? "").toLowerCase();

  if (tipoMensagem === "VIDEO" || mime.startsWith("video/") || arquivo.endsWith(".mp4") || arquivo.endsWith(".webm") || arquivo.endsWith(".mov")) {
    return "video";
  }
  if (tipoMensagem === "AUDIO" || mime.startsWith("audio/")) {
    return "audio";
  }
  if (tipoMensagem === "IMAGEM" || mime.startsWith("image/")) {
    return "imagem";
  }
  if (mime === "application/pdf" || arquivo.endsWith(".pdf")) {
    return "pdf";
  }
  return "documento";
}
