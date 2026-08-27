const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, 'backend', 'crm-app', 'src', 'main', 'resources', 'textos.json');
let data = JSON.parse(fs.readFileSync(filePath, 'utf8'));

// Adiciona o selo 'novo'
if (data.novidades && data.novidades.itensNovidades) {
  data.novidades.itensNovidades = data.novidades.itensNovidades.map((item, index) => {
    // marca como novo os itens de 2026-07-22
    if (item.data === "2026-07-22") {
      return { ...item, novo: true };
    }
    return { ...item, novo: false };
  });
}

// Em "itensEmBreve" - certificar que usa tokens compativeis com PillDeStatus ou similar
// PillDeStatus usa: "sucesso" | "info" | "atencao" | "perigo" | "neutro"
if (data.novidades && data.novidades.itensEmBreve) {
  data.novidades.itensEmBreve = data.novidades.itensEmBreve.map((item) => {
    if (item.tom === "warn") item.tom = "atencao";
    if (item.tom === "sky") item.tom = "info";
    return item;
  });
}
data.novidades.novoTag = "NOVO";

fs.writeFileSync(filePath, JSON.stringify(data, null, 2));
console.log("textos.json atualizado");
