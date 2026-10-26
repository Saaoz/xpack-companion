# XPack Companion — mod client

Mod **client uniquement** qui envoie en temps réel tes progrès Minecraft à XPack et prévient avant toute triche. Les modpacks n'ont **jamais** à le déclarer comme dépendance :
- un serveur sans le mod accepte le joueur ;
- un modpack sans le mod fonctionne normalement.

| Loader | Minecraft | Jar |
|---|---|---|
| Fabric (sans Fabric API) | 1.21.1 | `fabric/build/libs/xpack-companion-fabric-1.21.1-<version>.jar` |
| NeoForge | 1.21.1 | `neoforge/build/libs/xpack-companion-neoforge-1.21.1-<version>.jar` |
| Forge | 1.20.1 (Java 17) | `forge/build/libs/xpack-companion-forge-1.20.1-<version>.jar` (remappé pour Forge : c'est celui-là qu'il faut distribuer, pas celui de `build/devlibs`) |

## Ce que fait le mod

- **Liaison du compte** (#34) : uniquement depuis les réglages (menu pause → ⚙, ou `/xpack settings`, aussi au menu principal). Le code s'y affiche avec **Ouvrir le site** (`/link?code=…` prérempli, le site demande « Lier le compte Minecraft *Steve* ? ») et **Copier le code**. Dans le chat, un seul rappel par lancement (« lie ton compte via ⚙ »), jamais de code ni de lien. Un code expiré n'est pas relancé tout seul.
- **Mondes de test** : un monde que le mod découvre en créatif (ou en spectateur, ou créé en mode créatif) est un monde de test. Le joueur peut aussi en déclarer un depuis les réglages. Dans ces mondes, **rien n'est envoyé et rien n'est signalé** : ni succès, ni pénalité. C'est définitif pour le monde concerné, et les autres mondes ne changent pas. En revanche, un monde déjà suivi qui passe en créatif reste suivi et devient « signalé ».
- **Bouton ⚙ dans le menu pause** : réglages, liaison, déliaison, monde de test, contacter le support.
- **Détection automatique sans blocage** : créatif, spectateur, vol, invulnérabilité, mod interdit. Une notification reste 20 s : « Ce monde n'est plus comptabilisé », avec la raison et **[K] Contacter le support** (touche réglable avec `supportKey`). Le même message s'affiche dans le chat, avec un lien cliquable. Les commandes de triche gardent leur écran de confirmation.
- **Support** : le joueur voit ce qui part (détections, modpack, versions, liste des mods ; jamais le chat, le journal du jeu ni ses fichiers) et ajoute un message. La demande arrive par e-mail à l'équipe, et la réponse va à l'e-mail du compte XPack. Il faut un compte lié.

### Commandes (dans le chat, traitées par le mod, jamais envoyées au serveur)

| Commande | Rôle |
|---|---|
| `/xpack diagnostic` | Copie, anonymisés, les fichiers du lanceur qui décrivent le modpack dans `<instance>/xpack-diagnostic/diagnostic-<date>/`, avec la liste des mods et ce que le mod a détecté. Un lien du chat ouvre le dossier. C'est ce dossier qu'il faut envoyer pour les tests de détection |
| `/xpack support` | Écran de contact du support |
| `/xpack settings` | Réglages du mod |
| `/xpack` ou `/xpack help` | Aide : une ligne par commande, cliquable. Les commandes sont proposées en suggestion pendant la saisie, comme celles des autres mods (ajoutées à l'arbre de commandes du client) |
- **Les progrès du modpack sont les succès** (#80) : le mod envoie l'arbre des progrès tel que le jeu l'affiche (titres et descriptions du jeu et des mods, dans la langue du joueur). En solo, c'est l'arbre complet (progrès cachés compris) ; en multijoueur, ce que le serveur a rendu visible. Points : progrès 5, objectif 10, défi 25.
- **Progrès en temps réel** (#35) : lus dans les paquets que le serveur envoie au client, donc aussi sur un serveur distant sans mod. Le premier paquet (état complet) est envoyé comme « instantané », non vérifié.
- **Statistiques** (#36) : demandées au serveur toutes les 60 s, seulement celles utilisées par les succès du modpack.
- **File d'envoi** (#37) : lots toutes les 5 s, reprise en cas de coupure, événements non envoyés sauvegardés dans le monde.
- **Identification du modpack** (#38) : `modrinth.index.json`, `minecraftinstance.json` (CurseForge), `manifest.json`, sinon empreinte de la liste de mods.
- **Anti-triche** (#43, #44, #47, #48) :
  - alerte avant toute commande de triche, y compris F3+F4 ;
  - détection du mode créatif ou spectateur, de l'invulnérabilité et du vol en survie ;
  - un monde signalé le reste, et le serveur fait foi.
- **Toast** « Succès XPack » (#39) et messages en français et en anglais.

## Structure

```
common/    Logique pure Java, sans Minecraft : règles, intégrité, file, API, détection du modpack. Tests JUnit.
           Compilée en Java 21 pour 1.21.1 et en Java 17 pour Forge 1.20.1.
shared/    Code Minecraft vanilla (noms Mojang) commun à tous les loaders : mixins, écrans, toasts.
           src/main = commun · src/mc1211 = 1.21.1 · src/mc1201 = 1.20.1 (dont Compat.java : les quelques appels qui changent)
fabric/    Point d'entrée Fabric (+ run « smoke » pour le test de bout en bout).
neoforge/  Point d'entrée NeoForge.
forge/     Point d'entrée Forge 1.20.1 (ModDevGradle « legacyforge » : remappage SRG et refmap des mixins).
```

Les règles anti-triche viennent de `../spec/integrity-rules.json`, le même fichier que le site et le serveur.

## Commandes

```bash
./gradlew :common:test          # tests unitaires du cœur
./gradlew build                 # jars Fabric et NeoForge (1.21.1) et Forge (1.20.1)
./gradlew :forge:build          # jar Forge 1.20.1 seul (Windows : .\gradlew.bat :forge:build)
python3 smoke_test.py           # test de bout en bout dans le vrai client (voir ci-dessous)
```

### Test de bout en bout

`smoke_test.py` :
1. démarre l'API (`../server`) ;
2. lie un joueur de test ;
3. génère un monde avec le serveur officiel ;
4. lance le client Fabric sans écran (`xvfb-run`, rendu logiciel Mesa) avec `-Dxpack.smoke=true`.

Le mod tape alors `/give`, vérifie l'écran d'alerte, confirme, s'accorde deux progrès puis quitte. Le script vérifie ensuite, côté API, la session, les événements, le statut « signalé » et le succès débloqué.

Prérequis : Java 21, Node 20+, `xvfb-run`, Mesa, et `pip install nbtlib`.

## Configuration (`config/xpack.json`)

| Clé | Rôle |
|---|---|
| `apiUrl` | Adresse de l'API XPack |
| `linkUrl` | Page du site où saisir le code de liaison |
| `consented` | Réponse à l'écran de consentement du premier lancement (absent = pas encore demandé ; rien n'est envoyé sans `true`) |
| `enabled` | Coupe tout envoi sans délier le compte |
| `toasts` | Notifications de succès en jeu |
| `badge` | Pastille d'état XPack dans le menu pause (comptabilisé / hors-ligne / non comptabilisé) |
| `supportKey` | Touche qui ouvre le support pendant une alerte (`key.keyboard.k` par défaut ; J est pris par JourneyMap) |

Ces réglages sont aussi modifiables en jeu : bouton **⚙** du menu pause, bouton **Configurer** de la liste des mods sur NeoForge, ou Mod Menu sur Fabric (facultatif).

### Jetons : jamais dans l'instance

Le jeton de liaison **n'est pas** dans `config/xpack.json`. Le dossier `config/` part avec les exports de modpack (CurseForge, Modrinth, Prism), les archives d'instance partagées et les dépôts Git des créateurs : un jeton rangé là finirait publié, et n'importe qui pourrait envoyer de faux progrès au nom du joueur. Il est donc rangé dans le dossier de l'utilisateur, avec un jeton par serveur XPack et par compte Minecraft. Toutes les instances le partagent : on ne lie son compte qu'une fois.

| Système | Fichier |
|---|---|
| Windows | `%APPDATA%\XPack\credentials.json` |
| macOS | `~/Library/Application Support/XPack/credentials.json` |
| Linux | `$XDG_CONFIG_HOME/xpack/credentials.json` (sinon `~/.config/xpack/`) |
| Autre emplacement | `-Dxpack.credentials=<fichier>` (tests, installations portables) |

Un jeton laissé dans `config/xpack.json` par une ancienne version est déplacé automatiquement au démarrage.
