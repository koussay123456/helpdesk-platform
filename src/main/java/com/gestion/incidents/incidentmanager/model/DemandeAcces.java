package com.gestion.incidents.incidentmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Demande déposée depuis la page de connexion, par quelqu'un qui ne peut
 * justement pas se connecter. Deux cas :
 *   MOT_DE_PASSE : demande de réinitialisation
 *   ACCES_BLOQUE : compte bloqué, accès impossible
 *
 * Ces demandes n'envoient aucun courriel : l'application n'a pas de serveur
 * SMTP configuré. Elles sont déposées en base et traitées par un administrateur
 * depuis l'onglet Utilisateurs. C'est volontaire — mieux vaut une file visible
 * qu'un envoi silencieux qui n'arrive jamais.
 */
@Entity
@Table(name = "demande_acces", indexes = {
        @Index(name = "idx_demande_traitee", columnList = "traitee"),
        @Index(name = "idx_demande_date", columnList = "date_demande")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DemandeAcces {

    public static final String TYPE_MOT_DE_PASSE = "MOT_DE_PASSE";
    public static final String TYPE_ACCES_BLOQUE = "ACCES_BLOQUE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 100)
    private String nom;

    @Column(length = 100)
    private String prenom;

    /** Adresse professionnelle concernée par la demande. */
    @Column(nullable = false, length = 255)
    private String email;

    /** Téléphone ou adresse de secours, pour joindre la personne autrement. */
    @Column(length = 255)
    private String contactAlternatif;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_demande", nullable = false, updatable = false)
    private LocalDateTime dateDemande;

    @Column(nullable = false)
    private boolean traitee = false;

    public DemandeAcces() {
    }

    public DemandeAcces(String type, String nom, String prenom, String email,
                        String contactAlternatif, String description) {
        this.type = type;
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.contactAlternatif = contactAlternatif;
        this.description = description;
        this.dateDemande = LocalDateTime.now();
        this.traitee = false;
    }

    @PrePersist
    protected void onCreate() {
        if (dateDemande == null) {
            dateDemande = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getEmail() { return email; }
    public String getContactAlternatif() { return contactAlternatif; }
    public String getDescription() { return description; }
    public LocalDateTime getDateDemande() { return dateDemande; }
    public boolean isTraitee() { return traitee; }

    public void setId(Long id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setNom(String nom) { this.nom = nom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public void setEmail(String email) { this.email = email; }
    public void setContactAlternatif(String contactAlternatif) { this.contactAlternatif = contactAlternatif; }
    public void setDescription(String description) { this.description = description; }
    public void setDateDemande(LocalDateTime dateDemande) { this.dateDemande = dateDemande; }
    public void setTraitee(boolean traitee) { this.traitee = traitee; }
}
