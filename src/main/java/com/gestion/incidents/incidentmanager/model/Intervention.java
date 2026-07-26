package com.gestion.incidents.incidentmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Message d'un fil de discussion rattaché à un ticket.
 *
 * Deux colonnes ajoutées, toutes deux nullables : la migration est additive,
 * les lignes existantes restent valides.
 *  - destinataires    : e-mails visés, séparés par des virgules (demandeur, support, ou les deux)
 *  - dateModification : renseignée uniquement si l'auteur a édité son message
 */
@Entity
@Table(name = "intervention", indexes = {
        @Index(name = "idx_intervention_ticket", columnList = "ticket_id")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Intervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String commentaire;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateIntervention;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @Column(length = 512)
    private String destinataires;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", nullable = false)
    private Utilisateur auteur;

    // Constructeurs
    public Intervention() {
    }

    public Intervention(Ticket ticket, String commentaire, Utilisateur auteur) {
        this(ticket, commentaire, auteur, null);
    }

    public Intervention(Ticket ticket, String commentaire, Utilisateur auteur, String destinataires) {
        this.ticket = ticket;
        this.commentaire = commentaire;
        this.auteur = auteur;
        this.destinataires = destinataires;
        this.dateIntervention = LocalDateTime.now();
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public LocalDateTime getDateIntervention() { return dateIntervention; }
    public void setDateIntervention(LocalDateTime dateIntervention) { this.dateIntervention = dateIntervention; }

    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }

    public String getDestinataires() { return destinataires; }
    public void setDestinataires(String destinataires) { this.destinataires = destinataires; }

    public Utilisateur getAuteur() { return auteur; }
    public void setAuteur(Utilisateur auteur) { this.auteur = auteur; }

    @PrePersist
    protected void onCreate() {
        if (dateIntervention == null) {
            dateIntervention = LocalDateTime.now();
        }
    }
}
