"use client";

import { useQuery } from "@tanstack/react-query";
import { Boxes, CircleUserRound, UserCheck, Wifi } from "lucide-react";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { apiFetch } from "@/lib/api/http-client";
import { useTextos } from "@/lib/config/textos-provider";
import { useEquipe } from "@/lib/equipe/use-equipe";

export function PaginaVisaoGeralAdministracao() {
  const textos = useTextos().administracao.visaoGeral;
  const equipe = useEquipe();
  const features = useQuery({
    queryKey: ["config", "features"],
    queryFn: () => apiFetch<string[]>("/api/v1/config/features"),
  });

  if (equipe.isLoading || features.isLoading) {
    return <p className="text-sm text-muted-foreground">{textos.carregando}</p>;
  }

  if (equipe.isError || features.isError) {
    return (
      <ErroDeCarregamento
        mensagem={textos.erro}
        onTentarNovamente={() => Promise.all([equipe.refetch(), features.refetch()])}
      />
    );
  }

  const usuarios = equipe.data ?? [];
  const cartoes = [
    { rotulo: textos.usuarios, valor: usuarios.length, icone: CircleUserRound },
    { rotulo: textos.ativos, valor: usuarios.filter((usuario) => usuario.ativo).length, icone: UserCheck },
    {
      rotulo: textos.online,
      valor: usuarios.filter((usuario) => usuario.statusPresenca === "ONLINE").length,
      icone: Wifi,
    },
    { rotulo: textos.recursos, valor: features.data?.length ?? 0, icone: Boxes },
  ];

  return (
    <section className="space-y-5">
      <header>
        <h2 className="text-xl font-bold">{textos.titulo}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{textos.descricao}</p>
      </header>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cartoes.map((cartao) => {
          const Icone = cartao.icone;
          return (
            <article key={cartao.rotulo} className="rounded-xl border bg-card p-5 shadow-sm">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium text-muted-foreground">{cartao.rotulo}</p>
                <span className="rounded-lg bg-primary/10 p-2 text-primary">
                  <Icone className="size-5" aria-hidden />
                </span>
              </div>
              <p className="mt-4 text-3xl font-bold tabular-nums">{cartao.valor}</p>
            </article>
          );
        })}
      </div>
    </section>
  );
}
