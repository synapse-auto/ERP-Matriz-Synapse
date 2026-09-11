import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { afterEach, describe, expect, it, vi } from "vitest";

import { buscarTema, buscarTextos } from "./fetch-config";

vi.mock("@/lib/api/server-api-url", () => ({
  obterUrlApiServidor: () => "http://backend",
}));

function carregarJson(arquivo: "tema.json" | "textos.json"): Record<string, unknown> {
  return JSON.parse(
    readFileSync(
      resolve(process.cwd(), `../backend/crm-app/src/main/resources/${arquivo}`),
      "utf8",
    ),
  ) as Record<string, unknown>;
}

function responderCom(payload: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(JSON.stringify(payload), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    ),
  );
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("configuração raiz tolerante a versões desalinhadas", () => {
  it("buscarTextos aceita o catálogo anterior às E158 e E159", async () => {
    const catalogo = carregarJson("textos.json");
    const atendimentos = catalogo.atendimentos as Record<string, unknown>;
    const cartao = atendimentos.cartao as Record<string, unknown>;
    const agenda = catalogo.agenda as Record<string, unknown>;
    delete cartao.atrasoAtendente;
    delete agenda.importarCsv;
    delete agenda.exportarCsv;
    delete agenda.importacao;
    delete agenda.exportacao;
    responderCom(catalogo);

    await expect(buscarTextos()).resolves.toMatchObject({
      atendimentos: { cartao: { atrasoAtendente: expect.any(String) } },
      agenda: { importacao: { titulo: expect.any(String) } },
    });
  });

  it("buscarTema aceita a ausência de tokens adicionados depois da primeira versão", async () => {
    const tema = carregarJson("tema.json");
    delete tema.fundoCanvas;
    delete tema.sidebarItemTextoHover;
    responderCom(tema);

    await expect(buscarTema()).resolves.toMatchObject({
      fundoCanvas: expect.any(String),
      sidebarItemTextoHover: expect.any(String),
    });
  });

  it("sinaliza no log e rejeita configuração realmente corrompida", async () => {
    const erro = vi.spyOn(console, "error").mockImplementation(() => undefined);
    responderCom({ app: "isto não é um catálogo" });

    await expect(buscarTextos()).rejects.toThrow();
    expect(erro).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/config/textos"),
      expect.anything(),
    );
  });

  it("sinaliza no log e rejeita tema realmente corrompido", async () => {
    const erro = vi.spyOn(console, "error").mockImplementation(() => undefined);
    responderCom({ corPrimaria: 123 });

    await expect(buscarTema()).rejects.toThrow();
    expect(erro).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/config/tema"),
      expect.anything(),
    );
  });
});
