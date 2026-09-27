import { TIPOS_DE_ANEXO_ACEITOS as TIPOS_BASE } from "@/lib/atendimento/arquivos-do-composer";

// Alguns navegadores identificam M4A como audio/x-m4a. O backend continua validando
// o conteúdo real e recusando contêiner com vídeo disfarçado de áudio.
export const TIPOS_DE_ANEXO_ACEITOS = `${TIPOS_BASE},.m4a,video/mp4,video/3gpp,.mp4,.3gp`;
