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
