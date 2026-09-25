import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const copiarTexto = vi.fn<(texto: string) => Promise<boolean>>();
vi.mock("@/lib/mensagens/copiar-texto", () => ({
  copiarTexto: (texto: string) => copiarTexto(texto),
}));

const buscarAtendimentoPorTelefone = vi.hoisted(() => vi.fn());
vi.mock("@/lib/atendimento/api", () => ({
  buscarAtendimentoPorTelefone: (telefone: string) => buscarAtendimentoPorTelefone(telefone),
}));

import { ErroDeApi } from "@/lib/api/errors";
import {
  ProvedorDeAberturaDeConversa,
  telefoneParaNovoContato,
} from "@/lib/atendimento/abrir-conversa-do-contato";
import type { CartaoAtendimento } from "@/lib/atendimento/types";

import { BolhaContato, type TextosDaBolhaContato } from "./bolha-contato";

const TEXTOS: TextosDaBolhaContato = {
  contato: "Contato compartilhado",
  contatoSemNome: "Contato sem nome",
  contatoSemTelefone: "Sem telefone no cartão",
  copiarTelefone: "Copiar número",
  ligarPara: "Ligar para {numero}",
  copiar: "Copiar",
  copiada: "Copiado",
  copiarErro: "Falhou",
};

function metadados(contatos: unknown[]): string {
  return JSON.stringify({ contatos });
}

