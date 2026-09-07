export function ContadorDoPainel({
  valor,
  rotulo,
}: {
  valor: number;
  rotulo: string;
}) {
  return (
    <div className="rounded-xl bg-muted p-3">
      <p className="text-[0.7rem] font-semibold text-muted-foreground">{rotulo}</p>
      <p className="mt-1 text-xl font-bold text-foreground">{valor}</p>
    </div>
  );
}
