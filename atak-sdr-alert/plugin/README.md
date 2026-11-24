# Intégration dans le SDK ATAK-CIV

Le code du plugin ne peut se compiler qu'avec le SDK ATAK-CIV (tak.gov), qui fournit
`main.jar`, le plugin Gradle ATAK et le `plugintemplate`. La version du SDK doit être
**exactement** celle d'ATAK installée sur le téléphone.

## Étapes

1. Télécharger le SDK ATAK-CIV correspondant à la version d'ATAK du S22 Ultra
   (ATAK > Paramètres > À propos).
2. Copier `plugin-examples/plugintemplate` du SDK vers un nouveau dossier, par
   exemple `sdralert/`. Vérifier qu'il compile tel quel (`./gradlew assembleCivDebug`)
   et se charge dans ATAK **avant** toute modification.
3. Dans ce dossier :
   - Remplacer le `namespace`/`applicationId` par `fr.mariusbotkin.sdralert.plugin`.
   - Supprimer les sources Java du template (`com/atakmap/android/plugintemplate/...`).
   - Copier `plugin/src/main/java/fr/...` et `plugin/src/main/res/layout`,
     `res/values/strings.xml` (fusionner avec celui du template).
   - Copier les sources de `core/src/main/java/fr/...` dans `src/main/java/`
     (ou déclarer `core` comme module Gradle et `implementation project(':core')`).
   - Remplacer `src/main/assets/plugin.xml` par celui d'ici.
   - Ajouter au `AndroidManifest.xml` du template les permissions listées dans
     `plugin/src/main/AndroidManifest.xml` (garder le reste du manifeste du template).
   - Garder `res/drawable/ic_launcher` et le style `ATAKPluginTheme` du template.
4. `./gradlew assembleCivDebug`, installer l'APK, puis dans ATAK :
   Paramètres > Outils > Gestion des plugins > charger « Alerte SDR ».
5. Installer l'application **RTL2832U driver** (Play Store ou F-Droid), brancher la clé,
   ouvrir le panneau « Alerte SDR » et appuyer sur **Démarrer**. Accepter la permission
   USB demandée par le pilote.

## À vérifier à la première compilation

Le code a été vérifié contre l'API Android 13 réelle, mais les classes ATAK ont été
écrites d'après le plugintemplate classique, sans le SDK sous la main. Si le
compilateur se plaint, regarder ces points en priorité :

| Fichier | Point d'API ATAK |
|---|---|
| `SdrAlertLifecycle` | `transapps.maps.plugin.lifecycle.Lifecycle`. Les SDK 5.x récents utilisent parfois `gov.tak.api.plugin.IPlugin` : dans ce cas, garder la classe d'entrée du template et y créer `SdrAlertMapComponent`. |
| `SdrAlertTool` | `com.atak.plugins.impl.AbstractPluginTool` (constructeur à 5 arguments). |
| `SdrAlertDropDownReceiver` | `DropDownReceiver.showDropDown(...)`, `PluginLayoutInflater.inflate(...)`. |
| `CotAlertMarker` | `CotEvent`, `CotPoint`, `CotDetail`, `CoordinatedTime.addMinutes`, `CotMapComponent.getInternal/ExternalDispatcher()`. |

## Réglages du panneau

| Réglage | Plage | Défaut |
|---|---|---|
| Bande | PMR446, LPD433, SRD 868, ISM 915 | PMR446 |
| Seuil au-dessus du bruit | 3 à 30 dB | 10 dB |
| Durée minimale | 100 à 3000 ms | 300 ms |
| Repos entre alertes (par canal) | 5 à 300 s | 30 s |
| Gain | auto, 1 à 50 dB | 30 dB |
| Son / vibration | oui/non | oui |
| Marqueur sur la carte (type inconnu `a-u-G`) | oui/non | non |
| Partager le marqueur avec l'équipe | oui/non | non |

Le marqueur est posé à la **position de l'opérateur** (pas celle de l'émetteur,
qu'une seule antenne ne permet pas de localiser) et expire au bout de 15 minutes.

## Premier test terrain

1. Seuil 10 dB, durée 300 ms, gain 30 dB.
2. Talkie PMR446 à quelques mètres, canal 1, appui sur PTT 2 secondes :
   la cascade doit montrer une trace sur PMR 1 et une alerte doit arriver.
3. Recommencer en s'éloignant. Si la clé sature près du talkie (traces sur
   plusieurs canaux), baisser le gain.
