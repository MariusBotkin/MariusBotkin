# Plugin ATAK-CIV « SDR Alerte »

Le même moteur que l'application SDR Alerte, mais **dans ATAK** : un bouton dans la
barre d'outils ouvre un panneau avec la cascade, les réglages et l'historique des alertes.
À chaque alerte, le plugin pose directement un marqueur (type inconnu `a-u-G`) sur la carte,
et peut le partager avec l'équipe par les connexions d'ATAK (serveur TAK compris).

Projet construit sur le `plugintemplate` officiel d'ATAK-CIV
(github.com/TAK-Product-Center/atak-civ, API `gov.tak.api.plugin.IPlugin`).
Cible : **ATAK 5.8.0** (`ATAK_VERSION` dans `app/build.gradle`, ou `-PatakVersion=…`).

## Compilation automatique (GitHub Actions)

Pour compiler, il faut l'API d'ATAK (`main.jar`), qui n'est pas dans le dépôt public.
Le plugin Gradle officiel `atak-gradle-takdev` la télécharge depuis
`artifacts.tak.gov` avec un compte **tak.gov** (gratuit).

1. Créer un compte sur https://tak.gov et vérifier qu'il donne accès à
   https://artifacts.tak.gov (identifiant + mot de passe, ou jeton).
2. Sur GitHub : dépôt > **Settings > Secrets and variables > Actions > New repository secret**
   - `TAKREPO_USER` : identifiant tak.gov
   - `TAKREPO_PASSWORD` : mot de passe (ou jeton) tak.gov
3. Relancer le workflow « APK SDR Alerte » (onglet Actions > Run workflow) ou pousser un commit.
4. Le plugin apparaît dans les **Releases** : « Plugin ATAK SDR Alerte — ATAK 5.8.0 ».

Sans ces secrets, le job du plugin est simplement ignoré (l'application autonome est
toujours compilée).

## Compilation sur PC

```bash
cd atak-sdr-alert/plugin
nano local.properties   # contenu ci-dessous
./gradlew assembleCivDebug
# APK : app/build/outputs/apk/civ/debug/
```

`local.properties` :

```
sdk.dir=/chemin/vers/Android/Sdk
takrepo.url=https://artifacts.tak.gov/artifactory/maven
takrepo.user=<identifiant tak.gov>
takrepo.password=<mot de passe tak.gov>
```

## Installation

1. Installer l'APK du plugin sur le téléphone.
2. ATAK > Paramètres > Outils > **Gestion des plugins** : charger « SDR Alerte ».
3. Installer **RTL2832U driver** (Play Store ou F-Droid), fermer RF Analyzer, brancher la clé.
4. Toucher l'icône SDR Alerte dans la barre d'outils, puis **Démarrer**, et accepter la
   permission USB du pilote.

Si ATAK refuse le plugin (message de signature ou de version), noter le message exact :
- version : la version visée doit être exactement celle d'ATAK (Paramètres > À propos) ;
- signature : l'ATAK du Play Store peut exiger un plugin signé par TAK.gov
  (service de signature des plugins tiers sur tak.gov).

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

Le marqueur est posé à la **position de l'opérateur** (pas celle de l'émetteur, qu'une
seule antenne ne permet pas de localiser) et expire au bout de 15 minutes.

## Fichiers

| Fichier | Rôle |
|---|---|
| `SdrAlertPlugin` | Entrée du plugin : bouton de barre d'outils, panneau (`Pane`). |
| `SdrAlertPanel` | Contenu du panneau (`res/layout/sdr_alert_panel.xml`). |
| `SdrAlertController` | Moteur de surveillance, réglages, historique, alertes. |
| `WaterfallView` | Spectre + cascade. |
| `UsbDongleMonitor` | Détection de la clé RTL-SDR (API USB Host). |
| `RtlDriverLauncher` | Lancement du pilote « RTL2832U driver » (rtl_tcp local). |
| `AlertNotifier` | Notification, son, vibration. |
| `CotAlertMarker` | Marqueur CoT sur la carte et partage. |
| `../core` | Moteur DSP / détection (Java pur, testé). |

Licence : le gabarit de build provient du dépôt ATAK-CIV (GPLv3).
