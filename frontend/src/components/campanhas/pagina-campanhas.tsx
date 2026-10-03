"use client";

import Link from "next/link";
import { useState } from "react";
import { Plus, Settings } from "lucide-react";

import { Button, buttonVariants } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import {
  funcionalidadeIndisponivel,
  TAMANHO_DA_PAGINA,
  usePodeEmCampanhas,
  useListaDeCampanhas,
} from "@/lib/campanhas/hooks";
import { useTextos } from "@/lib/config/textos-provider";

import { AcaoSoDoAdministrador, CampanhasIndisponiveis, EstadoVazio } from "./estados";
import { CartaoDeCampanha } from "./cartao-de-campanha";
import { EsqueletoDaTabela, TabelaDeCampanhas } from "./tabela-de-campanhas";
import { EsqueletoDaTira, TiraDeIndicadores } from "./tira-de-indicadores";

function Cabecalho() {
  const textos = useTextos().campanhas;
  const podeCriar = usePodeEmCampanhas("criar");
  return (
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold">{textos.titulo}</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">{textos.descricao}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Link href="/campanhas/configuracao" className={buttonVariants({ variant: "outline" })}>
          <Settings aria-hidden />
          {textos.configuracao}
        </Link>
        {podeCriar ? (
          <Link href="/campanhas/nova" className={buttonVariants()}>
            <Plus aria-hidden />
            {textos.novaCampanha}
          </Link>
        ) : (
          <AcaoSoDoAdministrador id="nova-campanha-ajuda" rotulo={textos.novaCampanha} explicacao={textos.criarSomenteAdministrador} />
        )}
      </div>
    </header>
  );
}

function Paginacao({
  pagina,
  totalDePaginas,
  aoMudar,
}: {
  pagina: number;
  totalDePaginas: number;
  aoMudar: (pagina: number) => void;
}) {
  const textos = useTextos().campanhas.lista;
  if (totalDePaginas <= 1) return null;
  return (
    <nav aria-label={textos.titulo} className="flex items-center justify-end gap-3 text-sm">
      <Button variant="outline" size="sm" disabled={pagina === 0} onClick={() => aoMudar(pagina - 1)}>
        {textos.paginaAnterior}
      </Button>
      <span aria-live="polite">
        {interpolarCatalogo(textos.paginaDe, { pagina: String(pagina + 1), total: String(totalDePaginas) })}
      </span>
      <Button variant="outline" size="sm" disabled={pagina + 1 >= totalDePaginas} onClick={() => aoMudar(pagina + 1)}>
        {textos.paginaSeguinte}
      </Button>
    </nav>
  );
}

export function PaginaCampanhas() {
  const textos = useTextos().campanhas;
  const podeCriar = usePodeEmCampanhas("criar");
  const [pagina, setPagina] = useState(0);
  const consulta = useListaDeCampanhas(pagina);

  if (consulta.isError && funcionalidadeIndisponivel(consulta.error)) return <CampanhasIndisponiveis />;

  const lista = consulta.data;
  const totalDePaginas = lista ? Math.max(1, Math.ceil(lista.total / TAMANHO_DA_PAGINA)) : 1;

  return (
    <main className="space-y-5 p-6">
      <Cabecalho />
      {consulta.isError && !lista && (
        <ErroDeCarregamento mensagem={textos.erroCarregar} onTentarNovamente={() => consulta.refetch()} />
      )}
      {consulta.isPending && (
        <div role="status" aria-label={textos.lista.carregando} className="space-y-5">
          <EsqueletoDaTira />
          <EsqueletoDaTabela />
        </div>
      )}
      {lista && (
        <>
          <TiraDeIndicadores lista={lista} />
          {lista.itens.length === 0 ? (
            <EstadoVazio
              titulo={textos.lista.vazioTitulo}
              descricao={textos.lista.vazioDescricao}
              acao={
                podeCriar ? (
                  <Link href="/campanhas/nova" className={buttonVariants()}>
                    <Plus aria-hidden />
                    {textos.lista.vazioAcao}
                  </Link>
                ) : undefined
              }
            />
          ) : (
            <>
              <TabelaDeCampanhas campanhas={lista.itens} />
              <ul className="space-y-3 md:hidden" aria-label={textos.lista.titulo}>
                {lista.itens.map((campanha) => (
                  <CartaoDeCampanha key={campanha.id} campanha={campanha} />
                ))}
              </ul>
              <Paginacao pagina={pagina} totalDePaginas={totalDePaginas} aoMudar={setPagina} />
            </>
          )}
        </>
      )}
    </main>
  );
}
