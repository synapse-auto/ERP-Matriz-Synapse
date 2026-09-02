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
 *
 * Tema e largura são derivados no momento da construção (ver efeito abaixo), não recalculados
 * depois: o picker é remontado do zero a cada abertura do popover/dialog que o hospeda, então
 * não precisa reagir a uma troca de tema com ele já aberto.
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

    // Guarda contra construção dupla: o browser pode entregar mais um callback do
    // ResizeObserver em voo antes do disconnect() surtir efeito (a fila de notificações já
    // enfileirada não é cancelada por disconnect()); sem isto, um segundo disparo trocaria o
    // picker por uma instância nova no meio da interação do usuário.
    let construido = false;
    const construir = () => {
      if (construido) return;
      construido = true;
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
      raiz.replaceChildren(picker);
    };

    // dynamicWidth mede a largura do host NO INSTANTE da construção e nunca remede depois.
    // O popover ainda pode estar no primeiro passe de posicionamento (fora de tela / largura
    // zero) quando este efeito roda; construir ali entrega uma grade estreita que nunca mais
    // cresce, com o resto do popover aparecendo como faixa vazia ao lado. Espera a largura
    // real (via ResizeObserver no próprio host) antes de instanciar o picker.
    if (typeof ResizeObserver === "undefined" || raiz.offsetWidth > 0) {
      construir();
      return () => {
        raiz.replaceChildren();
      };
    }

    const observer = new ResizeObserver(() => {
      if (raiz.offsetWidth > 0) {
        observer.disconnect();
        construir();
      }
    });
    observer.observe(raiz);

    return () => {
      observer.disconnect();
      raiz.replaceChildren();
    };
  }, [i18n]);

  return <div ref={hospedeiro} data-slot="seletor-emoji" />;
}
