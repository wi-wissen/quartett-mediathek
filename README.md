# Quartett Mediathek

Eine Android-TV-App für die Mediatheken von **ARD, ZDF, 3sat und arte**. Beim Start siehst du deine Sendungen
und ihre neuen Folgen, ähnlich wie Abos bei YouTube. Dazu kommen eine gemeinsame Merkliste für alle vier
Mediatheken, Weiterschauen und die Empfehlungen der Sender.

Kein Konto, kein eigener Server: Abos, Merkliste und Fortschritt bleiben auf deinem Fernseher.

## Installation auf dem Fernseher

Die App gibt es nicht im Play Store, sie wird per Sideloading installiert.

1. Installiere auf dem Fernseher die App **Downloader** (von AFTVnews) aus dem Play Store bzw. Amazon Appstore.
2. Erlaube in den Einstellungen des Fernsehers, dass Downloader Apps aus unbekannten Quellen installieren darf.
   Auf Android TV / Google TV: *Einstellungen → System → Info → mehrmals auf „Android TV-Betriebssystem-Build“
   tippen*, dann *Einstellungen → Apps → Sicherheit → Unbekannte Quellen → Downloader*.
3. Öffne Downloader und gib diese Adresse ein:

   **`t1p.de/qmedia`**

4. Die neueste Version wird heruntergeladen. Bestätige die Installation.

Für ein Update wiederholst du Schritt 3 und 4. Abos, Merkliste und Fortschritt bleiben dabei erhalten.

Der Kurzlink zeigt auf
`https://github.com/wi-wissen/quartett-mediathek/releases/latest/download/quartett-mediathek.apk`.

## Bedienung

- **Steuerkreuz** wählt aus, **OK** öffnet, **Zurück** geht zurück.
- **OK lange drücken** auf einer Kachel: abonnieren, merken, als gesehen markieren.
- **Play/Pause** auf einer Kachel spielt die Folge direkt ab.
- Im Player springt **Links/Rechts** 10 Sekunden zurück bzw. 30 Sekunden vor. Gedrückt halten spult immer schneller.
- Die Oberfläche ist auf Deutsch, wenn der Fernseher auf Deutsch eingestellt ist, sonst auf Englisch.

## Woher kommen die Inhalte?

Empfehlungen und Streams kommen direkt von den Sendern (ARD, ZDF, arte). Abo-Feed, Suche und 3sat laufen über
[MediathekViewWeb](https://mediathekviewweb.de). Die App lädt einmal am Tag neu, oben rechts kannst du jederzeit
von Hand aktualisieren. Dieses Projekt ist kein Angebot der Sender und nicht mit ihnen verbunden.

## Entwicklung

Kotlin, Kotlin Multiplatform (`shared`) und Compose for TV (`app`). Quelltext und Kommentare sind englisch.

- Spezifikation: [`docs/SPEC.md`](docs/SPEC.md)
- Klickbares Mockup: [`docs/mockup.html`](docs/mockup.html) im Browser öffnen
- Bauen: `./gradlew :shared:jvmTest :app:assembleDebug` (Android SDK nötig)

## Release

1. Einmalig einen Signaturschlüssel erzeugen und **gut aufbewahren** (ohne ihn sind keine Updates möglich):
   ```
   keytool -genkeypair -v -keystore release.jks -alias quartett -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Im Repository unter *Settings → Secrets and variables → Actions* anlegen:
   `QM_KEYSTORE_BASE64` (Ausgabe von `base64 -w0 release.jks`), `QM_KEYSTORE_PASSWORD`, `QM_KEY_ALIAS`,
   `QM_KEY_PASSWORD`.
3. *Actions → Release → Run workflow*, Version eingeben (z. B. `1.0.0`). Die Action baut das signierte APK,
   legt den Tag `v1.0.0` an und veröffentlicht das Release mit `quartett-mediathek.apk`.

## Lizenz

MIT, siehe [`LICENSE`](LICENSE). Mitgelieferte Icons und Schriften: siehe
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
