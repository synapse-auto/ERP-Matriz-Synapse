import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useRef } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Button } from "@/components/ui/button";
import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { calcularTransbordo } from "@/lib/atendimento/transbordo-de-acoes";

import { BarraDeAcoesComTransbordo, type AcaoTransbordavel } from "./barra-de-acoes-com-transbordo";

/**
 * Larguras simuladas: o jsdom não faz layout, então o teste decide quanto cada peça ocupa. Sem CSS
 * do Tailwind, o `gap` computado é 0 — as contas abaixo não somam vãos.
 */
const layout = { container: 1000, larguras: {} as Record<string, number> };
const observadores = new Set<ResizeObserverCallback>();

class ResizeObserverFalso {
  constructor(private readonly callback: ResizeObserverCallback) {}
  observe() {
    observadores.add(this.callback);
  }
  unobserve() {}
  disconnect() {
    observadores.delete(this.callback);
  }
}

function retangulo(largura: number): DOMRect {
  return { width: largura, height: 32, x: 0, y: 0, top: 0, left: 0, right: largura, bottom: 32, toJSON: () => ({}) };
}

/** Simula sidebar recolhida, painel aberto etc.: muda a largura e dispara os observadores. */
function redimensionar(largura: number) {
  layout.container = largura;
  act(() => {
    observadores.forEach((callback) => callback([], {} as ResizeObserver));
  });
}

beforeEach(() => {
  layout.container = 1000;
  layout.larguras = { participacao: 120, convidar: 100, transferir: 110, finalizar: 100, buscar: 40, __menu: 40 };
  vi.stubGlobal("ResizeObserver", ResizeObserverFalso);
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
    if (this.dataset.testid === "container") return retangulo(layout.container);
    const id = this.dataset.medida;
    return retangulo(id ? (layout.larguras[id] ?? 0) : 0);
  });
});

