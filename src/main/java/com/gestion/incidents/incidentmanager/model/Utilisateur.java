package com.gestion.incidents.incidentmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * @JsonIgnoreProperties empêche Jackson de sérialiser les attributs internes
 * d'un proxy Hibernate (hibernateLazyInitializer / handler). Sans cela, toute
 * réponse contenant un Utilisateur chargé en LAZY échoue en 500 — c'était la
 * cause commune des erreurs de l'onglet Historique et de l'envoi de message.
 */
@Entity
@Table(name = "utilisateur", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Empreinte du mot de passe, jamais la valeur en clair.
     * @JsonIgnore garantit qu'elle ne quitte pas le serveur, même par
     * inadvertance lors de la sérialisation d'un compte.
     */
    @JsonIgnore
    @Column(nullable = false, length = 255)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(length = 80)
    private String departement;

    /**
     * Compte protégé. Un super-administrateur ne peut être ni modifié, ni
     * supprimé, ni désactivé depuis l'application, quel que soit l'auteur de
     * la demande. C'est le garde-fou qui garantit qu'il restera toujours un
     * accès d'administration opérationnel, même après une fausse manœuvre.
     */
    @Column(name = "super_admin")
    private Boolean superAdmin = Boolean.FALSE;

    /**
     * Un compte inactif est conservé pour l'historique mais ne peut plus se connecter.
     *
     * Colonne volontairement nullable et de type Boolean : déclarée NOT NULL,
     * son ajout automatique échouait sur une table déjà peuplée — PostgreSQL
     * refuse une colonne NOT NULL sans valeur par défaut, et Hibernate se
     * contentait d'un avertissement en laissant la colonne absente.
     * Une valeur nulle est lue comme « actif ».
     */
    @Column(name = "actif")
    private Boolean actif = Boolean.TRUE;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;


    // Constructeurs
    public Utilisateur() {
    }

    public Utilisateur(String nom, String prenom, String email, String motDePasse, Role role) {
        this(nom, prenom, email, motDePasse, role, null);
    }

    public Utilisateur(String nom, String prenom, String email, String motDePasse,
                       Role role, String departement) {
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.motDePasse = motDePasse;
        this.role = role;
        this.departement = departement;
        this.actif = Boolean.TRUE;
        this.dateCreation = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /** Le mot de passe ne sort jamais de l'API. */
    @JsonIgnore
    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getDepartement() { return departement; }

    public void setDepartement(String departement) { this.departement = departement; }

    public boolean isSuperAdmin() { return superAdmin != null && superAdmin; }

    public void setSuperAdmin(boolean superAdmin) { this.superAdmin = superAdmin; }

    public boolean isActif() { return actif == null || actif; }

    public void setActif(boolean actif) { this.actif = actif; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }


    public String getNomComplet() {
        return prenom + " " + nom;
    }
}