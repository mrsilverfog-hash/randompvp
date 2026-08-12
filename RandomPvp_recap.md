# RandomPvp — récap des mécaniques couvertes

**RandomPvp est un fork de TropiCalc adapté au format random battle de Tropimon.**
Tout le moteur (dégâts, résiduels, PP, pièges, panneaux PvP, scouting) est identique.
La seule différence de fond est le profil de stats — voir la section 0 ci-dessous.

Document de référence listant tout ce que le mod sait faire.
Vocabulaire : **estimé** = vient du set Smogon (affiché avec `?`) ; **confirmé** = déduit par observation en combat (fait certain, pas de `?`).

**Avant toute nouvelle session** : lire ce fichier en entier, vérifier `git log` (travail parallèle possible), et la section "Méthode qui marche" avant de coder quoi que ce soit.

---

## 0. Format random battle — la différence avec TropiCalc

En random battle, **tous** les Pokémon des deux camps ont **31 IV et 85 EV dans chaque
stat, nature neutre**. Seul le **niveau** varie (typiquement 78 à 98).

Conséquence : il n'y a plus rien à deviner sur les stats. Les valeurs adverses sont
exactes, pas estimées.

Implémentation — `calc/RandomBattleFormat.java` centralise les constantes et expose
`appliquer(Pokemon.Builder)`. Appelé en **dernier** à trois endroits, pour qu'aucune
estimation ne puisse repasser derrière :

1. `BattleStateTracker.convertir()` — chemin `ClientBattlePokemon`, commun au joueur
   et à l'adversaire. Les champs `props.getIvs()/getEvs()/getNature()` sont
   délibérément ignorés (partiels ou absents côté adverse, et côté joueur ils
   décrivent le Pokémon stocké, pas celui généré pour le combat).
2. `BattleStateTracker.convertirPokemonComplet()` — chemin équipe du joueur.
3. `ObservationCollector.construireAdversaireEstime()` — juste avant `build()`.

Ce qui a été **retiré** par rapport à TropiCalc :
- Les EV et la nature du top spread Smogon (spreads 252/4 + nature boostante :
  totalement faux ici). L'objet et le talent Smogon sont **conservés** — eux varient
  toujours en random battle.
- `appliquerHypothese()` et `trouverNatureBoostant()`, et les appels d'inférence d'EV
  dans `construireAdversaireEstime`. Le moteur `SetInferenceEngine` reste actif mais
  ne sert plus qu'à resserrer les candidats **objet et talent**.
- Toute énumération d'EV et de nature dans le moteur d'inférence.
  `SetInferenceEngine.narrow()` ne boucle plus que sur objet × talent (avant :
  0→252 EV par pas de 4 × 3 natures × objets × talents) ;
  `construirePokemonHypothetique()` applique `RandomBattleFormat` ;
  `NatureBoost`, `trouverNaturePour()` et `arrondirAuPlusProche4()` sont supprimés.
  `StatHypothesis` naît avec `evMin = evMax = 85` et la nature neutre seule
  autorisée, et `ProfilAdversaire` n'applique plus les plages d'EV Smogon — il n'en
  garde que les listes d'objets/talents candidats. L'inférence y gagne : tout écart
  de dégâts résiduel ne peut plus venir que de l'objet ou du talent.
- **À l'affichage** : le bandeau « Set estimé : HP x | Def y | DéfSpé z | NATURE »
  et la ligne « Inférence Def EV a-b » sont retirés de `CalcOverlay`. Seul
  « Objet confirmé : ... » subsiste, et uniquement sur un fait établi.

Ce qui n'a **pas** bougé : la lecture du niveau. `cbp.getLevel()` la fournit déjà pour
les deux camps, et `Pokemon.getStatCalculee()` utilise `niveau` comme variable, jamais
100 en dur. Un lvl 78 face à un lvl 98 était déjà correct.

**Point à valider en jeu** : que le serveur génère bien 85 EV / 31 IV / neutre et que
l'affichage colle aux stats réelles du menu d'équipe pendant un combat random.

---

## 0 bis. Interrupteur : le mod ne tourne qu'en random battle

Le profil 85 EV n'est vrai QU'EN random battle. Appliqué à un combat classé ou
sauvage, il produit des chiffres faux sans le signaler. Le mod est donc **inactif
par défaut** et passe par une porte unique, `battle/RandomBattleGate` :

