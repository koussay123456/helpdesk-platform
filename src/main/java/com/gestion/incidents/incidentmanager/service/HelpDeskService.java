package com.gestion.incidents.incidentmanager.service;

import com.gestion.incidents.incidentmanager.dto.AuditLogDTO;
import com.gestion.incidents.incidentmanager.dto.ChatMessageDTO;
import com.gestion.incidents.incidentmanager.dto.ContactDTO;
import com.gestion.incidents.incidentmanager.dto.TicketResumeDTO;
import com.gestion.incidents.incidentmanager.model.*;
import com.gestion.incidents.incidentmanager.repository.AuditLogRepository;
import com.gestion.incidents.incidentmanager.repository.DemandeAccesRepository;
import com.gestion.incidents.incidentmanager.repository.InterventionRepository;
import com.gestion.incidents.incidentmanager.repository.MessageRepository;
import com.gestion.incidents.incidentmanager.repository.TicketRepository;
import com.gestion.incidents.incidentmanager.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class HelpDeskService {

    private static final int LIMITE_RESULTATS_RECHERCHE = 12;

    private final TicketRepository ticketRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final InterventionRepository interventionRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private DemandeAccesRepository demandeAccesRepository;

    @Autowired
    public HelpDeskService(TicketRepository ticketRepository,
                           UtilisateurRepository utilisateurRepository,
                           InterventionRepository interventionRepository) {
        this.ticketRepository = ticketRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.interventionRepository = interventionRepository;
    }

    // ============================================================
    // AUDIT LOG
    // ============================================================

    public AuditLog enregistrerAudit(Utilisateur utilisateur, String typeAction, String description,
                                     String entiteAffectee, Long idEntite) {
        AuditLog log = new AuditLog(utilisateur, typeAction, description, entiteAffectee, idEntite);
        return auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> obtenirTousLesLogs() {
        return auditLogRepository.findAllOrderByDateDesc().stream()
                .map(AuditLogDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> obtenirLogsRecents(int limite) {
        return auditLogRepository.findAllOrderByDateDesc().stream()
                .limit(Math.max(1, limite))
                .map(AuditLogDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> obtenirLogsByUtilisateur(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return auditLogRepository.findByUtilisateur(utilisateur).stream()
                .map(AuditLogDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> obtenirLogsByTypeAction(String typeAction) {
        return auditLogRepository.findByTypeAction(typeAction).stream()
                .map(AuditLogDTO::from)
                .collect(Collectors.toList());
    }

    // ============================================================
    // AUTHENTIFICATION
    // ============================================================

    /**
     * Domaine en vigueur : toute adresse créée ou modifiée doit s'y conformer.
     */
    public static final String DOMAINE_ENTREPRISE = "@kipropha.tn";

    /**
     * Ancien domaine, encore accepté à la connexion. Les comptes historiques
     * continuent de fonctionner tant qu'ils n'ont pas été migrés ; seules les
     * adresses nouvellement saisies sont contraintes au domaine en vigueur.
     */
    public static final String DOMAINE_HISTORIQUE = "@kipropha.com";

    /** Adresse recevable pour se connecter : domaine en vigueur ou historique. */
    public static boolean estAdresseEntreprise(String email) {
        if (email == null) return false;
        String normalisee = email.trim().toLowerCase();
        return normalisee.endsWith(DOMAINE_ENTREPRISE) || normalisee.endsWith(DOMAINE_HISTORIQUE);
    }

    /** Adresse recevable à la création ou à la modification d'un compte. */
    public static boolean estAdresseCourante(String email) {
        return email != null && email.trim().toLowerCase().endsWith(DOMAINE_ENTREPRISE);
    }

    public Utilisateur authentifier(String email, String motDePasse) {
        Optional<Utilisateur> utilisateur = utilisateurRepository.findByEmail(email);

        if (utilisateur.isPresent() && utilisateur.get().getMotDePasse().equals(motDePasse)) {
            Utilisateur connecte = utilisateur.get();

            // Le contrôle est refait ici : la validation du navigateur ne protège rien.
            if (!connecte.isActif()) {
                throw new RuntimeException("Ce compte est désactivé. Contactez votre administrateur.");
            }
            // Le filtre "Connexion" de l'onglet Historique n'avait aucune donnée à afficher.
            enregistrerAudit(connecte, "CONNEXION",
                    "Connexion de " + connecte.getNomComplet(),
                    "UTILISATEUR", connecte.getId());
            return connecte;
        }
        return null;
    }

    // ============================================================
    // TICKETS
    // ============================================================

    public Ticket creerTicket(String titre, String description, Categorie categorie,
                              Priorite priorite, Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        Ticket ticket = new Ticket(titre, description, categorie, priorite, utilisateur);
        Ticket ticketSauvegarde = ticketRepository.save(ticket);

        enregistrerAudit(utilisateur, "CREATION",
                "Création du ticket: " + titre,
                "TICKET", ticketSauvegarde.getId());

        return ticketSauvegarde;
    }

    public Ticket affecterTicketAuSupport(Long ticketId, Long supportItId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        Utilisateur supportIt = utilisateurRepository.findById(supportItId)
                .orElseThrow(() -> new RuntimeException("Support IT non trouvé"));

        if (supportIt.getRole() != Role.SUPPORT_IT && supportIt.getRole() != Role.ADMINISTRATEUR) {
            throw new RuntimeException("L'utilisateur n'est pas un support IT");
        }

        ticket.setSupportIt(supportIt);
        ticket.setStatut(Statut.EN_COURS);
        Ticket sauvegarde = ticketRepository.save(ticket);

        enregistrerAudit(supportIt, "MODIFICATION",
                "Affectation du ticket #" + ticketId + " à " + supportIt.getNomComplet(),
                "TICKET", ticketId);

        return sauvegarde;
    }

    public Ticket changerStatutTicket(Long ticketId, Statut nouveauStatut, Long utilisateurId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        Utilisateur utilisateur = utilisateurId != null
                ? utilisateurRepository.findById(utilisateurId).orElse(null)
                : null;

        Statut ancienStatut = ticket.getStatut();
        ticket.setStatut(nouveauStatut);

        if (nouveauStatut == Statut.RESOLU || nouveauStatut == Statut.FERME) {
            ticket.setDateResolution(LocalDateTime.now());
        }

        Ticket ticketMisAJour = ticketRepository.save(ticket);

        if (utilisateur != null) {
            enregistrerAudit(utilisateur, "MODIFICATION",
                    "Changement du statut du ticket #" + ticketId + " de " + ancienStatut + " à " + nouveauStatut,
                    "TICKET", ticketId);
        }

        return ticketMisAJour;
    }

    public Intervention ajouterCommentaire(Long ticketId, String commentaire, Long auteurId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        Utilisateur auteur = utilisateurRepository.findById(auteurId)
                .orElseThrow(() -> new RuntimeException("Auteur non trouvé"));

        Intervention intervention = new Intervention(ticket, commentaire, auteur);
        return interventionRepository.save(intervention);
    }

    public void supprimerTicket(Long ticketId, Long adminId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        Utilisateur admin = utilisateurRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin non trouvé"));

        String titre = ticket.getTitre();

        List<Intervention> interventions = interventionRepository.findByTicket(ticket);
        interventionRepository.deleteAll(interventions);
        ticketRepository.deleteById(ticketId);

        enregistrerAudit(admin, "SUPPRESSION",
                "Suppression du ticket #" + ticketId + ": " + titre,
                "TICKET", ticketId);
    }

    // ============================================================
    // ESPACE SUPPORT IT
    // ============================================================

    /** Kanban global : tous les tickets, quel que soit leur statut ou leur affectation. */
    @Transactional(readOnly = true)
    public List<TicketResumeDTO> obtenirKanbanGlobal() {
        return ticketRepository.findRecentsAvecPersonnes().stream()
                .map(this::resumeAvecApercu)
                .collect(Collectors.toList());
    }

    /** Tickets pris en charge par un technicien donné. */
    @Transactional(readOnly = true)
    public List<TicketResumeDTO> obtenirTicketsAssignes(Long supportItId) {
        Utilisateur support = utilisateurRepository.findById(supportItId)
                .orElseThrow(() -> new RuntimeException("Technicien non trouvé"));

        return ticketRepository.findBySupportIt(support).stream()
                .map(this::resumeAvecApercu)
                .collect(Collectors.toList());
    }

    /**
     * Assignation simple : le technicien se réserve le ticket sans en changer
     * le statut. La progression (Prendre en charge, Résoudre, Fermer) reste une
     * action distincte, pour que « qui traite » et « où en est-on » ne soient
     * pas confondus.
     */
    public TicketResumeDTO assignerTicket(Long ticketId, Long supportItId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        Utilisateur support = utilisateurRepository.findById(supportItId)
                .orElseThrow(() -> new RuntimeException("Technicien non trouvé"));

        if (support.getRole() != Role.SUPPORT_IT && support.getRole() != Role.ADMINISTRATEUR) {
            throw new RuntimeException("Seul un technicien peut prendre un ticket en charge");
        }

        if (ticket.getSupportIt() != null
                && !Objects.equals(ticket.getSupportIt().getId(), support.getId())) {
            throw new RuntimeException("Ce ticket est déjà pris en charge par "
                    + ticket.getSupportIt().getNomComplet());
        }

        ticket.setSupportIt(support);
        Ticket sauvegarde = ticketRepository.save(ticket);

        enregistrerAudit(support, "MODIFICATION",
                "Prise en charge du ticket #" + ticketId + " par " + support.getNomComplet(),
                "TICKET", ticketId);

        return resumeAvecApercu(sauvegarde);
    }

    /**
     * Commentaire d'intervention saisi depuis le modal du Kanban.
     * Une ligne est écrite dans la table intervention, horodatée par le
     * serveur, et le geste est tracé dans le journal d'audit.
     */
    public ChatMessageDTO enregistrerIntervention(Long ticketId, Long auteurId, String commentaire) {
        if (commentaire == null || commentaire.trim().isEmpty()) {
            throw new RuntimeException("Le commentaire d'intervention est vide");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        Utilisateur auteur = verifierAccesTicket(ticket, auteurId);

        String destinataire = (ticket.getUtilisateur() != null)
                ? ticket.getUtilisateur().getEmail()
                : null;

        Intervention intervention = new Intervention(
                ticket, commentaire.trim(), auteur, destinataire);
        intervention.setDateIntervention(LocalDateTime.now());

        Intervention sauvegardee = interventionRepository.save(intervention);

        enregistrerAudit(auteur, "CREATION",
                "Intervention enregistrée sur le ticket #" + ticketId,
                "INTERVENTION", sauvegardee.getId());

        return ChatMessageDTO.from(sauvegardee);
    }

    /**
     * Le demandeur confirme que la solution proposée règle son problème.
     * C'est le seul geste qui clôture un ticket côté utilisateur : le support
     * le marque « Résolu », le demandeur valide, le ticket passe « Fermé ».
     */
    public TicketResumeDTO validerResolution(Long ticketId, Long utilisateurId) {
        Ticket ticket = ticketRepository.findByIdAvecPersonnes(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        Utilisateur demandeur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (ticket.getUtilisateur() == null
                || !Objects.equals(ticket.getUtilisateur().getId(), demandeur.getId())) {
            throw new RuntimeException("Seul le demandeur peut valider la résolution de son ticket");
        }
        if (ticket.getStatut() != Statut.RESOLU) {
            throw new RuntimeException("Le support doit d'abord marquer ce ticket comme résolu");
        }

        ticket.setStatut(Statut.FERME);
        if (ticket.getDateResolution() == null) {
            ticket.setDateResolution(LocalDateTime.now());
        }
        Ticket sauvegarde = ticketRepository.save(ticket);

        enregistrerAudit(demandeur, "MODIFICATION",
                "Validation de la résolution du ticket #" + ticketId,
                "TICKET", ticketId);

        return resumeAvecApercu(sauvegarde);
    }

    // ============================================================
    // DEMANDES D'ACCÈS DEPUIS LA PAGE DE CONNEXION
    // ============================================================

    /**
     * Dépose une demande de réinitialisation ou de déblocage.
     * Aucun courriel n'est envoyé : l'application n'a pas de serveur SMTP.
     * La demande atterrit dans une file que l'administrateur traite depuis
     * l'onglet Utilisateurs.
     */
    public DemandeAcces enregistrerDemandeAcces(String type, String nom, String prenom,
                                                String email, String contactAlternatif,
                                                String description) {
        if (!estAdresseEntreprise(email)) {
            throw new RuntimeException("Seules les adresses " + DOMAINE_ENTREPRISE
                    + " ou " + DOMAINE_HISTORIQUE + " sont acceptées");
        }

        String typeRetenu = DemandeAcces.TYPE_ACCES_BLOQUE.equals(type)
                ? DemandeAcces.TYPE_ACCES_BLOQUE
                : DemandeAcces.TYPE_MOT_DE_PASSE;

        return demandeAccesRepository.save(new DemandeAcces(
                typeRetenu, nom, prenom, email.trim().toLowerCase(), contactAlternatif, description));
    }

    @Transactional(readOnly = true)
    public List<DemandeAcces> obtenirDemandesAcces() {
        return demandeAccesRepository.findToutesTrieesParUrgence();
    }

    public void marquerDemandeTraitee(Long demandeId, Long adminId) {
        DemandeAcces demande = demandeAccesRepository.findById(demandeId)
                .orElseThrow(() -> new RuntimeException("Demande introuvable"));

        demande.setTraitee(true);
        demandeAccesRepository.save(demande);

        utilisateurRepository.findById(adminId).ifPresent(admin ->
                enregistrerAudit(admin, "MODIFICATION",
                        "Traitement de la demande d'accès de " + demande.getEmail(),
                        "DEMANDE_ACCES", demandeId));
    }

    /** Active ou désactive un compte. */
    public Utilisateur changerStatutCompte(Long utilisateurId, boolean actif, Long adminId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        utilisateur.setActif(actif);
        Utilisateur misAJour = utilisateurRepository.save(utilisateur);

        utilisateurRepository.findById(adminId).ifPresent(admin ->
                enregistrerAudit(admin, "MODIFICATION",
                        (actif ? "Activation" : "Désactivation") + " du compte "
                                + utilisateur.getEmail(),
                        "UTILISATEUR", utilisateurId));

        return misAJour;
    }

    // ============================================================
    // MON COMPTE
    // ============================================================

    /**
     * Le titulaire modifie son état civil. Ni le rôle ni l'adresse e-mail ne
     * sont modifiables ici : l'adresse est l'identifiant de connexion, le rôle
     * relève de l'administration.
     */
    public Utilisateur mettreAJourMonCompte(Long utilisateurId, String nom, String prenom) {
        if (nom == null || nom.isBlank() || prenom == null || prenom.isBlank()) {
            throw new RuntimeException("Le nom et le prénom sont obligatoires");
        }

        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        utilisateur.setNom(nom.trim());
        utilisateur.setPrenom(prenom.trim());
        Utilisateur misAJour = utilisateurRepository.save(utilisateur);

        enregistrerAudit(misAJour, "MODIFICATION",
                "Mise à jour de ses coordonnées",
                "UTILISATEUR", utilisateurId);

        return misAJour;
    }

    /** Changement de mot de passe par son titulaire, après vérification de l'ancien. */
    public void changerMotDePasse(Long utilisateurId, String ancien, String nouveau) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (ancien == null || !ancien.equals(utilisateur.getMotDePasse())) {
            throw new RuntimeException("Le mot de passe actuel est incorrect");
        }
        if (nouveau == null || nouveau.length() < 8) {
            throw new RuntimeException("Le nouveau mot de passe doit faire au moins 8 caractères");
        }
        if (nouveau.equals(ancien)) {
            throw new RuntimeException("Le nouveau mot de passe doit être différent de l'ancien");
        }

        utilisateur.setMotDePasse(nouveau);
        utilisateurRepository.save(utilisateur);

        enregistrerAudit(utilisateur, "MODIFICATION",
                "Changement de son mot de passe",
                "UTILISATEUR", utilisateurId);
    }

    /** Historique complet des tickets d'un demandeur, clôturés compris. */
    @Transactional(readOnly = true)
    public List<TicketResumeDTO> obtenirHistoriqueTickets(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        return ticketRepository.findByUtilisateur(utilisateur).stream()
                .map(this::resumeAvecApercu)
                .sorted(Comparator.comparing(
                                (TicketResumeDTO t) -> t.getDateCreation() != null
                                        ? t.getDateCreation()
                                        : LocalDateTime.MIN)
                        .reversed())
                .collect(Collectors.toList());
    }

    // ============================================================
    // MESSAGES NON LUS
    // ============================================================

    /** Repère utilisé quand la personne n'a jamais ouvert sa messagerie. */
    private static final LocalDateTime ORIGINE = LocalDateTime.of(1970, 1, 1, 0, 0);

    /**
     * Nombre de messages non lus, toutes sources confondues : fils de tickets
     * et conversations directes. Un message compte s'il vient de quelqu'un
     * d'autre et s'il est postérieur à la dernière ouverture de l'onglet.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compterMessagesNonLus(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        LocalDateTime depuis = utilisateur.getDerniereVisiteMessagerie() != null
                ? utilisateur.getDerniereVisiteMessagerie()
                : ORIGINE;

        long tickets = interventionRepository.compterNonLus(
                utilisateur.getId(), utilisateur.getEmail(), depuis);
        long directs = messageRepository.compterNonLus(
                utilisateur.getId(), utilisateur.getEmail(), depuis);

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("total", tickets + directs);
        resultat.put("tickets", tickets);
        resultat.put("directs", directs);
        resultat.put("depuis", depuis.equals(ORIGINE) ? null : depuis);
        return resultat;
    }

    /** Marque la messagerie comme consultée à l'instant. */
    public void marquerMessagerieVue(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        utilisateur.setDerniereVisiteMessagerie(LocalDateTime.now());
        utilisateurRepository.save(utilisateur);
    }

    // ============================================================
    // MESSAGERIE (fil de ticket + conversation directe par e-mail)
    // ============================================================

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");

    public static boolean estUnEmail(String valeur) {
        return valeur != null && EMAIL.matcher(valeur.trim()).matches();
    }

    /**
     * Recherche hybride de la barre de la messagerie.
     * Renvoie deux listes : les tickets correspondants et les personnes
     * correspondantes. Si le critère est une adresse e-mail inconnue de la
     * plateforme, elle est proposée comme contact externe.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> rechercheMessagerie(String critere) {
        String q = critere == null ? "" : critere.trim();

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("tickets", rechercherTicketsPourChat(q));

        List<ContactDTO> contacts = new ArrayList<>();
        if (!q.isEmpty()) {
            utilisateurRepository.rechercher(q).stream()
                    .limit(LIMITE_RESULTATS_RECHERCHE)
                    .map(ContactDTO::from)
                    .forEach(contacts::add);

            boolean dejaConnu = contacts.stream().anyMatch(c -> c.getEmail().equalsIgnoreCase(q));
            if (estUnEmail(q) && !dejaConnu) {
                contacts.add(ContactDTO.externe(q.toLowerCase()));
            }
        }
        resultat.put("contacts", contacts);
        return resultat;
    }

    /**
     * Alimente la liste des tickets. Accepte un identifiant ("108", "#108",
     * "TKT-000108") ou un fragment de titre / description.
     */
    @Transactional(readOnly = true)
    public List<TicketResumeDTO> rechercherTicketsPourChat(String critere) {
        String q = critere == null ? "" : critere.trim();

        Map<Long, Ticket> resultats = new LinkedHashMap<>();

        if (q.isEmpty()) {
            ticketRepository.findRecentsAvecPersonnes().stream()
                    .limit(LIMITE_RESULTATS_RECHERCHE)
                    .forEach(t -> resultats.put(t.getId(), t));
        } else {
            // 1. Correspondance exacte sur l'identifiant : "#108", "TKT-000108", "108"
            String chiffres = q.replaceAll("\\D", "");
            if (!chiffres.isEmpty() && chiffres.length() <= 18) {
                try {
                    Long id = Long.parseLong(chiffres);
                    ticketRepository.findByIdAvecPersonnes(id)
                            .ifPresent(t -> resultats.put(t.getId(), t));
                } catch (NumberFormatException ignored) {
                    // critère purement textuel
                }
            }
            // 2. Correspondance textuelle sur le titre / la description
            ticketRepository.rechercherPourChat(q).forEach(t -> resultats.putIfAbsent(t.getId(), t));
        }

        return resultats.values().stream()
                .limit(LIMITE_RESULTATS_RECHERCHE)
                .map(t -> TicketResumeDTO.from(t, interventionRepository.countByTicket(t)))
                .collect(Collectors.toList());
    }

    /**
     * Un ticket n'est lisible que par ses participants : le demandeur, le
     * support qui en a la charge, et les administrateurs. Le contrôle est fait
     * ici, côté serveur : l'interface masque ce qui n'est pas accessible, le
     * service le refuse.
     */
    private Utilisateur verifierAccesTicket(Ticket ticket, Long utilisateurId) {
        Utilisateur demandeur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // Le Kanban global du Support IT expose volontairement tous les tickets :
        // un technicien doit pouvoir ouvrir un ticket libre pour se l'assigner.
        // Seul l'utilisateur final reste cantonné à ses propres tickets.
        boolean autorise = demandeur.getRole() == Role.ADMINISTRATEUR
                || demandeur.getRole() == Role.SUPPORT_IT
                || (ticket.getUtilisateur() != null
                && Objects.equals(ticket.getUtilisateur().getId(), demandeur.getId()))
                || (ticket.getSupportIt() != null
                && Objects.equals(ticket.getSupportIt().getId(), demandeur.getId()));

        if (!autorise) {
            throw new RuntimeException("Ce ticket ne fait pas partie de vos conversations");
        }
        return demandeur;
    }

    /** Résumé enrichi de l'aperçu du dernier échange. */
    private TicketResumeDTO resumeAvecApercu(Ticket ticket) {
        TicketResumeDTO dto = TicketResumeDTO.from(ticket, interventionRepository.countByTicket(ticket));

        interventionRepository.findTopByTicketOrderByDateInterventionDescIdDesc(ticket)
                .ifPresent(dernier -> dto.setDernierMessage(
                        dernier.getCommentaire(),
                        dernier.getAuteur() != null ? dernier.getAuteur().getNomComplet() : null,
                        dernier.getDateIntervention()));

        return dto;
    }

    /**
     * Mes conversations, dans les deux dimensions de la messagerie :
     *   tickets  : ceux que j'ai ouverts et ceux qui me sont affectés
     *   contacts : les personnes avec qui j'ai échangé hors ticket
     *
     * Sans le second volet, un message direct reçu n'apparaissait nulle part
     * dans la colonne de gauche et son expéditeur restait introuvable.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenirMesConversations(Long utilisateurId) {
        Utilisateur moi = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        List<TicketResumeDTO> tickets = ticketRepository.findParticipations(utilisateurId).stream()
                .map(this::resumeAvecApercu)
                .sorted(Comparator.comparing(
                                (TicketResumeDTO t) -> t.getDateDernierMessage() != null
                                        ? t.getDateDernierMessage()
                                        : LocalDateTime.MIN)
                        .reversed())
                .collect(Collectors.toList());

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("tickets", tickets);
        resultat.put("contacts", obtenirMesCorrespondants(moi));
        return resultat;
    }

    /**
     * Un correspondant par personne, avec l'aperçu du dernier échange.
     * Les messages arrivent déjà triés du plus récent au plus ancien : la
     * première occurrence rencontrée est donc la bonne.
     */
    private List<ContactDTO> obtenirMesCorrespondants(Utilisateur moi) {
        Map<String, ContactDTO> parCorrespondant = new LinkedHashMap<>();

        for (Message message : messageRepository.findMesEchangesDirects(moi.getId(), moi.getEmail())) {
            boolean jeSuisAuteur = message.getAuteur() != null
                    && Objects.equals(message.getAuteur().getId(), moi.getId());

            String autre = jeSuisAuteur
                    ? message.getDestinataire()
                    : (message.getAuteur() != null ? message.getAuteur().getEmail() : null);

            // On écarte les annonces globales et les messages que je me serais
            // adressés à moi-même : ce ne sont pas des conversations.
            if (autre == null || autre.isBlank()
                    || autre.equalsIgnoreCase("TOUS")
                    || autre.equalsIgnoreCase(moi.getEmail())) {
                continue;
            }

            String cle = autre.toLowerCase();
            if (parCorrespondant.containsKey(cle)) {
                continue;
            }

            ContactDTO contact = utilisateurRepository.findByEmail(cle)
                    .map(ContactDTO::from)
                    .orElseGet(() -> ContactDTO.externe(cle));

            String auteurNom = jeSuisAuteur ? "Vous"
                    : (message.getAuteur() != null ? message.getAuteur().getNomComplet() : "");

            contact.setDernierMessage(message.getContenu(), auteurNom, message.getDateEnvoi());
            parCorrespondant.put(cle, contact);
        }

        return new ArrayList<>(parCorrespondant.values());
    }

    @Transactional(readOnly = true)
    public TicketResumeDTO obtenirResumeTicket(Long ticketId, Long utilisateurId) {
        Ticket ticket = ticketRepository.findByIdAvecPersonnes(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
        verifierAccesTicket(ticket, utilisateurId);
        return resumeAvecApercu(ticket);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> obtenirConversationTicket(Long ticketId, Long utilisateurId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
        verifierAccesTicket(ticket, utilisateurId);

        return interventionRepository.findDiscussionByTicketId(ticketId).stream()
                .map(ChatMessageDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> obtenirConversationDirecte(Long utilisateurId, String email) {
        Utilisateur moi = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        return messageRepository.findConversation(moi.getEmail(), email).stream()
                .map(ChatMessageDTO::from)
                .collect(Collectors.toList());
    }

    public ChatMessageDTO envoyerMessageTicket(Long ticketId, Long auteurId,
                                               String contenu, String destinataires) {
        if (contenu == null || contenu.trim().isEmpty()) {
            throw new RuntimeException("Le message est vide");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        Utilisateur auteur = verifierAccesTicket(ticket, auteurId);

        String cibles = (destinataires == null || destinataires.trim().isEmpty())
                ? null : destinataires.trim();

        Intervention intervention = interventionRepository.save(
                new Intervention(ticket, contenu.trim(), auteur, cibles));

        return ChatMessageDTO.from(intervention);
    }

    public ChatMessageDTO envoyerMessageDirect(Long auteurId, String email, String contenu) {
        if (contenu == null || contenu.trim().isEmpty()) {
            throw new RuntimeException("Le message est vide");
        }
        if (!estUnEmail(email)) {
            throw new RuntimeException("Adresse e-mail invalide : " + email);
        }

        Utilisateur auteur = utilisateurRepository.findById(auteurId)
                .orElseThrow(() -> new RuntimeException("Auteur non trouvé"));

        Message message = messageRepository.save(
                new Message(auteur, contenu.trim(), email.trim().toLowerCase()));

        return ChatMessageDTO.from(message);
    }

    /** Un utilisateur ne modifie que ses propres messages. Contrôle côté serveur. */
    public ChatMessageDTO modifierMessage(String source, Long messageId,
                                          Long utilisateurId, String contenu) {
        if (contenu == null || contenu.trim().isEmpty()) {
            throw new RuntimeException("Le message ne peut pas être vide");
        }

        if (ChatMessageDTO.SOURCE_DIRECT.equalsIgnoreCase(source)) {
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message introuvable"));
            verifierAuteur(message.getAuteur(), utilisateurId);

            message.setContenu(contenu.trim());
            message.setDateModification(LocalDateTime.now());
            return ChatMessageDTO.from(messageRepository.save(message));
        }

        Intervention intervention = interventionRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message introuvable"));
        verifierAuteur(intervention.getAuteur(), utilisateurId);

        intervention.setCommentaire(contenu.trim());
        intervention.setDateModification(LocalDateTime.now());
        return ChatMessageDTO.from(interventionRepository.save(intervention));
    }

    /** Un utilisateur ne supprime que ses propres messages. Contrôle côté serveur. */
    public void supprimerMessage(String source, Long messageId, Long utilisateurId) {
        Utilisateur demandeur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (ChatMessageDTO.SOURCE_DIRECT.equalsIgnoreCase(source)) {
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message introuvable"));
            verifierAuteur(message.getAuteur(), utilisateurId);

            messageRepository.delete(message);
            enregistrerAudit(demandeur, "SUPPRESSION",
                    "Suppression d'un message direct adressé à " + message.getDestinataire(),
                    "MESSAGE", messageId);
            return;
        }

        Intervention intervention = interventionRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message introuvable"));
        verifierAuteur(intervention.getAuteur(), utilisateurId);

        Long ticketId = intervention.getTicket() != null ? intervention.getTicket().getId() : null;
        interventionRepository.delete(intervention);

        enregistrerAudit(demandeur, "SUPPRESSION",
                "Suppression d'un message du ticket #" + ticketId,
                "MESSAGE", messageId);
    }

    private void verifierAuteur(Utilisateur auteur, Long utilisateurId) {
        if (auteur == null || !Objects.equals(auteur.getId(), utilisateurId)) {
            throw new RuntimeException("Vous ne pouvez agir que sur vos propres messages");
        }
    }

    // ============================================================
    // RECHERCHE / LECTURE DES TICKETS
    // ============================================================

    @Transactional(readOnly = true)
    public List<Ticket> rechercherTickets(String critere, String type) {
        if ("id".equalsIgnoreCase(type)) {
            try {
                Long id = Long.parseLong(critere);
                return ticketRepository.findById(id).stream().collect(Collectors.toList());
            } catch (NumberFormatException e) {
                return new ArrayList<>();
            }
        } else if ("statut".equalsIgnoreCase(type)) {
            try {
                return ticketRepository.findByStatut(Statut.valueOf(critere.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return new ArrayList<>();
            }
        } else if ("categorie".equalsIgnoreCase(type)) {
            try {
                return ticketRepository.findByCategorie(Categorie.valueOf(critere.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    @Transactional(readOnly = true)
    public List<Ticket> obtenirTicketsUtilisateur(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return ticketRepository.findTicketsOuvertsUtilisateur(utilisateur);
    }

    @Transactional(readOnly = true)
    public List<Ticket> obtenirTicketsSupportIt(Long supportItId) {
        Utilisateur supportIt = utilisateurRepository.findById(supportItId)
                .orElseThrow(() -> new RuntimeException("Support IT non trouvé"));
        return ticketRepository.findBySupportIt(supportIt);
    }

    @Transactional(readOnly = true)
    public List<Intervention> obtenirInterventionsTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
        return interventionRepository.findByTicketOrderByDateDesc(ticket);
    }

    @Transactional(readOnly = true)
    public List<Ticket> obtenirTousLesTickets() {
        return ticketRepository.findAllOuvertTickets();
    }

    @Transactional(readOnly = true)
    public Ticket obtenirTicketParId(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
    }

    // ============================================================
    // STATISTIQUES
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> obtenirStatistiques() {
        Map<String, Object> stats = new LinkedHashMap<>();

        long nouveau = ticketRepository.countByStatut(Statut.NOUVEAU);
        long enCours = ticketRepository.countByStatut(Statut.EN_COURS);
        long resolu = ticketRepository.countByStatut(Statut.RESOLU);
        long ferme = ticketRepository.countByStatut(Statut.FERME);

        stats.put("ticketsNouveaux", nouveau);
        stats.put("ticketsEnCours", enCours);
        stats.put("ticketsResolus", resolu);
        stats.put("ticketsFermes", ferme);
        stats.put("ticketsTotaux", nouveau + enCours + resolu + ferme);

        Map<String, Long> repartitionCategorie = new LinkedHashMap<>();
        for (Categorie cat : Categorie.values()) {
            repartitionCategorie.put(cat.name(), ticketRepository.countByCategorie(cat));
        }
        stats.put("repartitionCategorie", repartitionCategorie);

        Map<String, Long> repartitionSupport = new LinkedHashMap<>();
        for (Utilisateur support : utilisateurRepository.findByRole(Role.SUPPORT_IT)) {
            long count = ticketRepository.countBySupportIt(support);
            if (count > 0) {
                repartitionSupport.put(support.getNomComplet(), count);
            }
        }
        stats.put("repartitionSupport", repartitionSupport);

        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenirStatistiquesSupports() {
        List<Utilisateur> supportIts = utilisateurRepository.findByRole(Role.SUPPORT_IT);
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Map<String, Object>> supportStats = new ArrayList<>();

        for (Utilisateur support : supportIts) {
            List<Ticket> tickets = ticketRepository.findBySupportIt(support);

            long ticketsResolus = tickets.stream()
                    .filter(t -> t.getStatut() == Statut.RESOLU || t.getStatut() == Statut.FERME)
                    .count();

            long ticketsActuels = tickets.stream()
                    .filter(t -> t.getStatut() != Statut.FERME && t.getStatut() != Statut.RESOLU)
                    .count();

            long total = ticketsResolus + ticketsActuels;

            Map<String, Object> supportMap = new LinkedHashMap<>();
            supportMap.put("id", support.getId());
            supportMap.put("nom", support.getNomComplet());
            supportMap.put("email", support.getEmail());
            supportMap.put("ticketsResolus", ticketsResolus);
            supportMap.put("ticketsActuels", ticketsActuels);
            supportMap.put("performance", total > 0 ? Math.round((ticketsResolus * 100.0) / total) : 0);

            supportStats.add(supportMap);
        }

        // Tri par nombre de tickets résolus (décroissant).
        // Les compteurs sont des Long : on compare en Number pour éviter tout ClassCastException.
        supportStats.sort((a, b) -> Long.compare(
                ((Number) b.get("ticketsResolus")).longValue(),
                ((Number) a.get("ticketsResolus")).longValue()));

        stats.put("supports", supportStats);
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenirStatistiquesDashboard() {
        long ticketsEnCours = ticketRepository.countByStatut(Statut.NOUVEAU)
                + ticketRepository.countByStatut(Statut.EN_COURS);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ticketsEnCours", ticketsEnCours);
        stats.put("utilisateurs", utilisateurRepository.count());
        stats.put("supportIts", utilisateurRepository.findByRole(Role.SUPPORT_IT).size());
        stats.put("administrateurs", utilisateurRepository.findByRole(Role.ADMINISTRATEUR).size());
        stats.put("ticketsNouveaux", ticketRepository.countByStatut(Statut.NOUVEAU));
        stats.put("ticketsResolus", ticketRepository.countByStatut(Statut.RESOLU));
        stats.put("ticketsTotaux", ticketRepository.count());

        return stats;
    }

    // ============================================================
    // ANNONCES GLOBALES (ancienne messagerie, conservée)
    // ============================================================

    public Message envoyerMessage(Long auteurId, String contenu, String destinataire) {
        Utilisateur auteur = utilisateurRepository.findById(auteurId)
                .orElseThrow(() -> new RuntimeException("Auteur non trouvé"));

        return messageRepository.save(new Message(auteur, contenu, destinataire));
    }

    @Transactional(readOnly = true)
    public List<Message> obtenirTousLesMessages() {
        return messageRepository.findAllMessages();
    }

    public void supprimerMessage(Long messageId) {
        messageRepository.deleteById(messageId);
    }

    // ============================================================
    // UTILISATEURS
    // ============================================================

    public Utilisateur creerUtilisateur(String nom, String prenom, String email,
                                        String motDePasse, Role role, Long adminId) {
        return creerUtilisateur(nom, prenom, email, motDePasse, role, null, adminId);
    }

    public Utilisateur creerUtilisateur(String nom, String prenom, String email, String motDePasse,
                                        Role role, String departement, Long adminId) {
        if (!estAdresseCourante(email)) {
            throw new RuntimeException("L'adresse doit se terminer par " + DOMAINE_ENTREPRISE);
        }
        if (utilisateurRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email déjà utilisé");
        }

        Utilisateur utilisateurSauvegarde = utilisateurRepository.save(
                new Utilisateur(nom, prenom, email, motDePasse, role, departement));

        Utilisateur admin = adminId != null ? utilisateurRepository.findById(adminId).orElse(null) : null;
        if (admin != null) {
            enregistrerAudit(admin, "CREATION",
                    "Création de l'utilisateur: " + prenom + " " + nom + " (" + email + ")",
                    "UTILISATEUR", utilisateurSauvegarde.getId());
        }

        return utilisateurSauvegarde;
    }

    /**
     * Modification partielle : seuls les champs réellement fournis sont
     * appliqués. Un appel ne portant que sur le prénom laisse le reste intact,
     * et le journal d'audit ne mentionne que ce qui a changé.
     */
    public Utilisateur mettreAJourUtilisateur(Long userId, String nom, String prenom,
                                              String email, Role role, String departement,
                                              Long adminId) {
        Utilisateur utilisateur = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        List<String> changements = new ArrayList<>();

        if (renseigne(nom) && !nom.trim().equals(utilisateur.getNom())) {
            changements.add("nom : " + utilisateur.getNom() + " → " + nom.trim());
            utilisateur.setNom(nom.trim());
        }
        if (renseigne(prenom) && !prenom.trim().equals(utilisateur.getPrenom())) {
            changements.add("prénom : " + utilisateur.getPrenom() + " → " + prenom.trim());
            utilisateur.setPrenom(prenom.trim());
        }
        if (renseigne(departement) && !departement.trim().equals(utilisateur.getDepartement())) {
            changements.add("département : " + utilisateur.getDepartement() + " → " + departement.trim());
            utilisateur.setDepartement(departement.trim());
        }
        if (role != null && role != utilisateur.getRole()) {
            changements.add("rôle : " + utilisateur.getRole() + " → " + role);
            utilisateur.setRole(role);
        }

        if (renseigne(email) && !email.trim().equalsIgnoreCase(utilisateur.getEmail())) {
            String nouvelEmail = email.trim().toLowerCase();

            if (!estAdresseCourante(nouvelEmail)) {
                throw new RuntimeException("L'adresse doit se terminer par " + DOMAINE_ENTREPRISE);
            }
            if (utilisateurRepository.findByEmail(nouvelEmail)
                    .filter(autre -> !Objects.equals(autre.getId(), userId)).isPresent()) {
                throw new RuntimeException("Cette adresse est déjà utilisée par un autre compte");
            }

            changements.add("adresse : " + utilisateur.getEmail() + " → " + nouvelEmail);
            utilisateur.setEmail(nouvelEmail);
        }

        if (changements.isEmpty()) {
            return utilisateur;
        }

        Utilisateur utilisateurMisAJour = utilisateurRepository.save(utilisateur);

        Utilisateur admin = adminId != null ? utilisateurRepository.findById(adminId).orElse(null) : null;
        if (admin != null) {
            enregistrerAudit(admin, "MODIFICATION",
                    "Modification du compte " + utilisateurMisAJour.getEmail()
                            + " — " + String.join(", ", changements),
                    "UTILISATEUR", userId);
        }

        return utilisateurMisAJour;
    }

    private static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    @Transactional(readOnly = true)
    public List<Utilisateur> obtenirTousLesUtilisateurs() {
        return utilisateurRepository.findAll();
    }
}