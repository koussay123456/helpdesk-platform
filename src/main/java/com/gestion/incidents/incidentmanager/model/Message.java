package com.gestion.incidents.incidentmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Message hors contexte de ticket : conversation directe entre deux personnes,
 * identifiée par l'adresse e-mail du destinataire.
 * "TOUS" reste accepté pour les anciennes annonces globales.
 */
@Entity
@Table(name = "message", indexes = {
        @Index(name = "idx_message_auteur", columnList = "auteur_id"),
        @Index(name = "idx_message_date", columnList = "date_envoi"),
        @Index(name = "idx_message_destinataire", columnList = "destinataire")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", nullable = false)
    private Utilisateur auteur;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateEnvoi;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    /** Adresse e-mail du destinataire, ou "TOUS" pour une annonce. */
    @Column(nullable = false, length = 255)
    private String destinataire;

    // Constructeurs
    public Message() {
    }

    public Message(Utilisateur auteur, String contenu, String destinataire) {
        this.auteur = auteur;
        this.contenu = contenu;
        this.destinataire = destinataire;
        this.dateEnvoi = LocalDateTime.now();
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Utilisateur getAuteur() { return auteur; }
    public void setAuteur(Utilisateur auteur) { this.auteur = auteur; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }

    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }

    public String getDestinataire() { return destinataire; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }

    @PrePersist
    protected void onCreate() {
        if (dateEnvoi == null) {
            dateEnvoi = LocalDateTime.now();
        }
    }
}
