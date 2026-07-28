/* ==========================================================================
   Espace administrateur : table des tickets et indicateurs de performance.
   ========================================================================== */

/* ============================================================
   ADMIN — TABLEAU DE BORD : TICKETS ET PERFORMANCE
   ============================================================ */
let tousLesTickets = [];

async function chargerTicketsAdmin() {
    const tbody = document.getElementById('ticketsTableBody');
    try {
        tousLesTickets = await lireReponse(await fetch(`${API_BASE}/tickets/kanban`));
        rendreTicketsAdmin();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="5" class="etat-vide">Tickets indisponibles : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

function rendreTicketsAdmin() {
    const tbody = document.getElementById('ticketsTableBody');
    const recherche = (document.getElementById('rechercheTickets')?.value || '').trim().toLowerCase();
    const statut = document.getElementById('filtreTicketStatut')?.value || '';
    const priorite = document.getElementById('filtreTicketPriorite')?.value || '';
    const categorie = document.getElementById('filtreTicketCategorie')?.value || '';

    const retenus = tousLesTickets.filter(t => {
        const texte = `${t.numero} ${t.titre} ${t.demandeurNom || ''}`.toLowerCase();
        if (recherche && !texte.includes(recherche)) return false;
        if (statut && t.statut !== statut) return false;
        if (priorite && t.priorite !== priorite) return false;
        if (categorie && t.categorie !== categorie) return false;
        return true;
    });

    document.getElementById('compteTickets').textContent =
        `${retenus.length} ticket${retenus.length > 1 ? 's' : ''} sur ${tousLesTickets.length}`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="etat-vide">Aucun ticket ne correspond à ces critères.</td></tr>';
        return;
    }

    const libelles = { 'NOUVEAU': 'Nouveau', 'EN_COURS': 'En cours', 'RESOLU': 'Résolu', 'FERME': 'Fermé' };

    retenus.forEach(t => {
        const ligne = document.createElement('tr');
        ligne.style.cursor = 'pointer';
        ligne.title = 'Ouvrir la fiche du ticket';
        ligne.innerHTML = `
            <td><strong>${escapeHtml(t.numero)}</strong></td>
            <td>${escapeHtml(t.categorie || '—')}</td>
            <td><span class="ticket-status ${classeStatut(t.statut)}">${libelles[t.statut] || t.statut}</span></td>
            <td><span class="badge-priorite ${classePriorite(t.priorite)}">${libellePriorite(t.priorite)}</span></td>
            <td>${formatDate(t.dateCreation)}</td>`;
        ligne.addEventListener('click', () => ouvrirModalTicket(t.id));
        tbody.appendChild(ligne);
    });
}

/* ---------- Indicateurs de performance ---------- */
function initialiserPeriodeKpi() {
    const fin = new Date();
    const debut = new Date(); debut.setDate(debut.getDate() - 30);
    const iso = d => d.toISOString().slice(0, 10);
    if (!document.getElementById('kpiDebut').value) {
        document.getElementById('kpiDebut').value = iso(debut);
        document.getElementById('kpiFin').value = iso(fin);
    }
}

async function chargerKpi() {
    const tbody = document.getElementById('kpiTableBody');
    const grille = document.getElementById('grilleKpi');
    initialiserPeriodeKpi();

    try {
        const params = new URLSearchParams({
            debut: document.getElementById('kpiDebut').value,
            fin: document.getElementById('kpiFin').value
        });
        const data = await lireReponse(await fetch(`${API_BASE}/statistiques/performance?${params}`));
        const g = data.global;

        grille.innerHTML = [
            ['Tickets sur la période', g.ticketsPeriode, ''],
            ['Tickets résolus', g.ticketsResolus, ''],
            ['Taux de résolution', g.tauxResolution + ' %', 'résolus sur ouverts'],
            ['Délai moyen', g.delaiMoyenHeures === null ? '—' : g.delaiMoyenHeures + ' h',
                'de l\'ouverture à la résolution'],
            ['Non assignés', g.nonAssignes, 'en attente de prise en charge']
        ].map(([libelle, valeur, note]) => `
            <div class="carte-kpi">
                <span>${escapeHtml(libelle)}</span>
                <strong>${escapeHtml(String(valeur))}</strong>
                ${note ? `<small>${escapeHtml(note)}</small>` : ''}
            </div>`).join('');

        tbody.innerHTML = '';
        if (!data.agents.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="etat-vide">Aucun agent de support enregistré.</td></tr>';
            return;
        }

        data.agents.forEach(a => {
            const ligne = document.createElement('tr');
            ligne.innerHTML = `
                <td><strong>${escapeHtml(a.nom)}</strong><br>
                    <small style="color:var(--gris);">${escapeHtml(a.email)}</small></td>
                <td style="text-align:center;">${a.assignes}</td>
                <td style="text-align:center;"><strong style="color:var(--vert);">${a.resolus}</strong></td>
                <td style="text-align:center;">${a.enCours}</td>
                <td style="text-align:center;">${a.delaiMoyenHeures === null ? '—' : a.delaiMoyenHeures + ' h'}</td>
                <td style="text-align:center;">
                    <div class="barre-taux"><span style="width:${a.tauxResolution}%"></span></div>
                    <small style="color:var(--gris);">${a.tauxResolution} %</small>
                </td>`;
            tbody.appendChild(ligne);
        });
    } catch (error) {
        grille.innerHTML = '';
        tbody.innerHTML = '<tr><td colspan="6" class="etat-vide">Indicateurs indisponibles : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}
