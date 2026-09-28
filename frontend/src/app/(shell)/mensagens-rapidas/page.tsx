import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";
import { PaginaMensagensRapidas } from "@/components/mensagens-rapidas/pagina-mensagens-rapidas";

export default function MensagensRapidas() {
  return (
    <ExigeCapacidade capacidade="mensagens_rapidas.usar">
      <PaginaMensagensRapidas />
    </ExigeCapacidade>
  );
}
