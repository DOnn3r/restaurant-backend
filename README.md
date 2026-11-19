# La Passe — Backend

API REST de gestion de restaurant : commandes par table, cuisine, addition, comptes (chiffre d'affaires, dépenses, bénéfice).

**Stack** : Spring Boot 3 (Java 21) · PostgreSQL · Flyway · JWT · Docker Compose.
Le frontend est un projet séparé : `restaurant-frontend`.

## Lancement (Docker)

```bash
cp .env.example .env
nano .env        # changez POSTGRES_PASSWORD, JWT_SECRET, ADMIN_PASSWORD
docker compose up -d --build
```

L'API écoute sur `http://<hôte>:8080` (port réglable avec `BACKEND_PORT`). PostgreSQL n'est pas exposé à l'extérieur.
Le compte admin est créé au premier démarrage (base vide) avec `ADMIN_USERNAME` / `ADMIN_PASSWORD`.

Commandes utiles : `docker compose logs -f backend` · `docker compose down` (les données restent dans le volume `db-data`).

### Sauvegarde / restauration

```bash
docker compose exec -T db pg_dump -U restaurant restaurant > sauvegarde.sql
docker compose exec -T db psql -U restaurant restaurant < sauvegarde.sql
```

## Développement local

**Prérequis : JDK 21** (Spring Boot 3.3 ne supporte pas les JDK 24+ ; le build s'arrête avec un message clair si le JDK n'est pas adapté). Dans IntelliJ : *File → Project Structure → Project SDK = 21*, puis *Maven → Reload project*. Le plugin Lombok doit être actif (inclus par défaut) avec *Enable annotation processing* coché.

```bash
docker compose -f docker-compose.dev.yml up -d   # PostgreSQL sur localhost:5432 (mêmes valeurs que application.yml)
./mvnw spring-boot:run                            # ou mvn spring-boot:run ; API sur :8080
```

Depuis l'IDE, lancez la classe `com.restaurant.RestaurantApplication`. Compte admin par défaut en local : `admin` / `admin1234` (variables `ADMIN_USERNAME` / `ADMIN_PASSWORD`).

Variables lues par l'application : `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `APP_TIMEZONE`, `JWT_EXPIRATION_HOURS` (voir `src/main/resources/application.yml`).

## Base de données et migrations (Flyway)

Le schéma est géré **uniquement par Flyway** ; Hibernate est en `ddl-auto: validate` (il vérifie que les entités correspondent au schéma et refuse de démarrer sinon).

- **Fichiers de migration** : `src/main/resources/db/migration/`
- **Migration initiale** : `V1__init.sql` (7 tables : utilisateurs, tables, plats, ingrédients, commandes, lignes de commande, dépenses).
- Flyway s'exécute **automatiquement au démarrage** de l'API, avant tout le reste. L'historique est dans la table `flyway_schema_history`.

### Modifier le schéma

1. Modifiez l'entité Java (ex. ajout d'un champ dans `Dish`).
2. Créez un **nouveau** fichier `V2__description_courte.sql` (deux underscores, numéro suivant), par exemple :
   ```sql
   ALTER TABLE dishes ADD COLUMN calories INTEGER;
   ```
3. Redémarrez : Flyway applique V2 puis Hibernate valide.

Règles : ne **jamais modifier** une migration déjà appliquée (Flyway détecte le changement et refuse de démarrer) ; une migration = un changement ; les numéros ne se réutilisent pas.

### Base existante créée avant Flyway

`baseline-on-migrate: true` fait adopter une base déjà remplie comme « version 1 » sans rejouer V1. Si Hibernate signale ensuite une différence de schéma, corrigez-la avec une migration `V2__...`. Sur une base vide (nouvelle installation), V1 s'applique normalement.

### Repartir de zéro en développement

```bash
docker compose -f docker-compose.dev.yml down -v   # supprime le volume de la base
docker compose -f docker-compose.dev.yml up -d
```

## Accès depuis le frontend

L'API n'active pas CORS : le frontend appelle `/api/...` sur sa propre origine et c'est son serveur (Nginx en production, Vite en développement) qui relaie vers cette API. Il suffit donc de renseigner l'adresse de ce backend dans le projet frontend (`BACKEND_URL`).

## Règles de gestion

- **Chiffre d'affaires** = total des commandes *encaissées* sur la période. **Bénéfice** = chiffre d'affaires − dépenses saisies.
- Les prix et noms des plats sont **figés dans la commande** : modifier la carte ne change pas l'historique.
- Une table = une commande ouverte. Commander à nouveau sur la même table **ajoute** des plats à la commande en cours.
- Un serveur peut retirer un plat ou annuler la commande tant que la cuisine n'a rien lancé ; ensuite, seul l'admin le peut.
- Un plat déjà commandé ne peut pas être supprimé : on le **masque** (interrupteur « disponible »).
- Cuisinier : `PENDING → PREPARING → READY`. Serveur : `READY → SERVED`.

## API (résumé)

| Route | Accès |
|---|---|
| `POST /api/auth/login`, `GET /api/auth/me` | public / connecté |
| `GET /api/dishes` · `POST/PUT/DELETE /api/dishes/{id}` | lecture : tous · écriture : admin |
| `GET /api/tables`, `GET /api/tables/{id}/order`, `POST /api/tables/{id}/order` | connecté / serveur, admin |
| `POST/PUT/DELETE /api/tables`, `POST /api/tables/bulk` | admin |
| `GET /api/orders/{id}/bill`, `POST /api/orders/{id}/pay`, `POST /api/orders/{id}/cancel` | serveur, admin |
| `GET /api/kitchen/tickets`, `PATCH /api/kitchen/items/{id}/status` | connecté (règles par rôle) |
| `GET /api/stats?from&to`, `/api/expenses`, `/api/orders/history`, `/api/users` | admin |

## Sécurité

- Changez les mots de passe du `.env` avant la mise en service.
- Authentification JWT (12 h par défaut). Un compte désactivé perd l'accès immédiatement.
- Hors réseau local, placez l'API derrière HTTPS (reverse proxy + certificat) et limitez l'accès au port 8080 au serveur frontend.
- Faites une sauvegarde (`pg_dump`) avant de déployer une version contenant de nouvelles migrations.
