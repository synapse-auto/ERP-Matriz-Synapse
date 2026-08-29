import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

import type { MensagemResposta } from "@/lib/atendimento/types";

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      media: { imagem: "Imagem", audio: "Áudio", reproduzir: "Reproduzir áudio", pausar: "Pausar áudio", posicao: "Posição do áudio", documento: "Documento", baixar: "Baixar", botoes: "Opções", lista: "Lista" },
      mensagem: {
        status: {
          pendente: "Enviando",
          enviado: "Enviado",
          entregue: "Entregue",
          lido: "Lido",
          falhou: "Falha ao enviar",
        },
        reenviar: "Reenviar",
        acoes: { abrir: "Ações da mensagem", titulo: "Ações", copiar: "Copiar", copiada: "ok", copiarErro: "erro", reagir: "Reagir com {emoji}", reacaoQuantidade: "{emoji}, {quantidade}", reacaoMinha: "{emoji}, {quantidade}, sua reação", maisEmojis: "Mais emojis", seletorTitulo: "Escolher emoji", seletorFechar: "Fechar", reacaoErro: "erro reação", responder: "Responder", encaminhar: "Encaminhar", rapidas: ["👍", "❤️", "😂", "😮", "😢", "🙏"], seletor: { search: "Buscar", searchNoResults: "Nenhum", pick: "Escolha", addCustom: "Custom", categories: { activity: "A", custom: "C", flags: "F", foods: "Fo", frequent: "R", nature: "N", objects: "O", people: "P", places: "V", search: "B", symbols: "S" }, skins: { choose: "Tom", 1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6" } } },
        citacao: { resposta: "Respondendo a {autor}", encaminhamento: "Encaminhada", cancelar: "Cancelar resposta", origemIndisponivel: "Mensagem original indisponível", imagem: "Foto", audio: "Áudio", documento: "Documento" },
      },
    },
  }),
}));

vi.mock("@/components/mensagens/interacao-mensagem", () => ({
  InteracaoMensagem: ({ children }: { children: ReactNode }) => children,
}));

import { BolhaMensagem } from "./bolha-mensagem";

function mensagem(parcial: Partial<MensagemResposta>): MensagemResposta {
  return {
    id: "mensagem-1",
    remetenteTipo: "ATENDENTE",
    remetenteId: "usuario-1",
    remetenteNome: "Jardel Lima",
    tipo: "TEXTO",
    conteudo: "Olá",
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega: "LIDO",
    enviadoEm: "2026-08-16T12:00:00Z",
    ...parcial,
  };
}

describe("BolhaMensagem", () => {
  it("mostra balão recebido com presença visual e sem autoria inventada", () => {
    render(
      <BolhaMensagem
        mensagem={mensagem({
          remetenteTipo: "LEAD",
          remetenteId: null,
          remetenteNome: null,
          conteudo: "Preciso de um orçamento.",
        })}
        nomeDoRemetente="Nome que não deve aparecer"
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      />,
    );

    expect(screen.getByText("Preciso de um orçamento.")).toBeInTheDocument();
    expect(
      screen.queryByText("Nome que não deve aparecer"),
    ).not.toBeInTheDocument();
  });

  it("mostra o nome conhecido do atendente dentro da bolha enviada", () => {
    render(
      <BolhaMensagem mensagem={mensagem({})} nomeDoRemetente="Jardel Lima" onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} />,
    );

    expect(screen.getByText("Jardel Lima")).toBeInTheDocument();
  });

  it("mostra documento com nome, tamanho e descrição", () => {
    render(
      <BolhaMensagem
        mensagem={mensagem({
          tipo: "DOCUMENTO",
          conteudo: null,
          midiaUrl: "https://example.test/orcamento.pdf",
          midiaMetadados: JSON.stringify({
            nome: "Orcamento_2231.pdf",
            tamanho: 1_572_864,
            legenda: "Box temperado 8mm · 2 vãos",
          }),
        })}
        nomeDoRemetente="Jardel Lima"
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      />,
    );

    expect(screen.getByText("Orcamento_2231.pdf")).toBeInTheDocument();
    expect(screen.getByText("1.5 MB")).toBeInTheDocument();
    expect(screen.getByText("Box temperado 8mm · 2 vãos")).toBeInTheDocument();
  });

  it("mostra imagem com legenda", () => {
    render(
      <BolhaMensagem
        mensagem={mensagem({
          remetenteTipo: "LEAD",
          remetenteId: null,
          tipo: "IMAGEM",
          conteudo: null,
          midiaUrl: "https://example.test/medida.jpg",
          midiaMetadados: JSON.stringify({
            legenda: "Foto da medida da suíte",
          }),
        })}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("img", { name: "Foto da medida da suíte" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Foto da medida da suíte")).toBeInTheDocument();
  });

  it("mostra títulos e descrições das opções interativas", () => {
    render(
      <BolhaMensagem
        mensagem={mensagem({
          tipo: "BOTOES",
          conteudo: "Escolha uma opção",
          opcoes: JSON.stringify([{ id: "sim", titulo: "Sim", descricao: "Confirmar" }]),
        })}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      />,
    );

    expect(screen.getByText("Escolha uma opção")).toBeInTheDocument();
    expect(screen.getByText("Sim")).toBeInTheDocument();
    expect(screen.getByText("Confirmar")).toBeInTheDocument();
  });

  it("mostra o player compacto no áudio enviado, sem o controle nativo", () => {
    render(
      <BolhaMensagem
        mensagem={mensagem({
          tipo: "AUDIO",
          conteudo: null,
          midiaUrl: "https://example.test/voz.m4a",
        })}
        nomeDoRemetente="Jardel Lima"
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      />,
    );

    expect(document.querySelector('[data-slot="player-audio"]')).toBeInTheDocument();
    expect(document.querySelector("audio[controls]")).toBeNull();
    expect(screen.getByRole("button", { name: "Reproduzir áudio" })).toBeInTheDocument();
  });

  it("mostra a citação persistida com autor e prévia, sem HTML da origem", () => {
    render(
      <BolhaMensagem
        mensagem={mensagem({
          conteudo: "combinado",
          citacao: {
            origemId: "origem-1",
            tipoReferencia: "RESPOSTA",
            autor: "Maria",
            tipoConteudo: "TEXTO",
            previa: "<script>x</script> medida do vão",
          },
        })}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      />,
    );

    expect(screen.getByText("Respondendo a Maria")).toBeInTheDocument();
    expect(screen.getByText("<script>x</script> medida do vão")).toBeInTheDocument();
    expect(document.querySelector("script")).toBeNull();
  });

  it("cai no texto de origem indisponível quando a prévia veio vazia", () => {
    render(
      <BolhaMensagem
        mensagem={mensagem({
          conteudo: "ok",
          citacao: {
            origemId: "origem-sumiu",
            tipoReferencia: "RESPOSTA",
            autor: "",
            tipoConteudo: "TEXTO",
            previa: "",
          },
        })}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      />,
    );

    expect(screen.getByText("Respondendo a Mensagem original indisponível")).toBeInTheDocument();
    expect(screen.getByText("Mensagem original indisponível")).toBeInTheDocument();
  });
});
