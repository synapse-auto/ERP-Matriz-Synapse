import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

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
      },
    },
  }),
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
      />,
    );

    expect(screen.getByText("Preciso de um orçamento.")).toBeInTheDocument();
    expect(
      screen.queryByText("Nome que não deve aparecer"),
    ).not.toBeInTheDocument();
  });

  it("mostra o nome conhecido do atendente dentro da bolha enviada", () => {
    render(
      <BolhaMensagem mensagem={mensagem({})} nomeDoRemetente="Jardel Lima" />,
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
      />,
    );

    expect(document.querySelector('[data-slot="player-audio"]')).toBeInTheDocument();
    expect(document.querySelector("audio[controls]")).toBeNull();
    expect(screen.getByRole("button", { name: "Reproduzir áudio" })).toBeInTheDocument();
  });
});
