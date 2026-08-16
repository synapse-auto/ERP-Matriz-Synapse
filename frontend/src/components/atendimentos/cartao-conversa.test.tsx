import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      canais: { whatsapp: "WhatsApp" },
      cartao: { semAtendente: "Sem atendente" },
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
  });
});
