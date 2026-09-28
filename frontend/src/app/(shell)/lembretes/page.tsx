import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";
import { PaginaLembretes } from "@/components/lembretes/pagina-lembretes";

export default function Lembretes() {
  return (
    <ExigeCapacidade capacidade="lembretes.ver">
      <PaginaLembretes />
    </ExigeCapacidade>
  );
}
