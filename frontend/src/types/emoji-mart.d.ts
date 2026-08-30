declare module "@emoji-mart/data" {
  const data: unknown;
  export default data;
}

declare module "emoji-mart" {
  export class Picker extends HTMLElement {
    constructor(options: {
      data?: unknown;
      i18n?: unknown;
      set?: string;
      theme?: string;
      previewPosition?: string;
      skinTonePosition?: string;
      dynamicWidth?: boolean;
      onEmojiSelect?: (escolha: { native?: unknown }) => void;
    });
  }
}
