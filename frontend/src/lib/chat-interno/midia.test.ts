import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetchArquivo } from "@/lib/api/http-client";
import { baixarArquivoChat, metadadosDaMidiaInterna } from "./midia";
import type { ChatMensagem } from "./types";

vi.mock("@/lib/api/http-client", () => ({ apiFetchArquivo: vi.fn() }));
describe("download binário privado", () => {
  beforeEach(() => vi.clearAllMocks());
  it("obtém bytes antes de criar download, usa nome do backend e não usa URL antiga", async () => {
    const criar = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:download");
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function(this: HTMLAnchorElement) {
      expect(this.download).toBe("áudio.ogg");
      expect(this.href).toBe("blob:download");
    });
    vi.mocked(apiFetchArquivo).mockResolvedValue({ blob: new Blob(["bytes"], { type: "audio/ogg" }), nome: "áudio.ogg" });
    await baixarArquivoChat("c1", "m1", "arquivo.exe");
    expect(apiFetchArquivo).toHaveBeenCalledWith("/api/v1/chat-interno/conversas/c1/midias/m1/arquivo");
    expect(criar).toHaveBeenCalledOnce();
    expect(click).toHaveBeenCalledOnce();
    criar.mockRestore(); click.mockRestore();
  });
  it("rede/403/404/503 não criam link nem blob de download", async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click");
    vi.mocked(apiFetchArquivo).mockRejectedValue(new Error("falha"));
    await expect(baixarArquivoChat("c1", "m1", null)).rejects.toThrow("falha");
    expect(click).not.toHaveBeenCalled(); click.mockRestore();
  });
  it("metadados novos e antigos são aceitos e JSON inválido não quebra a bolha", () => {
    const mensagem = { conteudo: "legenda", midiaMetadados: '{"nome_original":"foto.png","tamanho_bytes":12}' } as ChatMensagem;
    expect(metadadosDaMidiaInterna(mensagem)).toMatchObject({ nome: "foto.png", tamanho: 12, legenda: "legenda" });
    expect(metadadosDaMidiaInterna({ ...mensagem, midiaMetadados: { nome: "antigo.pdf", tamanho: 30 } })).toMatchObject({ nome: "antigo.pdf", tamanho: 30 });
    expect(metadadosDaMidiaInterna({ ...mensagem, midiaMetadados: "null" })).toMatchObject({ nome: null, legenda: "legenda" });
  });
});