afterEach(() => {
  observadores.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function acao(id: string, prioridade: number, aoSelecionar = vi.fn(), desabilitado = false): AcaoTransbordavel {
  return {
    id,
    prioridade,
    inline: <Button type="button" onClick={aoSelecionar} disabled={desabilitado}>{id}</Button>,
    itemDeMenu: <DropdownMenuItem onClick={aoSelecionar} disabled={desabilitado}>{id}</DropdownMenuItem>,
  };
}

/** O container reserva 200px para a identificação; a barra usa o resto. */
function Cenario({ acoes }: { acoes: AcaoTransbordavel[] }) {
  const ref = useRef<HTMLDivElement>(null);
  return (
    <div ref={ref} data-testid="container">
      <BarraDeAcoesComTransbordo acoes={acoes} rotuloMenu="Mais ações" containerRef={ref} larguraReservada={200} />
    </div>
  );
}

function botaoVisivel(nome: string) {
  return screen.queryByRole("button", { name: nome });
}

function acoesPadrao(): AcaoTransbordavel[] {
  return [
    { id: "participacao", fixo: true, inline: <span>participação</span> },
    acao("convidar", 30),
    acao("transferir", 60),
    { id: "finalizar", fixo: true, inline: <Button type="button">finalizar</Button> },
    acao("buscar", 50),
  ];
}

describe("calcularTransbordo", () => {
  const itens = [
    { id: "fixo", largura: 100, fixo: true, prioridade: 0 },
    { id: "a", largura: 100, fixo: false, prioridade: 10 },
    { id: "b", largura: 100, fixo: false, prioridade: 20 },
  ];

  it("não usa menu quando tudo cabe", () => {
    expect([...calcularTransbordo(itens, 320, 40, 10)]).toEqual([]);
  });

  it("tira primeiro a menor prioridade e reserva espaço para o botão do menu", () => {
    // 3 × 100 + 2 vãos = 320 não cabe em 300; sem "a": 2 × 100 + menu 40 + 2 vãos = 260.
    expect([...calcularTransbordo(itens, 300, 40, 10)]).toEqual(["a"]);
  });

  it("nunca tira item fixo, mesmo sem espaço nenhum", () => {
    expect([...calcularTransbordo(itens, 0, 40, 10)].sort()).toEqual(["a", "b"]);
  });

  it("em empate de prioridade, sai primeiro o que está mais à direita", () => {
    const empatados = [
      { id: "x", largura: 100, fixo: false, prioridade: 1 },
      { id: "y", largura: 100, fixo: false, prioridade: 1 },
    ];
    expect([...calcularTransbordo(empatados, 160, 40, 10)]).toEqual(["y"]);
  });
});

describe("BarraDeAcoesComTransbordo", () => {
  it("mostra tudo e nenhum menu quando há espaço", () => {
    render(<Cenario acoes={acoesPadrao()} />);

    ["convidar", "transferir", "finalizar", "buscar"].forEach((nome) => expect(botaoVisivel(nome)).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
  });

  it("move para o ⋯ só o que não cabe, pela prioridade, e devolve quando o espaço volta", () => {
    render(<Cenario acoes={acoesPadrao()} />);

    // 460 disponíveis < 470 de ações: sai "convidar" (30); 370 + menu 40 cabe.
    redimensionar(660);
    expect(botaoVisivel("convidar")).not.toBeInTheDocument();
    expect(botaoVisivel("transferir")).toBeInTheDocument();
    expect(botaoVisivel("buscar")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mais ações" })).toBeInTheDocument();

    // 360 disponíveis: saem convidar (30), buscar (50) e transferir (60); fixos ficam.
    redimensionar(560);
    ["convidar", "buscar", "transferir"].forEach((nome) => expect(botaoVisivel(nome)).not.toBeInTheDocument());
    expect(botaoVisivel("finalizar")).toBeInTheDocument();
    expect(screen.getByText("participação")).toBeInTheDocument();

    redimensionar(1200);
    ["convidar", "transferir", "buscar"].forEach((nome) => expect(botaoVisivel(nome)).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
  });

  it("reage a uma ação que cresce (ex.: tags chegando) sem mudar a largura do container", () => {
    render(<Cenario acoes={acoesPadrao()} />);
    expect(botaoVisivel("buscar")).toBeInTheDocument();

    layout.larguras.buscar = 700;
    redimensionar(1000);

    expect(botaoVisivel("buscar")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mais ações" })).toBeInTheDocument();
  });

  it("item do menu executa a mesma ação e o menu fecha em seguida", async () => {
    const convidar = vi.fn();
    const acoes = acoesPadrao().map((item) => (item.id === "convidar" ? acao("convidar", 30, convidar) : item));
    render(<Cenario acoes={acoes} />);
    redimensionar(660);

    fireEvent.click(screen.getByRole("button", { name: "Mais ações" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: "convidar" }));

    expect(convidar).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.queryByRole("menuitem", { name: "convidar" })).not.toBeInTheDocument());
  });

  it("estado desabilitado é o mesmo dentro e fora do menu", async () => {
    render(<Cenario acoes={[acao("convidar", 30, vi.fn(), true)]} />);
    expect(botaoVisivel("convidar")).toBeDisabled();

    redimensionar(250);
    fireEvent.click(screen.getByRole("button", { name: "Mais ações" }));
    expect(await screen.findByRole("menuitem", { name: "convidar" })).toHaveAttribute("aria-disabled", "true");
  });

  it("gatilho tem rótulo acessível e o menu mantém a ordem visual das ações", async () => {
    render(<Cenario acoes={acoesPadrao()} />);
    redimensionar(560);

    const gatilho = screen.getByRole("button", { name: "Mais ações" });
    expect(gatilho).toHaveAttribute("title", "Mais ações");
    fireEvent.click(gatilho);

    expect(await screen.findByRole("menu")).toBeInTheDocument();
    expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual(["convidar", "transferir", "buscar"]);
  });

  it("quando nem os fixos cabem ao lado do nome, a barra desce para uma linha própria em vez de cortar", () => {
    const { container } = render(<Cenario acoes={acoesPadrao()} />);

    // 100 ao lado do nome < 220 dos fixos; na linha inteira (300) cabem fixos + menu.
    redimensionar(300);
    const barra = container.querySelector('[data-slot="acoes-cabecalho"]');
    expect(barra).toHaveAttribute("data-linha-propria", "true");
    expect(barra).toHaveClass("w-full", "flex-wrap");
    expect(botaoVisivel("finalizar")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mais ações" })).toBeInTheDocument();

    redimensionar(1200);
    expect(barra).not.toHaveAttribute("data-linha-propria");
  });

  it("sem largura medida (oculto ou antes do layout), não esconde nenhuma ação", () => {
    render(<Cenario acoes={acoesPadrao()} />);
    redimensionar(0);

    ["convidar", "transferir", "buscar"].forEach((nome) => expect(botaoVisivel(nome)).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
  });

  it("a régua de medição não é alcançável por leitor de tela nem por teclado", () => {
    const { container } = render(<Cenario acoes={acoesPadrao()} />);
    const regua = container.querySelector('[data-slot="regua-acoes-cabecalho"]');

    expect(regua).toHaveAttribute("aria-hidden", "true");
    expect(regua).toHaveAttribute("inert");
    expect(screen.getAllByRole("button", { name: "convidar" })).toHaveLength(1);
  });
});
