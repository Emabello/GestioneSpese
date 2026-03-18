const API_KEY = "9c7f3e2a6b8d4f1c0e9a7b6d5c4f3a2b";

function doGet(e) {
  try {
    checkKey_(e);
    const p = e.parameter || {};
    const resource = String(p.resource || "").toLowerCase();

    if (resource === "spese") return json_(listSpese_());
    if (resource === "spesa") {
      const id = String(p.id || "").trim();
      if (!id) return json_({ error: "Missing id" }, 400);
      return json_({ data: getSpesaById_(id) });
    }
    if (resource === "categorie") return json_(listCategorie_());
    if (resource === "sottocategorie") {
      const categoriaId = String(p.categoria_id || "").trim();
      return json_(listSottocategorie_(categoriaId));
    }
    if (resource === "categoria_link") {
      const categoriaId = String(p.categoria_id || "").trim();
      const sottocategoriaId = String(p.sottocategoria_id || "").trim();
      return json_(resolveCategoriaLinkId_(categoriaId, sottocategoriaId));
    }

    // ── Batch endpoint: restituisce tutti i dati in una sola chiamata ──────
    if (resource === "sync_all") {
      const utente = String(p.utente || "").trim();
      if (!utente) return json_({ error: "Missing utente" }, 400);
      return json_({
        ok: true,
        data: {
          tipologie:      listTipologie_(),
          categorie:      listCategorieRaw_(),
          sottocategorie: listSottocategorieRaw_(),
          conti:          listContiPerUtente_(utente),
          utcs:           listUtcsPerUtente_(utente),
          spese:          listSpesePerUtente_(utente)
        }
      });
    }

    return json_({ error: "Unknown resource" }, 404);
  } catch (err) {
    return json_({ error: String(err && err.message ? err.message : err) }, 500);
  }
}

function doPost(e) {
  try {
    checkKey_(e);
    const body = parseJsonBody_(e);
    const resource = String(body.resource || "").toLowerCase();

    if (resource === "utente") {
      const user = String(body.user || "").trim();
      const password = String(body.password || "").trim();
      if (!user) return json_({ error: "Missing user" }, 400);
      if (!password) return json_({ error: "Missing password" }, 400);
      return json_({ data: getUtenteByUser_(user, password) });
    }

    if (resource === "spese_batch") {
      const rows = body.rows || [];
      const saved = rows.map(insertSpesa_);
      return json_({ ok: true, saved });
    }

    if (resource === "spesa") return json_(insertSpesa_(body));
    if (resource === "spesa_update") return json_(updateSpesa_(body));
    if (resource === "spesa_delete") return json_(deleteSpesa_(body));

    return json_({ error: "Unknown resource" }, 404);
  } catch (err) {
    return json_({ error: String(err && err.message ? err.message : err) }, 500);
  }
}

function checkKey_(e) {
  const key = (e && e.parameter && e.parameter.key) ? String(e.parameter.key) : "";
  if (key !== API_KEY) throw new Error("Unauthorized");
}

