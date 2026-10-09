import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoInformacoesChatbot, MensagemResposta } from "@/lib/atendimento/types";
import { textosReais } from "@/test/textos-reais";

vi.mock("@tanstack/react-virtual", () => ({
  useVirtualizer: (opcoes: { count: number }) => ({
    getTotalSize: () => opcoes.count * 80,
    getVirtualItems: () => Array.from({ length: opcoes.count }, (_, index) => ({ index, start: index * 80 })),
    measureElement: vi.fn(),
    scrollToIndex: vi.fn(),
  }),
}));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));
vi.mock("@/lib/atendimento/api", () => ({ obterMensagem: vi.fn() }));
vi.mock("./bolha-mensagem", () => ({
  BolhaMensagem: ({ mensagem }: { mensagem: MensagemResposta }) => (
    <div data-slot="bolha-mensagem">{mensagem.conteudo}</div>
  ),
}));

import {
  cartoesDoTrechoCarregado,
  intercalarCartoes,
  ListaMensagens,
} from "./lista-mensagens";

function mensagem(id: string, enviadoEm: string, atendimentoId = "a-1"): MensagemResposta {
  return {
    id,
    atendimentoId,
    remetenteTipo: "LEAD",
    remetenteId: null,
    remetenteNome: null,
    tipo: "TEXTO",
    conteudo: `texto ${id}`,
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega: "ENTREGUE",
    erroEntrega: null,
    enviadoEm,
  };
}

function cartao(id: string, registradoEm: string, conteudo = `info ${id}`): CartaoInformacoesChatbot {
  return { id, atendimentoId: "a-1", conteudo, origem: "AUTOMACAO", registradoEm };
}

const propsBase = {
  carregando: false,
  onDefinirReacao: vi.fn(),
  onRemoverReacao: vi.fn(),
  temMais: false,
  carregandoMais: false,
  onCarregarMais: vi.fn(),
  buscaAberta: false,
  canalTipo: "WHATSAPP",
  atendenteId: null,
  atendenteNome: null,
};

describe("intercalarCartoes", () => {
  it("coloca o card entre as mensagens, pela ordem cronológica", () => {
    const linhas = intercalarCartoes(
      [mensagem("m1", "2026-10-08T14:00:00Z"), mensagem("m3", "2026-10-08T14:10:00Z")],
      [cartao("c1", "2026-10-08T14:05:00Z")],
    );

    expect(linhas.map((linha) => linha.chave)).toEqual(["m1", "cartao-c1", "m3"]);
  });

  it("compara instantes e não texto: precisão diferente de fração de segundo não desordena", () => {
    const linhas = intercalarCartoes(
      [mensagem("m1", "2026-10-08T14:00:00Z"), mensagem("m2", "2026-10-08T14:00:01Z")],
      [cartao("c1", "2026-10-08T14:00:00.500000Z")],
    );

    expect(linhas.map((linha) => linha.chave)).toEqual(["m1", "cartao-c1", "m2"]);
  });

  it("no empate a mensagem vem antes do card, e card depois da última mensagem fica no fim", () => {
    const linhas = intercalarCartoes(
      [mensagem("m1", "2026-10-08T14:00:00Z")],
      [cartao("c1", "2026-10-08T14:00:00Z"), cartao("c2", "2026-10-08T15:00:00Z")],
    );

    expect(linhas.map((linha) => linha.chave)).toEqual(["m1", "cartao-c1", "cartao-c2"]);
  });

  it("a mensagem anterior ignora cards: troca de atendimento continua sendo entre mensagens", () => {
    const linhas = intercalarCartoes(
      [mensagem("m1", "2026-10-08T14:00:00Z", "a-1"), mensagem("m2", "2026-10-08T14:10:00Z", "a-2")],
      [cartao("c1", "2026-10-08T14:05:00Z")],
    );

    const segunda = linhas.find((linha) => linha.chave === "m2");
    expect(segunda?.tipo === "mensagem" && segunda.anteriorMensagem?.id).toBe("m1");
  });

  it("sem cards a saída é exatamente a lista de mensagens", () => {
    const linhas = intercalarCartoes([mensagem("m1", "2026-10-08T14:00:00Z")], []);

    expect(linhas.map((linha) => linha.chave)).toEqual(["m1"]);
  });
});

describe("cartoesDoTrechoCarregado", () => {
  const mensagens = [mensagem("m1", "2026-10-08T14:00:00Z"), mensagem("m2", "2026-10-08T14:10:00Z")];
  const antigo = cartao("antigo", "2026-10-07T09:00:00Z");
  const recente = cartao("recente", "2026-10-08T14:05:00Z");

  it("com mais páginas a carregar, esconde o card anterior ao trecho já carregado", () => {
    expect(cartoesDoTrechoCarregado([antigo, recente], mensagens, true)).toEqual([recente]);
  });

  it("sem mais páginas, mostra todos", () => {
    expect(cartoesDoTrechoCarregado([antigo, recente], mensagens, false)).toEqual([antigo, recente]);
  });

  it("com mais páginas e nenhuma mensagem carregada ainda, não mostra card solto", () => {
    expect(cartoesDoTrechoCarregado([recente], [], true)).toEqual([]);
  });
});

describe("ListaMensagens com cards do chatbot", () => {
  it("mostra o card na sequência das mensagens e sem ele nada muda", () => {
    const mensagens = [mensagem("m1", "2026-10-08T14:00:00Z"), mensagem("m2", "2026-10-08T14:10:00Z")];
    const { container, rerender } = render(<ListaMensagens {...propsBase} mensagens={mensagens} />);
    expect(container.querySelector('[data-slot="cartao-informacoes-chatbot"]')).toBeNull();

    rerender(
      <ListaMensagens
        {...propsBase}
        mensagens={mensagens}
        cartoes={[cartao("c1", "2026-10-08T14:05:00Z", "Nome: Maria")]}
      />,
    );

    const ordem = Array.from(
      container.querySelectorAll('[data-slot="bolha-mensagem"], [data-slot="cartao-informacoes-chatbot"]'),
    ).map((no) => no.getAttribute("data-slot"));
    expect(ordem).toEqual(["bolha-mensagem", "cartao-informacoes-chatbot", "bolha-mensagem"]);
    expect(screen.getByText("Nome: Maria")).toBeInTheDocument();
    expect(screen.getByText(textosReais.atendimentos.informacoesChatbot.titulo)).toBeInTheDocument();
  });

  it("um atendimento só com card (sem mensagens) não cai no estado vazio", () => {
    render(<ListaMensagens {...propsBase} mensagens={[]} cartoes={[cartao("c1", "2026-10-08T14:05:00Z")]} />);

    expect(screen.queryByText(textosReais.estados.vazio)).toBeNull();
    expect(screen.getByText(textosReais.atendimentos.informacoesChatbot.titulo)).toBeInTheDocument();
  });
});
