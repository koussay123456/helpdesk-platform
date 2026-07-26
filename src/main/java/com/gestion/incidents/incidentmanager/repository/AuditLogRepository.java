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

    List<AuditLog> findByEntiteAffectee(String entiteAffectee);

    @Query("SELECT a FROM AuditLog a ORDER BY a.dateAction DESC")
    List<AuditLog> findAllOrderByDateDesc();

    @Query("SELECT a FROM AuditLog a WHERE a.dateAction BETWEEN :debut AND :fin ORDER BY a.dateAction DESC")
    List<AuditLog> findByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT a FROM AuditLog a WHERE a.utilisateur = :utilisateur ORDER BY a.dateAction DESC LIMIT 50")
    List<AuditLog> findRecentByUser(@Param("utilisateur") Utilisateur utilisateur);
}