function parseJsonBody_(e) {
  if (!e || !e.postData || !e.postData.contents) throw new Error("Missing body");
  return JSON.parse(e.postData.contents);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function getSheet_(name) {
  const sh = SpreadsheetApp.getActive().getSheetByName(name);
  if (!sh) throw new Error("Sheet " + name + " not found");
  return sh;
}

function listSpese_() {
  const sh = getSheet_("SPESE");
  const last = sh.getLastRow();
  if (last < 2) return [];

  const rows = sh.getRange(2, 1, last - 1, 11).getValues();
  return rows.map(r => ({
    id: Number(r[0]),
    utente_id: String(r[1] || ""),
    data: toIsoDate_(r[2]),
    conto_id: String(r[3] || ""),
    importo: Number(r[4] || 0),
    tipo: String(r[5] || ""),
    categoria_link_id: String(r[6] || ""),
    categoria_id: String(r[6] || ""),
    categoria: String(r[6] || ""),
    sottocategoria_id: String(r[7] || ""),
    sottocategoria: String(r[7] || ""),
    descrizione: String(r[8] || ""),
    mese: Number(r[9] || 0),
    anno: Number(r[10] || 0),
    metodo_pagamento: String(r[3] || "")
  }));
}

function listCategorie_() {
  const sh = getSheet_("CATEGORIE");
  const last = sh.getLastRow();
  if (last < 2) return [];
  const rows = sh.getRange(2, 1, last - 1, 3).getValues();
  return rows.map(r => ({ id: String(r[0] || ""), nome: String(r[1] || ""), ordine: Number(r[2] || 0), attiva: true }));
}

function listSottocategorie_(categoriaId) {
  const sh = getSheet_("SOTTOCATEGORIE");
  const last = sh.getLastRow();
  if (last < 2) return [];
  const rows = sh.getRange(2, 1, last - 1, 4).getValues();
  return rows
    .filter(r => !categoriaId || String(r[1] || "") === categoriaId)
    .map(r => ({
      ordine: Number(r[3] || 0),
      sottocategoria: { id: String(r[0] || ""), nome: String(r[2] || ""), ordine: Number(r[3] || 0), attiva: true }
    }));
}

function resolveCategoriaLinkId_(categoriaId, sottocategoriaId) {
  if (!categoriaId) return [];
  const id = sottocategoriaId ? categoriaId + "|" + sottocategoriaId : categoriaId;
  return [{ id }];
}

function getUtenteByUser_(utente, password) {
  const sh = getSheet_("UTENTE");
  const last = sh.getLastRow();
  if (last < 2) return null;
  const values = sh.getRange(2, 1, last - 1, 11).getValues();

  for (const r of values) {
    if (String(r[1] || "").trim() === utente && String(r[2] || "").trim() === password) {
      return { id: r[0], utente: r[1], password: r[2], nome: r[3], cognome: r[4], attivo: r[5], email: r[6] };
    }
  }
  return null;
}

function getSpesaById_(id) {
  const sh = getSheet_("SPESE");
  const last = sh.getLastRow();
  if (last < 2) return null;
  const values = sh.getRange(2, 1, last - 1, 11).getValues();

  for (const r of values) {
    if (String(r[0] || "").trim() === id) {
      return {
        id: r[0],
        utente_id: r[1],
        data: toIsoDate_(r[2]),
        conto_id: r[3],
        importo: r[4],
        tipo: r[5],
        categoria: r[6],
        sottocategoria: r[7],
        descrizione: r[8],
        mese: r[9],
        anno: r[10]
      };
    }
  }
  return null;
}

function insertSpesa_(body) {
  const sh = getSheet_("SPESE");
  const utenteId = String(body.utente_id || "").trim();
  const contoId = String(body.conto_id || body.metodo_pagamento || "").trim();
  const tipo = String(body.tipo || "").trim();
  const categoria = String(body.categoria || body.categoria_link_id || "").trim();
  const sottocategoria = String(body.sottocategoria || body.sottocategoria_id || "").trim();
  const descr = String(body.descrizione || body.note || "").trim();
  const importo = Number(body.importo);
  const dataStr = String(body.data || "").trim();

  if (!utenteId || !contoId || !dataStr || !Number.isFinite(importo)) {
    throw new Error("Missing/invalid fields (utente_id, conto_id, data, importo)");
  }

  const m = dataStr.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) throw new Error("Invalid date format. Expected YYYY-MM-DD");

  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  const dataObj = new Date(year, month - 1, day);
  const newId = nextId_(sh, 1);

  sh.appendRow([newId, utenteId, dataObj, contoId, importo, tipo, categoria, sottocategoria, descr, month, year]);
  return { id: newId };
}

function updateSpesa_(body) {
  const sh = getSheet_("SPESE");
  const id = Number(body.id);
  if (!Number.isFinite(id)) throw new Error("Missing id");

  const all = sh.getRange(2, 1, Math.max(sh.getLastRow() - 1, 0), 11).getValues();
  const idx = all.findIndex(r => Number(r[0]) === id);
  if (idx < 0) throw new Error("Spesa not found");

  const rowNumber = idx + 2;
  const m = String(body.data || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) throw new Error("Invalid date format. Expected YYYY-MM-DD");
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);

  sh.getRange(rowNumber, 3, 1, 9).setValues([[
    new Date(year, month - 1, day),
    String(body.conto_id || ""),
    Number(body.importo || 0),
    String(body.tipo || ""),
    String(body.categoria || ""),
    String(body.sottocategoria || ""),
    String(body.descrizione || ""),
    month,
    year
  ]]);

  return { ok: true };
}

function deleteSpesa_(body) {
  const sh = getSheet_("SPESE");
  const id = Number(body.id);
  if (!Number.isFinite(id)) throw new Error("Missing id");

  const all = sh.getRange(2, 1, Math.max(sh.getLastRow() - 1, 0), 1).getValues().flat();
  const idx = all.findIndex(v => Number(v) === id);
  if (idx < 0) throw new Error("Spesa not found");
  sh.deleteRow(idx + 2);
  return { ok: true };
}

function nextId_(sheet, idCol) {
  const last = sheet.getLastRow();
  if (last < 2) return 1;
  const ids = sheet.getRange(2, idCol, last - 1, 1).getValues().flat();
  return Math.max(0, ...ids.map(v => Number(v) || 0)) + 1;
}

function toIsoDate_(value) {
  const d = value instanceof Date ? value : new Date(value);
  if (isNaN(d.getTime())) return "";
  return Utilities.formatDate(d, Session.getScriptTimeZone(), "yyyy-MM-dd");
}

// ── Funzioni di supporto per endpoint sync_all ────────────────────────────────

