# Steamcraft Companion — mod client

Mod **client uniquement** qui envoie en temps réel tes progrès Minecraft à Steamcraft et prévient avant toute triche. Les modpacks n'ont **jamais** à le déclarer comme dépendance :
- un serveur sans le mod accepte le joueur ;
- un modpack sans le mod fonctionne normalement.

| Loader | Minecraft | Jar |
|---|---|---|
| Fabric (sans Fabric API) | 1.21.1 | `fabric/build/libs/steamcraft-companion-fabric-1.21.1-<version>.jar` |
| NeoForge | 1.21.1 | `neoforge/build/libs/steamcraft-companion-neoforge-1.21.1-<version>.jar` |

## Ce que fait le mod

- **Liaison du compte** (#34) : au premier monde, un code s'affiche dans le chat (lien cliquable vers `/link` sur le site).
- **Progrès en temps réel** (#35) : lus dans les paquets que le serveur envoie au client, donc aussi sur un serveur distant sans mod. Le premier paquet (état complet) est envoyé comme « instantané », non vérifié.
- **Statistiques** (#36) : demandées au serveur toutes les 60 s, seulement celles utilisées par les succès du modpack.
- **File d'envoi** (#37) : lots toutes les 5 s, reprise en cas de coupure, événements non envoyés sauvegardés dans le monde.
- **Identification du modpack** (#38) : `modrinth.index.json`, `minecraftinstance.json` (CurseForge), `manifest.json`, sinon empreinte de la liste de mods.
- **Anti-triche** (#43, #44, #47, #48) :
  - alerte avant toute commande de triche, y compris F3+F4 ;
  - détection du mode créatif ou spectateur, de l'invulnérabilité et du vol en survie ;
  - un monde signalé le reste, et le serveur fait foi.
- **Toast** « Succès Steamcraft » (#39) et messages en français et en anglais.

## Structure

```
common/    Logique pure Java 21, sans Minecraft : règles, intégrité, file, API, détection du modpack. Tests JUnit.
shared/    Code Minecraft vanilla (noms Mojang) commun aux deux loaders : mixins, écran d'alerte, toast.
fabric/    Point d'entrée Fabric (+ run « smoke » pour le test de bout en bout).
neoforge/  Point d'entrée NeoForge.
```

Les règles anti-triche viennent de `../spec/integrity-rules.json`, le même fichier que le site et le serveur.

## Commandes

```bash
./gradlew :common:test          # tests unitaires du cœur
./gradlew build                 # jars Fabric et NeoForge
python3 smoke_test.py           # test de bout en bout dans le vrai client (voir ci-dessous)
```

### Test de bout en bout

`smoke_test.py` :
1. démarre l'API (`../server`) ;
2. lie un joueur de test ;
3. génère un monde avec le serveur officiel ;
4. lance le client Fabric sans écran (`xvfb-run`, rendu logiciel Mesa) avec `-Dsteamcraft.smoke=true`.

Le mod tape alors `/give`, vérifie l'écran d'alerte, confirme, s'accorde deux progrès puis quitte. Le script vérifie ensuite, côté API, la session, les événements, le statut « signalé » et le succès débloqué.

Prérequis : Java 21, Node 20+, `xvfb-run`, Mesa, et `pip install nbtlib`.

## Configuration (`config/steamcraft.json`)

| Clé | Rôle |
|---|---|
| `apiUrl` | Adresse de l'API Steamcraft |
| `linkUrl` | Page du site où saisir le code de liaison |
| `token`, `tokenUuid` | Remplis automatiquement par la liaison |
| `enabled`, `consented` | Coupe tout envoi sans délier le compte |
| `toasts` | Notifications de succès en jeu |
