/* ==========================================================================
   Espace support : tickets ouverts et tableau de bord personnel.
   ========================================================================== */

/* ============================================================
   ESPACE SUPPORT IT
   ============================================================ */

const PROGRESSION = {
    'NOUVEAU':  { suivant: 'EN_COURS', libelle: 'Prendre en charge', icone: 'fa-play' },
    'EN_COURS': { suivant: 'RESOLU',   libelle: 'Résoudre',          icone: 'fa-check' },
    'RESOLU':   { suivant: 'FERME',    libelle: 'Clôturer',          icone: 'fa-box-archive' }
};

function classePriorite(priorite) {
    return ({ 'CRITIQUE': 'prio-critique', 'ELEVEE': 'prio-elevee',
              'MOYENNE': 'prio-moyenne', 'FAIBLE': 'prio-faible' })[priorite] || 'prio-faible';
}

function libellePriorite(priorite) {
    return ({ 'CRITIQUE': 'Critique', 'ELEVEE': 'Élevée',
              'MOYENNE': 'Moyenne', 'FAIBLE': 'Faible' })[priorite] || priorite;
}

const LIBELLES_STATUT = { 'NOUVEAU': 'Nouveau', 'EN_COURS': 'En cours',
                          'RESOLU': 'Résolu', 'FERME': 'Fermé' };

/* ---------- Onglet 1 : tickets ouverts, à prendre en charge ---------- */
let ticketsOuverts = [];

async function chargerTicketsOuverts() {
    const tbody = document.getElementById('ouvertsTableBody');
    try {
        const tous = await lireReponse(await fetch(`${API_BASE}/tickets/kanban`));

        // Un ticket est « ouvert » tant que personne ne l'a pris en charge,
        // ou qu'il n'est pas encore clos. C'est le vivier dans lequel un
        // technicien choisit son prochain dossier.
        ticketsOuverts = tous.filter(t => !t.supportEmail && t.statut !== 'FERME');
        rendreTicketsOuverts();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="7" class="etat-vide">Liste indisponible : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

function rendreTicketsOuverts() {
    const tbody = document.getElementById('ouvertsTableBody');
    const recherche = (document.getElementById('rechercheOuverts')?.value || '').trim().toLowerCase();
    const priorite = document.getElementById('filtreOuvertsPriorite')?.value || '';
    const categorie = document.getElementById('filtreOuvertsCategorie')?.value || '';

    const retenus = ticketsOuverts.filter(t => {
        const texte = `${t.numero} ${t.titre} ${t.demandeurNom || ''}`.toLowerCase();
        if (recherche && !texte.includes(recherche)) return false;
        if (priorite && t.priorite !== priorite) return false;
        if (categorie && t.categorie !== categorie) return false;
        return true;
    });

    document.getElementById('compteOuverts').textContent =
        `${retenus.length} ticket${retenus.length > 1 ? 's' : ''} en attente`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = '<tr><td colspan="7" class="etat-vide">Aucun ticket en attente de prise en charge.</td></tr>';
        return;
    }

    retenus.forEach(t => {
        const ligne = document.createElement('tr');
        // Priorité critique et personne aux commandes : la ligne doit sauter aux yeux.
        if (t.priorite === 'CRITIQUE') {
            ligne.style.background = 'var(--rouge-voile)';
            ligne.style.boxShadow = 'inset 3px 0 0 var(--rouge)';
        }
        ligne.innerHTML = `
            <td><strong>${escapeHtml(t.numero)}</strong></td>
            <td>${escapeHtml(t.titre)}</td>
            <td>${escapeHtml(t.categorie || '—')}</td>
            <td><span class="badge-priorite ${classePriorite(t.priorite)}">${libellePriorite(t.priorite)}</span></td>
            <td>${escapeHtml(t.demandeurNom || '—')}</td>
            <td>${formatDate(t.dateCreation)}</td>
            <td style="text-align:center;">
                <button type="button" class="btn-valider" style="padding:7px 14px;font-size:.82rem;">
                    <i class="fas fa-hand"></i> S'assigner
                </button>
            </td>`;

        ligne.querySelector('button').addEventListener('click', e => {
            e.stopPropagation();
            assignerTicket(t.id);
        });
        ligne.style.cursor = 'pointer';
        ligne.addEventListener('click', () => ouvrirModalTicket(t.id));
        tbody.appendChild(ligne);
    });
}

async function assignerTicket(ticketId) {
    try {
        await lireReponse(await fetch(`${API_BASE}/tickets/${ticketId}/assigner`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ supportItId: currentUser.id })
        }));
        showNotification('Ticket pris en charge', 'success');
        chargerTicketsOuverts();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

