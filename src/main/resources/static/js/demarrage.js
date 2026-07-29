/* ==========================================================================
   Amorçage : branchement des écouteurs une fois les vues chargées.
   ========================================================================== */

/* ============================================================
   BRANCHEMENT DES ÉVÉNEMENTS
   ============================================================ */
/**
 * Appelée par le chargeur une fois les vues injectées et les modules chargés.
 * Les écouteurs ne peuvent pas être posés avant : les éléments n'existent pas
 * encore au moment où le document est analysé.
 */
function demarrerApplication() {
    brancherReplisLogo();
    reprendreSession();

    document.getElementById('loginForm')?.addEventListener('submit', handleLogin);
    document.getElementById('createTicketForm')?.addEventListener('submit', handleCreateTicket);

    // Boutons d'action : écoute déléguée au document plutôt qu'attachée à
    // chaque élément. Un écouteur posé sur un élément absent au moment du
    // branchement est perdu sans le moindre message ; la délégation, elle,
    // fonctionne quel que soit le moment où l'élément apparaît.
    document.addEventListener('click', e => {
        if (e.target.closest('#btnAjoutAdmin'))        ouvrirModalUtilisateur(null);
        if (e.target.closest('#btnValiderSuppression')) validerSuppression();
        if (e.target.closest('#btnNouveauTicket'))      ouvrirNouveauTicket();

        const fermeture = e.target.closest('[data-fermer]');
        if (fermeture) document.getElementById(fermeture.dataset.fermer)?.classList.remove('open');

        // Cartes des tableaux de bord, quel que soit l'espace. Le composant
        // concerné est retrouvé à partir du conteneur qui porte data-bord.
        const carte = e.target.closest('.carte-choix');
        if (carte) {
            const bord = tableauDeBordDe(carte);
            // Un second clic sur la carte active ramène au tableau.
            if (bord) bord.afficherVue(carte.classList.contains('active') ? 'tableau' : carte.dataset.vue);
        }
        const retour = e.target.closest('[data-role="retour"]');
        if (retour) tableauDeBordDe(retour)?.afficherVue('tableau');
    });

    document.getElementById('formUtilisateur')?.addEventListener('submit', enregistrerUtilisateur);
    document.getElementById('formCommentaire')?.addEventListener('submit', enregistrerCommentaire);

    // Historique de connexion : les bornes et le résultat sont filtrés côté
    // serveur, le rôle en local — d'où deux traitements distincts.
    ['rechercheConnexions', 'filtreConnexionsResultat',
     'dateConnexionsDebut', 'dateConnexionsFin'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', chargerConnexions);
        champ?.addEventListener('change', chargerConnexions);
    });
    document.getElementById('filtreConnexionsRole')?.addEventListener('change', rendreConnexions);

    // Le sélecteur d'état du graphique par supportIT naît et meurt avec le
    // graphique lui-même : l'écoute est donc déléguée au document.
    document.addEventListener('change', e => {
        // Le champ libre de département suit le choix « Autres ».
        if (e.target.id === 'editDepartement') basculerChampDepartement();

        if (e.target.id === 'ticketCategory') {
            const liste = document.getElementById('ticketCategory');
            const libre = document.getElementById('ticketCategoryAutre');
            const autre = liste.value === 'AUTRE';
            libre.hidden = !autre;
            if (!autre) libre.value = '';
            else libre.focus();
        }

        if (e.target.id === 'etatSupportIT') {
            etatSupportIT = e.target.value;
            tableauDeBordDe(e.target)?.afficherVue('supports');
        }
    });

    const motDePasseCompte = document.getElementById('editMotDePasse');
    motDePasseCompte?.addEventListener('input', () => majJauge(
        motDePasseCompte.value, 'jaugeCreation', 'jaugeCreationBarre', 'jaugeCreationTexte'));

    // Fermeture par un clic sur le fond de la fenêtre
    ['modalUtilisateur', 'modalSuppression', 'modalCommentaire'].forEach(id =>
        document.getElementById(id)?.addEventListener('click', e => {
            if (e.target.id === id) document.getElementById(id).classList.remove('open');
        }));

    // Filtres des deux tables de l'espace administrateur
    ['filtreRecherche', 'filtreRole', 'filtreDepartement', 'filtreStatut'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', rendreUtilisateurs);
        champ?.addEventListener('change', rendreUtilisateurs);
    });
    ['rechercheTickets', 'filtreTicketStatut', 'filtreTicketPriorite', 'filtreTicketCategorie',
     'dateTicketsDebut', 'dateTicketsFin']
        .forEach(id => {
            const champ = document.getElementById(id);
            champ?.addEventListener('input', rendreTicketsAdmin);
            champ?.addEventListener('change', rendreTicketsAdmin);
        });
    // Onglets des espaces Support IT et Utilisateur.
    // Le panneau de messagerie gère lui-même ses propres écouteurs.
    document.querySelectorAll('#userDashboard .sidebar-tab-btn[data-pane]').forEach(btn =>
        btn.addEventListener('click', () => switchUserTab(btn.dataset.pane)));

    // Modal de création guidée
    document.getElementById('fermerNouveauTicket')?.addEventListener('click', fermerNouveauTicket);
    document.getElementById('modalNouveauTicket')?.addEventListener('click', e => {
        if (e.target.id === 'modalNouveauTicket') fermerNouveauTicket();
    });

    ['rechercheMesTickets', 'filtreMesTicketsStatut', 'filtreMesTicketsPriorite',
     'filtreMesTicketsCategorie', 'dateMesTicketsDebut', 'dateMesTicketsFin'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', rendreMesTickets);
        champ?.addEventListener('change', rendreMesTickets);
    });

    document.querySelectorAll('#supportDashboard .sidebar-tab-btn[data-pane]').forEach(btn =>
        btn.addEventListener('click', () => switchSupportTab(btn.dataset.pane)));

    ['perimetreTickets', 'rechercheTicketsSupport', 'filtreSupportStatut',
     'filtreSupportPriorite', 'filtreSupportCategorie',
     'dateSupportDebut', 'dateSupportFin'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', rendreTicketsSupport);
        champ?.addEventListener('change', rendreTicketsSupport);
    });
    ['rechercheMesInterventions', 'filtreMesInterventionsStatut', 'filtreMesInterventionsPriorite',
     'filtreMesInterventionsCategorie', 'dateMesInterventionsDebut', 'dateMesInterventionsFin'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', rendreMesInterventions);
        champ?.addEventListener('change', rendreMesInterventions);
    });

    // Modal d'intervention
    document.getElementById('modalFermer')?.addEventListener('click', fermerModalTicket);
    document.getElementById('formIntervention')?.addEventListener('submit', enregistrerIntervention);
    document.getElementById('modalTicket')?.addEventListener('click', e => {
        if (e.target.id === 'modalTicket') fermerModalTicket();
    });
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && document.getElementById('modalTicket').classList.contains('open')) {
            fermerModalTicket();
        }
    });
}