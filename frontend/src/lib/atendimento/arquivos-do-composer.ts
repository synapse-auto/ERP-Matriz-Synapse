export const TIPOS_DE_ANEXO_ACEITOS =
  "image/jpeg,image/png,image/webp,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.txt";

export function arquivoCompativel(arquivo: File, accept: string): boolean {
  const regras = accept
    .split(",")
    .map((regra) => regra.trim().toLowerCase())
    .filter(Boolean);
  const tipo = arquivo.type.toLowerCase();
  const nome = arquivo.name.toLowerCase();
  return regras.some((regra) => {
    if (regra.endsWith("/*")) {
      return tipo.startsWith(regra.slice(0, -1));
    }
    if (regra.startsWith(".")) {
      return nome.endsWith(regra);
    }
    return tipo === regra;
  });
}

export function filtrarArquivos(
  lista: Iterable<File>,
  accept: string,
): { aceitos: File[]; rejeitados: File[] } {
  const aceitos: File[] = [];
  const rejeitados: File[] = [];
  for (const arquivo of lista) {
    if (arquivoCompativel(arquivo, accept)) {
      aceitos.push(arquivo);
    } else {
      rejeitados.push(arquivo);
    }
  }
  return { aceitos, rejeitados };
}

export function arquivosDeDataTransfer(
  data: DataTransfer | null,
  accept: string,
): { aceitos: File[]; rejeitados: File[] } {
  if (!data) {
    return { aceitos: [], rejeitados: [] };
  }
  return filtrarArquivos(Array.from(data.files), accept);
}

const EXTENSAO_POR_MIMETYPE: Record<string, string> = {
  "image/jpeg": ".jpg",
  "image/png": ".png",
  "image/webp": ".webp",
  "image/gif": ".gif",
  "image/bmp": ".bmp",
  "image/heic": ".heic",
  "image/heif": ".heif",
  "audio/ogg": ".ogg",
  "audio/mpeg": ".mp3",
  "audio/wav": ".wav",
  "audio/mp4": ".m4a",
  "application/pdf": ".pdf",
  "text/plain": ".txt",
};

function extensaoDoMimetype(mimetype: string): string {
  const normalizado = mimetype.toLowerCase();
  return EXTENSAO_POR_MIMETYPE[normalizado] ?? `.${normalizado.split("/")[1]?.split("+")[0] || "bin"}`;
}

/**
 * Arquivos do clipboard frequentemente chegam sem nome (ou com `image.png`, mesmo
 * quando o MIME real e outro). Gere nomes estaveis e legiveis antes de entregar ao
 * mesmo caminho de filtragem usado pelo arrastar e soltar.
 */
export function arquivosDaAreaDeTransferencia(data: DataTransfer | null): File[] {
  if (!data) return [];
  return Array.from(data.files).map((arquivo, indice, todos) => {
    const prefixo = arquivo.type.toLowerCase().startsWith("image/") ? "imagem-colada" : "arquivo-colado";
    const sufixo = todos.length > 1 ? `-${indice + 1}` : "";
    return new File([arquivo], `${prefixo}${sufixo}${extensaoDoMimetype(arquivo.type)}`, {
      type: arquivo.type,
      lastModified: arquivo.lastModified,
    });
  });
}
