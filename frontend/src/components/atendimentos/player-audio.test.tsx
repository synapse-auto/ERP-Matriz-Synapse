import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { formatarDuracaoDoAudio, PlayerAudio } from "./player-audio";

describe("PlayerAudio", () => {
  beforeEach(() => {
    Object.defineProperty(HTMLMediaElement.prototype, "play", {
      configurable: true,
      value: vi.fn().mockResolvedValue(undefined),
    });
    Object.defineProperty(HTMLMediaElement.prototype, "pause", {
      configurable: true,
      value: vi.fn(),
    });
    Object.defineProperty(HTMLMediaElement.prototype, "paused", {
      configurable: true,
      get() {
        return true;
      },
    });
  });

  it("formata duração finita e trata valor inválido", () => {
    expect(formatarDuracaoDoAudio(65)).toBe("01:05");
    expect(formatarDuracaoDoAudio(Number.NaN)).toBe("00:00");
    expect(formatarDuracaoDoAudio(Number.POSITIVE_INFINITY)).toBe("00:00");
  });

  it("reproduz pelo botão sem usar o controle nativo e atualiza a duração", () => {
    const { rerender } = render(
      <PlayerAudio
        src="https://example.test/voz.m4a"
        rotulo="Áudio"
        reproduzir="Reproduzir áudio"
        pausar="Pausar áudio"
        posicao="Posição do áudio"
      />,
    );

    expect(document.querySelector("audio[controls]")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Reproduzir áudio" }));
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalled();

    const audio = document.querySelector("audio");
    expect(audio).not.toBeNull();
    Object.defineProperty(audio!, "duration", { configurable: true, value: 4 });
    fireEvent.durationChange(audio!);
    expect(screen.getByText("00:00 / 00:04")).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Posição do áudio" })).toBeInTheDocument();

    rerender(
      <PlayerAudio
        src="https://example.test/outra.m4a"
        rotulo="Áudio"
        reproduzir="Reproduzir áudio"
        pausar="Pausar áudio"
        posicao="Posição do áudio"
      />,
    );
    expect(screen.getByText("00:00 / 00:00")).toBeInTheDocument();
  });
});
