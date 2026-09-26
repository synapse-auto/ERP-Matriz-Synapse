import { describe, expect, it } from "vitest";
import { arquivoCompativel, TIPOS_DE_ANEXO_ACEITOS as TIPOS_BASE } from "@/lib/atendimento/arquivos-do-composer";
import { TIPOS_DE_ANEXO_ACEITOS } from "./arquivos";

describe("arquivos do chat interno", () => {
  it("aceita M4A de navegador sem modificar a lista de Atendimentos", () => {
    const audio = new File([new Uint8Array(8)], "voz.m4a", { type: "audio/x-m4a" });
    expect(arquivoCompativel(audio, TIPOS_DE_ANEXO_ACEITOS)).toBe(true);
    expect(arquivoCompativel(audio, TIPOS_BASE)).toBe(false);
    expect(arquivoCompativel(new File([""], "script.exe"), TIPOS_DE_ANEXO_ACEITOS)).toBe(false);
  });
  it("seleciona MP4 e 3GP internos sem liberar QuickTime", () => {
    expect(arquivoCompativel(new File([""], "video.mp4", { type: "video/mp4" }), TIPOS_DE_ANEXO_ACEITOS)).toBe(true);
    expect(arquivoCompativel(new File([""], "video.3gp", { type: "video/3gpp" }), TIPOS_DE_ANEXO_ACEITOS)).toBe(true);
    expect(arquivoCompativel(new File([""], "video.mov", { type: "video/quicktime" }), TIPOS_DE_ANEXO_ACEITOS)).toBe(false);
  });
});
