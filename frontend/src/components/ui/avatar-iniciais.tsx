import { useMemo, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";

import { apiFetchBlob } from "@/lib/api/http-client";
import { iniciaisDoNome } from "@/lib/utils";

/**
 * Avatar com as iniciais de uma pessoa e um tom de cor determinístico (E17b §Bloco 4 — extraído na
 * segunda tela que precisou disso, Lembretes, depois de já existir em Equipe).
 *
 * <p>O tom vem de um hash simples do id sobre a paleta de tokens (design/TOKENS.md) — nunca cor
 * literal, e nunca o backend precisando guardar uma cor por pessoa: a mesma pessoa sempre cai no
 * mesmo tom entre renderizações, sem estado extra. As iniciais reaproveitam `lib/utils.ts` — a
 * Sidebar já as calcula assim desde a E17; duplicar aqui divergiria em silêncio se alguém ajustar
 * uma cópia e esquecer a outra.
 */
const TONS_DE_AVATAR = [
  "var(--primary)",
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
];

export function tomDoAvatar(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  return TONS_DE_AVATAR[hash % TONS_DE_AVATAR.length];
}

type Props = {
  id: string;
  nome: string;
  fotoUrl?: string | null;
  className?: string;
};

export function AvatarIniciais({ id, nome, fotoUrl, className }: Props) {
  const classe = `${className ?? "flex size-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"} relative overflow-hidden`;

  return (
    <span
      className={classe}
      style={{ backgroundColor: tomDoAvatar(id) }}
      title={nome}
    >
      {fotoUrl ? <FotoCarregada fotoUrl={fotoUrl} fallback={iniciaisDoNome(nome)} /> : iniciaisDoNome(nome)}
    </span>
  );
}

function FotoCarregada({ fotoUrl, fallback }: { fotoUrl: string; fallback: string }) {
  const foto = useQuery({
    queryKey: ["avatar", fotoUrl],
    queryFn: () => apiFetchBlob(fotoUrl),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
  const url = useMemo(() => (foto.data ? URL.createObjectURL(foto.data) : null), [foto.data]);
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);

  if (!url) return <>{fallback}</>;
  // A URL e um blob criado pelo cliente a partir do endpoint autenticado; nao e URL assinada.
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={url} alt="" className="absolute inset-0 size-full object-cover" />;
}
