package com.gestion.incidents.incidentmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Trace horodatée d'une action sensible : création, modification, suppression,
 * et désormais tentative de connexion, réussie comme échouée.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_utilisateur", columnList = "utilisateur_id"),
        @Index(name = "idx_audit_date", columnList = "date_action"),
        @Index(name = "idx_audit_action", columnList = "type_action")
})
public class AuditLog {

    /** Connexion aboutie. */
    public static final String CONNEXION = "CONNEXION";

    /** Tentative repoussée : le motif précise laquelle des trois causes. */
    public static final String CONNEXION_ECHOUEE = "CONNEXION_ECHOUEE";

    public static final String MOTIF_INCONNU     = "Adresse inconnue";
    public static final String MOTIF_MOT_DE_PASSE = "Mot de passe incorrect";
    public static final String MOTIF_DESACTIVE   = "Compte désactivé";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Facultatif : une tentative sur une adresse inconnue ne référence aucun
     * compte. C'est pour cette raison que la colonne accepte la valeur nulle.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "motDePasse"})
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 100)
    private String typeAction;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String entiteAffectee;

    @Column
    private Long idEntite;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateAction;

    @Column(length = 50)
    private String adresseIp;

    /** L'adresse réellement saisie, conservée même sans compte correspondant. */
    @Column(length = 255)
    private String emailSaisi;

    /** Renseigné pour les seules tentatives échouées. */
    @Column(length = 255)
    private String motifEchec;

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

    /** Tentative de connexion repoussée, avec ou sans compte identifié. */
    public static AuditLog echecConnexion(Utilisateur utilisateur, String emailSaisi, String motif) {
        AuditLog log = new AuditLog(utilisateur, CONNEXION_ECHOUEE,
                "Tentative de connexion refusée pour " + emailSaisi + " — " + motif,
                "UTILISATEUR", utilisateur != null ? utilisateur.getId() : null);
        log.emailSaisi = emailSaisi;
        log.motifEchec = motif;
        return log;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Utilisateur getUtilisateur() { return utilisateur; }
    public void setUtilisateur(Utilisateur utilisateur) { this.utilisateur = utilisateur; }

    public String getTypeAction() { return typeAction; }
    public void setTypeAction(String typeAction) { this.typeAction = typeAction; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEntiteAffectee() { return entiteAffectee; }
    public void setEntiteAffectee(String entiteAffectee) { this.entiteAffectee = entiteAffectee; }

    public Long getIdEntite() { return idEntite; }
    public void setIdEntite(Long idEntite) { this.idEntite = idEntite; }

    public LocalDateTime getDateAction() { return dateAction; }
    public void setDateAction(LocalDateTime dateAction) { this.dateAction = dateAction; }

    public String getAdresseIp() { return adresseIp; }
    public void setAdresseIp(String adresseIp) { this.adresseIp = adresseIp; }

    public String getEmailSaisi() { return emailSaisi; }
    public void setEmailSaisi(String emailSaisi) { this.emailSaisi = emailSaisi; }

    public String getMotifEchec() { return motifEchec; }
    public void setMotifEchec(String motifEchec) { this.motifEchec = motifEchec; }

    @PrePersist
    protected void onCreate() {
        if (dateAction == null) {
            dateAction = LocalDateTime.now();
        }
    }
}