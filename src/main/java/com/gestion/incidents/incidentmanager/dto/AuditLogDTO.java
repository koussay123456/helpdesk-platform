package com.gestion.incidents.incidentmanager.dto;

import com.gestion.incidents.incidentmanager.model.AuditLog;

import java.time.LocalDateTime;

/**
 * Vue plate d'une ligne d'audit.
 *
 * C'est la cause de l'ancien message "Erreur de chargement" : l'entité AuditLog
 * porte une relation @ManyToOne(fetch = LAZY) vers Utilisateur, et Jackson
 * échouait à sérialiser le proxy Hibernate. On renvoie désormais des valeurs
 * simples.
 */
public class AuditLogDTO {

    private Long id;
    private String typeAction;
    private String description;
    private String entiteAffectee;
    private Long idEntite;
    private LocalDateTime dateAction;

    private Long utilisateurId;
    private String utilisateurNom;
    private String utilisateurEmail;

    public AuditLogDTO() {
    }

    public static AuditLogDTO from(AuditLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.id = log.getId();
        dto.typeAction = log.getTypeAction();
        dto.description = log.getDescription();
        dto.entiteAffectee = log.getEntiteAffectee();
        dto.idEntite = log.getIdEntite();
        dto.dateAction = log.getDateAction();

        if (log.getUtilisateur() != null) {
            dto.utilisateurId = log.getUtilisateur().getId();
            dto.utilisateurNom = log.getUtilisateur().getNomComplet();
            dto.utilisateurEmail = log.getUtilisateur().getEmail();
        } else {
            dto.utilisateurNom = "Utilisateur supprimé";
        }
        return dto;
    }

    public Long getId() { return id; }
    public String getTypeAction() { return typeAction; }
    public String getDescription() { return description; }
    public String getEntiteAffectee() { return entiteAffectee; }
    public Long getIdEntite() { return idEntite; }
    public LocalDateTime getDateAction() { return dateAction; }
    public Long getUtilisateurId() { return utilisateurId; }
    public String getUtilisateurNom() { return utilisateurNom; }
    public String getUtilisateurEmail() { return utilisateurEmail; }

    public void setId(Long id) { this.id = id; }
    public void setTypeAction(String typeAction) { this.typeAction = typeAction; }
    public void setDescription(String description) { this.description = description; }
    public void setEntiteAffectee(String entiteAffectee) { this.entiteAffectee = entiteAffectee; }
    public void setIdEntite(Long idEntite) { this.idEntite = idEntite; }
    public void setDateAction(LocalDateTime dateAction) { this.dateAction = dateAction; }
    public void setUtilisateurId(Long utilisateurId) { this.utilisateurId = utilisateurId; }
    public void setUtilisateurNom(String utilisateurNom) { this.utilisateurNom = utilisateurNom; }
    public void setUtilisateurEmail(String utilisateurEmail) { this.utilisateurEmail = utilisateurEmail; }
}