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
        if (e.target.closest('#btnKpi'))                chargerKpi();

        const fermeture = e.target.closest('[data-fermer]');
        if (fermeture) document.getElementById(fermeture.dataset.fermer)?.classList.remove('open');
    });

    document.getElementById('formUtilisateur')?.addEventListener('submit', enregistrerUtilisateur);

    const motDePasseCompte = document.getElementById('editMotDePasse');
    motDePasseCompte?.addEventListener('input', () => majJauge(
        motDePasseCompte.value, 'jaugeCreation', 'jaugeCreationBarre', 'jaugeCreationTexte'));

    // Fermeture par un clic sur le fond de la fenêtre
    ['modalUtilisateur', 'modalSuppression'].forEach(id =>
        document.getElementById(id)?.addEventListener('click', e => {
            if (e.target.id === id) document.getElementById(id).classList.remove('open');
        }));

    // Filtres des deux tables de l'espace administrateur
    ['filtreRecherche', 'filtreRole', 'filtreDepartement', 'filtreStatut'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', rendreUtilisateurs);
        champ?.addEventListener('change', rendreUtilisateurs);
    });
    ['rechercheTickets', 'filtreTicketStatut', 'filtreTicketPriorite', 'filtreTicketCategorie']
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
     'filtreMesTicketsCategorie'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', rendreMesTickets);
        champ?.addEventListener('change', rendreMesTickets);
    });

    document.querySelectorAll('#supportDashboard .sidebar-tab-btn[data-pane]').forEach(btn =>
        btn.addEventListener('click', () => switchSupportTab(btn.dataset.pane)));

    ['rechercheOuverts', 'filtreOuvertsPriorite', 'filtreOuvertsCategorie'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', rendreTicketsOuverts);
        champ?.addEventListener('change', rendreTicketsOuverts);
    });
    ['rechercheAssignes', 'filtreAssignesStatut', 'filtreAssignesPriorite'].forEach(id => {
        const champ = document.getElementById(id);
        champ?.addEventListener('input', rendreTicketsAssignes);
        champ?.addEventListener('change', rendreTicketsAssignes);
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