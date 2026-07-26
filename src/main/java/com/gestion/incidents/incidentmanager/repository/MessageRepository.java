package com.gestion.incidents.incidentmanager.repository;

import com.gestion.incidents.incidentmanager.model.Message;
import com.gestion.incidents.incidentmanager.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByAuteur(Utilisateur auteur);

    @Query("SELECT m FROM Message m WHERE m.destinataire = 'TOUS' OR m.destinataire = :destinataire ORDER BY m.dateEnvoi DESC")
    List<Message> findMessagesForUser(@Param("destinataire") String destinataire);

    @Query("SELECT m FROM Message m ORDER BY m.dateEnvoi DESC")
    List<Message> findAllMessages();

    /**
     * Conversation directe entre deux adresses : les messages que "moi" a envoyés
     * à "autre", et ceux que "autre" a envoyés à "moi". Ordre de lecture d'un chat.
     */
    @Query("SELECT m FROM Message m JOIN FETCH m.auteur a "
            + "WHERE (LOWER(a.email) = LOWER(:moi)   AND LOWER(m.destinataire) = LOWER(:autre)) "
            + "   OR (LOWER(a.email) = LOWER(:autre) AND LOWER(m.destinataire) = LOWER(:moi)) "
            + "ORDER BY m.dateEnvoi ASC, m.id ASC")
    List<Message> findConversation(@Param("moi") String moi, @Param("autre") String autre);

    /**
     * Tous mes échanges directs, du plus récent au plus ancien : ceux que j'ai
     * écrits et ceux que j'ai reçus. Le service regroupe ensuite par
     * correspondant pour construire la liste des conversations.
     */
    @Query("SELECT m FROM Message m JOIN FETCH m.auteur a "
            + "WHERE a.id = :utilisateurId OR LOWER(m.destinataire) = LOWER(:email) "
            + "ORDER BY m.dateEnvoi DESC, m.id DESC")
    List<Message> findMesEchangesDirects(@Param("utilisateurId") Long utilisateurId,
                                         @Param("email") String email);

    /** Messages directs qui m'ont été adressés depuis ma dernière visite. */
    @Query("SELECT COUNT(m) FROM Message m "
            + "WHERE m.dateEnvoi > :depuis "
            + "  AND m.auteur.id <> :utilisateurId "
            + "  AND LOWER(m.destinataire) = LOWER(:email)")
    long compterNonLus(@Param("utilisateurId") Long utilisateurId,
                       @Param("email") String email,
                       @Param("depuis") java.time.LocalDateTime depuis);
}