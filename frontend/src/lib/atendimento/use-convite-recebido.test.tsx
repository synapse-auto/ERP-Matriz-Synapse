import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ErroDeApi } from "@/lib/api/errors";
import type { EstadoAtendimentoSelecionado } from "./types";

const api = vi.hoisted(() => ({ aprovarPedido: vi.fn(), recusarPedido: vi.fn(), invalidarParticipacao: vi.fn() }));
vi.mock("./use-participacao", () => ({ ...api, useMeuPedido: () => ({ id: "pedido", tipo: "CONVITE", status: "PENDENTE" }) }));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => ({ atendimentos: { cabecalho: {
  sucessoConviteAceito: "Aceito", sucessoConviteRecusado: "Recusado", conviteAtualizado: "Atualizado",
  erroSemPermissao: "Sem permissão", erroParticipacaoNaoEncontrada: "Indisponível", erroParticipacao: "Erro seguro",
} } }) }));
import { useConviteRecebido } from "./use-convite-recebido";

const estado = { cartao: { atendimentoId: "atendimento", convitePendente: true }, usuarioAtualParticipa: false } as EstadoAtendimentoSelecionado;
function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}
describe("convite recebido compartilhado", () => {
  beforeEach(() => vi.clearAllMocks());
  it("trava cliques entre header/composer e não libera por aceite otimista", async () => {
    let confirmar!: () => void;
    api.aprovarPedido.mockImplementation(() => new Promise<void>((resolve) => { confirmar = resolve; }));
    const reconciliar = vi.fn(async () => undefined);
    const { result, rerender } = renderHook(({ snapshot }) => useConviteRecebido(snapshot, reconciliar), { wrapper, initialProps: { snapshot: estado } });
    let operacao!: Promise<void>;
    act(() => { operacao = result.current.aceitar(); void result.current.recusar(); });
    expect(api.aprovarPedido).toHaveBeenCalledOnce();
    expect(api.recusarPedido).not.toHaveBeenCalled();
    expect(result.current.processando).toBe(true);
    await act(async () => { confirmar(); await operacao; });
    expect(result.current.pendente).toBe(true);
    expect(result.current.podeResponder).toBe(false);
    expect(reconciliar).toHaveBeenCalledOnce();
    rerender({ snapshot: { ...estado, usuarioAtualParticipa: true } });
    expect(result.current.pendente).toBe(false);
    rerender({ snapshot: estado });
    expect(result.current.podeResponder).toBe(true);
  });
  it("falha sanitizada mantém bloqueio e permite nova tentativa", async () => {
    api.aprovarPedido.mockRejectedValueOnce(new Error("lead UUID segredo"));
    const { result } = renderHook(() => useConviteRecebido(estado, vi.fn()), { wrapper });
    await act(() => result.current.aceitar());
    expect(result.current.pendente).toBe(true);
    expect(result.current.podeResponder).toBe(true);
    expect(result.current.feedback?.texto).toBe("Erro seguro");
  });
  it("409 reconcilia acesso sem mensagem técnica", async () => {
    api.recusarPedido.mockRejectedValue(new ErroDeApi(409, null, "UUID interno"));
    const reconciliar = vi.fn(async () => undefined);
    const { result } = renderHook(() => useConviteRecebido(estado, reconciliar), { wrapper });
    await act(() => result.current.recusar());
    expect(result.current.feedback?.texto).toBe("Atualizado");
    expect(api.invalidarParticipacao).toHaveBeenCalledWith("atendimento");
    expect(reconciliar).toHaveBeenCalledOnce();
  });
});
