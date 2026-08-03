import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

const mutateMidia = vi.fn();
const mutateTexto = vi.fn();

vi.mock("@/lib/atendimento/use-enviar-mensagem", () => ({
  useEnviarMensagem: () => ({ mutate: mutateTexto, isPending: false }),
}));

vi.mock("@/lib/atendimento/use-enviar-midia", () => ({
  useEnviarMidia: () => ({
    mutate: mutateMidia,
    isPending: false,
    isError: false,
    error: null,
  }),
}));

vi.mock("@/lib/atendimento/janela-24h", () => ({
  janelaTextoLivreAberta: () => true,
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      composer: {
        placeholder: "Digite uma mensagem...",
        enviar: "Enviar",
        anexo: "Anexo",
        anexoIndisponivel: "indisponivel",
        anexoSelecionar: "Escolher arquivo",
        anexoRemover: "Remover anexo",
        anexoEnviando: "Enviando anexo...",
        anexoErro: "Falha ao enviar o anexo.",
        anexoLegendaPlaceholder: "Adicionar legenda (opcional)",
        anexoTipoNaoPermitido: "Tipo nao aceito.",
        anexoExcedeuLimite: "Excede o limite.",
        emoji: "Emoji",
        janelaFechadaTitulo: "",
        janelaFechadaDescricao: "",
        semTemplates: "",
      },
      mensagens: { reenviar: "Reenviar" },
    },
  }),
}));

import { Composer } from "./composer";

const conversa: CartaoAtendimento = {
  atendimentoId: "at-1",
  leadId: "lead-1",
  leadNome: "Cliente Teste",
  leadFotoUrl: null,
  leadEmpresa: null,
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: "at-usr",
  atendenteNome: "Ana",
  ultimaMensagemPreview: null,
  ultimaMensagemRemetenteTipo: null,
  ultimaMensagemEm: null,
  ultimaMensagemDoLeadEm: new Date().toISOString(),
};

function arquivoFake(nome: string, tipo: string): File {
  return new File(["conteudo"], nome, { type: tipo });
}

describe("Composer — anexo", () => {
  it("mostra o chip de preview com nome e tamanho ao selecionar um arquivo", () => {
    render(<Composer conversa={conversa} />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;

    fireEvent.change(input, { target: { files: [arquivoFake("foto.png", "image/png")] } });

    expect(screen.getByText("foto.png")).toBeInTheDocument();
  });

  it("remove o arquivo selecionado ao clicar em remover", () => {
    render(<Composer conversa={conversa} />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [arquivoFake("foto.png", "image/png")] } });
    expect(screen.getByText("foto.png")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Remover anexo"));

    expect(screen.queryByText("foto.png")).not.toBeInTheDocument();
  });

  it("envia o arquivo selecionado com a legenda digitada ao clicar em enviar", () => {
    render(<Composer conversa={conversa} />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [arquivoFake("orcamento.pdf", "application/pdf")] } });

    fireEvent.change(screen.getByPlaceholderText("Adicionar legenda (opcional)"), {
      target: { value: "segue o orçamento" },
    });
    fireEvent.click(screen.getByLabelText("Enviar"));

    expect(mutateMidia).toHaveBeenCalledWith(
      expect.objectContaining({
        atendimentoId: "at-1",
        leadId: "lead-1",
        legenda: "segue o orçamento",
      }),
      expect.anything(),
    );
    expect(mutateTexto).not.toHaveBeenCalled();
  });
});
