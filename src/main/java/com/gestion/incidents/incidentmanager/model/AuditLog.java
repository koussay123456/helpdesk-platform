package com.gestion.incidents.incidentmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_utilisateur", columnList = "utilisateur_id"),
        @Index(name = "idx_audit_date", columnList = "date_action"),
        @Index(name = "idx_audit_action", columnList = "type_action")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 100)
    private String typeAction; // CREATION, MODIFICATION, SUPPRESSION, CONNEXION, DECONNEXION, CONSULTATION

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String entiteAffectee; // TICKET, UTILISATEUR, MESSAGE, etc.

    @Column(nullable = true)
    private Long idEntite; // ID de l'entité modifiée

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateAction;

    @Column(nullable = true, length = 50)
    private String adresseIp;

    // Constructeurs
    public AuditLog() {
    }

    public AuditLog(Utilisateur utilisateur, String typeAction, String description,
                    String entiteAffectee, Long idEntite) {
        this.utilisateur = utilisateur;
        this.typeAction = typeAction;
        this.description = description;
        this.entiteAffectee = entiteAffectee;
        this.idEntite = idEntite;
        this.dateAction = LocalDateTime.now();
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public String getTypeAction() {
        return typeAction;
    }

    public void setTypeAction(String typeAction) {
        this.typeAction = typeAction;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEntiteAffectee() {
        return entiteAffectee;
    }

    public void setEntiteAffectee(String entiteAffectee) {
        this.entiteAffectee = entiteAffectee;
    }

    public Long getIdEntite() {
        return idEntite;
    }

    public void setIdEntite(Long idEntite) {
        this.idEntite = idEntite;
    }

    public LocalDateTime getDateAction() {
        return dateAction;
    }

    public void setDateAction(LocalDateTime dateAction) {
        this.dateAction = dateAction;
    }

    public String getAdresseIp() {
        return adresseIp;
    }

    public void setAdresseIp(String adresseIp) {
        this.adresseIp = adresseIp;
    }

    @PrePersist
    protected void onCreate() {
        if (dateAction == null) {
            dateAction = LocalDateTime.now();
        }
    }
}