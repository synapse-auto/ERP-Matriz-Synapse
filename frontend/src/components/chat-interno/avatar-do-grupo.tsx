import { UsersRound } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";

/** Um tamanho por lugar onde o avatar do grupo aparece; o ícone acompanha o quadro. */
const TAMANHOS = {
  lista: { quadro: "size-10", icone: "size-[calc(var(--tamanho-icone-interface)*1.1)]" },
  cabecalho: { quadro: "size-10", icone: "size-[calc(var(--tamanho-icone-interface)*1.25)]" },
  painel: { quadro: "size-16", icone: "size-[calc(var(--tamanho-icone-interface)*1.75)]" },
} as const;

type Props = {
  id: string;
  nome: string;
  fotoUrl?: string | null;
  tamanho: keyof typeof TAMANHOS;
  /** Texto alternativo da foto, já com o nome do grupo interpolado. */
  fotoAlt?: string;
};

/**
 * Avatar de um grupo do chat interno: a foto, quando existe; senão o ícone de grupo de sempre.
 * Os dois ocupam o mesmo quadro, então trocar a foto não desloca a lista nem o cabeçalho.
 */
export function AvatarDoGrupo({ id, nome, fotoUrl, tamanho, fotoAlt }: Props) {
  const { quadro, icone } = TAMANHOS[tamanho];
  if (fotoUrl) {
    return (
      <AvatarIniciais
        id={id}
        nome={nome}
        fotoUrl={fotoUrl}
        fotoAlt={fotoAlt}
        className={`flex ${quadro} shrink-0 items-center justify-center rounded-xl text-xs font-bold text-white`}
      />
    );
  }
  return (
    <span className={`flex ${quadro} shrink-0 items-center justify-center rounded-xl bg-primary/15 text-primary`} aria-hidden>
      <UsersRound className={icone} />
    </span>
  );
}
