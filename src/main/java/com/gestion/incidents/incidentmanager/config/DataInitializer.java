package com.gestion.incidents.incidentmanager.config;

import com.gestion.incidents.incidentmanager.model.*;
import com.gestion.incidents.incidentmanager.repository.AuditLogRepository;
import com.gestion.incidents.incidentmanager.repository.InterventionRepository;
import com.gestion.incidents.incidentmanager.repository.TicketRepository;
import com.gestion.incidents.incidentmanager.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Peuplement de la base pour la démonstration et les tests.
 *
 * Déclenchement : au démarrage, uniquement si la base contient moins de
 * SEUIL_UTILISATEURS comptes. Les trois comptes de data.sql sont donc
 * conservés et complétés, jamais dupliqués.
 *
 * Désactivation : app.seed.enabled=false dans application.properties.
 *
 * Le générateur est initialisé avec une graine fixe : deux exécutions sur une
 * base vide produisent exactement le même jeu de données, ce qui rend les
 * captures d'écran et les tests reproductibles.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final int SEUIL_UTILISATEURS = 10;

    // Cible : 50 comptes au total, les trois de data.sql compris.
    private static final int NB_ADMINS = 4;        // + alice.dupont  =  5 administrateurs
    private static final int NB_SUPPORTS = 14;     // + bob.martin    = 15 agents support
    private static final int NB_UTILISATEURS = 29; // + charlie.durand = 30 utilisateurs
    private static final int NB_TICKETS = 60;
    private static final String MOT_DE_PASSE = "password123";
    private static final String DOMAINE = "@kipropha.com";

    /** Un compte sur douze est désactivé, pour que le filtre de statut ait de quoi montrer. */
    private static final int UN_INACTIF_SUR = 12;

    /**
     * Comptes de démonstration affichés sur la page de connexion.
     * Leur existence et leur rôle sont vérifiés à chaque démarrage : ce sont
     * les identifiants annoncés à l'écran, ils ne doivent jamais être faux.
     * { email, prénom, nom, rôle, département }
     */
    private static final String[][] COMPTES_DEMONSTRATION = {
            {"alice.dupont@kipropha.com",   "Alice",   "Dupont", "ADMINISTRATEUR",
                    "Direction des systèmes d'information"},
            {"bob.martin@kipropha.com",     "Bob",     "Martin", "SUPPORT_IT",
                    "Direction des systèmes d'information"},
            {"charlie.durand@kipropha.com", "Charlie", "Durand", "UTILISATEUR", "Ventes"}
    };

    private final UtilisateurRepository utilisateurRepository;
    private final TicketRepository ticketRepository;
    private final InterventionRepository interventionRepository;
    private final AuditLogRepository auditLogRepository;

    private final Random random = new Random(20250725L);

    public DataInitializer(UtilisateurRepository utilisateurRepository,
                           TicketRepository ticketRepository,
                           InterventionRepository interventionRepository,
                           AuditLogRepository auditLogRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.ticketRepository = ticketRepository;
        this.interventionRepository = interventionRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // ============================================================
    // JEUX DE VALEURS
    // ============================================================

    private static final String[] PRENOMS = {
            "Amine", "Sonia", "Karim", "Leïla", "Hedi", "Nadia", "Youssef", "Ines",
            "Mehdi", "Rania", "Sami", "Dorra", "Walid", "Emna", "Tarek", "Salma",
            "Fares", "Nour", "Bilel", "Maha", "Anis", "Yasmine", "Ghazi", "Olfa",
            "Chokri", "Hela", "Marwan", "Sirine", "Nizar", "Amira"
    };

    private static final String[] NOMS = {
            "Ben Salah", "Trabelsi", "Gharbi", "Bouazizi", "Chaabane", "Mansouri",
            "Jelassi", "Hamdi", "Khalfallah", "Ayari", "Zouari", "Belhadj",
            "Nasri", "Ferchichi", "Sassi", "Kacem", "Rekik", "Baccouche",
            "Douiri", "Mejri", "Guesmi", "Larbi", "Ouali", "Riahi", "Sfaxi"
    };

    private static final String DSI = "Direction des systèmes d'information";

    private static final String[] DEPARTEMENTS_METIER = {
            "Ressources humaines", "Finance", "Marketing", "Ventes",
            "Logistique", "Production", "Qualité", "Direction générale"
    };

    /**
     * Comptes fonctionnels : ils ne portent pas un nom de personne mais une
     * fonction. Créés en premier pour que leur adresse ne subisse pas de
     * suffixe de dédoublonnage.
     */
    private static final String[][] COMPTES_FONCTIONNELS = {
            {"ADMINISTRATEUR", "Système", "Admin", "admin.systeme", DSI},
            {"SUPPORT_IT", "Réseau", "Support", "support.reseau", DSI},
            {"SUPPORT_IT", "Postes de travail", "Support", "support.postes", DSI},
            {"SUPPORT_IT", "Applications", "Support", "support.applications", DSI}
    };

    private static final String[][] SUJETS = {
            // { catégorie, titre, description }
            {"MATERIEL", "Écran secondaire non détecté",
                    "Le second écran reste noir après le branchement HDMI, la station d'accueil est pourtant reconnue."},
            {"MATERIEL", "Batterie du portable qui ne tient plus",
                    "L'autonomie est tombée sous les trente minutes en usage bureautique."},
            {"MATERIEL", "Clavier : plusieurs touches inertes",
                    "Les touches A, Z et E ne répondent plus depuis le nettoyage du poste."},
            {"MATERIEL", "Station d'accueil qui se déconnecte",
                    "La station coupe la connexion réseau toutes les dix minutes environ."},
            {"LOGICIEL", "Excel se ferme à l'ouverture d'un classeur",
                    "Le fichier de suivi mensuel provoque une fermeture immédiate de l'application."},
            {"LOGICIEL", "Mise à jour de l'ERP bloquée à 40 %",
                    "L'installation reste figée et aucun message d'erreur n'est affiché."},
            {"LOGICIEL", "Licence Office expirée",
                    "Un bandeau signale l'expiration de la licence et bloque l'enregistrement."},
            {"LOGICIEL", "Outlook ne synchronise plus le calendrier",
                    "Les invitations reçues n'apparaissent pas dans l'agenda partagé."},
            {"LOGICIEL", "Impossible d'exporter en PDF depuis l'application métier",
                    "Le bouton d'export renvoie une page blanche."},
            {"RESEAU", "Accès VPN refusé depuis le domicile",
                    "Le client retourne une erreur d'authentification alors que les identifiants sont corrects."},
            {"RESEAU", "Wi-Fi instable en salle de réunion",
                    "La connexion tombe dès que plus de six personnes sont connectées."},
            {"RESEAU", "Lenteur d'accès au serveur de fichiers",
                    "L'ouverture d'un document partagé prend plus d'une minute."},
            {"RESEAU", "Partage réseau inaccessible",
                    "Le lecteur réseau P: n'est plus monté au démarrage de la session."},
            {"IMPRIMANTE", "Bourrage papier signalé à vide",
                    "L'imprimante du couloir affiche une erreur de bourrage alors qu'aucune feuille n'est engagée."},
            {"IMPRIMANTE", "Impressions striées",
                    "Des bandes horizontales apparaissent sur toutes les pages imprimées."},
            {"IMPRIMANTE", "Scanner non détecté par le poste",
                    "La numérisation vers e-mail échoue depuis la mise à jour du pilote."},
            {"IMPRIMANTE", "File d'impression bloquée",
                    "Les travaux s'accumulent sans jamais être traités."},
            {"AUTRE", "Demande d'accès au dossier Qualité",
                    "Accès en lecture demandé pour le suivi des lots de production."},
            {"AUTRE", "Création d'un compte pour un nouvel arrivant",
                    "Poste à équiper pour un démarrage lundi prochain."},
            {"AUTRE", "Récupération d'un fichier supprimé",
                    "Un fichier de suivi a été supprimé par erreur en fin de journée."}
    };

    private static final String[] REPONSES_SUPPORT = {
            "Bonjour, je prends le ticket en charge. Pouvez-vous préciser depuis quand le problème se produit ?",
            "Merci pour le signalement. J'ai reproduit le comportement de mon côté, j'analyse.",
            "Un correctif a été appliqué sur votre poste. Merci de redémarrer puis de me confirmer.",
            "Le pilote a été mis à jour. Le poste doit être redémarré pour finaliser l'installation.",
            "J'ai besoin d'un créneau de quinze minutes pour intervenir à distance. Quand êtes-vous disponible ?",
            "Le dysfonctionnement venait de la configuration réseau, elle est corrigée."
    };

    private static final String[] REPONSES_DEMANDEUR = {
            "Merci, le problème est apparu ce matin en arrivant.",
            "C'est bien mieux depuis le redémarrage, merci beaucoup.",
            "Je suis disponible demain après quatorze heures.",
            "Le souci se reproduit malheureusement encore de temps en temps.",
            "Parfait, tout fonctionne de nouveau. Merci pour la réactivité."
    };

    // ============================================================
    // EXÉCUTION
    // ============================================================

    @Override
    @Transactional
    public void run(String... args) {
        // Toujours en premier, quel que soit l'état de la base : les identifiants
        // affichés sur la page de connexion doivent fonctionner.
        garantirComptesDemonstration();

        long existants = utilisateurRepository.count();
        if (existants >= SEUIL_UTILISATEURS) {
            log.info("Peuplement ignoré : {} utilisateurs déjà présents en base.", existants);
            return;
        }

        log.info("Peuplement de la base de démonstration…");

        List<Utilisateur> admins = new ArrayList<>(utilisateurRepository.findByRole(Role.ADMINISTRATEUR));
        List<Utilisateur> supports = new ArrayList<>(utilisateurRepository.findByRole(Role.SUPPORT_IT));
        List<Utilisateur> demandeurs = new ArrayList<>(utilisateurRepository.findByRole(Role.UTILISATEUR));

        Set<String> emailsUtilises = new HashSet<>();
        utilisateurRepository.findAll().forEach(u -> emailsUtilises.add(u.getEmail()));

        creerComptesFonctionnels(emailsUtilises, admins, supports);

        creerUtilisateurs(NB_ADMINS - 1, Role.ADMINISTRATEUR, emailsUtilises, admins);
        creerUtilisateurs(NB_SUPPORTS - 3, Role.SUPPORT_IT, emailsUtilises, supports);
        creerUtilisateurs(NB_UTILISATEURS, Role.UTILISATEUR, emailsUtilises, demandeurs);

        int tickets = creerTickets(demandeurs, supports);

        log.info("Peuplement terminé : {} utilisateurs ({} administrateurs, {} agents support, "
                        + "{} utilisateurs), {} tickets.",
                utilisateurRepository.count(), admins.size(), supports.size(),
                demandeurs.size(), tickets);
    }

    /**
     * Crée les comptes de démonstration s'ils manquent, et corrige rôle, mot de
     * passe ou statut s'ils ont dérivé. Sans cela, une base partiellement
     * peuplée — script d'initialisation interrompu, rôle modifié à la main —
     * laisse la page de connexion annoncer des identifiants qui échouent.
     */
    private void garantirComptesDemonstration() {
        for (String[] compte : COMPTES_DEMONSTRATION) {
            String email = compte[0];
            Role role = Role.valueOf(compte[3]);

            Utilisateur existant = utilisateurRepository.findByEmail(email).orElse(null);

            if (existant == null) {
                Utilisateur cree = new Utilisateur(compte[2], compte[1], email, MOT_DE_PASSE, role, compte[4]);
                cree.setDateCreation(LocalDateTime.now().minusDays(400));
                utilisateurRepository.save(cree);
                log.warn("Compte de démonstration recréé : {} ({})", email, role);
                continue;
            }

            boolean corrige = false;
            if (existant.getRole() != role) {
                log.warn("Rôle du compte {} corrigé : {} -> {}", email, existant.getRole(), role);
                existant.setRole(role);
                corrige = true;
            }
            if (!MOT_DE_PASSE.equals(existant.getMotDePasse())) {
                existant.setMotDePasse(MOT_DE_PASSE);
                corrige = true;
            }
            if (!existant.isActif()) {
                existant.setActif(true);
                corrige = true;
            }
            if (existant.getDepartement() == null) {
                existant.setDepartement(compte[4]);
                corrige = true;
            }

            if (corrige) {
                utilisateurRepository.save(existant);
                log.warn("Compte de démonstration remis en état : {}", email);
            }
        }
    }

    private void creerComptesFonctionnels(Set<String> emailsUtilises,
                                          List<Utilisateur> admins, List<Utilisateur> supports) {
        for (String[] compte : COMPTES_FONCTIONNELS) {
            String email = compte[3] + DOMAINE;
            if (emailsUtilises.contains(email)) {
                continue;
            }
            emailsUtilises.add(email);

            Role role = Role.valueOf(compte[0]);
            Utilisateur utilisateur = new Utilisateur(
                    compte[2], compte[1], email, MOT_DE_PASSE, role, compte[4]);
            utilisateur.setDateCreation(LocalDateTime.now().minusDays(300 + random.nextInt(60)));

            Utilisateur sauvegarde = utilisateurRepository.save(utilisateur);
            (role == Role.ADMINISTRATEUR ? admins : supports).add(sauvegarde);
        }
    }

    private void creerUtilisateurs(int nombre, Role role, Set<String> emailsUtilises,
                                   List<Utilisateur> cible) {
        for (int i = 0; i < nombre; i++) {
            String prenom = PRENOMS[random.nextInt(PRENOMS.length)];
            String nom = NOMS[random.nextInt(NOMS.length)];

            // Toutes les adresses appartiennent au domaine de l'entreprise ;
            // le suffixe numérique règle les homonymes.
            String base = normaliser(prenom) + "." + normaliser(nom);
            String email = base + DOMAINE;
            int suffixe = 2;
            while (emailsUtilises.contains(email)) {
                email = base + suffixe + DOMAINE;
                suffixe++;
            }
            emailsUtilises.add(email);

            String departement = (role == Role.UTILISATEUR)
                    ? DEPARTEMENTS_METIER[random.nextInt(DEPARTEMENTS_METIER.length)]
                    : DSI;

            Utilisateur utilisateur = new Utilisateur(nom, prenom, email, MOT_DE_PASSE, role, departement);
            utilisateur.setDateCreation(LocalDateTime.now()
                    .minusDays(random.nextInt(540))
                    .minusHours(random.nextInt(24)));

            // Quelques comptes désactivés : départs, longues absences.
            if (role == Role.UTILISATEUR && random.nextInt(UN_INACTIF_SUR) == 0) {
                utilisateur.setActif(false);
            }

            cible.add(utilisateurRepository.save(utilisateur));
        }
    }

    /**
     * Répartition volontairement contrastée pour que le tableau de bord et
     * l'onglet Performance affichent des chiffres lisibles :
     *   30 % en attente (aucun support assigné), 25 % en cours,
     *   30 % résolus, 15 % fermés.
     */
    private int creerTickets(List<Utilisateur> demandeurs, List<Utilisateur> supports) {
        if (demandeurs.isEmpty() || supports.isEmpty()) {
            log.warn("Peuplement des tickets ignoré : aucun demandeur ou aucun support en base.");
            return 0;
        }

        Priorite[] priorites = Priorite.values();

        for (int i = 0; i < NB_TICKETS; i++) {
            String[] sujet = SUJETS[i % SUJETS.length];

            Utilisateur demandeur = demandeurs.get(random.nextInt(demandeurs.size()));
            LocalDateTime creation = LocalDateTime.now()
                    .minusDays(random.nextInt(90))
                    .minusHours(random.nextInt(24))
                    .minusMinutes(random.nextInt(60));

            Ticket ticket = new Ticket(
                    sujet[1],
                    sujet[2],
                    Categorie.valueOf(sujet[0]),
                    priorites[random.nextInt(priorites.length)],
                    demandeur);
            ticket.setDateCreation(creation);

            int tirage = random.nextInt(100);
            Utilisateur support = null;

            if (tirage < 30) {
                // En attente de prise en charge : aucun support assigné.
                ticket.setStatut(Statut.NOUVEAU);
            } else if (tirage < 55) {
                support = supports.get(random.nextInt(supports.size()));
                ticket.setStatut(Statut.EN_COURS);
                ticket.setSupportIt(support);
            } else if (tirage < 85) {
                support = supports.get(random.nextInt(supports.size()));
                ticket.setStatut(Statut.RESOLU);
                ticket.setSupportIt(support);
                ticket.setDateResolution(creation.plusHours(2L + random.nextInt(70)));
            } else {
                support = supports.get(random.nextInt(supports.size()));
                ticket.setStatut(Statut.FERME);
                ticket.setSupportIt(support);
                ticket.setDateResolution(creation.plusHours(3L + random.nextInt(120)));
            }

            Ticket sauvegarde = ticketRepository.save(ticket);

            auditLogRepository.save(
                    horodater(new AuditLog(demandeur, "CREATION",
                            "Création du ticket: " + sujet[1],
                            "TICKET", sauvegarde.getId()), creation));

            if (support != null) {
                creerEchanges(sauvegarde, demandeur, support, creation);

                auditLogRepository.save(
                        horodater(new AuditLog(support, "MODIFICATION",
                                        "Changement du statut du ticket #" + sauvegarde.getId()
                                                + " de NOUVEAU à " + sauvegarde.getStatut(),
                                        "TICKET", sauvegarde.getId()),
                                creation.plusHours(1)));
            }
        }
        return NB_TICKETS;
    }

    private void creerEchanges(Ticket ticket, Utilisateur demandeur, Utilisateur support,
                               LocalDateTime creation) {
        int nbEchanges = 1 + random.nextInt(3);
        LocalDateTime horodatage = creation.plusMinutes(20L + random.nextInt(180));

        for (int i = 0; i < nbEchanges; i++) {
            boolean estSupport = (i % 2 == 0);

            Utilisateur auteur = estSupport ? support : demandeur;
            String texte = estSupport
                    ? REPONSES_SUPPORT[random.nextInt(REPONSES_SUPPORT.length)]
                    : REPONSES_DEMANDEUR[random.nextInt(REPONSES_DEMANDEUR.length)];
            String destinataires = estSupport ? demandeur.getEmail() : support.getEmail();

            Intervention intervention = new Intervention(ticket, texte, auteur, destinataires);
            intervention.setDateIntervention(horodatage);
            interventionRepository.save(intervention);

            horodatage = horodatage.plusMinutes(15L + random.nextInt(240));
        }
    }

    private AuditLog horodater(AuditLog entree, LocalDateTime date) {
        entree.setDateAction(date);
        return entree;
    }

    /** "Leïla Ben Salah" → "leila.ben-salah" : e-mails sans accent ni espace. */
    private String normaliser(String valeur) {
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sansAccent.toLowerCase(Locale.FRENCH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}