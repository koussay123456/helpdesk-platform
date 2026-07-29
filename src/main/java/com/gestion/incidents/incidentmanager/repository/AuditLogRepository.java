package com.gestion.incidents.incidentmanager.repository;

import com.gestion.incidents.incidentmanager.model.AuditLog;
import com.gestion.incidents.incidentmanager.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUtilisateur(Utilisateur utilisateur);

    List<AuditLog> findByTypeAction(String typeAction);

    @Query("SELECT a FROM AuditLog a LEFT JOIN FETCH a.utilisateur ORDER BY a.dateAction DESC")
    List<AuditLog> findAllOrderByDateDesc();

    /**
     * Historique des connexions, réussies et refusées, sur une période.
     *
     * Le JOIN FETCH est en LEFT : une tentative sur une adresse inconnue n'a
     * pas de compte associé, un INNER JOIN la ferait disparaître du résultat.
     */
    @Query("SELECT a FROM AuditLog a LEFT JOIN FETCH a.utilisateur "
            + "WHERE a.typeAction IN ('CONNEXION', 'CONNEXION_ECHOUEE') "
            + "AND a.dateAction BETWEEN :debut AND :fin "
            + "ORDER BY a.dateAction DESC")
    List<AuditLog> findConnexions(@Param("debut") LocalDateTime debut,
                                  @Param("fin") LocalDateTime fin);
}