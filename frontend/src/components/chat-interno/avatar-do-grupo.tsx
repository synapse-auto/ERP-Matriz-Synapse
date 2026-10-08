import { UsersRound } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { apiFetchBlob } from "@/lib/api/http-client";
import { classificarOrigemDeRecursoVisual } from "@/lib/midia/origem-de-recurso-visual";

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
  const origem = fotoUrl ? classificarOrigemDeRecursoVisual(fotoUrl) : null;
  if (origem?.tipo === "absoluta") {
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
    <span className={`relative flex ${quadro} shrink-0 items-center justify-center overflow-hidden rounded-xl bg-primary/15 text-primary`}>
      {origem?.tipo === "autenticada" ? (
        <FotoAutenticadaDoGrupo key={origem.caminho} caminho={origem.caminho} fotoAlt={fotoAlt} fallback={<UsersRound className={icone} aria-hidden />} />
      ) : <UsersRound className={icone} aria-hidden />}
    </span>
  );
}

/** Reutiliza cliente e cache autenticados, mas vincula a URL local ao efeito que a libera. */
function FotoAutenticadaDoGrupo({ caminho, fotoAlt, fallback }: { caminho: string; fotoAlt?: string; fallback: ReactNode }) {
  const imagem = useRef<HTMLImageElement>(null);
  const foto = useQuery({
    queryKey: ["avatar", caminho],
    queryFn: () => apiFetchBlob(caminho),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
  useEffect(() => {
    const elemento = imagem.current;
    if (!foto.data || !elemento) return;
    // Criar em useMemo e revogar no cleanup deixa o mesmo src revogado no remount do StrictMode.
    const url = URL.createObjectURL(foto.data);
    elemento.src = url;
    return () => {
      elemento.removeAttribute("src");
      URL.revokeObjectURL(url);
    };
  }, [foto.data]);
  if (!foto.data) return <>{fallback}</>;
  // eslint-disable-next-line @next/next/no-img-element
  return <img ref={imagem} alt={fotoAlt ?? ""} className="absolute inset-0 size-full object-cover" />;
}
