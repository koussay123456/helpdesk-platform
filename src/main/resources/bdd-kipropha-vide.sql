-- ==========================================================================
--  KIPROPHA Help Desk — base vierge
--
--  Crée les cinq tables et un unique compte administrateur, sans données
--  de démonstration. À utiliser pour partir d'une base propre et saisir
--  soi-même utilisateurs et tickets depuis l'application.
--
--  Pour une base déjà remplie de données d'exemple, utiliser plutôt
--  bdd-kipropha.sql.
-- ==========================================================================

-- --------------------------------------------------------------- SCHÉMA --
CREATE TABLE IF NOT EXISTS utilisateur (
                                           id                         BIGSERIAL PRIMARY KEY,
                                           nom                        VARCHAR(100) NOT NULL,
    prenom                     VARCHAR(100) NOT NULL,
    email                      VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe               VARCHAR(255) NOT NULL,
    role                       VARCHAR(30)  NOT NULL,
    departement                VARCHAR(80),
    actif                      BOOLEAN DEFAULT TRUE,
    date_creation              TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS ticket (
                                      id              BIGSERIAL PRIMARY KEY,
                                      titre           VARCHAR(255) NOT NULL,
    description     TEXT         NOT NULL,
    categorie       VARCHAR(30)  NOT NULL,
    priorite        VARCHAR(30)  NOT NULL,
    statut          VARCHAR(30)  NOT NULL,
    date_creation   TIMESTAMP    NOT NULL,
    date_resolution TIMESTAMP,
    utilisateur_id  BIGINT       NOT NULL REFERENCES utilisateur(id),
    support_it_id   BIGINT       REFERENCES utilisateur(id)
    );
CREATE INDEX IF NOT EXISTS idx_ticket_utilisateur ON ticket(utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_ticket_support     ON ticket(support_it_id);
CREATE INDEX IF NOT EXISTS idx_ticket_statut      ON ticket(statut);

CREATE TABLE IF NOT EXISTS intervention (
                                            id                BIGSERIAL PRIMARY KEY,
                                            ticket_id         BIGINT NOT NULL REFERENCES ticket(id),
    commentaire       TEXT   NOT NULL,
    date_intervention TIMESTAMP NOT NULL,
    date_modification TIMESTAMP,
    destinataires     VARCHAR(512),
    auteur_id         BIGINT NOT NULL REFERENCES utilisateur(id)
    );
CREATE INDEX IF NOT EXISTS idx_intervention_ticket ON intervention(ticket_id);


CREATE TABLE IF NOT EXISTS audit_log (
                                         id              BIGSERIAL PRIMARY KEY,
                                         utilisateur_id  BIGINT NOT NULL REFERENCES utilisateur(id),
    type_action     VARCHAR(100) NOT NULL,
    description     TEXT         NOT NULL,
    entite_affectee VARCHAR(100) NOT NULL,
    id_entite       BIGINT,
    date_action     TIMESTAMP    NOT NULL,
    adresse_ip      VARCHAR(50)
    );
CREATE INDEX IF NOT EXISTS idx_audit_utilisateur ON audit_log(utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_audit_date        ON audit_log(date_action);

CREATE TABLE IF NOT EXISTS demande_acces (
                                             id                 BIGSERIAL PRIMARY KEY,
                                             type               VARCHAR(30)  NOT NULL,
    nom                VARCHAR(100),
    prenom             VARCHAR(100),
    email              VARCHAR(255) NOT NULL,
    contact_alternatif VARCHAR(255),
    description        TEXT,
    date_demande       TIMESTAMP NOT NULL,
    traitee            BOOLEAN   NOT NULL DEFAULT FALSE
    );


-- ------------------------------------------------ PREMIER ADMINISTRATEUR --
-- Sans ce compte, personne ne pourrait se connecter pour créer les autres.
-- Changez l'adresse et le mot de passe avant de vous en servir.
INSERT INTO utilisateur (nom, prenom, email, mot_de_passe, role, departement, actif, date_creation)
VALUES ('Dupont', 'Alice', 'alice.dupont@kipropha.tn', 'password123', 'ADMINISTRATEUR',
        'Direction des systèmes d''information', TRUE, NOW())
    ON CONFLICT (email) DO NOTHING;

-- ------------------------------------------------------- VÉRIFICATION --
SELECT COUNT(*) AS tables_creees
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('utilisateur','ticket','intervention','audit_log','demande_acces');

SELECT id, email, role FROM utilisateur;
