import { describe, expect, it, vi } from "vitest";
import { ErroDeApi } from "@/lib/api/errors";
import { ErrosTextosSchema } from "@/lib/config/schema-erros";
import { classificarFalhaDeLeitura, registrarErroDeTela } from "./conexao";

describe("classificação e diagnóstico", () => {
  it.each([401, 403])("HTTP %s nunca significa indisponibilidade, mesmo offline", (status) => {
    expect(classificarFalhaDeLeitura(new ErroDeApi(status, null, ""), false)).toBe("acesso");
  });
  it.each([502, 503, 504])("HTTP %s identifica falha de gateway/backend, não permissão", (status) => {
    expect(classificarFalhaDeLeitura(new ErroDeApi(status, null, ""), true)).toBe("backend");
  });
  it("não infere queda do backend só porque fetch falhou", () => {
    expect(classificarFalhaDeLeitura(new TypeError(), true)).toBe("conexao");
    expect(classificarFalhaDeLeitura(new Error(), false)).toBe("offline");
    expect(classificarFalhaDeLeitura(new Error(), true)).toBe("renderizacao");
  });
  it("catálogos antigos recebem somente defaults das chaves de erro", () => {
    expect(ErrosTextosSchema.parse(undefined)).toHaveProperty("naoEncontrada");
    expect(ErrosTextosSchema.parse({ inicio: "Início personalizado" }).inicio).toBe("Início personalizado");
  });
  it("diagnóstico não inclui dados sensíveis nem digest arbitrário", () => {
    const log = vi.spyOn(console, "error").mockImplementation(() => undefined);
    registrarErroDeTela("rota", Object.assign(new Error("token privado"), { digest: "https://privado/token" }));
    expect(log).toHaveBeenCalledWith("[tela] Falha de renderização", { escopo: "rota", digest: null });
    log.mockRestore();
  });
});
