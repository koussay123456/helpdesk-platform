package com.gestion.incidents.incidentmanager.repository;

import com.gestion.incidents.incidentmanager.model.Intervention;
import com.gestion.incidents.incidentmanager.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    List<Intervention> findByTicket(Ticket ticket);

    @Query("SELECT i FROM Intervention i WHERE i.ticket = :ticket ORDER BY i.dateIntervention DESC")
    List<Intervention> findByTicketOrderByDateDesc(@Param("ticket") Ticket ticket);

    /**
     * Fil de discussion d'un ticket, du plus ancien au plus récent (ordre de lecture
     * d'un chat). Le JOIN FETCH charge l'auteur dans la même requête : indispensable
     * pour construire les DTO sans requêtes N+1 ni proxy non initialisé.
     */
    @Query("SELECT i FROM Intervention i JOIN FETCH i.auteur WHERE i.ticket.id = :ticketId ORDER BY i.dateIntervention ASC, i.id ASC")
    List<Intervention> findDiscussionByTicketId(@Param("ticketId") Long ticketId);

    long countByTicket(Ticket ticket);

    long countByAuteur(com.gestion.incidents.incidentmanager.model.Utilisateur auteur);

    /** Dernier message d'un ticket, pour l'aperçu de la liste des conversations. */
    Optional<Intervention> findTopByTicketOrderByDateInterventionDescIdDesc(Ticket ticket);
}