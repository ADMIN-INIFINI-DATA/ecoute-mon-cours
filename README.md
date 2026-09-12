# Écoute mon cours

Application Android qui transforme une page de cours — photo, PDF, document Word — en
lecture audio, avec surlignage du texte lu, reprise à l'endroit exact où l'on s'était
arrêtée, écoute écran éteint et export du cours en fichier audio.

Tout ce qui peut être fait sans connexion l'est : OCR des pages imprimées et synthèse
vocale fonctionnent hors-ligne, sans compte, sans abonnement, sans quota. Les seules
fonctions qui sortent du téléphone sont optionnelles et déclenchées explicitement.

---

## 1. Ce que fait l'application

| Fonction | Comment | Hors-ligne |
|---|---|---|
| Scanner une ou plusieurs pages | Scanner ML Kit : détection des bords, redressement, suppression des ombres, jusqu'à 20 pages en rafale | ✅ |
| Importer une photo | Sélecteur de photos Android (aucune permission de stockage demandée) | ✅ |
| Importer un PDF ou un DOCX | PDF rendu page par page puis lu ; DOCX lu directement | ✅ |
| Recevoir un fichier depuis une autre appli | « Partager vers Écoute mon cours » | ✅ |
| Lecture à voix haute | Synthèse vocale française du téléphone | ✅ |
| Surlignage du mot lu | `onRangeStart`, avec repli à la phrase si le moteur ne le gère pas | ✅ |
| Reprise de lecture | Position mémorisée en continu, par cours | ✅ |
| Écoute écran éteint | Service de premier plan `mediaPlayback` + notification avec commandes | ✅ |
| Export audio (.m4a) | Synthèse en fichier puis encodage AAC par le codec du système | ✅ |
| Bibliothèque, matière devinée, progression | Base locale Room | ✅ |
| Trouver un cours en ligne | Wikiversité, Wikipédia, Vikidia (licences libres, source affichée) | Réseau |
| Page manuscrite / photo du tableau | Transcription assistée par IA, si une clé est fournie | Réseau |
| Fiche de révision et questions | Génération IA à partir du cours, si une clé est fournie | Réseau |

## 2. Compiler l'application

### Option A — Android Studio (le plus simple)

1. Ouvrir le dossier du projet dans Android Studio (version Meerkat ou plus récente).
2. Laisser l'IDE télécharger le SDK Android 36 et les dépendances.
3. `Build > Generate Signed App Bundle / APK…` → APK → créer une clé (voir §3) → variante `release`.

L'APK se trouve ensuite dans `app/build/outputs/apk/release/`.

### Option B — en ligne de commande

```bash
export KEYSTORE_FILE=/chemin/vers/release.jks
export KEYSTORE_PASSWORD='…'
export KEY_ALIAS='ecoute'
export KEY_PASSWORD='…'
./gradlew assembleRelease
```

Sans ces variables, la compilation fonctionne quand même : l'APK est alors signé avec la
clé de débogage, installable, mais à ne pas diffuser.

### Option C — GitHub Actions

