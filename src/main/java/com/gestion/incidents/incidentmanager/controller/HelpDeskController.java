package com.gestion.incidents.incidentmanager.controller;

import com.gestion.incidents.incidentmanager.dto.AuditLogDTO;
import com.gestion.incidents.incidentmanager.dto.ChatMessageDTO;
import com.gestion.incidents.incidentmanager.dto.TicketResumeDTO;
import com.gestion.incidents.incidentmanager.model.*;
import com.gestion.incidents.incidentmanager.repository.InterventionRepository;
import com.gestion.incidents.incidentmanager.repository.TicketRepository;
import com.gestion.incidents.incidentmanager.repository.UtilisateurRepository;
import com.gestion.incidents.incidentmanager.service.HelpDeskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", maxAge = 3600)
public class HelpDeskController {

    private final HelpDeskService helpDeskService;
    private final UtilisateurRepository utilisateurRepository;
    private final TicketRepository ticketRepository;
    private final InterventionRepository interventionRepository;

    @Autowired
    public HelpDeskController(HelpDeskService helpDeskService,
                              UtilisateurRepository utilisateurRepository,
                              TicketRepository ticketRepository,
                              InterventionRepository interventionRepository) {
        this.helpDeskService = helpDeskService;
        this.utilisateurRepository = utilisateurRepository;
        this.ticketRepository = ticketRepository;
        this.interventionRepository = interventionRepository;
    }

    // ============ RÉPONSES UTILITAIRES ============

    private Map<String, Object> succes(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        return body;
    }