async function faireProgresser(ticketId, nouveauStatut) {
    try {
        const params = new URLSearchParams({ statut: nouveauStatut, utilisateurId: currentUser.id });
        await lireReponse(await fetch(`${API_BASE}/tickets/${ticketId}/changer-statut?${params}`,
                { method: 'POST' }));
        showNotification('Statut mis à jour', 'success');
        rafraichirEspaceSupport();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

function rafraichirEspaceSupport() {
    const racine = document.getElementById('supportDashboard');
    if (!racine || !racine.classList.contains('active')) return;

    if (racine.querySelector('.admin-tab-pane[data-pane="ouverts"]').classList.contains('active')) {
        chargerTicketsOuverts();
    } else if (racine.querySelector('.admin-tab-pane[data-pane="dashboard"]').classList.contains('active')) {
        chargerTableauBordSupport();
    }
}

/* ---------- Onglet 2 : mes tickets et mes statistiques ---------- */
let ticketsAssignes = [];

async function chargerTableauBordSupport() {
    const tbody = document.getElementById('assignesTableBody');
    try {
        ticketsAssignes = await lireReponse(await fetch(
            `${API_BASE}/tickets/assignes?supportItId=${currentUser.id}`));
        rendreStatistiquesSupport();
        rendreTicketsAssignes();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="7" class="etat-vide">Historique indisponible : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

function rendreStatistiquesSupport() {
    const resolus = ticketsAssignes.filter(t => t.statut === 'RESOLU' || t.statut === 'FERME');
    const enCours = ticketsAssignes.filter(t => t.statut === 'NOUVEAU' || t.statut === 'EN_COURS');

    // Le délai ne se calcule que sur les tickets effectivement résolus :
    // inclure les dossiers ouverts fausserait la moyenne.
    let heures = 0, mesures = 0;
    resolus.forEach(t => {
        if (t.dateCreation && t.dateResolution) {
            heures += (new Date(t.dateResolution) - new Date(t.dateCreation)) / 3600000;
            mesures++;
        }
    });

    const taux = ticketsAssignes.length
        ? Math.round(resolus.length * 1000 / ticketsAssignes.length) / 10 : 0;

    document.getElementById('grilleKpiSupport').innerHTML = [
        ['Tickets pris en charge', ticketsAssignes.length, ''],
        ['Résolus', resolus.length, ''],
        ['En cours', enCours.length, 'à traiter'],
        ['Taux de résolution', taux + ' %', ''],
        ['Délai moyen', mesures ? (Math.round(heures / mesures * 10) / 10) + ' h' : '—',
            'de l\'ouverture à la résolution']
    ].map(([libelle, valeur, note]) => `
        <div class="carte-kpi">
            <span>${escapeHtml(libelle)}</span>
            <strong>${escapeHtml(String(valeur))}</strong>
            ${note ? `<small>${escapeHtml(note)}</small>` : ''}
        </div>`).join('');
}

function rendreTicketsAssignes() {
    const tbody = document.getElementById('assignesTableBody');
    const recherche = (document.getElementById('rechercheAssignes')?.value || '').trim().toLowerCase();
    const statut = document.getElementById('filtreAssignesStatut')?.value || '';
    const priorite = document.getElementById('filtreAssignesPriorite')?.value || '';

    const retenus = ticketsAssignes.filter(t => {
        const texte = `${t.numero} ${t.titre} ${t.demandeurNom || ''}`.toLowerCase();
        if (recherche && !texte.includes(recherche)) return false;
        if (statut && t.statut !== statut) return false;
        if (priorite && t.priorite !== priorite) return false;
        return true;
    });

    document.getElementById('compteAssignes').textContent =
        `${retenus.length} ticket${retenus.length > 1 ? 's' : ''} sur ${ticketsAssignes.length}`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = '<tr><td colspan="7" class="etat-vide">Aucun ticket ne correspond à ces critères.</td></tr>';
        return;
    }

    retenus.forEach(t => {
        const ligne = document.createElement('tr');
        ligne.style.cursor = 'pointer';
        ligne.title = 'Ouvrir la fiche du ticket';
        ligne.innerHTML = `
            <td><strong>${escapeHtml(t.numero)}</strong></td>
            <td>${escapeHtml(t.titre)}</td>
            <td>${escapeHtml(t.categorie || '—')}</td>
            <td><span class="ticket-status ${classeStatut(t.statut)}">${LIBELLES_STATUT[t.statut] || t.statut}</span></td>
            <td><span class="badge-priorite ${classePriorite(t.priorite)}">${libellePriorite(t.priorite)}</span></td>
            <td>${formatDate(t.dateCreation)}</td>
            <td>${t.dateResolution ? formatDate(t.dateResolution) : '—'}</td>`;
        ligne.addEventListener('click', () => ouvrirModalTicket(t.id));
        tbody.appendChild(ligne);
    });
}
