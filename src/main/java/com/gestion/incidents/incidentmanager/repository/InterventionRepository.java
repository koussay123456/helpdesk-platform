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

    /** Dernier message d'un ticket, pour l'aperçu de la liste des conversations. */
    Optional<Intervention> findTopByTicketOrderByDateInterventionDescIdDesc(Ticket ticket);

    /**
     * Messages de fil de ticket non lus : postérieurs à la dernière visite,
     * écrits par quelqu'un d'autre, et qui me concernent — soit je suis
     * explicitement destinataire, soit je suis partie prenante du ticket.
     *
     * Les jointures sont explicites et à gauche : écrire t.supportIt.id dans
     * le WHERE produirait une jointure interne qui écarterait les tickets non
     * assignés de tout le décompte.
     */
    @Query("SELECT COUNT(i) FROM Intervention i "
            + "JOIN i.ticket t "
            + "LEFT JOIN t.utilisateur d "
            + "LEFT JOIN t.supportIt s "
            + "WHERE i.dateIntervention > :depuis "
            + "  AND i.auteur.id <> :utilisateurId "
            + "  AND (LOWER(COALESCE(i.destinataires, '')) LIKE LOWER(CONCAT('%', :email, '%')) "
            + "       OR d.id = :utilisateurId "
            + "       OR s.id = :utilisateurId)")
    long compterNonLus(@Param("utilisateurId") Long utilisateurId,
                       @Param("email") String email,
                       @Param("depuis") java.time.LocalDateTime depuis);
}