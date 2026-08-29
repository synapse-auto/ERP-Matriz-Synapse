import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { EXPANSAO_DA_SIDEBAR, useExpansaoDaSidebar } from "./expansao-da-sidebar";

describe("useExpansaoDaSidebar", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("so expande apos a intencao de hover e recolhe so depois do atraso", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useExpansaoDaSidebar());

    expect(result.current.expandida).toBe(false);

    act(() => result.current.aoPonteiroEntrar());
    expect(result.current.expandida).toBe(false);

    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.intencaoAbrirMs - 1));
    expect(result.current.expandida).toBe(false);

    act(() => vi.advanceTimersByTime(1));
    expect(result.current.expandida).toBe(true);
    expect(result.current.fixada).toBe(false);

    act(() => result.current.aoPonteiroSair());
    expect(result.current.expandida).toBe(true);

    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.atrasoFecharMs - 1));
    expect(result.current.expandida).toBe(true);

    act(() => vi.advanceTimersByTime(1));
    expect(result.current.expandida).toBe(false);
  });

  it("cancela a abertura se o ponteiro sair antes da intencao", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useExpansaoDaSidebar());

    act(() => result.current.aoPonteiroEntrar());
    act(() => result.current.aoPonteiroSair());
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.intencaoAbrirMs));
    expect(result.current.expandida).toBe(false);
  });

  it("cancela o fechamento se o ponteiro voltar durante o atraso", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useExpansaoDaSidebar());

    act(() => result.current.aoPonteiroEntrar());
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.intencaoAbrirMs));
    act(() => result.current.aoPonteiroSair());
    act(() => result.current.aoPonteiroEntrar());
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.atrasoFecharMs));
    expect(result.current.expandida).toBe(true);
  });

  it("abre imediatamente no foco de teclado e recolhe no blur fora da barra", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useExpansaoDaSidebar());

    act(() => result.current.aoFocoDentro());
    expect(result.current.expandida).toBe(true);

    act(() => result.current.aoFocoFora());
    expect(result.current.expandida).toBe(false);
  });

  it("fixada permanece aberta ao sair o ponteiro e volta a 76px ao desafixar fora da area", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useExpansaoDaSidebar());

    act(() => result.current.alternarFixacao());
    expect(result.current.fixada).toBe(true);
    expect(result.current.expandida).toBe(true);

    act(() => result.current.aoPonteiroSair());
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.atrasoFecharMs));
    expect(result.current.expandida).toBe(true);

    act(() => result.current.alternarFixacao());
    expect(result.current.fixada).toBe(false);
    expect(result.current.expandida).toBe(false);
  });

  it("ao desafixar com o ponteiro ainda na barra, permanece expandida", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useExpansaoDaSidebar());

    act(() => result.current.aoPonteiroEntrar());
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.intencaoAbrirMs));
    act(() => result.current.alternarFixacao());
    act(() => result.current.alternarFixacao());
    expect(result.current.fixada).toBe(false);
    expect(result.current.expandida).toBe(true);
  });

  it("limpa timers no unmount", () => {
    vi.useFakeTimers();
    const { result, unmount } = renderHook(() => useExpansaoDaSidebar());

    act(() => result.current.aoPonteiroEntrar());
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