Le workflow `.github/workflows/build-apk.yml` compile et signe l'APK à chaque tag `v*`.
Secrets à créer dans le dépôt : `KEYSTORE_BASE64` (le fichier `.jks` encodé en base64 avec
`openssl base64 -A < release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## 3. Créer la clé de signature

```bash
keytool -genkeypair -v -keystore release.jks -alias ecoute \
        -keyalg RSA -keysize 4096 -validity 10000
```

Conserver ce fichier et son mot de passe en lieu sûr, en trois copies. **Une clé perdue
signifie qu'aucune mise à jour de l'application ne pourra plus jamais être installée
par-dessus la version existante** : il faudrait désinstaller puis réinstaller, en perdant
les cours enregistrés.

## 4. Installer sur le téléphone

1. Transférer le `.apk` (câble, Drive, e-mail).
2. L'ouvrir depuis le gestionnaire de fichiers : Android demande d'autoriser
   l'installation depuis cette source. C'est l'autorisation normale pour une application
   qui ne vient pas du Play Store.
3. Play Protect affiche un avertissement au premier lancement d'une application peu
   répandue : « Envoyer pour analyse » ou « Installer quand même ».
4. Au premier démarrage, l'application demande l'autorisation d'afficher des
   notifications (nécessaire pour les commandes de lecture écran éteint).

**Vérification développeur Google.** Depuis septembre 2026, l'installation d'applications
hors magasin est réservée aux développeurs vérifiés dans quatre pays (Brésil, Indonésie,
Singapour, Thaïlande), avec une extension mondiale annoncée pour 2027. Pour continuer à
installer cette application librement en France après cette échéance, il faudra créer un
compte sur l'Android Developer Console : un palier gratuit « étudiant / amateur » permet
de distribuer à un nombre illimité d'applications sur un maximum de 20 appareils, sans
pièce d'identité ni frais d'inscription. À faire une fois, en amont.

## 5. Sécurité et données personnelles

- **Permissions demandées** : notifications, service de premier plan audio, Internet.
  Pas de permission caméra (le scanner utilise celle de Google Play services), pas
  d'accès au stockage (sélecteur de photos et de fichiers du système).
- **Les cours ne quittent pas le téléphone.** La base est locale, la sauvegarde
  automatique Android est désactivée pour cette application.
- **Aucune clé d'API n'est embarquée dans l'APK.** La clé IA, si elle est renseignée, est
  fournie par l'utilisateur et chiffrée par le Keystore matériel du téléphone
  (`EncryptedSharedPreferences`). Elle n'est envoyée qu'au service concerné.
- **Aucune publicité, aucune analyse d'usage, aucun compte.**
- L'application est destinée à une mineure : c'est la raison pour laquelle le mode
  hors-ligne est le mode par défaut et la transmission d'une page à un service d'IA reste
  un choix explicite, réversible dans les Réglages.
- Signature APK en schémas v2 et v3, minification et obfuscation R8 activées en release.

## 6. Limites connues

- L'OCR hors-ligne ne lit pas l'écriture manuscrite : c'est une limite du modèle ML Kit,
  pas un réglage. Les pages manuscrites nécessitent la lecture assistée.
- Un PDF est traité par rendu d'image puis OCR : robuste sur tous les PDF, y compris
  scannés, mais plus lent qu'une extraction de texte native. Plafonné à 40 pages.
- L'export audio passe par la synthèse fichier par fichier : compter environ une minute
  de traitement pour dix minutes d'écoute.
- Les schémas, tableaux et formules ne sont pas lus : le texte les contourne.
- La qualité de la voix est celle du moteur du téléphone. Installer « Speech Recognition
  & Synthesis » de Google et une voix française de haute qualité améliore nettement le
  résultat, et conditionne le surlignage mot à mot.

## 7. Organisation du code

```
app/src/main/java/com/infinidata/ecoutemoncours/
├── EcouteApp.kt              Application, canal de notification, dépendances partagées
├── MainActivity.kt           Point d'entrée, réception des fichiers partagés
├── data/                     Réglages chiffrés, base Room (documents, progression, fiches)
├── ingest/                   Aiguillage des imports, OCR ML Kit, PDF, DOCX, nettoyage du texte
├── ai/GeminiClient.kt        Transcription manuscrite et fiche de révision (optionnel, BYOK)
├── web/CourseSearch.kt       Recherche de cours sur les corpus libres
├── speech/                   Découpage en passages, moteur de lecture, service audio, export
└── ui/                       Écrans Compose : bibliothèque, lecture, recherche, réglages
```

## 8. Apparences

Cinq styles sont livrés, changeables à tout moment depuis l'accueil ou les réglages, et
mémorisés : Néon (violet/turquoise), Fluo (vert électrique), Coucher (corail/ambre), Papier
(thème clair) et Sobre (charte TIBCO : vert `#4CA22F` sur anthracite `#1C1F26`).

Tout passe par `ui/theme/Theme.kt` : une `data class Skin` décrit une apparence,
`SkinController` porte celle en cours, et `EcouteTheme` construit le jeu de couleurs
Material 3 correspondant. Ajouter un sixième style revient à ajouter une entrée dans
`Skins.ALL` — aucun écran n'est à modifier, ils lisent tous `MaterialTheme.colorScheme`
ou `LocalSkin.current`.
