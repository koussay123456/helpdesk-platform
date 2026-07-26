package com.gestion.incidents.incidentmanager.repository;

import com.gestion.incidents.incidentmanager.model.DemandeAcces;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandeAccesRepository extends JpaRepository<DemandeAcces, Long> {

    @Query("SELECT d FROM DemandeAcces d ORDER BY d.traitee ASC, d.dateDemande DESC")
    List<DemandeAcces> findToutesTrieesParUrgence();

    long countByTraiteeFalse();
}
