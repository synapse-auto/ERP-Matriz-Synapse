"use client";

import { useEffect, useRef } from "react";
import data from "@emoji-mart/data";
import { Picker } from "emoji-mart";

import type { Textos } from "@/lib/config/schema";

type Props = {
  i18n: Textos["atendimentos"]["mensagem"]["acoes"]["seletor"];
  onEscolher: (emoji: string) => void;
};

export function nativoSelecionado(escolha: { native?: unknown }): string | null {
  return typeof escolha.native === "string" && escolha.native.length > 0 ? escolha.native : null;
}

/**
 * Web Component do emoji-mart (Preact interno). Sem peer React: o pacote nao declara
 * dependencia de React, entao o picker nao depende da versao 19 do CRM.
 * Dados versionados em `@emoji-mart/data`; `set: native` usa o Unicode do sistema.
 */
export function SeletorEmojiCompleto({ i18n, onEscolher }: Props) {
  const hospedeiro = useRef<HTMLDivElement>(null);
  const onEscolherRef = useRef(onEscolher);

  useEffect(() => {
    onEscolherRef.current = onEscolher;
  }, [onEscolher]);

  useEffect(() => {
    const raiz = hospedeiro.current;
    if (!raiz) return;

    // O CRM não segue prefers-color-scheme (o tema é a classe `dark` na raiz, ver
    // globals.css: `@custom-variant dark (&:is(.dark *))`). "auto" no emoji-mart lê o
    // esquema de cor do SISTEMA OPERACIONAL do usuário, que pode divergir do tema do CRM.
    const tema = document.documentElement.classList.contains("dark") ? "dark" : "light";

    const picker = new Picker({
      data,
      i18n,
      set: "native",
      theme: tema,
      previewPosition: "none",
      skinTonePosition: "search",
      dynamicWidth: true,
      onEmojiSelect: (escolha: { native?: unknown }) => {
        const nativo = nativoSelecionado(escolha);
        if (nativo) onEscolherRef.current(nativo);
      },
    });
    // O <em-emoji-picker> (custom element) define no próprio :host, dentro do shadow DOM,
    // `display: flex; width: min-content`. Mantemos o flex para que o `#root` e a região `.scroll`
    // recebam uma altura real; trocar por block faz o flex-grow perder a restrição e desabilita a
    // rolagem da lista (além de deixar os controles de categoria fora da área clicável).
    // A largura explícita preenche o popover e mantém as categorias acessíveis em telas estreitas.
    picker.style.display = "flex";
    picker.style.width = "100%";
    // O Positioner do Popover fornece --available-height para evitar que a janela seja cortada
    // pelo viewport. O fallback mantém o picker utilizável fora de um Popover (por exemplo, em
    // testes ou em uma composição futura).
    picker.style.height = "min(435px, var(--available-height, 100vh))";
    picker.style.minHeight = "0";
    raiz.replaceChildren(picker);

    return () => {
      raiz.replaceChildren();
    };
  }, [i18n]);

  return <div ref={hospedeiro} data-slot="seletor-emoji" />;
}
