import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      canais: { whatsapp: "WhatsApp" },
      cartao: {
        semAtendente: "Sem atendente",
        naoLidas: "{quantidade} mensagens não lidas",
      },
    },
  }),
}));

import { CartaoConversa } from "./cartao-conversa";

const cartao: CartaoAtendimento = {
  atendimentoId: "at-1",
  leadId: "lead-1",
  leadNome: "Cliente E12",
  leadFotoUrl: null,
  leadEmpresa: null,
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: null,
  atendenteNome: null,
  ultimaMensagemPreview: null,
  ultimaMensagemRemetenteTipo: null,
  ultimaMensagemEm: null,
  ultimaMensagemDoLeadEm: null,
  naoLidas: 3,
};

describe("CartaoConversa — RN-CRM-05", () => {
  it("um clique abre a conversa sem exibir overlay da ficha", () => {
    const abrirAtendimento = vi.fn();
    render(
      <CartaoConversa
        cartao={cartao}
        selecionado={false}
        onAbrirAtendimento={abrirAtendimento}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /Cliente E12/ }));

    expect(abrirAtendimento).toHaveBeenCalledOnce();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText("3")).toHaveAttribute(
      "title",
      "3 mensagens não lidas",
    );
  });

  it("mostra empresa, canal e etapa no rodapé, com neutro quando a etapa não tem cor", () => {
    render(
      <CartaoConversa
        cartao={{
          ...cartao,
          leadEmpresa: "Vidraçaria Cristal Clara",
          etapaNome: "Orçamento",
          etapaCor: null,
        }}
        selecionado={false}
        onAbrirAtendimento={vi.fn()}
      />,
    );

    expect(screen.getByText("Vidraçaria Cristal Clara")).toBeInTheDocument();
    expect(screen.getByText("Orçamento")).toHaveClass("bg-muted");
    expect(screen.getByTitle("WhatsApp")).toBeInTheDocument();
  });

  it("não cria empresa, etapa ou canal quando o backend não fornece os dados", () => {
    render(
      <CartaoConversa
        cartao={{ ...cartao, canalTipo: null }}
        selecionado={false}
        onAbrirAtendimento={vi.fn()}
      />,
    );

    expect(screen.queryByText("Vidraçaria Cristal Clara")).not.toBeInTheDocument();
    expect(screen.queryByTitle("WhatsApp")).not.toBeInTheDocument();
  });
});
