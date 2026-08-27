import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { useTextos } from "@/lib/config/textos-provider";
import { Sparkles, Megaphone, Brain, BarChart, Smartphone } from "lucide-react";
import { cn } from "@/lib/utils";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { Badge } from "@/components/ui/badge";

interface NovidadesDialogProps {
  aberto: boolean;
  onFechar: () => void;
}

const ICONES: Record<string, React.ElementType> = {
  megaphone: Megaphone,
  brain: Brain,
  "bar-chart": BarChart,
  smartphone: Smartphone,
};

export function NovidadesDialog({ aberto, onFechar }: NovidadesDialogProps) {
  const textos = useTextos();
  const n = textos.novidades;
  const [aba, setAba] = useState<"novidades" | "embreve">("novidades");

  if (!n) return null;

  // Agrupa novidades por data
  const agrupadasPorData = n.itensNovidades.reduce((acc, item) => {
    if (!acc[item.data]) {
      acc[item.data] = [];
    }
    acc[item.data].push(item);
    return acc;
  }, {} as Record<string, typeof n.itensNovidades>);

  const datasOrdenadas = Object.keys(agrupadasPorData).sort((a, b) => b.localeCompare(a));

  return (
    <Dialog open={aberto} onOpenChange={(v) => !v && onFechar()}>
      <DialogContent className="sm:max-w-[600px] max-h-[85vh] overflow-hidden flex flex-col p-0">
        <DialogHeader className="px-6 pt-6 pb-2 shrink-0">
          <DialogTitle>{n.titulo}</DialogTitle>
          <DialogDescription>
            {aba === "novidades" ? n.subtituloNovidades : n.subtituloEmBreve}
          </DialogDescription>
        </DialogHeader>

        <div className="flex gap-2 px-6 shrink-0 border-b" role="tablist" aria-label={n.titulo}>
          <button
            role="tab"
            aria-selected={aba === "novidades"}
            aria-controls="panel-novidades"
            onClick={() => setAba("novidades")}
            className={cn(
              "px-4 py-2 text-sm font-semibold border-b-2 transition-colors",
              aba === "novidades" ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            {n.abas.novidades}
          </button>
          <button
            role="tab"
            aria-selected={aba === "embreve"}
            aria-controls="panel-embreve"
            onClick={() => setAba("embreve")}
            className={cn(
              "px-4 py-2 text-sm font-semibold border-b-2 transition-colors",
              aba === "novidades" ? "border-transparent text-muted-foreground hover:text-foreground" : "border-primary text-primary"
            )}
          >
            {n.abas.embreve}
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 bg-muted/20">
          {aba === "novidades" && (
            <div id="panel-novidades" role="tabpanel" className="flex flex-col gap-6">
              {datasOrdenadas.map(data => (
                <div key={data} className="space-y-4">
                  <h3 className="text-sm font-semibold text-muted-foreground border-b pb-2">{
                    new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" }).format(new Date(data + "T12:00:00"))
                  }</h3>
                  <div className="flex flex-col gap-6">
                    {agrupadasPorData[data].map((item, i) => (
                      <div key={i} className="flex gap-4">
                        <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                          <Sparkles className="size-5" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2 mb-1">
                            <h4 className="font-semibold">{item.titulo}</h4>
                            {item.novo && n.novoTag && (
                              <Badge className="h-5 px-1.5 text-[10px] bg-primary/20 text-primary hover:bg-primary/20 border-none">{n.novoTag}</Badge>
                            )}
                          </div>
                          <p className="text-sm text-muted-foreground">{item.descricao}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          {aba === "embreve" && (
            <div id="panel-embreve" role="tabpanel" className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {n.itensEmBreve.map((item, i) => {
                const Icon = ICONES[item.icone] || Sparkles;
                return (
                  <div key={i} className="rounded-xl border bg-card text-card-foreground shadow-sm p-4 flex flex-col gap-3">
                    <div className="flex items-center justify-between">
                      <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                        <Icon className="size-5" />
                      </div>
                      <PillDeStatus tom={item.tom as "erro" | "sucesso" | "info" | "atencao" | "neutro"}>
                        {item.status}
                      </PillDeStatus>
                    </div>
                    <h4 className="font-semibold">{item.titulo}</h4>
                    <p className="text-sm text-muted-foreground flex-1">{item.descricao}</p>
                    {item.previsao && (
                      <span className="text-xs font-medium text-muted-foreground mt-2 block">
                        {item.previsao}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
