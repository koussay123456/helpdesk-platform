package com.gestion.incidents.incidentmanager.dto;

import com.gestion.incidents.incidentmanager.model.Utilisateur;

/**
 * Personne joignable depuis la messagerie : un compte de la plateforme,
 * ou une adresse externe saisie librement dans la barre de recherche.
 */
public class ContactDTO {

    private Long id;
    private String nom;
    private String email;
    private String role;
    private boolean externe;

    private String dernierMessage;
    private String dernierAuteur;
    private java.time.LocalDateTime dateDernierMessage;

    public ContactDTO() {
    }

    public static ContactDTO from(Utilisateur utilisateur) {
        ContactDTO dto = new ContactDTO();
        dto.id = utilisateur.getId();
        dto.nom = utilisateur.getNomComplet();
        dto.email = utilisateur.getEmail();
        dto.role = utilisateur.getRole() != null ? utilisateur.getRole().getLabel() : null;
        dto.externe = false;
        return dto;
    }

    public static ContactDTO externe(String email) {
        ContactDTO dto = new ContactDTO();
        dto.nom = email;
        dto.email = email;
        dto.role = "Adresse externe";
        dto.externe = true;
        return dto;
    }

    public Long getId() { return id; }
    public String getNom() { return nom; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isExterne() { return externe; }
    public String getDernierMessage() { return dernierMessage; }
    public String getDernierAuteur() { return dernierAuteur; }
    public java.time.LocalDateTime getDateDernierMessage() { return dateDernierMessage; }

    public void setId(Long id) { this.id = id; }
    public void setNom(String nom) { this.nom = nom; }
    public void setEmail(String email) { this.email = email; }
    public void setRole(String role) { this.role = role; }
    public void setExterne(boolean externe) { this.externe = externe; }

    /** Aperçu affiché dans la liste des conversations : 110 caractères au plus. */
    public void setDernierMessage(String contenu, String auteur, java.time.LocalDateTime date) {
        this.dernierMessage = (contenu != null && contenu.length() > 110)
                ? contenu.substring(0, 110) + "…"
                : contenu;
        this.dernierAuteur = auteur;
        this.dateDernierMessage = date;
    }
}