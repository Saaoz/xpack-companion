# Fixture : BMC4 (manifest CurseForge dans le dossier du jeu)

Premier diagnostic réel (`/xpack diagnostic`, 26 octobre 2026). Forge 1.20.1, modpack CurseForge,
lancé hors de l'app CurseForge. Seul `manifest.json` a été trouvé (fichier `jeu-manifest.json` du diagnostic).

- La liste `files` est réduite à 3 des ~400 entrées : le détecteur ne lit que l'en-tête.
- **Pas de `projectID` du modpack** dans ce manifest, et `author` est vide : le mod ne peut envoyer que
  le nom et la version (`BMC4`, `v62hf`). Le serveur en fait `pack-bmc4` → cas A1 de l'audit, à
  résoudre par la recherche côté serveur (docs/DETECTION-MODPACK.md).
- Piste : les `projectID` / `fileID` des mods de `files` permettent, avec l'API CurseForge,
  de retrouver le pack et de vérifier que le contenu correspond à la version officielle.
