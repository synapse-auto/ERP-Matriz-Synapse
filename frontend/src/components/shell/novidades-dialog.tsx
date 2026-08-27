import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { useTextos } from "@/lib/config/textos-provider";
import { Sparkles, Megaphone, Brain, BarChart, Smartphone } from "lucide-react";
import { cn } from "@/lib/utils";

interface NovidadesDialogProps {
  aberto: boolean;
  onFechar: () => void;
}

export function NovidadesDialog({ aberto, onFechar }: NovidadesDialogProps) {
  const textos = useTextos();
  const n = textos.novidades;
  const [aba, setAba] = useState<"novidades" | "embreve">("novidades");

  if (!n) return null;

  return (
    <Dialog open={aberto} onOpenChange={(v) => !v && onFechar()}>
      <DialogContent className="sm:max-w-[600px] max-h-[85vh] overflow-hidden flex flex-col p-0">
        <DialogHeader className="px-6 pt-6 pb-2 shrink-0">
          <DialogTitle>{n.titulo}</DialogTitle>
          <DialogDescription>
            {aba === "novidades" ? n.subtituloNovidades : n.subtituloEmBreve}
          </DialogDescription>
        </DialogHeader>
        
        <div className="flex gap-2 px-6 shrink-0 border-b">
          <button
            onClick={() => setAba("novidades")}
            className={cn(
              "px-4 py-2 text-sm font-semibold border-b-2 transition-colors",
              aba === "novidades" ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            {n.abas.novidades}
          </button>
          <button
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
            <div className="flex flex-col gap-6">
              {n.itensNovidades.map((item, i) => (
                <div key={i} className="flex gap-4">
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                    <Sparkles className="size-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <h4 className="font-semibold">{item.titulo}</h4>
                      <span className="text-[10px] uppercase font-bold text-muted-foreground">{item.data}</span>
                    </div>
                    <p className="text-sm text-muted-foreground">{item.descricao}</p>
                  </div>
                </div>
              ))}
            </div>
          )}

          {aba === "embreve" && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {n.itensEmBreve.map((item, i) => (
                <div key={i} className="rounded-xl border bg-card text-card-foreground shadow-sm p-4 flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                      {item.icone === "megaphone" && <Megaphone className="size-5" />}
                      {item.icone === "brain" && <Brain className="size-5" />}
                      {item.icone === "bar-chart" && <BarChart className="size-5" />}
                      {item.icone === "smartphone" && <Smartphone className="size-5" />}
                    </div>
                    <span className={cn(
                      "px-2.5 py-0.5 rounded-full text-xs font-bold",
                      item.tom === "warn" ? "bg-amber-100 text-amber-700" : "bg-sky-100 text-sky-700"
                    )}>
                      {item.status}
                    </span>
                  </div>
                  <h4 className="font-semibold">{item.titulo}</h4>
                  <p className="text-sm text-muted-foreground flex-1">{item.descricao}</p>
                  {item.previsao && (
                    <span className="text-xs font-medium text-muted-foreground mt-2 block">
                      {item.previsao}
                    </span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

