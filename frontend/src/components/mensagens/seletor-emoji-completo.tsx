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
    // `display: flex; width: min-content` — encolhe para o conteúdo que ELE MESMO gerou, em vez
    // de preencher o popover. dynamicWidth usa exatamente essa largura (getBoundingClientRect do
    // elemento) para calcular quantos emojis cabem por linha: o resultado é um equilíbrio que
    // convergiu errado (grade de 5 colunas em vez de 9 nos 352px do popover), não uma medição
    // única no lugar errado. Sobrescrever o :host por fora força o elemento a preencher o host —
    // style inline tem precedência sobre a regra :host do shadow root.
    picker.style.display = "block";
    picker.style.width = "100%";
    raiz.replaceChildren(picker);

    return () => {
      raiz.replaceChildren();
    };
  }, [i18n]);

  return <div ref={hospedeiro} data-slot="seletor-emoji" />;
}
