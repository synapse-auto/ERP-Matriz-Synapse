import { cn } from "@/lib/utils";

interface Props {
  /** 0 a 100. */
  valor: number;
  rotulo: string;
  className?: string;
  /** Preenchimento fora do padrão (ex.: alerta perto do limite). */
  tom?: "primario" | "atencao" | "sucesso";
}

const PREENCHIMENTO = {
  primario: "bg-primary",
  atencao: "bg-cor-erro",
  sucesso: "bg-cor-sucesso",
} as const;

/** Barra de progresso acessível: o rótulo diz o que ela mede, o valor vai em aria-valuenow. */
export function BarraDeProgresso({ valor, rotulo, className, tom = "primario" }: Props) {
  const limitado = Math.max(0, Math.min(100, valor));
  return (
    <div
      role="progressbar"
      aria-label={rotulo}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={limitado}
      className={cn("h-2 w-full overflow-hidden rounded-full bg-muted", className)}
    >
      <div
        className={cn("h-full rounded-full transition-[width] duration-500 ease-out motion-reduce:transition-none", PREENCHIMENTO[tom])}
        style={{ width: `${limitado}%` }}
      />
    </div>
  );
}
