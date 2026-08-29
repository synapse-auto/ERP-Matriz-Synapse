"use client";

import data from "@emoji-mart/data";
import Picker from "@emoji-mart/react";

import type { Textos } from "@/lib/config/schema";

type Props = {
  i18n: Textos["atendimentos"]["mensagem"]["acoes"]["seletor"];
  onEscolher: (emoji: string) => void;
};

/** Dados versionados do pacote, sem CDN; `native` usa o conjunto Unicode do sistema. */
export function SeletorEmojiCompleto({ i18n, onEscolher }: Props) {
  return (
    <Picker
      data={data}
      i18n={i18n}
      set="native"
      theme="light"
      previewPosition="none"
      skinTonePosition="search"
      dynamicWidth
      onEmojiSelect={(emoji) => onEscolher(emoji.native)}
    />
  );
}
