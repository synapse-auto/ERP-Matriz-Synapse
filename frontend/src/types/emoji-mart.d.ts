declare module "@emoji-mart/react" {
  import type { ComponentType } from "react";

  export interface EmojiMartSelecionado {
    native: string;
  }

  const Picker: ComponentType<{
    data: unknown;
    i18n?: unknown;
    set?: "native" | "apple" | "facebook" | "google" | "twitter";
    theme?: "auto" | "light" | "dark";
    previewPosition?: "none" | "top" | "bottom";
    skinTonePosition?: "none" | "search" | "preview";
    onEmojiSelect?: (emoji: EmojiMartSelecionado) => void;
    dynamicWidth?: boolean;
  }>;

  export default Picker;
}

declare module "@emoji-mart/data" {
  const data: unknown;
  export default data;
}
