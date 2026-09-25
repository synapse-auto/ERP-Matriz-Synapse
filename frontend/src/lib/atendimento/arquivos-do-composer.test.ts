import { describe, expect, it } from "vitest";

import {
  arquivoCompativel,
  arquivosDaAreaDeTransferencia,
  arquivosDeDataTransfer,
  filtrarArquivos,
  TIPOS_DE_ANEXO_ACEITOS,
  TIPOS_DE_ANEXO_ACEITOS_NO_ATENDIMENTO,
} from "./arquivos-do-composer";

const ACCEPT = TIPOS_DE_ANEXO_ACEITOS;

describe("E215 — vídeo só no atendimento", () => {
  const mp4 = new File([new Uint8Array(8)], "visita.mp4", { type: "video/mp4" });
  const tresGp = new File([new Uint8Array(8)], "clip.3gp", { type: "video/3gpp" });
  const mov = new File([new Uint8Array(8)], "iphone.mov", { type: "video/quicktime" });

  it("aceita MP4 e 3GP no composer do atendimento e recusa .mov", () => {
    expect(arquivoCompativel(mp4, TIPOS_DE_ANEXO_ACEITOS_NO_ATENDIMENTO)).toBe(true);
    expect(arquivoCompativel(tresGp, TIPOS_DE_ANEXO_ACEITOS_NO_ATENDIMENTO)).toBe(true);
    expect(arquivoCompativel(mov, TIPOS_DE_ANEXO_ACEITOS_NO_ATENDIMENTO)).toBe(false);
  });

  it("não abre vídeo para o chat interno, que usa a lista base", () => {
    expect(arquivoCompativel(mp4, TIPOS_DE_ANEXO_ACEITOS)).toBe(false);
    expect(TIPOS_DE_ANEXO_ACEITOS).not.toContain("video/");
  });
});

function arquivo(nome: string, tipo: string): File {
  return new File(["x"], nome, { type: tipo });
}

describe("arquivos-do-composer", () => {
  it("aceita imagem, pdf e áudio; recusa executável", () => {
    expect(arquivoCompativel(arquivo("foto.png", "image/png"), ACCEPT)).toBe(true);
    expect(arquivoCompativel(arquivo("voz.mp3", "audio/mpeg"), ACCEPT)).toBe(true);
    expect(arquivoCompativel(arquivo("orcamento.pdf", "application/pdf"), ACCEPT)).toBe(true);
    expect(
      arquivoCompativel(
        arquivo("apresentacao.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        ACCEPT,
      ),
    ).toBe(true);
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

  it("gera nome legivel com extensao do MIME para arquivos colados", () => {
    const data = { files: [arquivo("image.png", "image/jpeg")] } as unknown as DataTransfer;

    const arquivos = arquivosDaAreaDeTransferencia(data);

    expect(arquivos).toHaveLength(1);
    expect(arquivos[0]?.name).toBe("imagem-colada.jpg");
    expect(arquivos[0]?.type).toBe("image/jpeg");
  });

  it("numera arquivos colados quando o clipboard fornece mais de um", () => {
    const data = {
      files: [arquivo("", "image/png"), arquivo("", "application/pdf")],
    } as unknown as DataTransfer;

    expect(arquivosDaAreaDeTransferencia(data).map((item) => item.name)).toEqual([
      "imagem-colada-1.png",
      "arquivo-colado-2.pdf",
    ]);
  });
});
