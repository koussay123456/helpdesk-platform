/* ==========================================================================
   Passage d'un onglet à l'autre dans les trois espaces.
   ========================================================================== */

function classeStatut(statut) {
    return ({
        'NOUVEAU': 'status-nouveau',
        'EN_COURS': 'status-en-cours',
        'RESOLU': 'status-resolu',
        'FERME': 'status-ferme'
    })[statut] || 'status-ferme';
}

/* ---------- Onglets des espaces Support IT et Utilisateur ---------- */
function basculerOnglet(dashboardId, pane) {
    // Un seul sélecteur pour les boutons et les volets : l'espace Support IT
    // utilise les classes de l'espace Administrateur, l'espace Utilisateur
    // les siennes, et data-pane suffit à les relier.
    document.getElementById(dashboardId)
            .querySelectorAll('[data-pane]')
            .forEach(el => el.classList.toggle('active', el.dataset.pane === pane));

}

function switchUserTab(pane) {
    basculerOnglet('userDashboard', pane);

    const titres = {
        'dashboard':  '<i class="fas fa-chart-line"></i> Mes tickets',
    };
    document.getElementById('userPageTitle').innerHTML = titres[pane] || titres['dashboard'];

    // Le bouton d'ajout ne concerne que le suivi des tickets.
    document.getElementById('btnNouveauTicket').style.visibility =
        (pane === 'dashboard') ? 'visible' : 'hidden';

    chargerMesTickets();
}

function switchSupportTab(pane) {
    basculerOnglet('supportDashboard', pane);

    const titres = {
        'ouverts':    '<i class="fas fa-inbox"></i> Tickets ouverts',
        'dashboard':  '<i class="fas fa-chart-line"></i> Tableau de bord',
    };
    document.getElementById('supportPageTitle').innerHTML = titres[pane] || titres['ouverts'];

    if (pane === 'dashboard') chargerTableauBordSupport();
    else chargerTicketsOuverts();
}

/**
 * Ouvre la discussion d'un ticket depuis le Kanban ou la liste des tickets.
 * On bascule l'onglet sans passer par switchXTab : celui-ci relance un
 * chargement de la liste qui entrerait en concurrence avec l'ouverture
 * du ticket demandé.
 */
/* ============================================================
   UTILISATEUR — TICKETS
   ============================================================ */
async function handleCreateTicket(e) {
    e.preventDefault();

    const params = new URLSearchParams({
        titre:         document.getElementById('ticketTitle').value,
        description:   document.getElementById('ticketDescription').value,
        categorie:     document.getElementById('ticketCategory').value,
        priorite:      document.getElementById('ticketPriority').value,
        utilisateurId: currentUser.id
    });

    try {
        await lireReponse(await fetch(`${API_BASE}/tickets/creer`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        }));
        showNotification('Votre demande a bien été transmise', 'success');
        document.getElementById('createTicketForm').reset();
        fermerNouveauTicket();
        chargerMesTickets();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

/* ============================================================
   ADMIN — NAVIGATION
   ============================================================ */
// Sélecteurs cloisonnés : l'espace Support IT réutilise .sidebar-tab-btn et
// .admin-tab-pane, un sélecteur global piloterait les deux espaces à la fois.
document.querySelectorAll('#adminDashboard .sidebar-tab-btn[data-tab]').forEach(btn => {
    btn.addEventListener('click', () => switchAdminTab(btn.dataset.tab));
});

function switchAdminTab(tab) {
    const racine = document.getElementById('adminDashboard');

    racine.querySelectorAll('.sidebar-tab-btn[data-tab]').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tab);
    });

    racine.querySelectorAll('.admin-tab-pane').forEach(pane => {
        pane.classList.toggle('active', pane.dataset.tab === tab);
    });

    const titres = {
        'utilisateurs': '<i class="fas fa-users"></i> Gestion utilisateur',
        'tableau-bord': '<i class="fas fa-chart-line"></i> Tableau de bord',
        };
    document.getElementById('adminPageTitle').innerHTML = titres[tab] || titres['utilisateurs'];

    if (tab === 'utilisateurs')      loadAllUsers();
    else if (tab === 'tableau-bord') { chargerTicketsAdmin(); chargerKpi(); }
}
