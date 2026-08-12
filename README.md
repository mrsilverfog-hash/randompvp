# RandomPvp

Calculateur de dégâts automatique pour les combats Cobblemon sur le serveur **Tropimon**, en français.

## Fonctionnement

- Overlay affiché automatiquement pendant un combat (format **Simple** uniquement, pas de double/triple).
- Lit les stats connues de tes propres Pokémon et les informations révélées sur les Pokémon adverses.
- Estime les dégâts min/max pour chaque coup disponible.
- Format **random battle** : tous les Pokémon, des deux côtés, sont traités comme 31 IV / 85 EV partout / nature neutre. Seul le niveau varie (lu en combat, typiquement 78-98).
- Déduit progressivement l'objet et le talent probables de l'adversaire à partir des dégâts observés (les EV et la nature, elles, sont connues — rien à deviner).

## Build

Ce projet compile entièrement via GitHub Actions (onglet **Actions** → artefact `randompvp-build`). Aucune compilation locale n'est nécessaire.

## Statut

🚧 En développement — étape actuelle : squelette du mod + moteur de calcul de base.

<!-- Test push direct par Claude - 2026-07-05 08:39 UTC -->
