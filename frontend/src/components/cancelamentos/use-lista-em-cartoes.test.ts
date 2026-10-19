import { act, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { useListaEmCartoes } from "./use-lista-em-cartoes";

afterEach(() => vi.unstubAllGlobals());

it("acompanha o breakpoint local e remove listener sem depender de consultas", () => {
  let cartoes = true;
  const ouvintes = new Set<() => void>();
  const matchMedia = vi.fn(() => ({
    matches: cartoes,
    addEventListener: (_evento: string, notificar: () => void) => ouvintes.add(notificar),
    removeEventListener: (_evento: string, notificar: () => void) => ouvintes.delete(notificar),
  }));
  vi.stubGlobal("matchMedia", matchMedia);
  const r = renderHook(() => useListaEmCartoes());
  expect(r.result.current).toBe(true);
  expect(matchMedia).toHaveBeenCalledWith("(max-width: 1023px)");
  act(() => { cartoes = false; ouvintes.forEach(notificar => notificar()); });
  expect(r.result.current).toBe(false);
  r.unmount(); expect(ouvintes.size).toBe(0);
});

it("mantem fallback seguro quando matchMedia nao existe", () => {
  vi.stubGlobal("matchMedia", undefined);
  expect(renderHook(() => useListaEmCartoes()).result.current).toBe(false);
});
