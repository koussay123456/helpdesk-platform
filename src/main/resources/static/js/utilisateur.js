/* ==========================================================================
   Espace utilisateur : suivi de ses propres tickets.
   ========================================================================== */

let mesTickets = [];

async function chargerMesTickets() {
    const tbody = document.getElementById('mesTicketsTableBody');
    try {
        mesTickets = await lireReponse(await fetch(
            `${API_BASE}/tickets/historique?utilisateurId=${currentUser.id}`));
        rendreIndicateursUtilisateur();
        rendreMesTickets();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="7" class="etat-vide">Tickets indisponibles : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

function rendreIndicateursUtilisateur() {
    const ouverts = mesTickets.filter(t => t.statut === 'NOUVEAU' || t.statut === 'EN_COURS');
    const aValider = mesTickets.filter(t => t.statut === 'RESOLU');
    const clos = mesTickets.filter(t => t.statut === 'FERME');

    document.getElementById('grilleKpiUtilisateur').innerHTML = [
        ['Tickets déclarés', mesTickets.length, ''],
        ['En cours de traitement', ouverts.length, ''],
        ['En attente de validation', aValider.length, 'ouvrez la fiche pour clôturer'],
        ['Clôturés', clos.length, '']
    ].map(([libelle, valeur, note]) => `
        <div class="carte-kpi">
            <span>${escapeHtml(libelle)}</span>
            <strong>${escapeHtml(String(valeur))}</strong>
            ${note ? `<small>${escapeHtml(note)}</small>` : ''}
        </div>`).join('');
}

function rendreMesTickets() {
    const tbody = document.getElementById('mesTicketsTableBody');
    const recherche = (document.getElementById('rechercheMesTickets')?.value || '').trim().toLowerCase();
    const statut = document.getElementById('filtreMesTicketsStatut')?.value || '';
    const priorite = document.getElementById('filtreMesTicketsPriorite')?.value || '';
    const categorie = document.getElementById('filtreMesTicketsCategorie')?.value || '';

    const retenus = mesTickets.filter(t => {
        const texte = `${t.numero} ${t.titre}`.toLowerCase();
        if (recherche && !texte.includes(recherche)) return false;
        if (statut && t.statut !== statut) return false;
        if (priorite && t.priorite !== priorite) return false;
        if (categorie && t.categorie !== categorie) return false;
        return true;
    });

    document.getElementById('compteMesTickets').textContent =
        `${retenus.length} ticket${retenus.length > 1 ? 's' : ''} sur ${mesTickets.length}`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = mesTickets.length
            ? '<tr><td colspan="7" class="etat-vide">Aucun ticket ne correspond à ces critères.</td></tr>'
            : `<tr><td colspan="7" class="etat-vide">Vous n'avez encore déclaré aucun incident.<br>
               Utilisez le bouton + en haut à gauche.</td></tr>`;
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
            <td><span class="ticket-status ${classeStatut(t.statut)}">${LIBELLES_STATUT[t.statut] || t.statut}</span>
                ${t.statut === 'RESOLU'
                    ? '<br><small style="color:var(--vert);font-weight:600;">à valider</small>' : ''}</td>
            <td><span class="badge-priorite ${classePriorite(t.priorite)}">${libellePriorite(t.priorite)}</span></td>
            <td>${formatDate(t.dateCreation)}</td>
            <td>${escapeHtml(t.supportNom || 'Non assigné')}</td>`;
        ligne.addEventListener('click', () => ouvrirModalTicket(t.id));
        tbody.appendChild(ligne);
    });
}
