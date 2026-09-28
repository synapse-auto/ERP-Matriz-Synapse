import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";
import { PaginaAutomacao } from "@/components/automacao/pagina-automacao";

export default function Automacao() {
  return (
    <ExigeCapacidade capacidade="automacao.ver">
      <PaginaAutomacao />
    </ExigeCapacidade>
  );
}
