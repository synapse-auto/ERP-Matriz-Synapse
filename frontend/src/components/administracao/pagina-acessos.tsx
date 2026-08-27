"use client";

import { useState } from "react";
import { KeyRound, Pencil, UserRoundX } from "lucide-react";

import { Formulario, SenhaProvisoriaDialog } from "@/components/equipe/pagina-equipe";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { useTextos } from "@/lib/config/textos-provider";
import {
  useDesativarUsuario,
  useEquipe,
  useGerarSenhaProvisoria,
} from "@/lib/equipe/use-equipe";
import type { UsuarioEquipe } from "@/lib/equipe/types";

export function PaginaAcessosAdministracao() {
  const textos = useTextos();
  const t = textos.administracao.acessos;
  const equipe = useEquipe();
  const desativar = useDesativarUsuario();
  const gerarSenha = useGerarSenhaProvisoria();
  const [novo, setNovo] = useState(false);
  const [edicao, setEdicao] = useState<UsuarioEquipe | null>(null);
  const [paraDesativar, setParaDesativar] = useState<UsuarioEquipe | null>(null);
  const [senhaGeradaPara, setSenhaGeradaPara] = useState<UsuarioEquipe | null>(null);
  const [senhaGerada, setSenhaGerada] = useState<string | null>(null);

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold tracking-tight">{t.titulo}</h2>
          <p className="mt-1 text-[13px] text-muted-foreground">{t.descricao}</p>
        </div>
        <Button onClick={() => setNovo(true)}>{t.novo}</Button>
      </header>

      {equipe.isLoading ? (
        <p className="text-sm text-muted-foreground">{t.carregando}</p>
      ) : equipe.isError ? (
        <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => equipe.refetch()} />
      ) : (equipe.data?.length ?? 0) === 0 ? (
        <div className="rounded-lg border border-dashed border-border bg-card p-10 text-center text-sm text-muted-foreground shadow-md">
          {t.vazio}
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card shadow-md">
          <table className="w-full min-w-[760px] text-[13px]">
            <thead className="border-b border-border bg-muted/50 text-left text-xs font-semibold tracking-[0.08em] text-muted-foreground uppercase">
              <tr>
                <th className="px-4 py-3">{t.usuario}</th>
                <th className="px-4 py-3">{t.papel}</th>
                <th className="px-4 py-3">{t.presenca}</th>
                <th className="px-4 py-3">{t.situacao}</th>
                <th className="px-4 py-3 text-right">{t.acoes}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {equipe.data?.map((usuario) => {
                const gerenciavel = usuario.papel === "ATENDENTE" || usuario.papel === "SUBGESTOR";
                return (
                  <tr key={usuario.id} className="transition-colors hover:bg-muted/30">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <AvatarIniciais
                          id={usuario.id}
                          nome={usuario.nome}
                          fotoUrl={usuario.fotoUrl}
                          className="flex size-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
                        />
                        <div className="min-w-0">
                          <p className="truncate font-semibold">{usuario.nome}</p>
                          <p className="truncate text-xs text-muted-foreground">{usuario.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <PillDeStatus tom="info">{t.papeis[usuario.papel]}</PillDeStatus>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {t.presencas[usuario.statusPresenca]}
                    </td>
                    <td className="px-4 py-3">
                      <PillDeStatus tom={usuario.ativo ? "sucesso" : "neutro"}>
                        {usuario.ativo ? t.ativo : t.inativo}
                      </PillDeStatus>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1">
                        {gerenciavel && (
                          <Button
                            type="button"
                            size="icon"
                            variant="ghost"
                            aria-label={`${t.editar} ${usuario.nome}`}
                            onClick={() => setEdicao(usuario)}
                          >
                            <Pencil className="size-4" aria-hidden />
                          </Button>
                        )}
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          aria-label={`${t.senha} ${usuario.nome}`}
                          onClick={() => {
                            setSenhaGeradaPara(usuario);
                            setSenhaGerada(null);
                            gerarSenha.mutate(usuario.id, {
                              onSuccess: (resposta) => setSenhaGerada(resposta.senha),
                            });
                          }}
                        >
                          <KeyRound className="size-4" aria-hidden />
                        </Button>
                        {usuario.ativo && (
                          <Button
                            type="button"
                            size="icon"
                            variant="ghost"
                            className="text-destructive hover:text-destructive"
                            aria-label={`${t.desativar} ${usuario.nome}`}
                            onClick={() => setParaDesativar(usuario)}
                          >
                            <UserRoundX className="size-4" aria-hidden />
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <Formulario aberto={novo} onFechar={() => setNovo(false)} />
      {edicao && <Formulario aberto existente={edicao} onFechar={() => setEdicao(null)} />}
      {senhaGeradaPara && (
        <SenhaProvisoriaDialog
          usuario={senhaGeradaPara}
          senha={senhaGerada}
          erro={gerarSenha.isError}
          onFechar={() => setSenhaGeradaPara(null)}
        />
      )}
      {paraDesativar && (
        <Dialog open onOpenChange={(aberto) => !aberto && setParaDesativar(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{textos.equipe.desativacao.titulo}</DialogTitle>
              <DialogDescription>
                {textos.equipe.desativacao.descricao.replace("{nome}", paraDesativar.nome)}
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setParaDesativar(null)}>
                {textos.equipe.desativacao.cancelar}
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={desativar.isPending}
                onClick={() =>
                  desativar.mutate(paraDesativar.id, {
                    onSuccess: () => setParaDesativar(null),
                  })
                }
              >
                {textos.equipe.desativacao.confirmar}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </section>
  );
}
