package com.gestion.incidents.incidentmanager.service;

import com.gestion.incidents.incidentmanager.dto.InterventionDTO;
import com.gestion.incidents.incidentmanager.dto.TicketResumeDTO;
import com.gestion.incidents.incidentmanager.model.*;
import com.gestion.incidents.incidentmanager.repository.AuditLogRepository;
import com.gestion.incidents.incidentmanager.repository.DemandeAccesRepository;
import com.gestion.incidents.incidentmanager.repository.InterventionRepository;
import com.gestion.incidents.incidentmanager.repository.TicketRepository;
import com.gestion.incidents.incidentmanager.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@Transactional
public class HelpDeskService {

    private final TicketRepository ticketRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final InterventionRepository interventionRepository;

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

    // ============================================================
    // AUTHENTIFICATION
    // ============================================================

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
    public InterventionDTO enregistrerIntervention(Long ticketId, Long auteurId, String commentaire) {
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

        return InterventionDTO.from(sauvegardee);
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
        if (email == null || email.isBlank()) {
            throw new RuntimeException("L'adresse e-mail est obligatoire");
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

    /**
     * Refuse toute opération portant sur un compte protégé.
     * Le contrôle vit dans le service et non dans l'interface : masquer un
     * bouton n'empêche personne d'appeler la route directement.
     */
    private void refuserSiProtege(Utilisateur utilisateur) {
        if (utilisateur.isSuperAdmin()) {
            throw new RuntimeException("Le compte " + utilisateur.getEmail()
                    + " est protégé : il ne peut être ni modifié, ni supprimé.");
        }
    }

    /** Suppression d'un compte, hors comptes protégés. */
    public void supprimerUtilisateur(Long utilisateurId, Long adminId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        refuserSiProtege(utilisateur);

        if (adminId != null && adminId > 0) {
            utilisateurRepository.findById(adminId).ifPresent(admin ->
                    enregistrerAudit(admin, "SUPPRESSION",
                            "Suppression du compte " + utilisateur.getNomComplet()
                                    + " (" + utilisateur.getEmail() + ")",
                            "UTILISATEUR", utilisateurId));
        }

        utilisateurRepository.deleteById(utilisateurId);
    }

    /** Active ou désactive un compte. */
    public Utilisateur changerStatutCompte(Long utilisateurId, boolean actif, Long adminId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        refuserSiProtege(utilisateur);

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
    // LECTURE D'UN TICKET
    // ============================================================

    /**
     * Un ticket n'est consultable que par ses participants : le demandeur, le
     * technicien qui en a la charge, un agent de support ou un administrateur.
     * Le contrôle est fait ici, côté serveur : l'interface masque ce qui n'est
     * pas accessible, le service le refuse.
     */
    private Utilisateur verifierAccesTicket(Ticket ticket, Long utilisateurId) {
        Utilisateur demandeur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        boolean autorise = demandeur.getRole() == Role.ADMINISTRATEUR
                || demandeur.getRole() == Role.SUPPORT_IT
                || (ticket.getUtilisateur() != null
                && Objects.equals(ticket.getUtilisateur().getId(), demandeur.getId()))
                || (ticket.getSupportIt() != null
                && Objects.equals(ticket.getSupportIt().getId(), demandeur.getId()));

        if (!autorise) {
            throw new RuntimeException("Ce ticket ne vous est pas accessible");
        }
        return demandeur;
    }

    /** Résumé d'un ticket, enrichi de l'aperçu du dernier échange. */
    private TicketResumeDTO resumeAvecApercu(Ticket ticket) {
        TicketResumeDTO dto = TicketResumeDTO.from(ticket, interventionRepository.countByTicket(ticket));

        interventionRepository.findTopByTicketOrderByDateInterventionDescIdDesc(ticket)
                .ifPresent(dernier -> dto.setDernierMessage(
                        dernier.getCommentaire(),
                        dernier.getAuteur() != null ? dernier.getAuteur().getNomComplet() : null,
                        dernier.getDateIntervention()));

        return dto;
    }

    @Transactional(readOnly = true)
    public TicketResumeDTO obtenirResumeTicket(Long ticketId, Long utilisateurId) {
        Ticket ticket = ticketRepository.findByIdAvecPersonnes(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
        verifierAccesTicket(ticket, utilisateurId);
        return resumeAvecApercu(ticket);
    }

    @Transactional(readOnly = true)
    public List<InterventionDTO> obtenirConversationTicket(Long ticketId, Long utilisateurId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
        verifierAccesTicket(ticket, utilisateurId);

        return interventionRepository.findDiscussionByTicketId(ticketId).stream()
                .map(InterventionDTO::from)
                .collect(Collectors.toList());
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

    /**
     * Indicateurs de performance des agents sur une période.
     *
     * Le périmètre retenu est celui des tickets ouverts dans la période : un
     * agent n'est pas jugé sur des dossiers antérieurs à celle-ci. Le délai de
     * résolution ne compte que les tickets effectivement résolus, sans quoi un
     * dossier encore ouvert ferait artificiellement baisser la moyenne.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenirIndicateursPerformance(LocalDateTime debut, LocalDateTime fin) {
        List<Ticket> periode = ticketRepository.findParPeriode(debut, fin);

        List<Map<String, Object>> agents = new ArrayList<>();
        long totalResolus = 0;
        double totalHeures = 0;
        long nbMesures = 0;

        for (Utilisateur agent : utilisateurRepository.findByRole(Role.SUPPORT_IT)) {
            List<Ticket> siens = periode.stream()
                    .filter(t -> t.getSupportIt() != null
                            && Objects.equals(t.getSupportIt().getId(), agent.getId()))
                    .collect(Collectors.toList());

            List<Ticket> resolus = siens.stream()
                    .filter(t -> t.getStatut() == Statut.RESOLU || t.getStatut() == Statut.FERME)
                    .collect(Collectors.toList());

            long enCours = siens.stream()
                    .filter(t -> t.getStatut() == Statut.NOUVEAU || t.getStatut() == Statut.EN_COURS)
                    .count();

            double heures = 0;
            long mesures = 0;
            for (Ticket t : resolus) {
                if (t.getDateCreation() != null && t.getDateResolution() != null) {
                    heures += Duration.between(t.getDateCreation(), t.getDateResolution()).toMinutes() / 60.0;
                    mesures++;
                }
            }

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("id", agent.getId());
            ligne.put("nom", agent.getNomComplet());
            ligne.put("email", agent.getEmail());
            ligne.put("assignes", siens.size());
            ligne.put("resolus", resolus.size());
            ligne.put("enCours", enCours);
            ligne.put("tauxResolution", siens.isEmpty() ? 0
                    : Math.round(resolus.size() * 1000.0 / siens.size()) / 10.0);
            ligne.put("delaiMoyenHeures", mesures == 0 ? null
                    : Math.round(heures / mesures * 10) / 10.0);
            agents.add(ligne);

            totalResolus += resolus.size();
            totalHeures += heures;
            nbMesures += mesures;
        }

        agents.sort((a, b) -> Integer.compare((Integer) b.get("resolus"), (Integer) a.get("resolus")));

        Map<String, Object> global = new LinkedHashMap<>();
        global.put("ticketsPeriode", periode.size());
        global.put("ticketsResolus", totalResolus);
        global.put("tauxResolution", periode.isEmpty() ? 0
                : Math.round(totalResolus * 1000.0 / periode.size()) / 10.0);
        global.put("delaiMoyenHeures", nbMesures == 0 ? null
                : Math.round(totalHeures / nbMesures * 10) / 10.0);
        global.put("nonAssignes", periode.stream().filter(t -> t.getSupportIt() == null).count());

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("debut", debut);
        resultat.put("fin", fin);
        resultat.put("global", global);
        resultat.put("agents", agents);
        return resultat;
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
    // UTILISATEURS
    // ============================================================

    public Utilisateur creerUtilisateur(String nom, String prenom, String email,
                                        String motDePasse, Role role, Long adminId) {
        return creerUtilisateur(nom, prenom, email, motDePasse, role, null, adminId);
    }

    public Utilisateur creerUtilisateur(String nom, String prenom, String email, String motDePasse,
                                        Role role, String departement, Long adminId) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("L'adresse e-mail est obligatoire");
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

        refuserSiProtege(utilisateur);

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