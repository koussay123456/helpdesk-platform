package com.gestion.incidents.incidentmanager.controller;

import com.gestion.incidents.incidentmanager.dto.InterventionDTO;
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

    /**
     * Fiche d'un ticket : description, métadonnées, demandeur et technicien
     * en charge. Alimente la fenêtre qui s'ouvre au clic sur une ligne.
     */
    @GetMapping("/tickets/{ticketId}/fiche")
    public ResponseEntity<?> obtenirFicheTicket(@PathVariable Long ticketId,
                                                @RequestParam Long utilisateurId) {
        try {
            return ResponseEntity.ok(helpDeskService.obtenirResumeTicket(ticketId, utilisateurId));
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
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
            InterventionDTO intervention =
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

    // ============ STATISTIQUES ============

    @GetMapping("/statistiques")
    public ResponseEntity<Map<String, Object>> obtenirStatistiques() {
        return ResponseEntity.ok(helpDeskService.obtenirStatistiques());
    }

    @GetMapping("/statistiques/dashboard")
    public ResponseEntity<Map<String, Object>> obtenirStatistiquesDashboard() {
        return ResponseEntity.ok(helpDeskService.obtenirStatistiquesDashboard());
    }

    /**
     * Indicateurs de performance sur une période. Les dates sont attendues au
     * format ISO (2026-07-01) ; à défaut, les trente derniers jours.
     */
    @GetMapping("/statistiques/performance")
    public ResponseEntity<?> obtenirIndicateursPerformance(
            @RequestParam(required = false) String debut,
            @RequestParam(required = false) String fin) {
        try {
            LocalDateTime borneFin = (fin == null || fin.isBlank())
                    ? LocalDateTime.now()
                    : java.time.LocalDate.parse(fin).atTime(23, 59, 59);
            LocalDateTime borneDebut = (debut == null || debut.isBlank())
                    ? borneFin.minusDays(30).toLocalDate().atStartOfDay()
                    : java.time.LocalDate.parse(debut).atStartOfDay();

            return ResponseEntity.ok(helpDeskService.obtenirIndicateursPerformance(borneDebut, borneFin));
        } catch (Exception e) {
            return echec(e, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/statistiques/supports")
    public ResponseEntity<Map<String, Object>> obtenirStatistiquesSupports() {
        return ResponseEntity.ok(helpDeskService.obtenirStatistiquesSupports());
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
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false, defaultValue = "-1") Long adminId) {
        try {
            Utilisateur utilisateur = helpDeskService.mettreAJourUtilisateur(
                    id, nom, prenom, email, role, departement, actif, adminId);
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
            helpDeskService.supprimerUtilisateur(id, adminId);
            return ResponseEntity.ok(succes("Compte supprimé"));
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

    /**
     * Chiffre tous les mots de passe encore en clair, sans attendre que
     * chaque titulaire se connecte. Réservé à un administrateur.
     */
    @PostMapping("/admin/chiffrer-mots-de-passe")
    public ResponseEntity<?> chiffrerMotsDePasse(@RequestParam Long adminId) {
        try {
            return ResponseEntity.ok(helpDeskService.chiffrerMotsDePasseEnClair(adminId));
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
    }

    // ============ COMMENTAIRES ============

    /** Modification d'un commentaire par son auteur. */
    @PutMapping("/interventions/{id}")
    public ResponseEntity<?> modifierIntervention(@PathVariable Long id,
                                                  @RequestParam Long auteurId,
                                                  @RequestParam String commentaire) {
        try {
            return ResponseEntity.ok(helpDeskService.modifierIntervention(id, auteurId, commentaire));
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
    }

    /** Suppression d'un commentaire par son auteur. */
    @DeleteMapping("/interventions/{id}")
    public ResponseEntity<Map<String, Object>> supprimerIntervention(@PathVariable Long id,
                                                                     @RequestParam Long auteurId) {
        try {
            helpDeskService.supprimerIntervention(id, auteurId);
            return ResponseEntity.ok(succes("Commentaire supprimé"));
        } catch (Exception e) {
            return echec(e, HttpStatus.FORBIDDEN);
        }
    }

    // ============ HISTORIQUE DE CONNEXION ============

    /**
     * Tentatives de connexion sur une période, réussies et refusées.
     * Les dates sont attendues au format ISO ; à défaut, les trente
     * derniers jours.
     */
    @GetMapping("/audit/connexions")
    public ResponseEntity<?> obtenirHistoriqueConnexions(
            @RequestParam(required = false) String debut,
            @RequestParam(required = false) String fin,
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) String resultat) {
        try {
            LocalDateTime borneFin = (fin == null || fin.isBlank())
                    ? LocalDateTime.now()
                    : java.time.LocalDate.parse(fin).atTime(23, 59, 59);
            LocalDateTime borneDebut = (debut == null || debut.isBlank())
                    ? borneFin.minusDays(30).toLocalDate().atStartOfDay()
                    : java.time.LocalDate.parse(debut).atStartOfDay();

            return ResponseEntity.ok(helpDeskService.obtenirHistoriqueConnexions(
                    borneDebut, borneFin, recherche, resultat));
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