# DSDPlus Launcher

Une interface graphique Windows simple pour utiliser [DSDPlus](https://www.dsdplus.com)
sans avoir à retenir de lignes de commande.

DSDPlus lui-même n'est **pas inclus** dans ce dépôt : téléchargez-le depuis le
site officiel (https://www.dsdplus.com) puis indiquez à DSDPlus Launcher où se
trouve `DSDPlus.exe`.

## Ce que fait l'application

- **Profils** : enregistrez autant de configurations que vous avez de
  scanners/sites (chemin de `DSDPlus.exe`, dossier de travail, arguments de
  ligne de commande, notes).
- **Démarrage / arrêt en un clic** : la fenêtre console de DSDPlus reste
  visible normalement (pour ses raccourcis clavier habituels), le launcher
  se contente de la lancer et de la surveiller.
- **Assistant de paramètres** : détecte automatiquement les périphériques
  audio Windows (entrée/sortie) et génère un exemple d'arguments à adapter.
- **Journal** : trace les événements de démarrage/arrêt et tout ce que
  DSDPlus écrit sur sa sortie standard.
- **Redémarrage automatique** (optionnel) en cas d'arrêt inattendu.
- **Icône dans la zone de notification** avec démarrage/arrêt rapide.

> ℹ️ La syntaxe exacte des arguments de DSDPlus varie selon la version
> installée (standard, Fastlane, etc.). Reportez-vous au fichier
> `DSDPlus.txt` fourni avec votre téléchargement pour la syntaxe précise ;
> l'assistant de paramètres ne fournit qu'un modèle de départ.

## Prérequis

- Windows 10/11
- [.NET 8 Desktop Runtime](https://dotnet.microsoft.com/download/dotnet/8.0)
  (uniquement si vous utilisez le build "framework-dependent" ; le build
  autonome ci-dessous n'en a pas besoin)

## Récupérer l'exécutable

Un exécutable Windows prêt à l'emploi est généré automatiquement à chaque
mise à jour du code par le workflow GitHub Actions
[`build-dsdplus-launcher.yml`](../.github/workflows/build-dsdplus-launcher.yml) :
onglet **Actions** du dépôt → dernier run → artefact `DSDPlusLauncher-win-x64`.

## Compiler soi-même (sur Windows, avec le SDK .NET 8)

```powershell
cd dsdplus-launcher/src/DSDPlusLauncher
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o ../../publish
```

L'exécutable `DSDPlusLauncher.exe` sera généré dans `dsdplus-launcher/publish/`.

Pour un simple test de développement :

```powershell
dotnet run --project dsdplus-launcher/src/DSDPlusLauncher
```

## Utilisation

1. Lancez `DSDPlusLauncher.exe`.
2. Cliquez sur **Nouveau** pour créer un profil, indiquez le chemin vers
   votre `DSDPlus.exe` (bouton *Parcourir...*).
3. Utilisez le bouton **Assistant...** pour générer un modèle d'arguments
   à partir de vos périphériques audio, puis ajustez-le si besoin.
4. Cliquez sur **Démarrer** pour lancer DSDPlus, **Arrêter** pour le stopper.
5. Créez d'autres profils pour vos différentes configurations (scanners,
   sites, entrées audio) et basculez de l'un à l'autre en un clic.

Vos profils sont enregistrés dans
`%AppData%\DSDPlusLauncher\profiles.json` (accessible via le menu
*Outils → Ouvrir le dossier de configuration*).
