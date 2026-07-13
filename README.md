# NutriSonar

Technische Analyse von Nahrungsergänzungsmitteln als Android-App — keine
ärztliche Beratung. Überblick, welche Nährstoffe eine Person pro Tag
aufnimmt, ob tolerierbare Höchstmengen (UL) überschritten werden und welche
möglichen Folgen das hat.

## Was die App macht

- **Personen-Profile:** Name/Kürzel, Alter, Gewicht; Medikamente optional
  und jederzeit nachreichbar. Pro Person eine eigene Produktliste und ein
  eigener Bericht. Alle Daten bleiben lokal auf dem Gerät.
- **Etiketten fotografieren:** Die Vision-KI liest Produktname, Nährstoffe
  und Mengen ab; Bezugsgrößen („pro 6 Tabletten“) werden auf ein Stück
  normiert, %NRV ignoriert. Nicht sicher lesbare Werte werden als
  „⚠️ unsicher“ markiert — nie geraten — und sind antippbar korrigierbar.
  Pro Produkt wird das Einnahmeschema erfasst (1-0-1, „jeden 2. Tag“, …).
- **Deterministische Analyse:** Die App selbst summiert jeden Nährstoff über
  alle Produkte (Alias-Erkennung für Doppelungen wie B12/Cobalamin) und
  vergleicht mit einer fest eingebauten UL-Tabelle (EFSA primär, sonst
  US-Wert, gekennzeichnet; Niacin mit beiden formabhängigen Grenzwerten).
  Ampel: 🔴 deutlich über Grenze, ⚠️ am Limit, ✅ unkritisch. Die KI liefert
  nur Texte: mögliche Folgen, Wechselwirkungen (inkl. Medikamente,
  Grapefruit, Piperin, Ca/Fe/Mg, Vitamin K), Kurzfazit.
- **Bericht:** native Ansicht mit Gesamt-Ampel, Verlauf, Tagesbilanz,
  Wechselwirkungen und Kurzfazit; Export als Markdown-Datei
  (`analyse-<kürzel>-JJJJ-MM-TT.md`) über den Teilen-Dialog.
- **Simulieren:** Szenarien („Niacin weglassen“) lokal durchrechnen —
  ohne Speichern, ohne KI-Kosten.

## Technik

- Kotlin, ViewBinding, Material Components; gemeinsame Bibliothek
  `common/android` (Theme, Toolbar, komplette Provider-/Key-Verwaltung
  mit Kostenzähler).
- Direkte HTTP-Anbindung (OkHttp) an Anthropic Messages API bzw. OpenAI
  Chat Completions; Kamera ohne eigene Berechtigung über den System-Intent.
- Datenhaltung als JSON in den App-Dateien; minSdk 26, targetSdk 34.

## Build

CI baut per `.github/workflows/build-nutrisonar.yml` und veröffentlicht
die APKs samt Versions-Manifest im GitHub-Release `nutrisonar-latest`;
`deploy-website.yml` synct beides nach [appsonar.de](https://appsonar.de).
Die App prüft beim Start und über „Nach Update suchen“ gegen das Manifest
auf neue Versionen (gemeinsamer `UpdateChecker` aus `common/android`).
