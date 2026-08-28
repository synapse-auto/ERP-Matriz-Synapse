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
    {
      rotulo: textos.usuarios,
      valor: usuarios.length,
      icone: CircleUserRound,
      classeIcone: "bg-cor-ia/10 text-cor-ia",
    },
    {
      rotulo: textos.ativos,
      valor: usuarios.filter((usuario) => usuario.ativo).length,
      icone: UserCheck,
      classeIcone: "bg-cor-sucesso/10 text-cor-sucesso",
    },
    {
      rotulo: textos.online,
      valor: usuarios.filter((usuario) => usuario.statusPresenca === "ONLINE").length,
      icone: Wifi,
      classeIcone: "bg-cor-info/10 text-cor-info",
    },
    {
      rotulo: textos.recursos,
      valor: features.data?.length ?? 0,
      icone: Boxes,
      classeIcone: "bg-cor-atencao/10 text-cor-atencao",
    },
  ];

  return (
    <section className="space-y-6">
      <header>
        <h2 className="text-lg font-bold tracking-tight">{textos.titulo}</h2>
        <p className="mt-1 text-[13px] text-muted-foreground">{textos.descricao}</p>
      </header>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {cartoes.map((cartao) => {
          const Icone = cartao.icone;
          return (
            <article
              key={cartao.rotulo}
              className="rounded-xl border border-border bg-card p-5 shadow-sm"
            >
              <span className={`flex size-10 items-center justify-center rounded-xl ${cartao.classeIcone}`}>
                <Icone className="size-5" aria-hidden />
              </span>
              <p className="mt-3.5 text-3xl font-bold tracking-tight tabular-nums">{cartao.valor}</p>
              <p className="mt-0.5 text-[12.5px] font-semibold text-muted-foreground">{cartao.rotulo}</p>
            </article>
          );
        })}
      </div>
    </section>
  );
}
