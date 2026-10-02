import { act, render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  obterMeuPedido: vi.fn(),
  listarPedidosPendentes: vi.fn(),
}));

vi.mock("./api", () => ({
  ...api,
  pedirEntrada: vi.fn(),
  entrarAtendimento: vi.fn(),
  sairAtendimento: vi.fn(),
  aprovarPedido: vi.fn(),
  recusarPedido: vi.fn(),
}));

import { invalidarParticipacao, useMeuPedido, usePedidosPendentes } from "./use-participacao";

const ATENDIMENTO = "c0c00000-0000-4000-8000-0000000000a1";

function Sonda({ pendentesHabilitados }: { pendentesHabilitados: boolean }) {
  useMeuPedido(ATENDIMENTO);
  usePedidosPendentes(ATENDIMENTO, pendentesHabilitados);
  return null;
}

async function esvaziarFila() {
  for (let i = 0; i < 10; i += 1) {
    await act(async () => {
      await Promise.resolve();
    });
  }
}

describe("use-participacao", () => {
  beforeEach(() => {
    invalidarParticipacao(ATENDIMENTO);
    // Como no navegador: cada resposta é um objeto novo, o que provoca nova renderização.
    api.obterMeuPedido.mockReset().mockImplementation(async () => ({ id: "p-1", status: "PENDENTE" }));
    api.listarPedidosPendentes.mockReset().mockRejectedValue(new Error("404"));
  });

  it("busca o meu pedido uma vez por atendimento, e não a cada render", async () => {
    const { rerender } = render(<Sonda pendentesHabilitados={false} />);
    await esvaziarFila();
    // O cabeçalho re-renderiza a cada evento de tempo real, digitação e relógio.
    for (let i = 0; i < 5; i += 1) {
      rerender(<Sonda pendentesHabilitados={false} />);
      await esvaziarFila();
    }

    expect(api.obterMeuPedido).toHaveBeenCalledTimes(1);
  });

  it("volta a buscar só depois de invalidar", async () => {
    render(<Sonda pendentesHabilitados={false} />);
    await esvaziarFila();

    await act(async () => invalidarParticipacao(ATENDIMENTO));
    await esvaziarFila();

    expect(api.obterMeuPedido).toHaveBeenCalledTimes(2);
  });

  it("não lista pedidos pendentes de quem não é responsável (evita 404 em loop)", async () => {
    render(<Sonda pendentesHabilitados={false} />);
    await esvaziarFila();

    expect(api.listarPedidosPendentes).not.toHaveBeenCalled();
  });

  it("falha ao listar pendentes do responsável não vira nova busca a cada render", async () => {
    render(<Sonda pendentesHabilitados />);
    await esvaziarFila();

    expect(api.listarPedidosPendentes).toHaveBeenCalledTimes(1);
  });
});
