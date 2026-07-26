package com.gestion.incidents.incidentmanager.repository;

import com.gestion.incidents.incidentmanager.model.Utilisateur;
import com.gestion.incidents.incidentmanager.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmail(String email);

    List<Utilisateur> findByRole(Role role);

    long countByRole(Role role);

    /** Alimente le volet "Personnes" de la recherche de la messagerie. */
    @Query("SELECT u FROM Utilisateur u WHERE "
            + "LOWER(u.email)  LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "LOWER(u.nom)    LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "LOWER(u.prenom) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "ORDER BY u.nom ASC, u.prenom ASC")
    List<Utilisateur> rechercher(@Param("q") String q);
}
