"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { LoaderCircle } from "lucide-react";

import { Button } from "@/components/ui/button";
import { MarcaSynapse } from "@/components/auth/marca-synapse";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

const LIMITE_ABERTURA_DO_PAINEL_MS = 8_000;

/**
 * Cobre a troca real entre o POST de login e a primeira montagem do shell. O limite evita um
 * carregamento infinito quando uma consulta inicial do CRM fica indisponível; não há atraso
 * mínimo artificial, então um shell pronto some imediatamente.
 */
export function TransicaoDeAbertura() {
  const textos = useTextos().login;
  const pathname = usePathname();
  const router = useRouter();
  const aberturaDoPainel = useAuthStore((estado) => estado.aberturaDoPainel);
  const falharAberturaDoPainel = useAuthStore((estado) => estado.falharAberturaDoPainel);
  const concluirAberturaDoPainel = useAuthStore((estado) => estado.concluirAberturaDoPainel);

  useEffect(() => {
    if (aberturaDoPainel !== "em-andamento" || pathname === "/login") return;

    const limite = window.setTimeout(falharAberturaDoPainel, LIMITE_ABERTURA_DO_PAINEL_MS);
    return () => window.clearTimeout(limite);
  }, [aberturaDoPainel, falharAberturaDoPainel, pathname]);

  if (aberturaDoPainel === "ociosa") return null;

  function tentarNovamente() {
    concluirAberturaDoPainel();
    router.replace("/");
  }

  return (
    <div className="synapse-transicao" role="status" aria-live="polite">
      <div className="synapse-transicao__conteudo">
        <MarcaSynapse alt="" className="synapse-transicao__marca" />
        {aberturaDoPainel === "erro" ? (
          <>
            <p className="synapse-transicao__erro">{textos.erroAbrirPainel}</p>
            <Button type="button" className="synapse-transicao__botao" onClick={tentarNovamente}>
              {textos.tentarAbrirPainel}
            </Button>
          </>
        ) : (
          <>
            <LoaderCircle aria-hidden="true" className="synapse-transicao__icone" />
            <p className="synapse-transicao__texto">{textos.abrindoPainel}</p>
          </>
        )}
      </div>
    </div>
  );
}
