"use client";

import { useRef, useState } from "react";
import { Pause, Play } from "lucide-react";

import { cn } from "@/lib/utils";

type Props = {
  src: string;
  rotulo: string;
  reproduzir: string;
  pausar: string;
  posicao: string;
};

export function formatarDuracaoDoAudio(segundos: number): string {
  if (!Number.isFinite(segundos) || segundos < 0) return "00:00";
  const total = Math.floor(segundos);
  const minutos = Math.floor(total / 60);
  return `${String(minutos).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

function duracaoUtil(valor: number): number {
  return Number.isFinite(valor) && valor > 0 ? valor : 0;
}

/**
 * Player compacto que herda a cor da bolha (`currentColor`), no lugar do controle nativo
 * branco que quebrava o balão de áudio enviado.
 */
export function PlayerAudio({ src, rotulo, reproduzir, pausar, posicao }: Props) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [srcAtual, setSrcAtual] = useState(src);
  const [tocando, setTocando] = useState(false);
  const [atual, setAtual] = useState(0);
  const [duracao, setDuracao] = useState(0);

  if (srcAtual !== src) {
    setSrcAtual(src);
    setTocando(false);
    setAtual(0);
    setDuracao(0);
  }

  function registrarDuracao(elemento: HTMLAudioElement) {
    setDuracao(duracaoUtil(elemento.duration));
  }

  function alternar() {
    const elemento = audioRef.current;
    if (!elemento) return;
    if (elemento.paused) {
      void elemento.play();
      return;
    }
    elemento.pause();
  }

  const teto = duracaoUtil(duracao);

  return (
    <div
      data-slot="player-audio"
      role="group"
      aria-label={rotulo}
      className="flex w-[13.75rem] items-center gap-2.5"
    >
      <audio
        ref={audioRef}
        src={src}
        preload="metadata"
        onPlay={() => setTocando(true)}
        onPause={() => setTocando(false)}
        onEnded={() => {
          setTocando(false);
          setAtual(0);
        }}
        onTimeUpdate={(evento) => {
          setAtual(evento.currentTarget.currentTime);
          registrarDuracao(evento.currentTarget);
        }}
        onLoadedMetadata={(evento) => registrarDuracao(evento.currentTarget)}
        onDurationChange={(evento) => registrarDuracao(evento.currentTarget)}
      />
      <button
        type="button"
        className="flex size-9 shrink-0 items-center justify-center rounded-full bg-current/15 text-current hover:bg-current/25 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current/40"
        aria-label={tocando ? pausar : reproduzir}
        onClick={alternar}
      >
        {tocando ? (
          <Pause className="size-4 fill-current" aria-hidden />
        ) : (
          <Play className="size-4 translate-x-px fill-current" aria-hidden />
        )}
      </button>
      <div className="min-w-0 flex-1">
        <input
          type="range"
          min={0}
          max={teto || 0}
          step={0.1}
          value={Math.min(atual, teto)}
          aria-label={posicao}
          aria-valuetext={`${formatarDuracaoDoAudio(atual)} / ${formatarDuracaoDoAudio(teto)}`}
          className="h-1.5 w-full cursor-pointer appearance-none rounded-full bg-current/25 accent-current focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current/40"
          onChange={(evento) => {
            const proximo = Number(evento.target.value);
            if (audioRef.current) audioRef.current.currentTime = proximo;
            setAtual(proximo);
          }}
        />
        <p className={cn("mt-0.5 font-mono text-[0.65rem] tabular-nums opacity-80")}>
          {formatarDuracaoDoAudio(atual)} / {formatarDuracaoDoAudio(teto)}
        </p>
      </div>
    </div>
  );
}
