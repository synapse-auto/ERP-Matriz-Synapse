import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/campanhas/api", () => ({}));
vi.mock("@/lib/lead/api", () => ({ listarEtapas: vi.fn() }));
vi.mock("@/lib/auth/auth-store", () => ({ useAuthStore: vi.fn() }));

import { useValorComAtraso } from "./hooks";

describe("useValorComAtraso", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("só entrega o valor novo depois da pausa na digitação", () => {
    const { result, rerender } = renderHook(({ valor }) => useValorComAtraso(valor, 400), { initialProps: { valor: { busca: "" } } });
    rerender({ valor: { busca: "ana" } });
    expect(result.current).toEqual({ busca: "" });

    act(() => vi.advanceTimersByTime(399));
    expect(result.current).toEqual({ busca: "" });
    act(() => vi.advanceTimersByTime(1));
    expect(result.current).toEqual({ busca: "ana" });
  });

  it("objeto novo com o mesmo conteúdo a cada render não reinicia o temporizador nem gera laço de render", () => {
    let renders = 0;
    const { rerender } = renderHook(
      ({ n }) => {
        renders += 1;
        return useValorComAtraso({ limite: 100, n: n > 0 ? 1 : 1 }, 400);
      },
      { initialProps: { n: 0 } },
    );
    for (let i = 1; i <= 5; i++) rerender({ n: i });
    act(() => vi.advanceTimersByTime(5000));
    // 6 renders pedidos + no máximo 1 do setState final; um laço de render passaria de dezenas.
    expect(renders).toBeLessThanOrEqual(8);
  });
});
