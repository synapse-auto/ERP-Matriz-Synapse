import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoInformacoesChatbot } from "@/lib/atendimento/types";
import { textosReais } from "@/test/textos-reais";

vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));

import {
  alturaEstimadaDoCartao,
  CARACTERES_VISIVEIS_RECOLHIDO,
  CartaoInformacoesChatbot as Cartao,
  conteudoPrecisaRecolher,
  LINHAS_VISIVEIS_RECOLHIDO,
} from "./cartao-informacoes-chatbot";

const textos = textosReais.atendimentos.informacoesChatbot;

function cartao(conteudo: string): CartaoInformacoesChatbot {
  return {
    id: "c-1",
    atendimentoId: "a-1",
    conteudo,
    origem: "AUTOMACAO",
    registradoEm: "2026-10-08T14:05:00Z",
  };
}

describe("CartaoInformacoesChatbot", () => {
  it("mostra título, origem da automação e conteúdo vindos do catálogo", () => {
    render(<Cartao cartao={cartao("Nome: Maria\nInteresse: avaliação")} />);

    expect(screen.getByText(textos.titulo)).toBeInTheDocument();
    expect(screen.getByText(textos.origem)).toBeInTheDocument();
    expect(screen.getByRole("region", { name: textos.titulo })).toHaveAttribute("data-origem", "AUTOMACAO");
    const hora = document.querySelector("time");
    expect(hora).toHaveAttribute("datetime", "2026-10-08T14:05:00Z");
    expect(hora?.textContent).toMatch(/^\d{2}:\d{2}$/);
  });

  it("segue o modelo: título e subtítulo do catálogo, texto corrido e a hora no canto inferior direito", () => {
    const { container } = render(<Cartao cartao={cartao("Texto do resumo")} />);

    expect(textos.titulo).toBe("Resumo da IA para o atendimento");
    expect(textos.origem).toBe("Transferência para atendimento humano");
    expect(container.querySelector("time")?.parentElement?.className).toContain("text-right");
  });

  it("preserva as quebras de linha sem interpretar nada como HTML", () => {
    const perigoso = 'linha 1\n<img src=x onerror="window.__invadido = true">\n<b>negrito</b>';

    const { container } = render(<Cartao cartao={cartao(perigoso)} />);

    const paragrafo = container.querySelector("#informacoes-chatbot-c-1");
    expect(paragrafo?.textContent).toBe(perigoso);
    expect(paragrafo?.className).toContain("whitespace-pre-wrap");
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("b")).toBeNull();
    expect((window as unknown as { __invadido?: boolean }).__invadido).toBeUndefined();
  });

  it("não oferece envio, reenvio, resposta nem reação: é um registro interno", () => {
    render(<Cartao cartao={cartao("Nome: Maria")} />);

    expect(screen.queryAllByRole("button")).toHaveLength(0);
  });

  it("conteúdo curto aparece inteiro, sem botão de expandir", () => {
    render(<Cartao cartao={cartao("curto")} />);

    expect(screen.queryByRole("button", { name: textos.verMais })).toBeNull();
    expect(conteudoPrecisaRecolher("curto")).toBe(false);
  });

  it("conteúdo longo nasce recolhido e expande e recolhe pelo botão", () => {
    const longo = "x".repeat(CARACTERES_VISIVEIS_RECOLHIDO + 1);
    const { container } = render(<Cartao cartao={cartao(longo)} />);
    const paragrafo = container.querySelector("#informacoes-chatbot-c-1");

    expect(paragrafo).toHaveAttribute("data-recolhido", "true");
    const verMais = screen.getByRole("button", { name: textos.verMais });
    expect(verMais).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(verMais);

    expect(paragrafo).not.toHaveAttribute("data-recolhido");
    expect(screen.getByRole("button", { name: textos.verMenos })).toHaveAttribute("aria-expanded", "true");

    fireEvent.click(screen.getByRole("button", { name: textos.verMenos }));
    expect(paragrafo).toHaveAttribute("data-recolhido", "true");
  });

  it("estima a altura pelo texto visível: cresce com as linhas e para de crescer no recolhido", () => {
    const curto = alturaEstimadaDoCartao("uma linha");
    const cinco = alturaEstimadaDoCartao(Array.from({ length: 5 }, () => "l").join("\n"));
    const longo = alturaEstimadaDoCartao(Array.from({ length: 40 }, () => "l").join("\n"));
    const maisLongo = alturaEstimadaDoCartao(Array.from({ length: 80 }, () => "l").join("\n"));

    expect(cinco).toBeGreaterThan(curto);
    expect(longo).toBeGreaterThan(cinco);
    expect(maisLongo).toBe(longo);
    expect(alturaEstimadaDoCartao("x".repeat(140))).toBeGreaterThan(curto);
  });

  it("muitas linhas curtas também recolhem", () => {
    const muitasLinhas = Array.from({ length: LINHAS_VISIVEIS_RECOLHIDO + 1 }, (_, i) => `l${i}`).join("\n");

    expect(conteudoPrecisaRecolher(muitasLinhas)).toBe(true);
    expect(conteudoPrecisaRecolher(Array.from({ length: LINHAS_VISIVEIS_RECOLHIDO }, () => "l").join("\n"))).toBe(false);
  });
});
