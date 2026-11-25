# SDR Alerte — RTL-SDR + ATAK (PMR446)

Deux formes, même moteur :

- **Application Android « SDR Alerte »** (`app/`) — prête à installer, sans SDK ATAK.
  Elle envoie ses marqueurs d'alerte à l'ATAK du téléphone par le réseau local.
- **Plugin ATAK-CIV 5.8.0** (`plugin/`) — intégré à ATAK (bouton + panneau), compilé
  automatiquement par GitHub Actions dès que des identifiants tak.gov sont ajoutés en
  secrets du dépôt (voir `plugin/README.md`).

Avec une clé RTL-SDR branchée en USB OTG, elles permettent de :

- **surveiller** une bande (PMR446 par défaut, aussi LPD433, SRD 868, ISM 915) ;
- **afficher la cascade** (spectre + waterfall) dans le panneau ATAK ;
- **alerter** (notification, son, vibration, marqueur CoT optionnel) quand un canal
  dépasse le bruit d'un seuil réglable pendant une durée minimale.

Pas de décodage ni d'identification : le plugin signale de l'énergie sur un canal,
l'opérateur interprète.

## Installer l'application (APK)

1. Sur le téléphone, ouvrir la page **Releases** du dépôt GitHub et télécharger
   `SDR-Alerte.apk` (version « dernière compilation »).
2. L'ouvrir et autoriser l'installation depuis le navigateur / le gestionnaire de fichiers.
3. Installer **RTL2832U driver** (Play Store ou F-Droid).
4. Fermer RF Analyzer, brancher la clé, ouvrir SDR Alerte, appuyer sur **Démarrer**,
   accepter la permission USB du pilote, les notifications et la localisation.
5. Passer sur ATAK : la surveillance continue en arrière-plan (notification permanente
   « Surveillance … » avec un bouton Arrêter).

L'APK est recompilé automatiquement par GitHub Actions à chaque modification
(`.github/workflows/sdr-alerte-apk.yml`). Les nouvelles versions s'installent par-dessus
les anciennes (même clé de signature).

### Marqueur dans ATAK

À chaque alerte, l'appli envoie un message CoT de type inconnu (`a-u-G`), nommé
`RF PMR 3` par exemple, à **votre position GPS** (pas celle de l'émetteur), valable 15 min :

- **Marqueur dans ATAK sur ce téléphone** : UDP vers `127.0.0.1:4242`, l'entrée UDP
  par défaut d'ATAK. Si rien n'apparaît : ATAK > Paramètres > Préférences réseau >
  Gérer les entrées > vérifier qu'une entrée UDP sur le port 4242 existe et est activée
  (sinon l'ajouter : protocole UDP, adresse 0.0.0.0, port 4242).
- **Partager sur le réseau local** : multicast SA `239.2.3.1:6969`, reçu par les ATAK
  connectés au même Wi-Fi / maillage. Ça ne passe pas par un serveur TAK.

## Organisation

```
atak-sdr-alert/
├── core/      Java pur, sans Android : DSP, bandes, détection, client rtl_tcp, CoT.
│              Compilé et testé sur PC (./gradlew :core:test).
├── app/       Application Android autonome (écran, service d'arrière-plan, CoT UDP).
└── plugin/    Plugin ATAK 5.8.0 (projet Gradle basé sur le plugintemplate officiel) :
               panneau, cascade, USB, alertes, CoT. Voir plugin/README.md.
```

### Chaîne de traitement

```
Clé RTL-SDR ──USB──> appli « RTL2832U driver » ──rtl_tcp (127.0.0.1:14423)──> RtlTcpSource
   ──IQ u8──> SpectrumAnalyzer (FFT 4096, Hann, moyenne)  ──spectre dB──┬──> WaterfallView
                                                                       └──> ChannelLevelExtractor
   ──niveau par canal──> ChannelDetector (plancher glissant, seuil, anti-rebond, repos) ──> AlertEvent
   ──> notification / son / vibration / marqueur CoT « a-u-G » / historique
```

- **Accord décalé** : la bande est placée à côté du pic DC de la RTL-SDR
  (PMR446 : accord à 445,98 MHz, échantillonnage 1,024 MS/s, case FFT de 250 Hz).
- **Plancher de bruit** : 20e percentile sur 5 s, par canal, gelé pendant une
  émission (une émission longue ne fait pas monter le plancher).
- **Hystérésis** de 3 dB et **délai de repos** par canal (30 s par défaut) contre le spam.
- **Changement de bande/gain à chaud** sans couper la connexion au pilote.

### Pourquoi passer par l'application « RTL2832U driver » ?

C'est le pilote Android standard (marto.rtl_tcp_andro, utilisé aussi par RF Analyzer
et SDR Touch). Il gère la permission USB et l'accès bas niveau à la clé, et sert les
échantillons en rtl_tcp. Le plugin reste en Java pur, sans bibliothèque native à
compiler pour ATAK. Le plugin détecte quand même la clé lui-même (API USB Host)
pour afficher son état. Le récepteur est derrière l'interface `IqSource` : un HackRF
ou un pilote USB intégré pourra être ajouté plus tard sans toucher au reste.

Une clé USB ne se partage pas : **fermer RF Analyzer** avant de démarrer la surveillance.

## Tester sans téléphone (PC + clé RTL-SDR)

```bash
rtl_tcp -a 127.0.0.1 -p 1234           # paquet rtl-sdr
./gradlew :core:runCli -PcliArgs="pmr446 127.0.0.1 1234 10"
```

Une ligne `[niveaux]` par seconde (`.` calme, `+` au-dessus du bruit, `#` actif) et
une ligne `[ALERTE]` quand un talkie émet.

## Tests automatiques

```bash
./gradlew :core:test
```

16 tests : FFT, bandes et plan de fréquences, détecteur (chauffe, anti-rebond, repos,
plancher gelé, sous le seuil), message CoT et envoi UDP, et deux tests de bout en bout avec un faux serveur
rtl_tcp (signal synthétique sur PMR 9 qui déclenche l'alerte, changement de bande à chaud).

## Feuille de route

1. ✅ Cœur DSP + détection + client rtl_tcp + CoT (testés).
2. ✅ Application Android autonome, APK compilé par GitHub Actions.
3. ✅ Plugin aligné sur le plugintemplate officiel (API IPlugin), compilation CI prête.
4. ⏳ Test réel de l'APK sur le S22 Ultra avec un talkie PMR446.
5. ⏳ Identifiants tak.gov en secrets GitHub, puis premier chargement du plugin dans ATAK 5.8.0.
6. Plus tard : écoute audio NFM du canal en alerte, HackRF (2,4 / 5,8 GHz).
