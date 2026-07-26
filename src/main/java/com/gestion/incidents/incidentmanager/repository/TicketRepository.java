package com.gestion.incidents.incidentmanager.repository;

import com.gestion.incidents.incidentmanager.model.Ticket;
import com.gestion.incidents.incidentmanager.model.Utilisateur;
import com.gestion.incidents.incidentmanager.model.Statut;
import com.gestion.incidents.incidentmanager.model.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUtilisateur(Utilisateur utilisateur);
    List<Ticket> findByStatut(Statut statut);
    List<Ticket> findByCategorie(Categorie categorie);
    List<Ticket> findBySupportIt(Utilisateur supportIt);

    @Query("SELECT t FROM Ticket t WHERE t.utilisateur = :utilisateur AND t.statut != 'FERME' ORDER BY t.priorite DESC, t.dateCreation DESC")
    List<Ticket> findTicketsOuvertsUtilisateur(@Param("utilisateur") Utilisateur utilisateur);

    @Query("SELECT t FROM Ticket t WHERE t.statut != 'FERME' ORDER BY t.priorite DESC, t.dateCreation DESC")
    List<Ticket> findAllOuvertTickets();

    /**
     * Recherche utilisée par la barre de recherche de la messagerie.
     * On charge demandeur et support en une seule requête pour construire
     * directement le badge du ticket.
     */
    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.utilisateur LEFT JOIN FETCH t.supportIt "
            + "WHERE LOWER(t.titre) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "   OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "ORDER BY t.dateCreation DESC")
    List<Ticket> rechercherPourChat(@Param("q") String q);

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.utilisateur LEFT JOIN FETCH t.supportIt ORDER BY t.dateCreation DESC")
    List<Ticket> findRecentsAvecPersonnes();

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.utilisateur LEFT JOIN FETCH t.supportIt WHERE t.id = :id")
    Optional<Ticket> findByIdAvecPersonnes(@Param("id") Long id);

    /**
     * Tickets auxquels une personne participe : ceux qu'elle a ouverts et ceux
     * qui lui sont affectés. C'est la liste de conversations des espaces
     * Utilisateur et Support IT.
     */
    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.utilisateur LEFT JOIN FETCH t.supportIt "
            + "WHERE t.utilisateur.id = :utilisateurId OR t.supportIt.id = :utilisateurId "
            + "ORDER BY t.dateCreation DESC")
    List<Ticket> findParticipations(@Param("utilisateurId") Long utilisateurId);

    long countByStatut(Statut statut);
    long countByCategorie(Categorie categorie);
    long countBySupportIt(Utilisateur supportIt);
}