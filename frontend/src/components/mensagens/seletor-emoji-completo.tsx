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
    const picker = new Picker({
      data,
      i18n,
      set: "native",
      theme: "light",
      previewPosition: "none",
      skinTonePosition: "search",
      dynamicWidth: true,
      onEmojiSelect: (escolha: { native?: unknown }) => {
        const nativo = nativoSelecionado(escolha);
        if (nativo) onEscolherRef.current(nativo);
      },
    });
    raiz.replaceChildren(picker);
    return () => {
      raiz.replaceChildren();
    };
  }, [i18n]);

  return <div ref={hospedeiro} data-slot="seletor-emoji" />;
}