- `RandomBattleGate.estActif()` est testé au tout début de chaque point d'entrée
  **qui affiche ou calcule** : `CalcOverlay.onHudRender`,
  `SwitchOverlayRenderer.render`, `PvpOverlay.render`, `PvpOverlay.handleDrag` et
  `BattleMessageHandlerMixin`. Désarmé, le mod n'affiche rien et n'accumule
  aucune observation. Les panneaux d'équipe PvP suivent donc la touche, comme le
  HUD principal.
- **Exception délibérée** : `PvpDetector` (scan de l'écran de sélection) n'est PAS
  soumis à la porte. Il ne fait que remplir des listes en mémoire, sans rien
  afficher. S'il était bloqué, armer le mod après la fermeture de l'écran de
  sélection laisserait les panneaux vides pour tout le combat — le moment où on
  appuie sur la touche ne doit pas changer le résultat.
- Armement manuel par **F8** (rebindable, Options > Contrôles > catégorie
  RandomPvp). Seul retour visuel : un message en chat à chaque bascule. Pas de
  bandeau HUD (retiré à la demande).
- L'état **n'est pas persisté** : chaque lancement du jeu repart désarmé. Un état
  sauvegardé qui traîne est exactement le scénario où le mod s'activerait sur un
  combat classé sans qu'on le remarque.
- Le désarmement appelle `ObservationCollector.reinitialiser()` pour qu'aucune
  observation d'un autre format ne survive.

### Pas de détection automatique — décision prise

L'activation reste **manuelle et définitive** sur ce point : pas de détection du
format par titre d'écran ou message serveur. Une heuristique de ce genre dépend
de chaînes de caractères du serveur qui peuvent changer sans prévenir, et un faux
positif signifierait des chiffres faux affichés en combat classé sans avertissement.
La touche dédiée est explicite et ne peut pas se tromper.

Le `DetectionDebugLogger` qui servait à identifier ce signal a été retiré
(commit de nettoyage). Le `MessageDebugLogger` d'origine, lui, reste en place :
il sert au débogage des messages de combat, pas à la détection de format.

---

## 1. Calcul de dégâts

### Capacités à puissance variable
Elles ont toutes une puissance de base de 0 dans les données Showdown — sans traitement spécial, elles affichaient zéro dégât.

- Gyroball et Boule Élek : selon le ratio de vitesse (avec stages, Écharpe Choix, paralysie, talents météo).
- Châtiment : ×2 si la cible a un statut.
- Façade : ×2 si brûlure/poison/paralysie, et ignore correctement la pénalité d'attaque de la brûlure.
- Balayage, Nœud Herbe : paliers selon le poids de la cible.
- Tacle Lourd, Tacle Feu : paliers selon le ratio de poids attaquant/défenseur.
- Retour et Frustration (102, bonheur supposé optimal), Puissance Cachée (60).
- Acrobatie (×2 sans objet), Force Ajoutée et Total Contrôle (20 + 20 par boost positif).
- Fléau et Contre (paliers de PV jusqu'à 200), Éruption/Giclédo/Draco-Énergie (proportionnel aux PV).
- Ball'Météo (type et puissance selon la météo, STAB recalculé).
- Triple Pied (60 total) et Triple Axel (120 total).

### Capacités spéciales
- Choc Psy, Frappe Psy, Lame Ointe : frappent la Défense physique (contournent le boost Déf. Spé. Roche sous sable, et le routage des talents de Ruine qui en tient compte — voir plus bas).
- Tricherie : utilise l'Attaque et les boosts du défenseur ; objet/talent du défenseur ignorés, brûlure de l'attaquant appliquée.
- Sabotage : ×1,5 si la cible tient un objet.
- Mille Flèches : touche les Vol/Lévitation malgré l'immunité Sol normale.
- Lyophilisation : toujours super efficace contre l'Eau.
- Œil Révélateur (talent, pas capacité) : Normal et Combat touchent les Spectre de façon neutre pour son porteur.

### Dégâts fixes
Frappe Atlas et Ombre Nocturne (= niveau, immunités respectées), Sonicboom, Draco-Rage, Requiem Final (PV actuels).

### Multi-coups
Total affiché = dégâts par coup × nombre de coups. Multi-Coups (Skill Link) force le maximum ; Dé Pipé donne 4-5 coups sur les capacités 2-5.

### Poids
Lu depuis Cobblemon, formes régionales exactes. Modifié par Heavy Metal, Light Metal, Pierrallégée.

### Terrains
Champ Élec/Herbu/Psy : +30% sur le type correspondant si l'attaquant est au sol. Champ Brumeux : Dragon ×0,5 si le défenseur est au sol. Champ Herbu : Séisme/Ébranlement/Amplitude ×0,5 si le défenseur est au sol (en plus du boost Plante).

### Vitesse en combat
Stages, Écharpe Choix (×1,5), paralysie (×0,5), talents météo qui doublent la vitesse (Chlorophylle/Glissade/Baigne Sable/Chasse-Neige), et Pied Véloce (×1,5 sous n'importe quel statut, ignore le malus de paralysie). Même calcul utilisé partout (HUD principal, écran de switch, Gyroball/Boule Élek). La ligne Vitesse affiche en bleu, entre parenthèses, la vitesse qu'aurait l'adversaire avec un Mouchoir Choix.

### Ordre des multiplicateurs
Conforme au jeu réel : météo → critique → aléatoire (85-100%) → STAB → efficacité de type → étape "autre" (écrans + terrain défensif + objets/talents, combinés et arrondis **une seule fois**).

### Talents à portée globale (affectent tout le terrain, pas seulement le porteur)
- **Épée du Fléau** (Chien-Pao) : -25% Défense de tous sauf le porteur.
- **Tablettes du Fléau** (Wo-Chien) : -25% Attaque de tous sauf le porteur.
- **Urne du Fléau** (Ting-Lu) : -25% Attaque Spéciale de tous sauf le porteur.
- **Perles du Fléau** (Chi-Yu) : -25% Défense Spéciale de tous sauf le porteur.
- **Aura Sombre / Aura Fée** (Yveltal/Xerneas) : +33% dégâts du type correspondant pour tout le terrain, inversé en -25% par **Aura Brisée**.

Attention aux noms : ces 4 talents "de Ruine" ont été mal nommés puis corrigés cette nuit (voir section Audit) — vérifier `git log` si un doute sur leur état.

### Talents qui contournent les immunités/réductions adverses
**Brise Moule / Turboblaze / Téravolt** ignorent le talent défensif de la cible (Garde Mystik, Lévitation, Filtre, Pare-Balles, Anti-Bruit...) — sauf si le défenseur porte **Garde-Talent**, qui protège explicitement contre ce contournement.

---

## 2. Projections résiduelles (cœur du stall)

Deux lignes séparées : **l'adversaire** (`Résiduel`) et **toi** (`Résiduel toi`).

Sources gérées : poison, Toxik (compteur croissant réel, suivi tour par tour), brûlure, tempête de sable (immunités de type et de talent), Restes, Boue Noire, Salaison (1/8, 1/4 pour Eau/Acier), Vampigraine, Cuvette, Corps Gel, Peau Sèche.

Talents qui annulent/inversent : Garde Magik (aucun dégât indirect), Soin Poison (régénère au lieu de subir le poison), Sel Purificateur (immunité totale aux statuts — le statut n'étant jamais posé par le jeu, aucun code spécifique requis, cohérent par construction).

Le moteur de correction rapide par observation reste **désactivé** (voir commit historique) — calcul basé sur le set Smogon estimé + inférence classique par hypothèses d'EV (seuil : 3 observations).

---

## 3. Détection par observation

Deux familles de mécanismes, qui alimentent les **mêmes structures centrales** (donc cohérentes entre elles automatiquement) :

### Heuristiques par seuils de PV (historiques)
- **Restes** : soin de ~1/16 en fin de tour (gère le soin plafonné près des PV max).
- **Casque Brut** : sur un tour propre, la perte de PV signe l'objet — 14-20% = Casque seul, 25-33% = Casque + Épine de Fer/Peau Dure.
- **Soin Poison** : un empoisonné qui gagne ~1/8 par tour.

### Confirmation directe par message explicite du jeu (ajoutée cette nuit, plus fiable)
Découverte via des logs de diagnostic réels (`MessageDebugLogger`, temporaire, toujours actif) :
- `cobblemon.battle.ability.generic` → confirme **n'importe quel talent** adverse dès son activation, quel qu'il soit.
- `cobblemon.battle.damage.rockyhelmet/ironbarbs/roughskin` → confirme Casque Brut/Épine de Fer/Peau Dure dès le premier recul, sans attendre un "tour propre".
- `cobblemon.battle.heal.XXX` (hors `.generic`) → confirme n'importe quel objet de soin résiduel dès le premier soin (le nom d'objet arrive en anglais espacé, ex. "Black Sludge" — la normalisation du mapper le gère directement).

Ces confirmations directes **priment** sur les heuristiques et sur le scouting d'un combat précédent (ordre d'application vérifié dans `construireAdversaireEstime`).

---

## 4. Recul par contact

Face à Casque Brut et/ou Épine de Fer/Peau Dure, chaque attaque de contact affiche son coût : `Pisto-Poing : 15% - 18% | -29% toi`. Multiplié par le nombre de coups (Double Volée = ×2).

---

## 5. Suivi de terrain et d'état

- **Compteur de PP adverses** : par espèce, Pression pris en compte (seulement sur les capacités qui ciblent le porteur — vérifié via le champ target Showdown, pas juste "offensif vs statut").
- **Pièges d'entrée** (écran de switch) : Piège de Roc, Picots, Pics Toxik, Toile Gluante. Grosses Bottes/Garde Magik/Lévitation/Ballon reconnus.
- **Verrou Choix** : objet Choix + coup déjà utilisé depuis l'entrée.
- **Durées** : météo et écrans, prolongées à 8 tours si le lanceur porte la Roche/Argile Pouvoir correspondante (vérifie les deux actifs, approximation).
- **Boosts** : reset fiable à la fois par déduction indirecte (comparaison d'espèce entre frames) ET par message explicite `cobblemon.battle.switch.self/.other` (plus rapide, ajouté cette nuit).
- **Type override (Protéen/Libéro/Détrempage)** : voir bug majeur corrigé, section Audit.
- **Métamorph/Imposteur** : capacités et stats affichées sont celles de la cible copiée ; PV/statut restent ceux de Métamorph.

---

## 6. Scouting persistant entre combats

Faits confirmés sauvegardés dans `config/randompvp-scouting.json`, indexés par pseudo adverse + espèce. **Migration automatique** des anciens noms français au chargement (voir section Audit) — sans ça, un fait scouté avant un renommage devenait silencieusement inerte.

---

## 7. Panneaux d'équipe PvP

Portage fidèle du code 1.3.8 de TropiHunterBoard (licence MIT, attribution PiikaPops), enrichissements RandomPvp en surcouche d'affichage uniquement. Le tooltip lit les mêmes structures centrales que la section 3 — profite donc automatiquement de toute nouvelle confirmation ou tout nouveau talent ajouté, sans modification Kotlin nécessaire.

Limite connue, cosmétique uniquement (aucun impact sur le calcul de dégâts) : la ligne "types" du tooltip ne reflète pas la Téracristallisation en cours.

---

## Bug majeur trouvé cette nuit : le format des messages Cobblemon n'est PAS uniforme

**Découverte critique, à retenir pour tout futur ajout de parsing de message.** Le suivi du changement de type (Protéen/Libéro/Détrempage) ne fonctionnait JAMAIS, ni pour le joueur ni pour l'adversaire, malgré un code qui semblait correct. Cause : le message `cobblemon.battle.start.typechange` envoie le type en **string brute** (`"Ice"`), alors que le code supposait un objet de traduction imbriqué (`cobblemon.type.ice.name`) — comme c'est le cas pour `owned_pokemon`. **Ces deux formats coexistent dans le même jeu**, sans règle générale : ne jamais supposer le format d'un message sans l'avoir vu dans un vrai log.

Outil de diagnostic : `MessageDebugLogger` (toujours actif, écrit `config/randompvp-messages-debug.txt`, tronqué à 500 Ko). Demander à l'utilisateur un extrait après le combat concerné plutôt que deviner.

---

## Fix majeur : désynchronisation des listes candidates d'inférence

`SetInferenceEngine.TALENTS_OFFENSIFS/TALENTS_DEFENSIFS/OBJETS_DEFENSIFS` servent de candidats au moteur de narrowing. **26 talents/objets** implémentés au fil de la nuit dans `AbilityModifier`/`ItemModifier` (dont certains testés "en dur" comme Robuste/Fantômasque/Ceinture Focus/Garde-Talent) en étaient absents — donc l'inférence ne pouvait jamais converger dessus pour un Pokémon ayant plusieurs talents possibles (ex. Gigansel : Sel Purificateur ou Robuste).

**Garde-fou ajouté** : `InferenceCoverageCheck.verifier()`, appelé au démarrage, compare `AbilityModifier.REGISTRE`/`ItemModifier.REGISTRE` aux listes candidates et log un avertissement pour toute désynchronisation future. Pur log, aucune correction automatique. **Limite** : ne couvre que le registre standard, pas les cas testés en dur (à surveiller manuellement pour ceux-là).

---

## Audit systématique du dictionnaire de traduction (sur plusieurs sessions)

Méthode qui a le mieux fonctionné toute la nuit : **rechercher le nom officiel avant de coder**, jamais deviner un nom plausible. Bilan cumulé (non exhaustif, voir historique git pour le détail) :

**Erreurs de nom trouvées et corrigées** : Glissade/Chlorophylle/Baigne Sable/Chasse-Neige, Casque Brut (mappé "Casque Clou"), Grosses Bottes (faute de frappe), Soin Poison, Multi-Coups, Heavy Metal/Light Metal/Pierrallégée, Agitation (mappé "Adrénaline"), Farceur (mappé "Lunatique"), Énergie Booster (mappée "Énergie Turbo"), Gant de Boxe (mappé "Gant Boxe"), et surtout **les 4 talents de Ruine** — mal nommés ("de Ruine" au lieu de "du Fléau") ET avec Ting-Lu/Chi-Yu ayant leurs stats affectées inversées.

**Environ 30 talents/objets ajoutés** cette nuit, absents jusque-là (liste complète dans le code, pas ici pour éviter la redondance) — dont plusieurs découverts pertinents car directement rencontrés dans les combats de l'utilisateur (Sel Purificateur/Robuste sur Gigansel).

**Confirmé sain, non modifié** : STAB/Téracristallisation (4 cas), gestion des critiques, simulation résiduelle tour par tour, listes de showdown ids (structurellement à l'abri du bug de dictionnaire, aucune traduction à ce niveau), cohérence BoostTracker (format `unboost.slight` confirmé correct par log réel).

**Gap volontairement non comblé** : baies de résistance de type et effets de stage à usage unique (Weakness/Blunder Policy, boosts post-KO comme Chilling/Grim Neigh) — nécessitent un vrai suivi de consommation d'objet/état persistant, absent de l'architecture actuelle. Les ajouter à l'aveugle risquerait une sous-estimation silencieuse pire que l'absence actuelle.

---

## Limites connues (à valider en jeu)

1. Moteur de correction rapide désactivé (calcul stable mais moins réactif à un set hors standard).
2. Heuristiques par seuils de PV non testées en volume — faux positifs possibles (mais désormais doublées par les confirmations directes, plus fiables).
3. Listes de capacités (contact, multi-coups, variables, tranchantes, poing, morsure) curatées à la main, incomplètes par nature. Absence = silence, jamais un faux avertissement.
4. Reconnexion en plein combat : compteurs repartent de zéro.
5. Imposteur n'hérite pas des stages de boost de la cible au moment de la transformation.
6. Baies de résistance et effets de stage à usage unique non modélisés (voir ci-dessus).
7. Garde-fou de couverture d'inférence : ne détecte pas les cas testés en dur.
8. Tooltip PvP : ligne "types" ne reflète pas la Téracristallisation (cosmétique).

---

## Méthode de test qui marche

Un seul principe, confirmé encore et encore cette nuit : **jouer, repérer un écart entre l'affiché et le réel, remonter les chiffres précis + un extrait de log si possible**. C'est ce qui a permis de trouver le bug Protéen (invisible sans log réel), le talent Épine de Fer écrasé par l'inférence, et tout l'audit du dictionnaire. Deviner un nom ou un format sans vérifier a produit plusieurs erreurs cette nuit — toujours rechercher avant d'écrire.

---

## Note sur le travail en parallèle

Plusieurs conversations ont travaillé simultanément sur ce dépôt par le passé. Toujours vérifier `git log` en début de session avant de continuer.

---

## Note de sécurité

Le token GitHub doit être **révoqué et régénéré** entre les sessions. Fine-grained, dépôt `randompvp` uniquement, permission Contents lecture/écriture (+ Workflows si besoin du diagnostic `ci-log`), expiration courte. Un run de build anormalement lent une fois cette nuit s'est résolu par annulation + relance (commit vide) — pas forcément lié au code.