    private ResponseEntity<Map<String, Object>> echec(Exception e, HttpStatus statut) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", e.getMessage() != null ? e.getMessage() : "Erreur inattendue");
        return ResponseEntity.status(statut).body(body);
    }

    // ============ AUTHENTIFICATION ============

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestParam String email,
                                                     @RequestParam String motDePasse) {
        if (!HelpDeskService.estAdresseEntreprise(email)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Seules les adresses e-mail professionnelles de l'entreprise Kipropha ("
                    + HelpDeskService.DOMAINE_ENTREPRISE + " ou "
                    + HelpDeskService.DOMAINE_HISTORIQUE + ") sont autorisées.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        Utilisateur utilisateur;
        try {
            utilisateur = helpDeskService.authentifier(email, motDePasse);
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }

        if (utilisateur == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Email ou mot de passe incorrect");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        Map<String, Object> response = succes("Connexion réussie");
        response.put("utilisateur", utilisateur);
        return ResponseEntity.ok(response);
    }

    // ============ TICKETS ============

    @PostMapping("/tickets/creer")
    public ResponseEntity<Map<String, Object>> creerTicket(@RequestParam String titre,
                                                           @RequestParam String description,
                                                           @RequestParam Categorie categorie,
                                                           @RequestParam Priorite priorite,
                                                           @RequestParam Long utilisateurId) {
        try {
            Ticket ticket = helpDeskService.creerTicket(titre, description, categorie, priorite, utilisateurId);
            Map<String, Object> response = succes("Ticket créé avec succès");
            response.put("ticket", ticket);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<Ticket>> obtenirTousLesTickets() {
        return ResponseEntity.ok(helpDeskService.obtenirTousLesTickets());
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<Ticket> obtenirTicketParId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(helpDeskService.obtenirTicketParId(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/tickets/utilisateur/{utilisateurId}")
    public ResponseEntity<List<Ticket>> obtenirTicketsUtilisateur(@PathVariable Long utilisateurId) {
        return ResponseEntity.ok(helpDeskService.obtenirTicketsUtilisateur(utilisateurId));
    }

    @GetMapping("/tickets/support/{supportItId}")
    public ResponseEntity<List<Ticket>> obtenirTicketsSupportIt(@PathVariable Long supportItId) {
        return ResponseEntity.ok(helpDeskService.obtenirTicketsSupportIt(supportItId));
    }

    // ============================================================
    // ESPACE SUPPORT IT
    // ============================================================

    /** Kanban global : tous les tickets, à plat, prêts à être répartis en colonnes. */
    @GetMapping("/tickets/kanban")
    public ResponseEntity<List<TicketResumeDTO>> obtenirKanbanGlobal() {
        return ResponseEntity.ok(helpDeskService.obtenirKanbanGlobal());
    }

    /** Tickets pris en charge par le technicien connecté. */
    @GetMapping("/tickets/assignes")
    public ResponseEntity<?> obtenirTicketsAssignes(@RequestParam Long supportItId) {
        try {
            return ResponseEntity.ok(helpDeskService.obtenirTicketsAssignes(supportItId));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    /** Le technicien se réserve un ticket libre, sans en changer le statut. */
    @PostMapping("/tickets/{id}/assigner")
    public ResponseEntity<Map<String, Object>> assignerTicket(@PathVariable Long id,
                                                              @RequestParam Long supportItId) {
        try {
            TicketResumeDTO ticket = helpDeskService.assignerTicket(id, supportItId);
            Map<String, Object> response = succes("Ticket pris en charge");
            response.put("ticket", ticket);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/tickets/{id}/affecter")
    public ResponseEntity<Map<String, Object>> affecterTicket(@PathVariable Long id,
                                                              @RequestParam Long supportItId) {
        try {
            Ticket ticket = helpDeskService.affecterTicketAuSupport(id, supportItId);
            Map<String, Object> response = succes("Ticket affecté avec succès");
            response.put("ticket", ticket);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/tickets/{id}/changer-statut")
    public ResponseEntity<Map<String, Object>> changerStatut(
            @PathVariable Long id,
            @RequestParam Statut statut,
            @RequestParam(required = false, defaultValue = "-1") Long utilisateurId) {
        try {
            Ticket ticket = helpDeskService.changerStatutTicket(id, statut,
                    utilisateurId != null && utilisateurId > 0 ? utilisateurId : null);

            Map<String, Object> response = succes("Statut modifié avec succès");
            response.put("ticket", ticket);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/tickets/{id}")
    public ResponseEntity<Map<String, Object>> supprimerTicket(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "-1") Long adminId) {
        try {
            if (adminId != null && adminId > 0) {
                helpDeskService.supprimerTicket(id, adminId);
            } else {
                Ticket ticket = ticketRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
                interventionRepository.deleteAll(interventionRepository.findByTicket(ticket));
                ticketRepository.deleteById(id);
            }
            return ResponseEntity.ok(succes("Ticket supprimé avec succès"));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/tickets/rechercher")
    public ResponseEntity<List<Ticket>> rechercherTickets(@RequestParam String critere,
                                                          @RequestParam(defaultValue = "id") String type) {
        return ResponseEntity.ok(helpDeskService.rechercherTickets(critere, type));
    }

    // ============ INTERVENTIONS ============

    /**
     * Commentaire d'intervention : écrit une ligne dans la table intervention,
     * horodatée côté serveur, et l'ajoute à l'historique technique du ticket.
     */
    @PostMapping("/tickets/{ticketId}/interventions")
    public ResponseEntity<Map<String, Object>> ajouterIntervention(@PathVariable Long ticketId,
                                                                   @RequestParam String commentaire,
                                                                   @RequestParam Long auteurId) {
        try {
            ChatMessageDTO intervention =
                    helpDeskService.enregistrerIntervention(ticketId, auteurId, commentaire);

            Map<String, Object> response = succes("Intervention enregistrée");
            response.put("intervention", intervention);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/tickets/{ticketId}/interventions")
    public ResponseEntity<?> obtenirInterventions(@PathVariable Long ticketId,
                                                  @RequestParam Long utilisateurId) {
        try {
            return ResponseEntity.ok(helpDeskService.obtenirConversationTicket(ticketId, utilisateurId));
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
    }

    // ============================================================
    // MESSAGERIE (chat administrateur)
    // ============================================================

    /**
     * Recherche hybride : renvoie { tickets: [...], contacts: [...] }.
     * Le critère peut être un numéro de ticket, un mot du titre, un nom
     * ou une adresse e-mail complète.
     */
    @GetMapping("/chat/recherche")
    public ResponseEntity<Map<String, Object>> rechercheMessagerie(
            @RequestParam(required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(helpDeskService.rechercheMessagerie(q));
    }

    /** Badge affiché au-dessus du fil : demandeur + support en charge. */
    @GetMapping("/chat/tickets/{ticketId}")
    public ResponseEntity<?> obtenirResumeTicket(@PathVariable Long ticketId,
                                                 @RequestParam Long utilisateurId) {
        try {
            return ResponseEntity.ok(helpDeskService.obtenirResumeTicket(ticketId, utilisateurId));
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Colonne de gauche de la messagerie : { tickets: [...], contacts: [...] }.
     * Les tickets où la personne intervient, et les correspondants avec qui
     * elle a échangé hors ticket.
     */
    @GetMapping("/chat/mes-conversations")
    public ResponseEntity<?> obtenirMesConversations(@RequestParam Long utilisateurId) {
        try {
            return ResponseEntity.ok(helpDeskService.obtenirMesConversations(utilisateurId));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Fil de discussion. Fournir soit ticketId (fil du ticket),
     * soit email + utilisateurId (conversation directe).
     */
    @GetMapping("/chat/conversation")
    public ResponseEntity<?> obtenirConversation(
            @RequestParam(required = false) Long ticketId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long utilisateurId) {
        try {
            if (ticketId != null) {
                if (utilisateurId == null) {
                    throw new IllegalArgumentException("utilisateurId est requis pour lire un fil de ticket");
                }
                return ResponseEntity.ok(helpDeskService.obtenirConversationTicket(ticketId, utilisateurId));
            }
            if (email != null && utilisateurId != null) {
                return ResponseEntity.ok(helpDeskService.obtenirConversationDirecte(utilisateurId, email));
            }
            throw new IllegalArgumentException("Indiquez un ticketId, ou un email, avec utilisateurId");
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Envoi d'un message.
     *  - fil de ticket        : ticketId (+ destinataires, e-mails séparés par des virgules)
     *  - conversation directe : email
     * Les paramètres sont acceptés en query string ou en corps form-urlencoded.
     */
    @PostMapping("/chat/messages")
    public ResponseEntity<Map<String, Object>> envoyerMessageChat(
            @RequestParam Long auteurId,
            @RequestParam String contenu,
            @RequestParam(required = false) Long ticketId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String destinataires) {
        try {
            ChatMessageDTO message = (ticketId != null)
                    ? helpDeskService.envoyerMessageTicket(ticketId, auteurId, contenu, destinataires)
                    : helpDeskService.envoyerMessageDirect(auteurId, email, contenu);

            Map<String, Object> response = succes("Message envoyé");
            response.put("data", message);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/chat/messages/{messageId}")
    public ResponseEntity<Map<String, Object>> modifierMessageChat(
            @PathVariable Long messageId,
            @RequestParam Long utilisateurId,
            @RequestParam String contenu,
            @RequestParam(required = false, defaultValue = "TICKET") String source) {
        try {
            ChatMessageDTO message = helpDeskService.modifierMessage(source, messageId, utilisateurId, contenu);
            Map<String, Object> response = succes("Message modifié");
            response.put("data", message);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
    }

    /** Pastille de notification : nombre de messages non lus, toutes sources. */
    @GetMapping("/chat/non-lus")
    public ResponseEntity<?> compterMessagesNonLus(@RequestParam Long utilisateurId) {
        try {
            return ResponseEntity.ok(helpDeskService.compterMessagesNonLus(utilisateurId));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    /** Appelé à l'ouverture de l'onglet Messagerie : remet le compteur à zéro. */
    @PostMapping("/chat/vu")
    public ResponseEntity<Map<String, Object>> marquerMessagerieVue(@RequestParam Long utilisateurId) {
        try {
            helpDeskService.marquerMessagerieVue(utilisateurId);
            return ResponseEntity.ok(succes("Messagerie marquée comme consultée"));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/chat/messages/{messageId}")
    public ResponseEntity<Map<String, Object>> supprimerMessageChat(
            @PathVariable Long messageId,
            @RequestParam Long utilisateurId,
            @RequestParam(required = false, defaultValue = "TICKET") String source) {
        try {
            helpDeskService.supprimerMessage(source, messageId, utilisateurId);
            return ResponseEntity.ok(succes("Message supprimé"));
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
    }

    // ============ STATISTIQUES ============

    @GetMapping("/statistiques")
    public ResponseEntity<Map<String, Object>> obtenirStatistiques() {
        return ResponseEntity.ok(helpDeskService.obtenirStatistiques());
    }

    @GetMapping("/statistiques/dashboard")
    public ResponseEntity<Map<String, Object>> obtenirStatistiquesDashboard() {
        return ResponseEntity.ok(helpDeskService.obtenirStatistiquesDashboard());
    }

    @GetMapping("/statistiques/supports")
    public ResponseEntity<Map<String, Object>> obtenirStatistiquesSupports() {
        return ResponseEntity.ok(helpDeskService.obtenirStatistiquesSupports());
    }

    // ============ ANNONCES GLOBALES (conservées) ============

    @PostMapping("/messages/envoyer")
    public ResponseEntity<Map<String, Object>> envoyerMessage(
            @RequestParam Long auteurId,
            @RequestParam String contenu,
            @RequestParam(defaultValue = "TOUS") String destinataire) {
        try {
            Message message = helpDeskService.envoyerMessage(auteurId, contenu, destinataire);

            // On renvoie une vue plate : sérialiser l'entité exposait le proxy
            // Hibernate de l'auteur et faisait échouer la requête ("Erreur").
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", message.getId());
            data.put("contenu", message.getContenu());
            data.put("dateEnvoi", message.getDateEnvoi());
            data.put("destinataire", message.getDestinataire());
            data.put("auteurId", message.getAuteur().getId());
            data.put("auteurNom", message.getAuteur().getNomComplet());

            Map<String, Object> response = succes("Message envoyé avec succès");
            response.put("data", data);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/messages")
    public ResponseEntity<List<Map<String, Object>>> obtenirMessages() {
        List<Map<String, Object>> messageList = new ArrayList<>();

        for (Message msg : helpDeskService.obtenirTousLesMessages()) {
            Map<String, Object> msgMap = new LinkedHashMap<>();
            msgMap.put("id", msg.getId());
            msgMap.put("contenu", msg.getContenu());
            msgMap.put("dateEnvoi", msg.getDateEnvoi());
            msgMap.put("destinataire", msg.getDestinataire());

            Map<String, Object> auteurMap = new LinkedHashMap<>();
            if (msg.getAuteur() != null) {
                auteurMap.put("id", msg.getAuteur().getId());
                auteurMap.put("prenom", msg.getAuteur().getPrenom());
                auteurMap.put("nom", msg.getAuteur().getNom());
                auteurMap.put("email", msg.getAuteur().getEmail());
            }
            msgMap.put("auteur", auteurMap);
            messageList.add(msgMap);
        }
        return ResponseEntity.ok(messageList);
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Map<String, Object>> supprimerMessage(@PathVariable Long id) {
        try {
            helpDeskService.supprimerMessage(id);
            return ResponseEntity.ok(succes("Message supprimé avec succès"));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    // ============ UTILISATEURS ============

    @PostMapping("/utilisateurs/creer")
    public ResponseEntity<Map<String, Object>> creerUtilisateur(
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam String email,
            @RequestParam String motDePasse,
            @RequestParam Role role,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false, defaultValue = "-1") Long adminId) {
        try {
            Utilisateur utilisateur = helpDeskService.creerUtilisateur(
                    nom, prenom, email, motDePasse, role, departement, adminId);
            Map<String, Object> response = succes("Utilisateur créé avec succès");
            response.put("utilisateur", utilisateur);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Modification partielle : tous les champs sont facultatifs, seuls ceux
     * qui sont transmis sont appliqués. Modifier le seul prénom n'oblige donc
     * pas à renvoyer l'ensemble des coordonnées.
     */
    @PutMapping("/utilisateurs/{id}")
    public ResponseEntity<Map<String, Object>> mettreAJourUtilisateur(
            @PathVariable Long id,
            @RequestParam(required = false) String nom,
            @RequestParam(required = false) String prenom,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false, defaultValue = "-1") Long adminId) {
        try {
            Utilisateur utilisateur = helpDeskService.mettreAJourUtilisateur(
                    id, nom, prenom, email, role, departement, adminId);
            Map<String, Object> response = succes("Utilisateur modifié avec succès");
            response.put("utilisateur", utilisateur);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/utilisateurs")
    public ResponseEntity<List<Utilisateur>> obtenirUtilisateurs() {
        return ResponseEntity.ok(helpDeskService.obtenirTousLesUtilisateurs());
    }

    @DeleteMapping("/utilisateurs/{id}")
    public ResponseEntity<Map<String, Object>> supprimerUtilisateur(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "-1") Long adminId) {
        try {
            Utilisateur user = utilisateurRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            if (adminId != null && adminId > 0) {
                Utilisateur admin = utilisateurRepository.findById(adminId).orElse(null);
                if (admin != null) {
                    helpDeskService.enregistrerAudit(admin, "SUPPRESSION",
                            "Suppression de l'utilisateur: " + user.getNomComplet() + " (" + user.getEmail() + ")",
                            "UTILISATEUR", id);
                }
            }

            utilisateurRepository.deleteById(id);
            return ResponseEntity.ok(succes("Utilisateur supprimé avec succès"));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    /** Le demandeur confirme que son problème est réglé : le ticket est clôturé. */
    @PostMapping("/tickets/{id}/valider-resolution")
    public ResponseEntity<Map<String, Object>> validerResolution(@PathVariable Long id,
                                                                 @RequestParam Long utilisateurId) {
        try {
            TicketResumeDTO ticket = helpDeskService.validerResolution(id, utilisateurId);
            Map<String, Object> response = succes("Merci, le ticket est clôturé");
            response.put("ticket", ticket);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    // ============================================================
    // MON COMPTE
    // ============================================================

    /** Le titulaire met à jour son nom et son prénom. */
    @PutMapping("/mon-compte")
    public ResponseEntity<Map<String, Object>> mettreAJourMonCompte(@RequestParam Long utilisateurId,
                                                                    @RequestParam String nom,
                                                                    @RequestParam String prenom) {
        try {
            Utilisateur utilisateur = helpDeskService.mettreAJourMonCompte(utilisateurId, nom, prenom);
            Map<String, Object> response = succes("Coordonnées enregistrées");
            response.put("utilisateur", utilisateur);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    /** Changement de mot de passe, après vérification de l'ancien. */
    @PostMapping("/mon-compte/mot-de-passe")
    public ResponseEntity<Map<String, Object>> changerMotDePasse(@RequestParam Long utilisateurId,
                                                                 @RequestParam String ancien,
                                                                 @RequestParam String nouveau) {
        try {
            helpDeskService.changerMotDePasse(utilisateurId, ancien, nouveau);
            return ResponseEntity.ok(succes("Mot de passe mis à jour"));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    /** Historique complet des tickets d'un demandeur, clôturés compris. */
    @GetMapping("/tickets/historique")
    public ResponseEntity<?> obtenirHistoriqueTickets(@RequestParam Long utilisateurId) {
        try {
            return ResponseEntity.ok(helpDeskService.obtenirHistoriqueTickets(utilisateurId));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    // ============================================================
    // DEMANDES D'ACCÈS (page de connexion, sans authentification)
    // ============================================================

    /** Réinitialisation de mot de passe ou déblocage de compte. */
    @PostMapping("/acces-urgence")
    public ResponseEntity<Map<String, Object>> deposerDemandeAcces(
            @RequestParam String type,
            @RequestParam String email,
            @RequestParam(required = false) String nom,
            @RequestParam(required = false) String prenom,
            @RequestParam(required = false) String contactAlternatif,
            @RequestParam(required = false) String description) {
        try {
            DemandeAcces demande = helpDeskService.enregistrerDemandeAcces(
                    type, nom, prenom, email, contactAlternatif, description);

            Map<String, Object> response = succes(
                    "Votre demande a été transmise au support. Vous serez recontacté.");
            response.put("demande", demande);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/acces-urgence")
    public ResponseEntity<List<DemandeAcces>> obtenirDemandesAcces() {
        return ResponseEntity.ok(helpDeskService.obtenirDemandesAcces());
    }

    @PostMapping("/acces-urgence/{id}/traitee")
    public ResponseEntity<Map<String, Object>> marquerDemandeTraitee(@PathVariable Long id,
                                                                     @RequestParam Long adminId) {
        try {
            helpDeskService.marquerDemandeTraitee(id, adminId);
            return ResponseEntity.ok(succes("Demande marquée comme traitée"));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    /** Active ou désactive un compte sans le supprimer. */
    @PostMapping("/utilisateurs/{id}/statut")
    public ResponseEntity<Map<String, Object>> changerStatutCompte(@PathVariable Long id,
                                                                   @RequestParam boolean actif,
                                                                   @RequestParam Long adminId) {
        try {
            Utilisateur utilisateur = helpDeskService.changerStatutCompte(id, actif, adminId);
            Map<String, Object> response = succes(actif ? "Compte activé" : "Compte désactivé");
            response.put("utilisateur", utilisateur);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    // ============ JOURNAL D'AUDIT ============

    @GetMapping("/audit/logs")
    public ResponseEntity<List<AuditLogDTO>> obtenirAuditLogs() {
        return ResponseEntity.ok(helpDeskService.obtenirTousLesLogs());
    }

    @GetMapping("/audit/logs/recent")
    public ResponseEntity<List<AuditLogDTO>> obtenirAuditLogsRecent(
            @RequestParam(defaultValue = "100") int limite) {
        return ResponseEntity.ok(helpDeskService.obtenirLogsRecents(limite));
    }

    @GetMapping("/audit/logs/utilisateur/{utilisateurId}")
    public ResponseEntity<List<AuditLogDTO>> obtenirAuditLogsByUser(@PathVariable Long utilisateurId) {
        return ResponseEntity.ok(helpDeskService.obtenirLogsByUtilisateur(utilisateurId));
    }

    @GetMapping("/audit/logs/action/{typeAction}")
    public ResponseEntity<List<AuditLogDTO>> obtenirAuditLogsByAction(@PathVariable String typeAction) {
        return ResponseEntity.ok(helpDeskService.obtenirLogsByTypeAction(typeAction));
    }

    @PostMapping("/audit/enregistrer")
    public ResponseEntity<Map<String, Object>> enregistrerAudit(
            @RequestParam Long utilisateurId,
            @RequestParam String typeAction,
            @RequestParam String description,
            @RequestParam String entiteAffectee,
            @RequestParam(required = false) Long idEntite) {
        try {
            Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            AuditLog log = helpDeskService.enregistrerAudit(utilisateur, typeAction, description,
                    entiteAffectee, idEntite);

            Map<String, Object> response = succes("Action enregistrée");
            response.put("log", AuditLogDTO.from(log));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    // ============ HEALTH CHECK ============

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("application", "IncidentManager - KIPROPHA");
        response.put("version", "1.1.0");
        response.put("date", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}