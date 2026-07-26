package com.gestion.incidents.incidentmanager.dto;

import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) pour les messages
 * Évite les problèmes de sérialisation JSON circulaires
 */
public class MessageDTO {
    public Long id;
    public Long auteurId;
    public String auteurPrenom;
    public String auteurNom;
    public String auteurEmail;
    public String contenu;
    public LocalDateTime dateEnvoi;
    public String destinataire;

    // Constructeurs
    public MessageDTO() {}

    public MessageDTO(Long id, Long auteurId, String auteurPrenom, String auteurNom,
                      String auteurEmail, String contenu, LocalDateTime dateEnvoi, String destinataire) {
        this.id = id;
        this.auteurId = auteurId;
        this.auteurPrenom = auteurPrenom;
        this.auteurNom = auteurNom;
        this.auteurEmail = auteurEmail;
        this.contenu = contenu;
        this.dateEnvoi = dateEnvoi;
        this.destinataire = destinataire;
    }

    // Getters
    public Long getId() { return id; }
    public Long getAuteurId() { return auteurId; }
    public String getAuteurPrenom() { return auteurPrenom; }
    public String getAuteurNom() { return auteurNom; }
    public String getAuteurEmail() { return auteurEmail; }
    public String getContenu() { return contenu; }
    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public String getDestinataire() { return destinataire; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setAuteurId(Long auteurId) { this.auteurId = auteurId; }
    public void setAuteurPrenom(String auteurPrenom) { this.auteurPrenom = auteurPrenom; }
    public void setAuteurNom(String auteurNom) { this.auteurNom = auteurNom; }
    public void setAuteurEmail(String auteurEmail) { this.auteurEmail = auteurEmail; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }
}