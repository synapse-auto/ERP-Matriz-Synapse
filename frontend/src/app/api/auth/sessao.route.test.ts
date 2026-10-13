import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  MAX_AGE_COOKIE_REFRESH_SEGUNDOS,
  NOME_COOKIE_PERSISTENCIA_SESSAO,
  NOME_COOKIE_REFRESH,
} from "@/lib/auth/constants";

const { cookiesMock } = vi.hoisted(() => ({ cookiesMock: vi.fn() }));

vi.mock("next/headers", () => ({ cookies: cookiesMock }));
vi.mock("@/lib/api/server-api-url", () => ({ obterUrlApiServidor: () => "http://api-interna" }));

import { POST as login } from "./login/route";
import { POST as logout } from "./logout/route";
import { POST as refresh } from "./refresh/route";

function requisicaoLogin(manterSessaoAtiva: boolean) {
  return new Request("http://crm.local/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: "ana@empresa.test", senha: "senha", manterSessaoAtiva }),
  });
}

function respostaDeSessao() {
  return {
    ok: true,
    json: async () => ({
      accessToken: "access-token",
      refreshToken: "refresh-token-novo",
      expiraEmSegundos: 900,
    }),
  };
}

describe("Route Handlers de sessão", () => {
  const set = vi.fn();
  const deleteCookie = vi.fn();

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respostaDeSessao()));
    set.mockReset();
    deleteCookie.mockReset();
    cookiesMock.mockResolvedValue({
      get: vi.fn(),
      set,
      delete: deleteCookie,
    });
  });

  it.each([502, 503, 504])("refresh HTTP %s preserva cookies; não é sessão recusada", async (status) => {
    cookiesMock.mockResolvedValue({ get: () => ({ value: "token-sintetico" }), set, delete: deleteCookie });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status })));
    expect((await refresh()).status).toBe(status);
    expect(deleteCookie).not.toHaveBeenCalled();
  });

  it("falha de transporte retorna 503 sanitizado e preserva cookies", async () => {
    cookiesMock.mockResolvedValue({ get: () => ({ value: "token-sintetico" }), set, delete: deleteCookie });
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("URL privada / token")));
    const resposta = await refresh();
    expect(resposta.status).toBe(503);
    expect(await resposta.json()).toEqual({ status: 503 });
    expect(deleteCookie).not.toHaveBeenCalled();
  });

  it.each([401, 403])("refresh HTTP %s mantém recusa e remove a sessão", async (status) => {
    cookiesMock.mockResolvedValue({ get: () => ({ value: "token-sintetico" }), set, delete: deleteCookie });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status })));
    expect((await refresh()).status).toBe(status);
    expect(deleteCookie).toHaveBeenCalledTimes(2);
  });

  it("grava refresh persistente somente quando a caixa foi marcada", async () => {
    const resposta = await login(requisicaoLogin(true) as never);

    expect(fetch).toHaveBeenCalledWith(
      "http://api-interna/api/v1/auth/login",
      expect.objectContaining({ body: JSON.stringify({ email: "ana@empresa.test", senha: "senha" }) }),
    );
    expect(set).toHaveBeenNthCalledWith(
      1,
      NOME_COOKIE_REFRESH,
      "refresh-token-novo",
      expect.objectContaining({
        httpOnly: true,
        maxAge: MAX_AGE_COOKIE_REFRESH_SEGUNDOS,
      }),
    );
    expect(set).toHaveBeenNthCalledWith(
      2,
      NOME_COOKIE_PERSISTENCIA_SESSAO,
      "1",
      expect.objectContaining({ maxAge: MAX_AGE_COOKIE_REFRESH_SEGUNDOS }),
    );
    expect(await resposta.json()).toEqual({ accessToken: "access-token", expiraEmSegundos: 900 });
  });

  it("grava cookies de sessão quando a caixa fica desmarcada", async () => {
    await login(requisicaoLogin(false) as never);

    const opcoesRefresh = set.mock.calls[0][2] as Record<string, unknown>;
    const opcoesPreferencia = set.mock.calls[1][2] as Record<string, unknown>;
    expect(set.mock.calls[1].slice(0, 2)).toEqual([NOME_COOKIE_PERSISTENCIA_SESSAO, "0"]);
    expect(opcoesRefresh).not.toHaveProperty("maxAge");
    expect(opcoesRefresh).not.toHaveProperty("expires");
    expect(opcoesPreferencia).not.toHaveProperty("maxAge");
    expect(opcoesPreferencia).not.toHaveProperty("expires");
  });

  it("preserva cookie de sessão na rotação de refresh", async () => {
    cookiesMock.mockResolvedValue({
      get: vi.fn((nome: string) => {
        if (nome === NOME_COOKIE_REFRESH) return { value: "refresh-token-antigo" };
        if (nome === NOME_COOKIE_PERSISTENCIA_SESSAO) return { value: "0" };
        return undefined;
      }),
      set,
      delete: deleteCookie,
    });

    await refresh();

    const opcoesRefresh = set.mock.calls[0][2] as Record<string, unknown>;
    const opcoesPreferencia = set.mock.calls[1][2] as Record<string, unknown>;
    expect(opcoesRefresh).not.toHaveProperty("maxAge");
    expect(opcoesPreferencia).not.toHaveProperty("maxAge");
    expect(set.mock.calls[1].slice(0, 2)).toEqual([NOME_COOKIE_PERSISTENCIA_SESSAO, "0"]);
  });

  it("remove refresh e preferência no logout", async () => {
    cookiesMock.mockResolvedValue({
      get: vi.fn((nome: string) => (nome === NOME_COOKIE_REFRESH ? { value: "refresh-token" } : undefined)),
      set,
      delete: deleteCookie,
    });

    await logout();

    expect(deleteCookie).toHaveBeenCalledWith(NOME_COOKIE_REFRESH);
    expect(deleteCookie).toHaveBeenCalledWith(NOME_COOKIE_PERSISTENCIA_SESSAO);
  });
});
