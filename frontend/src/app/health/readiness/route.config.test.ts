import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/server-api-url", () => ({
  obterUrlApiServidor: () => "http://backend",
}));

import { GET as liveness } from "../liveness/route";
import { GET as readiness } from "./route";

function carregarJson(arquivo: "tema.json" | "textos.json"): Record<string, unknown> {
  return JSON.parse(
    readFileSync(
      resolve(process.cwd(), `../backend/crm-app/src/main/resources/${arquivo}`),
      "utf8",
    ),
  ) as Record<string, unknown>;
}

function responderConfiguracoes(tema: unknown, textos: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn((entrada: string | URL | Request) => {
      const caminho = String(entrada);
      const payload = caminho.endsWith("/tema") ? tema : textos;
      return Promise.resolve(
        new Response(JSON.stringify(payload), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    }),
  );
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("GET /health/readiness com os catalogos reais", () => {
  it("aceita o catalogo anterior as E158 e E159", async () => {
    const tema = carregarJson("tema.json");
    const textos = carregarJson("textos.json");
    const atendimentos = textos.atendimentos as Record<string, unknown>;
    const cartao = atendimentos.cartao as Record<string, unknown>;
    const agenda = textos.agenda as Record<string, unknown>;
    delete cartao.atrasoAtendente;
    delete agenda.importarCsv;
    delete agenda.exportarCsv;
    delete agenda.importacao;
    delete agenda.exportacao;
    delete tema.fundoCanvas;
    delete tema.sidebarItemTextoHover;

    responderConfiguracoes(tema, textos);

    const resposta = await readiness();

    expect(resposta.status).toBe(200);
  });

  it("recusa catalogo fundacionalmente corrompido, sem alterar a liveness", async () => {
    const erro = vi.spyOn(console, "error").mockImplementation(() => undefined);
    responderConfiguracoes(carregarJson("tema.json"), { app: "catalogo corrompido" });

    const [respostaReadiness, respostaLiveness] = await Promise.all([readiness(), liveness()]);

    expect(respostaReadiness.status).toBe(503);
    await expect(respostaReadiness.json()).resolves.toEqual({ status: "DOWN" });
    expect(respostaLiveness.status).toBe(200);
    expect(erro).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/config/textos"),
      expect.anything(),
    );
  });
});
