-- Données initiales KIPROPHA : les trois comptes de démonstration.
-- Le volume (utilisateurs, tickets, échanges, audit) est produit par
-- DataInitializer.java, qui s'exécute juste après ce script.
--
-- Mots de passe EN TEXTE BRUT (password123) — à remplacer par du BCrypt
-- avant toute mise en production.
--
-- ATTENTION : ce script repart de zéro à chaque démarrage. Une fois la base
-- peuplée comme vous le souhaitez, désactivez-le avec
--     spring.sql.init.mode=never
-- sinon les données saisies pendant vos tests seront effacées au redémarrage.

-- L'ordre compte : audit_log et message référencent utilisateur.
-- Sans ces deux premières lignes, le DELETE FROM utilisateur échoue
-- dès qu'un log d'audit ou un message existe.
DELETE FROM demande_acces;
DELETE FROM audit_log;
DELETE FROM message;
DELETE FROM intervention;
DELETE FROM ticket;
DELETE FROM utilisateur;

ALTER SEQUENCE utilisateur_id_seq RESTART WITH 1;
ALTER SEQUENCE ticket_id_seq RESTART WITH 1;
ALTER SEQUENCE intervention_id_seq RESTART WITH 1;
ALTER SEQUENCE message_id_seq RESTART WITH 1;
ALTER SEQUENCE audit_log_id_seq RESTART WITH 1;
ALTER SEQUENCE demande_acces_id_seq RESTART WITH 1;

-- Rattrapage de schéma : si la base existait avant l'ajout de ces colonnes,
-- Hibernate a pu échouer à les créer. Ces instructions sont sans effet si les
-- colonnes sont déjà là, et règlent le cas contraire.
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS actif BOOLEAN DEFAULT TRUE;
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS departement VARCHAR(80);
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS date_creation TIMESTAMP;
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS derniere_visite_messagerie TIMESTAMP;
UPDATE utilisateur SET actif = TRUE WHERE actif IS NULL;

-- Toutes les adresses appartiennent au domaine de l'entreprise : la connexion
-- refuse désormais toute autre provenance.
INSERT INTO utilisateur (nom, prenom, email, mot_de_passe, role, departement, actif, date_creation) VALUES
                                                                                                        ('Dupont',  'Alice',   'alice.dupont@kipropha.com',   'password123', 'ADMINISTRATEUR',
                                                                                                         'Direction des systèmes d''information', TRUE, NOW() - INTERVAL '400 days'),
                                                                                                        ('Martin',  'Bob',     'bob.martin@kipropha.com',     'password123', 'SUPPORT_IT',
                                                                                                         'Direction des systèmes d''information', TRUE, NOW() - INTERVAL '360 days'),
                                                                                                        ('Durand',  'Charlie', 'charlie.durand@kipropha.com', 'password123', 'UTILISATEUR',
                                                                                                         'Ventes', TRUE, NOW() - INTERVAL '320 days');