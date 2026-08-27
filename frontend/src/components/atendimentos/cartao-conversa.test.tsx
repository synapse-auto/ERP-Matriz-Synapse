import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

import type { CartaoAtendimento, CartaoEquipeInterna } from "@/lib/atendimento/types";
import { tomDoAvatar } from "@/components/ui/avatar-iniciais";

vi.mock("@/components/ui/avatar", () => {
  const Container = ({ children, ...props }: { children: ReactNode; [key: string]: unknown }) => (
    <div {...props}>{children}</div>
  );
  return {
    Avatar: Container,
    AvatarFallback: Container,
    // eslint-disable-next-line @next/next/no-img-element
    AvatarImage: (props: { [key: string]: unknown }) => <img alt="" {...props} />,
  };
});

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    chatInterno: { titulo: "Chat interno" },
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
    expect(screen.queryByText("—")).not.toBeInTheDocument();
  });

  it("usa tom determinístico pelo id do lead e deixa a foto vencer", () => {
    const { rerender } = render(
      <CartaoConversa
        cartao={cartao}
        selecionado={false}
        onAbrirAtendimento={vi.fn()}
      />,
    );
    const fallback = screen.getByText("CE");
    expect(fallback).toHaveStyle({ backgroundColor: tomDoAvatar("lead-1") });

    rerender(
      <CartaoConversa
        cartao={{ ...cartao, leadFotoUrl: "https://cdn.example/foto.webp" }}
        selecionado={false}
        onAbrirAtendimento={vi.fn()}
      />,
    );
    expect(screen.getByRole("img", { name: "Cliente E12" })).toHaveAttribute(
      "src",
      "https://cdn.example/foto.webp",
    );
  });

  it("mantém o card interno na mesma densidade, com indicador e sem dados de lead", () => {
    const interno: CartaoEquipeInterna = {
      tipo: "EQUIPE_INTERNA",
      atendimentoId: null,
      conversaId: "conversa-1",
      nome: "Equipe comercial",
      avatarUrl: null,
      identificadorVisual: "conversa-1",
      ultimaMensagemPreview: "Vamos revisar a proposta",
      ultimaMensagemEm: "2026-08-16T12:45:00Z",
      naoLidas: 2,
      participantes: "Ana, Bruno",
      tipoConversa: "GRUPO",
    };
    render(<CartaoConversa cartao={interno} selecionado onAbrirAtendimento={vi.fn()} />);
    expect(screen.getByRole("button", { name: /Equipe comercial/ })).toHaveAttribute("aria-current", "true");
    expect(screen.getByText("Chat interno")).toBeInTheDocument();
    expect(screen.getByText("Vamos revisar a proposta")).toBeInTheDocument();
    expect(screen.queryByText("WhatsApp")).not.toBeInTheDocument();
  });
});