function listTipologie_() {
  try {
    const sh = getSheet_("TIPOLOGIA");
    const last = sh.getLastRow();
    if (last < 2) return [];
    const headers = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0].map(h => String(h).toLowerCase().trim());
    const rows = sh.getRange(2, 1, last - 1, sh.getLastColumn()).getValues();
    const idx = (k) => headers.indexOf(k);
    return rows.map(r => ({
      id:            String(r[idx("id")] || r[0] || ""),
      descrizione:   String(r[idx("descrizione")] !== undefined ? r[idx("descrizione")] : r[1] || ""),
      attivo:        r[idx("attivo")] !== false && r[idx("attivo")] !== 0 && r[idx("attivo")] !== "",
      tipo_movimento: String(r[idx("tipo_movimento")] || "uscita")
    }));
  } catch(e) { return []; }
}

function listCategorieRaw_() {
  try {
    const sh = getSheet_("CATEGORIE");
    const last = sh.getLastRow();
    if (last < 2) return [];
    const rows = sh.getRange(2, 1, last - 1, 3).getValues();
    return rows.map(r => ({
      id:          String(r[0] || ""),
      descrizione: String(r[1] || ""),
      ordine:      Number(r[2] || 0),
      attiva:      true
    }));
  } catch(e) { return []; }
}

function listSottocategorieRaw_() {
  try {
    const sh = getSheet_("SOTTOCATEGORIE");
    const last = sh.getLastRow();
    if (last < 2) return [];
    const rows = sh.getRange(2, 1, last - 1, 4).getValues();
    return rows.map(r => ({
      id_categoria: String(r[1] || ""),
      descrizione:  String(r[2] || ""),
      ordine:       Number(r[3] || 0),
      attiva:       true
    }));
  } catch(e) { return []; }
}

function listContiPerUtente_(utente) {
  try {
    const sh = getSheet_("UC");
    const last = sh.getLastRow();
    if (last < 2) return [];
    const headers = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0].map(h => String(h).toLowerCase().trim());
    const rows = sh.getRange(2, 1, last - 1, sh.getLastColumn()).getValues();
    const utenteIdx = headers.findIndex(h => h === "id_utente" || h === "utente");
    const contoIdx  = headers.findIndex(h => h === "id_conto"  || h === "conto");
    const attivoIdx = headers.indexOf("attivo");
    return rows
      .filter(r => String(r[utenteIdx] || "").trim() === utente)
      .map(r => ({
        ID_UTENTE: String(r[utenteIdx] || ""),
        ID_CONTO:  String(r[contoIdx] || ""),
        attivo:    attivoIdx < 0 || (r[attivoIdx] !== false && r[attivoIdx] !== 0 && r[attivoIdx] !== "")
      }));
  } catch(e) { return []; }
}

function listUtcsPerUtente_(utente) {
  try {
    const sh = getSheet_("UTCS");
    const last = sh.getLastRow();
    if (last < 2) return [];
    const headers = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0].map(h => String(h).toLowerCase().trim());
    const rows = sh.getRange(2, 1, last - 1, sh.getLastColumn()).getValues();
    const utenteIdx    = headers.findIndex(h => h === "id_utente"        || h === "utente");
    const tipologiaIdx = headers.findIndex(h => h === "id_tipologia"     || h === "tipologia");
    const categoriaIdx = headers.findIndex(h => h === "id_categoria"     || h === "categoria");
    const sottoIdx     = headers.findIndex(h => h === "id_sottocategoria"|| h === "sottocategoria");
    const attivoIdx    = headers.indexOf("attivo");
    return rows
      .filter(r => String(r[utenteIdx] || "").trim() === utente)
      .map(r => ({
        ID_UTENTE:         String(r[utenteIdx] || ""),
        ID_TIPOLOGIA:      String(r[tipologiaIdx] || ""),
        ID_CATEGORIA:      String(r[categoriaIdx] || ""),
        ID_SOTTOCATEGORIA: String(r[sottoIdx] || ""),
        attivo:            attivoIdx < 0 || (r[attivoIdx] !== false && r[attivoIdx] !== 0 && r[attivoIdx] !== "")
      }));
  } catch(e) { return []; }
}

function listSpesePerUtente_(utente) {
  try {
    const sh = getSheet_("SPESE");
    const last = sh.getLastRow();
    if (last < 2) return [];
    const rows = sh.getRange(2, 1, last - 1, 11).getValues();
    return rows
      .filter(r => String(r[1] || "").trim() === utente)
      .map(r => ({
        id:            Number(r[0]),
        utente:        String(r[1] || ""),
        data:          toIsoDate_(r[2]),
        conto:         String(r[3] || ""),
        importo:       Number(r[4] || 0),
        tipo:          String(r[5] || ""),
        categoria:     String(r[6] || ""),
        sottocategoria: String(r[7] || ""),
        descrizione:   String(r[8] || ""),
        mese:          Number(r[9] || 0),
        anno:          Number(r[10] || 0)
      }));
  } catch(e) { return []; }
}