describe("BolhaContato", () => {
  it("preserva vários contatos e todos os números, com ligar e copiar em cada número válido", () => {
    render(
      <BolhaContato
        midiaMetadados={metadados([
          {
            nome: "Arquiteta Exemplo",
            telefones: [
              { numero: "+55 61 3333-0000", tipo: "WORK" },
              { numero: "+55 61 98888-0000", waId: "5561988880000", tipo: "CELL" },
            ],
          },
          { nome: "Bruno Exemplo", telefones: [{ numero: "(61) 3000-0000" }] },
        ])}
        textos={TEXTOS}
      />,
    );

    expect(screen.getByText("Arquiteta Exemplo")).toBeInTheDocument();
    expect(screen.getByText("Bruno Exemplo")).toBeInTheDocument();
    expect(screen.getByText("+55 61 3333-0000")).toBeInTheDocument();
    expect(screen.getByText("+55 61 98888-0000")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ligar para +55 61 98888-0000" }))
      .toHaveAttribute("href", "tel:+5561988880000");
    expect(screen.getByRole("link", { name: "Ligar para (61) 3000-0000" }))
      .toHaveAttribute("href", "tel:6130000000");
    expect(screen.getAllByRole("link")).toHaveLength(3);
    expect(screen.getAllByRole("button", { name: "Copiar número" })).toHaveLength(3);
  });

  it("mostra contato sem telefone sem nenhuma ação de ligar ou copiar", () => {
    render(
      <BolhaContato midiaMetadados={metadados([{ nome: "Sem Telefone", telefones: [] }])} textos={TEXTOS} />,
    );

    expect(screen.getByText("Sem Telefone")).toBeInTheDocument();
    expect(screen.getByText("Sem telefone no cartão")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("não oferece ação para número que não é discável", () => {
    render(
      <BolhaContato
        midiaMetadados={metadados([{ nome: "Ramal", telefones: [{ numero: "ramal 12" }, { numero: "123" }] }])}
        textos={TEXTOS}
      />,
    );

    expect(screen.getByText("ramal 12")).toBeInTheDocument();
    expect(screen.getByText("123")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("copia o número exibido e anuncia o resultado", async () => {
    copiarTexto.mockResolvedValueOnce(true);
    render(
      <BolhaContato
        midiaMetadados={metadados([{ nome: "A", telefones: [{ numero: "+55 61 98888-0000" }] }])}
        textos={TEXTOS}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Copiar número" }));

    await waitFor(() => expect(screen.getByText("Copiado")).toBeInTheDocument());
    expect(copiarTexto).toHaveBeenCalledWith("+55 61 98888-0000");
  });

  it("anuncia falha de cópia em vez de silenciar", async () => {
    copiarTexto.mockResolvedValueOnce(false);
    render(
      <BolhaContato
        midiaMetadados={metadados([{ nome: "A", telefones: [{ numero: "+55 61 98888-0000" }] }])}
        textos={TEXTOS}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Copiar número" }));

    await waitFor(() => expect(screen.getByText("Falhou")).toBeInTheDocument());
  });

  it("com catálogo antigo, sem as chaves de contato, mostra o dado e usa a ação genérica", () => {
    render(
      <BolhaContato
        midiaMetadados={metadados([{ telefones: [{ numero: "+55 61 98888-0000" }] }])}
        textos={{ copiar: "Copiar", copiada: "Copiado", copiarErro: "Falhou" }}
      />,
    );

    // Sem nome e sem "contatoSemNome": o próprio número identifica o cartão.
    expect(screen.getAllByText("+55 61 98888-0000")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "Copiar" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "+55 61 98888-0000" })).toBeInTheDocument();
  });

  it("não inventa contato quando os metadados são ilegíveis", () => {
    render(<BolhaContato midiaMetadados="{nao e json" textos={TEXTOS} />);

    expect(screen.getByText("Contato compartilhado")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});

const TEXTOS_COM_ABERTURA: TextosDaBolhaContato = {
  ...TEXTOS,
  abrirConversa: "Abrir conversa",
  abrirConversaCom: "Abrir conversa com {numero}",
  procurandoConversa: "Procurando conversa…",
  conversaNaoEncontrada: "Nenhuma conversa acessível com este número.",
  iniciarNovoContato: "Iniciar novo contato",
  iniciarNovoContatoCom: "Iniciar novo contato com {numero}",
  telefoneInvalidoParaConversa: "Número inválido para conversa.",
  erroAbrirConversa: "Não foi possível verificar.",
  tentarAbrirConversaDeNovo: "Tentar de novo",
};

const CARTAO = { atendimentoId: "atendimento-9", leadId: "lead-9" } as CartaoAtendimento;

function renderComAbertura(contatos: unknown[]) {
  const abrirCartao = vi.fn();
  const iniciarNovoContato = vi.fn();
  render(
    <ProvedorDeAberturaDeConversa valor={{ abrirCartao, iniciarNovoContato }}>
      <BolhaContato midiaMetadados={metadados(contatos)} textos={TEXTOS_COM_ABERTURA} />
    </ProvedorDeAberturaDeConversa>,
  );
  return { abrirCartao, iniciarNovoContato };
}

describe("BolhaContato — abrir conversa (E211)", () => {
  beforeEach(() => {
    buscarAtendimentoPorTelefone.mockReset();
  });

  it("fora da tela de Atendimentos (sem provedor) não mostra a ação", () => {
    render(
      <BolhaContato
        midiaMetadados={metadados([{ nome: "A", telefones: [{ numero: "+55 61 98888-0000" }] }])}
        textos={TEXTOS_COM_ABERTURA}
      />,
    );

    expect(screen.queryByRole("button", { name: /Abrir conversa/ })).not.toBeInTheDocument();
  });

  it("oferece uma ação por número válido, com rótulo que identifica o número; nenhuma para inválido", () => {
    renderComAbertura([
      { nome: "Arquiteta", telefones: [{ numero: "+55 61 98888-0000" }, { numero: "ramal 12" }, { numero: "(61) 3000-0000" }] },
      { nome: "Sem telefone", telefones: [] },
    ]);

    expect(screen.getByRole("button", { name: "Abrir conversa com +55 61 98888-0000" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Abrir conversa com (61) 3000-0000" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /Abrir conversa/ })).toHaveLength(2);
  });

  it("abre a conversa autorizada pelo backend, usando o waId quando existe", async () => {
    buscarAtendimentoPorTelefone.mockResolvedValue(CARTAO);
    const { abrirCartao, iniciarNovoContato } = renderComAbertura([
      { nome: "A", telefones: [{ numero: "+55 61 98888-0000", waId: "5561988880000" }] },
    ]);

    fireEvent.click(screen.getByRole("button", { name: "Abrir conversa com +55 61 98888-0000" }));

    await waitFor(() => expect(abrirCartao).toHaveBeenCalledWith(CARTAO));
    expect(buscarAtendimentoPorTelefone).toHaveBeenCalledWith("5561988880000");
    expect(iniciarNovoContato).not.toHaveBeenCalled();
  });

  it("sem conversa acessível, avisa e só oferece novo contato explícito com nome e telefone preenchidos", async () => {
    buscarAtendimentoPorTelefone.mockResolvedValue(null);
    const { abrirCartao, iniciarNovoContato } = renderComAbertura([
      { nome: "Maria Silva", telefones: [{ numero: "+55 61 98888-0000" }] },
    ]);

    fireEvent.click(screen.getByRole("button", { name: "Abrir conversa com +55 61 98888-0000" }));

    expect(await screen.findByText("Nenhuma conversa acessível com este número.")).toBeInTheDocument();
    expect(abrirCartao).not.toHaveBeenCalled();
    expect(iniciarNovoContato).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Iniciar novo contato com +55 61 98888-0000" }));
    expect(iniciarNovoContato).toHaveBeenCalledWith({ nome: "Maria Silva", telefone: "61988880000" });
  });

  it("número recusado pelo backend (400) mostra erro e não oferece novo contato", async () => {
    buscarAtendimentoPorTelefone.mockRejectedValue(new ErroDeApi(400, null, "telefone inválido"));
    renderComAbertura([{ nome: "A", telefones: [{ numero: "+55 61 98888-0000" }] }]);

    fireEvent.click(screen.getByRole("button", { name: /Abrir conversa/ }));

    expect(await screen.findByText("Número inválido para conversa.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Iniciar novo contato/ })).not.toBeInTheDocument();
  });

  it("falha de rede avisa e permite tentar de novo", async () => {
    buscarAtendimentoPorTelefone
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce(CARTAO);
    const { abrirCartao } = renderComAbertura([{ nome: "A", telefones: [{ numero: "+55 61 98888-0000" }] }]);

    fireEvent.click(screen.getByRole("button", { name: /Abrir conversa/ }));
    expect(await screen.findByText("Não foi possível verificar.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Abrir conversa/ })).toHaveTextContent("Tentar de novo");

    fireEvent.click(screen.getByRole("button", { name: /Abrir conversa/ }));
    await waitFor(() => expect(abrirCartao).toHaveBeenCalledWith(CARTAO));
  });

  it("enquanto procura, o botão fica ocupado e um segundo clique não repete a busca", async () => {
    let resolver: (valor: CartaoAtendimento | null) => void = () => undefined;
    buscarAtendimentoPorTelefone.mockReturnValue(new Promise((resolve) => { resolver = resolve; }));
    renderComAbertura([{ nome: "A", telefones: [{ numero: "+55 61 98888-0000" }] }]);

    const botao = screen.getByRole("button", { name: /Abrir conversa/ });
    fireEvent.click(botao);
    fireEvent.click(botao);

    expect(botao).toBeDisabled();
    expect(botao).toHaveAttribute("aria-busy", "true");
    expect(buscarAtendimentoPorTelefone).toHaveBeenCalledTimes(1);
    resolver(null);
    expect(await screen.findByText("Nenhuma conversa acessível com este número.")).toBeInTheDocument();
  });

  it("com vários contatos e telefones, cada número tem estado próprio", async () => {
    buscarAtendimentoPorTelefone.mockResolvedValue(null);
    renderComAbertura([
      { nome: "A", telefones: [{ numero: "+55 61 98888-0000" }, { numero: "+55 61 97777-0000" }] },
      { nome: "B", telefones: [{ numero: "(61) 3000-0000" }] },
    ]);

    fireEvent.click(screen.getByRole("button", { name: "Abrir conversa com +55 61 97777-0000" }));

    expect(await screen.findAllByText("Nenhuma conversa acessível com este número.")).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: /Iniciar novo contato/ })).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Iniciar novo contato com +55 61 97777-0000" })).toBeInTheDocument();
  });

  it("nome longo fica visível com quebra e com o nome completo no título", () => {
    const nome = "Maria Aparecida dos Santos Vasconcelos Albuquerque de Oliveira Filha";
    renderComAbertura([{ nome, telefones: [{ numero: "+55 61 98888-0000" }] }]);

    expect(screen.getByTitle(nome)).toHaveClass("break-words", "line-clamp-2");
  });
});

describe("telefoneParaNovoContato", () => {
  it.each([
    ["+55 61 98888-0000", "61988880000"],
    ["5561988880000", "61988880000"],
    ["(61) 3000-0000", "6130000000"],
    ["+1 415 555 0100", ""],
    ["123", ""],
  ])("%s → %s", (entrada, esperado) => {
    expect(telefoneParaNovoContato(entrada)).toBe(esperado);
  });
});
