package com.emanuele.gestionespese.data.local

fun sottoKey(categoria: String, sottocategoria: String): String =
    "${categoria.trim().lowercase()}||${sottocategoria.trim().lowercase()}"

fun utcKey(utente: String, tipologia: String, categoria: String, sottocategoria: String): String =
    "${utente.trim().lowercase()}||${tipologia.trim().lowercase()}||${categoria.trim().lowercase()}||${sottocategoria.trim().lowercase()}"