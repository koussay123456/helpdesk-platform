package com.gestion.incidents.incidentmanager.dto;

import com.gestion.incidents.incidentmanager.model.Ticket;

/**
 * Résumé d'un ticket, utilisé par la recherche et par le badge affiché
 * au-dessus du fil de discussion. Aucun proxy Hibernate n'est exposé :
 * toutes les valeurs sont copiées ici, ce qui évite les erreurs de
 * sérialisation JSON (ByteBuddyInterceptor / LazyInitializationException).
 */
public class TicketResumeDTO {

    private Long id;
    private String numero;        // TKT-000108
    private String titre;
    private String description;
    private java.time.LocalDateTime dateCreation;
    private java.time.LocalDateTime dateResolution;
    private String statut;
    private String statutLabel;
    private String priorite;
    private String categorie;

    private String demandeurNom;
    private String demandeurEmail;

    private String supportNom;    // null si non assigné
    private String supportEmail;  // null si non assigné

    private long nbMessages;

    private String dernierMessage;
    private String dernierAuteur;
    private java.time.LocalDateTime dateDernierMessage;

    public TicketResumeDTO() {
    }

    public static TicketResumeDTO from(Ticket ticket, long nbMessages) {
        TicketResumeDTO dto = new TicketResumeDTO();
        dto.id = ticket.getId();
        dto.numero = ticket.getNumeroTicket();
        dto.titre = ticket.getTitre();
        dto.description = ticket.getDescription();
        dto.dateCreation = ticket.getDateCreation();
        dto.dateResolution = ticket.getDateResolution();
        dto.statut = ticket.getStatut() != null ? ticket.getStatut().name() : null;
        dto.statutLabel = ticket.getStatut() != null ? ticket.getStatut().getLabel() : null;
        dto.priorite = ticket.getPriorite() != null ? ticket.getPriorite().name() : null;
        dto.categorie = ticket.getCategorie() != null ? ticket.getCategorie().name() : null;

        if (ticket.getUtilisateur() != null) {
            dto.demandeurNom = ticket.getUtilisateur().getNomComplet();
            dto.demandeurEmail = ticket.getUtilisateur().getEmail();
        }
        if (ticket.getSupportIt() != null) {
            dto.supportNom = ticket.getSupportIt().getNomComplet();
            dto.supportEmail = ticket.getSupportIt().getEmail();
        }
        dto.nbMessages = nbMessages;
        return dto;
    }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public String getTitre() { return titre; }
    public String getDescription() { return description; }
    public java.time.LocalDateTime getDateCreation() { return dateCreation; }
    public java.time.LocalDateTime getDateResolution() { return dateResolution; }
    public String getStatut() { return statut; }
    public String getStatutLabel() { return statutLabel; }
    public String getPriorite() { return priorite; }
    public String getCategorie() { return categorie; }
    public String getDemandeurNom() { return demandeurNom; }
    public String getDemandeurEmail() { return demandeurEmail; }
    public String getSupportNom() { return supportNom; }
    public String getSupportEmail() { return supportEmail; }
    public long getNbMessages() { return nbMessages; }
    public String getDernierMessage() { return dernierMessage; }
    public String getDernierAuteur() { return dernierAuteur; }
    public java.time.LocalDateTime getDateDernierMessage() { return dateDernierMessage; }

    public void setId(Long id) { this.id = id; }
    public void setNumero(String numero) { this.numero = numero; }
    public void setTitre(String titre) { this.titre = titre; }
    public void setDescription(String description) { this.description = description; }
    public void setDateCreation(java.time.LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    public void setDateResolution(java.time.LocalDateTime dateResolution) { this.dateResolution = dateResolution; }
    public void setStatut(String statut) { this.statut = statut; }
    public void setStatutLabel(String statutLabel) { this.statutLabel = statutLabel; }
    public void setPriorite(String priorite) { this.priorite = priorite; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public void setDemandeurNom(String demandeurNom) { this.demandeurNom = demandeurNom; }
    public void setDemandeurEmail(String demandeurEmail) { this.demandeurEmail = demandeurEmail; }
    public void setSupportNom(String supportNom) { this.supportNom = supportNom; }
    public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }
    public void setNbMessages(long nbMessages) { this.nbMessages = nbMessages; }

    /** Aperçu affiché dans la liste des conversations : 110 caractères au plus. */
    public void setDernierMessage(String contenu, String auteur, java.time.LocalDateTime date) {
        this.dernierMessage = (contenu != null && contenu.length() > 110)
                ? contenu.substring(0, 110) + "…"
                : contenu;
        this.dernierAuteur = auteur;
        this.dateDernierMessage = date;
    }
}
