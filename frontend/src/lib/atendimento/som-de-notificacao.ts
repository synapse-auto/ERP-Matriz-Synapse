let contextoDeAudio: AudioContext | null = null;

function obterContextoDeAudio(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const Construtor = window.AudioContext
    ?? (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Construtor) return null;
  contextoDeAudio ??= new Construtor();
  return contextoDeAudio;
}

/** Registra o desbloqueio do áudio após uma interação real, sem pedir permissão ao navegador. */
export function registrarDesbloqueioDeAudio(): () => void {
  if (typeof window === "undefined") return () => undefined;
  const desbloquear = () => {
    const contexto = obterContextoDeAudio();
    if (contexto?.state === "suspended") void contexto.resume().catch(() => undefined);
  };
  window.addEventListener("pointerdown", desbloquear, { capture: true });
  window.addEventListener("keydown", desbloquear, { capture: true });
  return () => {
    window.removeEventListener("pointerdown", desbloquear, { capture: true });
    window.removeEventListener("keydown", desbloquear, { capture: true });
  };
}

/** Tenta um som curto e discreto; falha de autoplay é deliberadamente silenciosa. */
export function tocarSomDeNotificacao(): boolean {
  const contexto = obterContextoDeAudio();
  if (!contexto || contexto.state !== "running") return false;

  const oscilador = contexto.createOscillator();
  const ganho = contexto.createGain();
  const inicio = contexto.currentTime;
  oscilador.type = "sine";
  oscilador.frequency.setValueAtTime(660, inicio);
  oscilador.frequency.exponentialRampToValueAtTime(520, inicio + 0.12);
  ganho.gain.setValueAtTime(0.0001, inicio);
  ganho.gain.exponentialRampToValueAtTime(0.045, inicio + 0.01);
  ganho.gain.exponentialRampToValueAtTime(0.0001, inicio + 0.14);
  oscilador.connect(ganho);
  ganho.connect(contexto.destination);
  oscilador.start(inicio);
  oscilador.stop(inicio + 0.15);
  return true;
}
