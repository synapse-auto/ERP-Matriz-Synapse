"use client";

import Link from "next/link";
import { ShieldX } from "lucide-react";

import { buttonVariants } from "@/components/ui/button";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

export function ProtecaoAdministrador({ children }: { children: React.ReactNode }) {
  const textos = useTextos();
  const papel = useAuthStore((estado) => estado.papel);
  const status = useAuthStore((estado) => estado.status);

  if (status === "carregando") {
    return <p className="p-6 text-sm text-muted-foreground">{textos.estados.carregando}</p>;
  }

  if (papel !== "ADMINISTRADOR") {
    return (
      <main className="flex min-h-[60vh] items-center justify-center p-6">
        <div className="max-w-md space-y-4 rounded-xl border bg-card p-8 text-center shadow-sm">
          <ShieldX className="mx-auto size-[calc(var(--tamanho-icone-interface)*2.5)] text-destructive" aria-hidden />
          <div>
            <h1 className="text-xl font-bold">{textos.administracao.semPermissaoTitulo}</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              {textos.administracao.semPermissaoDescricao}
            </p>
          </div>
          <Link href="/atendimentos" className={buttonVariants()}>
            {textos.administracao.voltar}
          </Link>
        </div>
      </main>
    );
  }

  return children;
}
