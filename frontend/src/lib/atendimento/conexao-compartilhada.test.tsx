import { StrictMode, useEffect, useState } from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useConexaoTempoReal, type ConexaoTempoReal } from "./tempo-real";

const transporte = vi.hoisted(() => ({ criados: [] as object[], assinaturas: [] as Array<{ destino: string; receber: (m: { body: string }) => void }> }));
vi.mock("@stomp/stompjs", () => ({
  TickerStrategy: { Worker: "worker" },
  Client: class {
    connected = false;
    onConnect?: () => void;
    subscribe = vi.fn((destino: string, receber: (m: { body: string }) => void) => {
      transporte.assinaturas.push({ destino, receber });
      return { unsubscribe: vi.fn() };
    });
    constructor() { transporte.criados.push(this); }
    activate() { this.connected = true; this.onConnect?.(); }
    deactivate() { this.connected = false; }
  },
}));

describe("conexão compartilhada na montagem real dos hooks", () => {
  it("StrictMode não deixa a ficha tardia numa conexão órfã enquanto o layout recebe eventos", async () => {
    const conexoes = new Map<string, ConexaoTempoReal>();
    const recebidos = { layout: vi.fn(), ficha: vi.fn() };
    function Consumidor({ nome }: { nome: "layout" | "ficha" }) {
      const { conexao, estado } = useConexaoTempoReal(() => "token-sintetico", undefined, evento => recebidos[nome](evento));
      useEffect(() => { conexoes.set(nome, conexao); }, [nome, conexao]);
      return <output data-testid={nome}>{estado}</output>;
    }
    function Tela() {
      const [ficha, mostrar] = useState(false);
      return <><Consumidor nome="layout" /><button onClick={() => mostrar(true)}>Abrir ficha</button>{ficha && <Consumidor nome="ficha" />}</>;
    }
    render(<StrictMode><Tela /></StrictMode>);
    await waitFor(() => expect(screen.getByTestId("layout")).toHaveTextContent("conectado"));
    const clientesAntes = transporte.criados.length;
    fireEvent.click(screen.getByRole("button", { name: "Abrir ficha" }));
    await waitFor(() => expect(screen.getByTestId("ficha")).toHaveTextContent(/^conectado$/));
    expect(conexoes.get("ficha")).toBe(conexoes.get("layout"));
    expect(transporte.criados).toHaveLength(clientesAntes);
    const evento = { tipo: "ATENDIMENTO_ESTADO", contrato: "atendimento.estado.v1", versaoContrato: 1,
      eventoId: "evento-sintetico", dados: { atendimentoId: "atendimento-a", leadId: "lead-a", eventoTipo: "DADOS_CLIENTE_ATUALIZADOS", versao: 2, ocorridoEm: "2026-11-05T12:00:00Z" } };
    act(() => transporte.assinaturas.filter(a => a.destino === "/user/queue/notificacoes").at(-1)!.receber({ body: JSON.stringify(evento) }));
    expect(recebidos.layout).toHaveBeenCalledExactlyOnceWith(evento);
    expect(recebidos.ficha).toHaveBeenCalledExactlyOnceWith(evento);
  });
});
