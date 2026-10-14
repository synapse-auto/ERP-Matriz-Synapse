"use client";

import { useRef, useState, useTransition } from "react";
import { ArrowLeft, House, LoaderCircle, RefreshCw, ShieldAlert, WifiOff } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextosOpcionais } from "@/lib/config/textos-provider";
import { TEXTOS_DE_ERRO, type TextosDeErro } from "@/lib/config/schema-erros";
import { useOnline } from "@/lib/erros/conexao";
import { temPaginaAnteriorDoCrm } from "@/lib/erros/retorno";
import { cn, urlSegura } from "@/lib/utils";

type Props = {
  tipo: "404" | "renderizacao" | "backend" | "offline";
  textos?: TextosDeErro;
  marca?: string;
  logoUrl?: string | null;
  onTentarNovamente?: () => void | Promise<unknown>;
  compacta?: boolean;
};

export function TelaDeErro({ tipo, textos: fornecidos, marca, logoUrl, onTentarNovamente, compacta }: Props) {
  const catalogo = useTextosOpcionais();
  const textos = fornecidos ?? catalogo?.erros ?? TEXTOS_DE_ERRO;
  const nome = marca ?? catalogo?.app.marca ?? textos.marcaFallback;
  const online = useOnline();
  const tipoEfetivo = tipo !== "404" && !online ? "offline" : tipo;
  const [logoFalhou, setLogoFalhou] = useState(false);
  const [pendente, iniciarTransicao] = useTransition();
  const emCurso = useRef(false);
  const precisaTrocarSenha = useAuthStore((estado) => estado.precisaTrocarSenha);
  // / já escolhe Atendimentos e delega autenticação ao servidor; nenhuma permissão é inferida aqui.
  const inicio = precisaTrocarSenha ? "/trocar-senha" : "/";
  const titulo = tipoEfetivo === "404" ? textos.naoEncontrada
    : tipoEfetivo === "offline" ? textos.offline
    : tipoEfetivo === "backend" ? textos.indisponivel : textos.erroRenderizacao;
  const descricao = tipoEfetivo === "404" ? textos.descricao404
    : tipoEfetivo === "offline" ? textos.descricaoOffline
    : tipoEfetivo === "backend" ? textos.descricaoIndisponivel : textos.descricaoRenderizacao;
  const logoSegura = logoUrl?.startsWith("/") && !logoUrl.startsWith("//") ? logoUrl : urlSegura(logoUrl);
  const srcLogo = logoSegura?.startsWith("/api/v1/")
    ? `${process.env.NEXT_PUBLIC_API_URL ?? ""}${logoSegura}` : logoSegura;

  function tentarNovamente() {
    if (!onTentarNovamente || emCurso.current) return;
    emCurso.current = true;
    iniciarTransicao(async () => {
      try { await onTentarNovamente(); }
      finally { emCurso.current = false; }
    });
  }

  return (
    <section
      role={compacta ? "status" : undefined}
      aria-live={compacta ? "polite" : undefined}
      aria-busy={pendente}
      aria-labelledby={compacta ? undefined : "titulo-erro"}
      className={cn("bg-background text-foreground", compacta
        ? "flex shrink-0 flex-wrap items-center justify-between gap-3 border-b p-3"
        : "flex min-h-full w-full flex-1 items-center justify-center overflow-y-auto p-6")}
    >
      <div className={cn(compacta ? "min-w-0 flex-1" : "w-full max-w-lg rounded-[var(--raio-xl,var(--radius))] border bg-card p-6 text-center shadow-md sm:p-10")}>
        {!compacta && (
          <div className="mb-8 flex items-center justify-center gap-3 font-semibold text-[var(--texto-forte)]">
            {logoSegura && !logoFalhou ? (
              // O mesmo recurso público da Sidebar; a logo não é necessária para exibir o erro.
              // eslint-disable-next-line @next/next/no-img-element
              <img src={srcLogo} alt="" className="size-10 object-contain"
                ref={(imagem) => { if (imagem?.complete && imagem.naturalWidth === 0) setLogoFalhou(true); }}
                onError={() => setLogoFalhou(true)} />
            ) : <ShieldAlert className="size-8 text-primary" aria-hidden />}
            <span className="break-words">{nome}</span>
          </div>
        )}
        {tipoEfetivo === "404" && <p className="mb-4 font-mono text-5xl font-semibold text-primary">{textos.codigo404}</p>}
        {tipoEfetivo === "offline" && !compacta && <WifiOff aria-hidden className="mx-auto mb-4 size-10 text-muted-foreground" />}
        <h1 id={compacta ? undefined : "titulo-erro"} className={cn("font-semibold text-[var(--texto-forte)]", compacta ? "text-sm" : "text-2xl")}>
          {titulo}
        </h1>
        <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{descricao}</p>
        {!compacta && (
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            {tipo === "404" ? (
              <Button className="rounded-[var(--raio-botao-lg,var(--radius))]" variant="outline" onClick={() => temPaginaAnteriorDoCrm(window.history.length, document.referrer, window.location.origin) ? window.history.back() : window.location.assign(inicio)}>
                <ArrowLeft aria-hidden />{textos.voltar}
              </Button>
            ) : onTentarNovamente && (
              <Button className="rounded-[var(--raio-botao-lg,var(--radius))]" onClick={tentarNovamente} disabled={pendente}>
                {pendente ? <LoaderCircle aria-hidden className="animate-spin" /> : <RefreshCw aria-hidden />}
                {pendente ? textos.tentando : textos.tentarNovamente}
              </Button>
            )}
            <a href={inicio} className={buttonVariants({ variant: "outline", className: "rounded-[var(--raio-botao-lg,var(--radius))]" })}><House aria-hidden />{textos.inicio}</a>
          </div>
        )}
      </div>
      {compacta && onTentarNovamente && (
        <Button variant="outline" onClick={tentarNovamente} disabled={pendente}>
          {pendente ? <LoaderCircle aria-hidden className="animate-spin" /> : <RefreshCw aria-hidden />}
          {pendente ? textos.tentando : textos.tentarNovamente}
        </Button>
      )}
    </section>
  );
}
