# Alerte SDR — plugin ATAK-CIV (RTL-SDR, PMR446)

Plugin ATAK-CIV qui utilise une clé RTL-SDR branchée en USB OTG pour :

- **surveiller** une bande (PMR446 par défaut, aussi LPD433, SRD 868, ISM 915) ;
- **afficher la cascade** (spectre + waterfall) dans le panneau ATAK ;
- **alerter** (notification, son, vibration, marqueur CoT optionnel) quand un canal
  dépasse le bruit d'un seuil réglable pendant une durée minimale.

Pas de décodage ni d'identification : le plugin signale de l'énergie sur un canal,
l'opérateur interprète.

## Organisation

```
atak-sdr-alert/
├── core/      Java pur, sans Android : DSP, bandes, détection, client rtl_tcp.
│              Compilé et testé sur PC (gradle :core:test).
└── plugin/    Code Android/ATAK : panneau, cascade, USB, alertes, CoT.
               Se compile à l'intérieur du SDK ATAK-CIV (voir plugin/README.md).
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
gradle :core:runCli -PcliArgs="pmr446 127.0.0.1 1234 10"
```

Une ligne `[niveaux]` par seconde (`.` calme, `+` au-dessus du bruit, `#` actif) et
une ligne `[ALERTE]` quand un talkie émet.

## Tests automatiques

```bash
gradle :core:test
```

14 tests : FFT, bandes et plan de fréquences, détecteur (chauffe, anti-rebond, repos,
plancher gelé, sous le seuil), et deux tests de bout en bout avec un faux serveur
rtl_tcp (signal synthétique sur PMR 9 qui déclenche l'alerte, changement de bande à chaud).

## Feuille de route

1. ✅ Cœur DSP + détection + client rtl_tcp (testés).
2. ✅ Code du plugin : panneau, cascade, détection USB, alertes, CoT.
3. ⏳ Intégration dans le plugintemplate du SDK et premier chargement sur le S22 Ultra.
4. ⏳ Test réel avec un talkie PMR446, à distance croissante, et réglage des seuils.
5. Plus tard : écoute audio NFM du canal en alerte, HackRF (2,4 / 5,8 GHz).
