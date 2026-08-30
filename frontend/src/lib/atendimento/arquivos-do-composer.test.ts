import { describe, expect, it } from "vitest";

import {
  arquivoCompativel,
  arquivosDeDataTransfer,
  filtrarArquivos,
} from "./arquivos-do-composer";

const ACCEPT = "image/jpeg,image/png,image/webp,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.txt";

function arquivo(nome: string, tipo: string): File {
  return new File(["x"], nome, { type: tipo });
}

describe("arquivos-do-composer", () => {
  it("aceita imagem, pdf e áudio; recusa executável", () => {
    expect(arquivoCompativel(arquivo("foto.png", "image/png"), ACCEPT)).toBe(true);
    expect(arquivoCompativel(arquivo("voz.mp3", "audio/mpeg"), ACCEPT)).toBe(true);
    expect(arquivoCompativel(arquivo("orcamento.pdf", "application/pdf"), ACCEPT)).toBe(true);
    expect(arquivoCompativel(arquivo("setup.exe", "application/x-msdownload"), ACCEPT)).toBe(false);
  });

  it("separa aceitos e rejeitados sem misturar", () => {
    const { aceitos, rejeitados } = filtrarArquivos(
      [arquivo("a.png", "image/png"), arquivo("b.exe", "application/x-msdownload")],
      ACCEPT,
    );
    expect(aceitos.map((item) => item.name)).toEqual(["a.png"]);
    expect(rejeitados.map((item) => item.name)).toEqual(["b.exe"]);
  });

  it("lê a lista do DataTransfer do explorador", () => {
    const data = {
      files: [arquivo("a.png", "image/png"), arquivo("b.txt", "text/plain")],
    } as unknown as DataTransfer;
    const { aceitos } = arquivosDeDataTransfer(data, ACCEPT);
    expect(aceitos.map((item) => item.name)).toEqual(["a.png", "b.txt"]);
  });
});
