import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const estado = { usuarioId: "usuario-teste" };

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (selecionar: (valor: typeof estado) => unknown) => selecionar(estado),
}));

import {
  usePreferenciaChatInternoDeNotificacao,
  usePreferenciaDuracaoDeNotificacao,
  usePreferenciaPosicaoDeNotificacao,
  usePreferenciaSomDeNotificacao,
  usePreferenciaVisualDeNotificacao,
} from "./preferencias-notificacoes";

beforeEach(() => {
  window.localStorage.clear();
});

describe("preferências de notificações", () => {
  it("usa os defaults e persiste a preferência visual", () => {
    const { result } = renderHook(() => usePreferenciaVisualDeNotificacao());
    expect(result.current.visualHabilitado).toBe(true);

    act(() => result.current.definirVisualHabilitado(false));
    expect(window.localStorage.getItem("synapse:preferencias-notificacoes:visual:usuario-teste")).toBe("false");
  });

  it("preserva a semântica legada do som para valores já persistidos", () => {
    window.localStorage.setItem("synapse:preferencias-notificacoes:som:usuario-teste", "valor-antigo");
    const { result } = renderHook(() => usePreferenciaSomDeNotificacao());
    expect(result.current.somHabilitado).toBe(true);
  });

  it("sincroniza o toggle de chat interno pelo evento compartilhado", () => {
    const { result } = renderHook(() => usePreferenciaChatInternoDeNotificacao());
    act(() => {
      window.localStorage.setItem("synapse:preferencias-notificacoes:chat-interno:usuario-teste", "false");
      window.dispatchEvent(new Event("synapse:preferencia-notificacao-alterada"));
    });
    expect(result.current.chatInternoHabilitado).toBe(false);
  });

  it("normaliza duração inválida e aceita valores dentro da faixa", () => {
    window.localStorage.setItem("synapse:preferencias-notificacoes:duracao-segundos:usuario-teste", "999");
    const { result } = renderHook(() => usePreferenciaDuracaoDeNotificacao());
    expect(result.current.duracaoSegundos).toBe(3);
    act(() => result.current.definirDuracaoSegundos(15));
    expect(result.current.duracaoSegundos).toBe(15);
  });

  it("mantém TOPO como padrão e persiste BAIXO", () => {
    const { result } = renderHook(() => usePreferenciaPosicaoDeNotificacao());
    expect(result.current.posicao).toBe("TOPO");
    act(() => result.current.definirPosicao("BAIXO"));
    expect(result.current.posicao).toBe("BAIXO");
  });
});
