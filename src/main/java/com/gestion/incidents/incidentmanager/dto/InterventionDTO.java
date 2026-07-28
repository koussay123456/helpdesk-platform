package com.gestion.incidents.incidentmanager.dto;

import com.gestion.incidents.incidentmanager.model.Intervention;
import com.gestion.incidents.incidentmanager.model.Utilisateur;

import java.time.LocalDateTime;

/**
 * Vue unifiée d'un message, quelle que soit sa provenance.
 *
 * source = "TICKET"  → ligne de la table intervention (fil rattaché a un ticket)
 * source = "DIRECT"  → ligne de la table message      (conversation par e-mail)
 *
 * Le couple (source, id) identifie le message pour l'édition et la suppression :
 * les deux tables ont leurs propres séquences, l'id seul ne suffirait pas.
 */
public class InterventionDTO {


    private Long id;
    private Long ticketId;

    private String contenu;
    private LocalDateTime dateEnvoi;
    private LocalDateTime dateModification;
    private boolean modifie;

    private String destinataires;

    private Long auteurId;
    private String auteurNom;
    private String auteurEmail;
    private String auteurRole;

    public InterventionDTO() {
    }

    public static InterventionDTO from(Intervention intervention) {
        InterventionDTO dto = new InterventionDTO();
        dto.id = intervention.getId();
        dto.ticketId = intervention.getTicket() != null ? intervention.getTicket().getId() : null;
        dto.contenu = intervention.getCommentaire();
        dto.dateEnvoi = intervention.getDateIntervention();
        dto.dateModification = intervention.getDateModification();
        dto.modifie = intervention.getDateModification() != null;
        dto.destinataires = intervention.getDestinataires();
        appliquerAuteur(dto, intervention.getAuteur());
        return dto;
    }


    private static void appliquerAuteur(InterventionDTO dto, Utilisateur auteur) {
        if (auteur == null) {
            return;
        }
        dto.auteurId = auteur.getId();
        dto.auteurNom = auteur.getNomComplet();
        dto.auteurEmail = auteur.getEmail();
        dto.auteurRole = auteur.getRole() != null ? auteur.getRole().getLabel() : null;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public String getContenu() { return contenu; }
    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public LocalDateTime getDateModification() { return dateModification; }
    public boolean isModifie() { return modifie; }
    public String getDestinataires() { return destinataires; }
    public Long getAuteurId() { return auteurId; }
    public String getAuteurNom() { return auteurNom; }
    public String getAuteurEmail() { return auteurEmail; }
    public String getAuteurRole() { return auteurRole; }

    public void setId(Long id) { this.id = id; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }
    public void setModifie(boolean modifie) { this.modifie = modifie; }
    public void setDestinataires(String destinataires) { this.destinataires = destinataires; }
    public void setAuteurId(Long auteurId) { this.auteurId = auteurId; }
    public void setAuteurNom(String auteurNom) { this.auteurNom = auteurNom; }
    public void setAuteurEmail(String auteurEmail) { this.auteurEmail = auteurEmail; }
    public void setAuteurRole(String auteurRole) { this.auteurRole = auteurRole; }
}
