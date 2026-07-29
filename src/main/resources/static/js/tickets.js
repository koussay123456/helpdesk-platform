/* ==========================================================================
   Fiche d'un ticket : consultation, intervention, progression du statut.
   ========================================================================== */

/* ---------- Modal d'intervention ---------- */
let ticketOuvert = null;

async function ouvrirModalTicket(ticketId) {
    try {
        const ticket = await lireReponse(await fetch(
            `${API_BASE}/tickets/${ticketId}/fiche?utilisateurId=${currentUser.id}`));
        ticketOuvert = ticket;

        document.getElementById('modalNumero').textContent = ticket.numero;
        document.getElementById('modalTitre').textContent = ticket.titre;
        document.getElementById('modalDescription').textContent = ticket.description || 'Aucune description.';

        document.getElementById('modalInfos').innerHTML = [
            ['Statut', ticket.statutLabel],
            ['Priorité', libellePriorite(ticket.priorite)],
            ['Catégorie', ticket.categorie],
            ['Ouvert le', formatDate(ticket.dateCreation)],
            ['Déclarant', (ticket.demandeurNom || '—') + ' — ' + (ticket.demandeurEmail || '')],
            ['Pris en charge par', ticket.supportNom || 'Non assigné']
        ].map(([libelle, valeur]) => `
            <div class="modal-info">
                <span>${escapeHtml(libelle)}</span>
                <strong>${escapeHtml(valeur || '—')}</strong>
            </div>`).join('');

        document.getElementById('champIntervention').value = '';
        adapterModalAuRole(ticket);
        document.getElementById('modalTicket').classList.add('open');
        await chargerHistorique(ticketId);
        document.getElementById('champIntervention').focus();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

/**
 * Le même modal sert au technicien et au demandeur : au premier une saisie
 * d'intervention, au second une réponse et, quand le support a marqué le
 * ticket résolu, le bouton qui clôture réellement la demande.
 */
function adapterModalAuRole(ticket) {
    const estDemandeur = currentUser && ticket.demandeurEmail === currentUser.email;
    const actions = document.getElementById('modalActions');
    const label = document.getElementById('labelIntervention');
    const champ = document.getElementById('champIntervention');

    label.textContent = estDemandeur
        ? 'Répondre au support'
        : "Commentaire d'intervention";
    champ.placeholder = estDemandeur
        ? 'Apportez une précision, ou signalez que le problème persiste…'
        : "Diagnostic, action menée, suite à donner…";

    const estTechnicien = currentUser
        && (currentUser.role === 'SUPPORT_IT' || currentUser.role === 'ADMINISTRATEUR');
    const enCharge = currentUser && ticket.supportEmail === currentUser.email;
    const etape = PROGRESSION[ticket.statut];

    actions.innerHTML = '';
    actions.style.display = 'none';

    if (estDemandeur && ticket.statut === 'RESOLU') {
        // Seul le demandeur clôture réellement son ticket.
        actions.style.display = 'block';
        actions.innerHTML = `
            <div class="modal-action">
                <i class="fas fa-circle-check"></i>
                <span>Le support a marqué ce ticket comme résolu. Votre problème est-il réglé ?</span>
                <button type="button" id="btnValiderResolution">Oui, clôturer</button>
            </div>`;
        document.getElementById('btnValiderResolution')
                .addEventListener('click', () => validerResolution(ticket.id));

    } else if (estTechnicien && !ticket.supportEmail) {
        // Ticket libre : le technicien peut se le réserver depuis la fiche.
        actions.style.display = 'block';
        actions.innerHTML = `
            <div class="modal-action">
                <i class="fas fa-hand"></i>
                <span>Ce ticket n'est pris en charge par personne.</span>
                <button type="button" id="btnAssignerFiche">S'assigner ce ticket</button>
            </div>`;
        document.getElementById('btnAssignerFiche').addEventListener('click', async () => {
            await assignerTicket(ticket.id);
            fermerModalTicket();
        });

    } else if (estTechnicien && enCharge && etape) {
        // Progression du statut : c'est ici qu'un technicien fait avancer
        // son dossier, le tableau Kanban ayant cédé la place aux tables.
        actions.style.display = 'block';
        actions.innerHTML = `
            <div class="modal-action">
                <i class="fas ${etape.icone}"></i>
                <span>Statut actuel : <strong>${escapeHtml(ticket.statutLabel || ticket.statut)}</strong>.</span>
                <button type="button" id="btnProgression">${escapeHtml(etape.libelle)}</button>
            </div>`;
        document.getElementById('btnProgression').addEventListener('click', async () => {
            await faireProgresser(ticket.id, etape.suivant);
            fermerModalTicket();
        });
    }
}

async function validerResolution(ticketId) {
    try {
        await lireReponse(await fetch(`${API_BASE}/tickets/${ticketId}/valider-resolution`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ utilisateurId: currentUser.id })
        }));
        showNotification('Merci, le ticket est clôturé', 'success');
        fermerModalTicket();
        chargerMesTickets();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

async function chargerHistorique(ticketId) {
    const zone = document.getElementById('modalHistorique');
    zone.innerHTML = '<p class="etat-vide" style="padding:16px;">Chargement…</p>';

    try {
        const lignes = await lireReponse(await fetch(
            `${API_BASE}/tickets/${ticketId}/interventions?utilisateurId=${currentUser.id}`));

        if (!lignes.length) {
            zone.innerHTML = '<p class="etat-vide" style="padding:16px;">Aucune intervention enregistrée.</p>';
            return;
        }

        // Le double-clic sur son propre commentaire fait apparaître les deux
        // actions. Elles restent masquées le reste du temps : un fil de
        // discussion criblé d'icônes se lit mal, et rien n'invite à modifier
        // par mégarde.
        zone.innerHTML = lignes.map(ligne => {
            const aMoi = ligne.auteurId === currentUser.id;
            return `
            <div class="historique-ligne${aMoi ? ' modifiable' : ''}"
                 data-intervention="${ligne.id}" ${aMoi ? 'title="Double-cliquez pour modifier ou supprimer"' : ''}>
                <div class="historique-haut">
                    <span class="historique-auteur">${escapeHtml(ligne.auteurNom || '')} · ${escapeHtml(ligne.auteurRole || '')}</span>
                    <span class="historique-date">
                        ${formatDate(ligne.dateEnvoi)} à ${formatHeure(ligne.dateEnvoi)}
                        ${ligne.modifie ? '<em class="mention-modifie">modifié</em>' : ''}
                    </span>
                </div>
                <div class="historique-texte">${escapeHtml(ligne.contenu)}</div>
                ${aMoi ? `
                <div class="historique-actions">
                    <button type="button" class="icone-action" data-modifier title="Modifier">
                        <i class="fas fa-pen"></i>
                    </button>
                    <button type="button" class="icone-action danger" data-supprimer title="Supprimer">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>` : ''}
            </div>`;
        }).join('');

        brancherActionsHistorique(ticketId, lignes);
    } catch (error) {
        zone.innerHTML = '<p class="etat-vide" style="padding:16px;">Historique indisponible : '
            + escapeHtml(error.message) + '</p>';
    }
}

function fermerModalTicket() {
    document.getElementById('modalTicket').classList.remove('open');
    ticketOuvert = null;
}

async function enregistrerIntervention(e) {
    e.preventDefault();
    if (!ticketOuvert) return;

    const champ = document.getElementById('champIntervention');
    const commentaire = champ.value.trim();
    if (!commentaire) return;

    try {
        await lireReponse(await fetch(`${API_BASE}/tickets/${ticketOuvert.id}/interventions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: new URLSearchParams({ commentaire, auteurId: currentUser.id })
        }));

        champ.value = '';
        showNotification('Message enregistré', 'success');
        await chargerHistorique(ticketOuvert.id);

        if (currentUser.role === 'UTILISATEUR') chargerMesTickets();
        else rafraichirEspaceSupport();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

/* ---------- Création guidée d'un ticket ---------- */
function ouvrirNouveauTicket() {
    document.getElementById('createTicketForm').reset();
    document.getElementById('modalNouveauTicket').classList.add('open');
    document.getElementById('ticketCategory').focus();
}

function fermerNouveauTicket() {
    document.getElementById('modalNouveauTicket').classList.remove('open');
}

/* ------------------------------- Modification d'un commentaire --- */

/**
 * Branche le double-clic et les deux icônes de chaque commentaire.
 *
 * Seuls les commentaires dont on est l'auteur portent ces actions : le
 * serveur refuse de toute façon les autres, mais mieux vaut ne rien proposer
 * qu'afficher un bouton qui échouera.
 */
function brancherActionsHistorique(ticketId, lignes) {
    const parId = new Map(lignes.map(l => [String(l.id), l]));

    document.querySelectorAll('#modalHistorique .historique-ligne.modifiable').forEach(bloc => {
        const ligne = parId.get(bloc.dataset.intervention);

        bloc.addEventListener('dblclick', () => {
            const deja = bloc.classList.contains('actions-visibles');
            document.querySelectorAll('#modalHistorique .actions-visibles')
                    .forEach(autre => autre.classList.remove('actions-visibles'));
            bloc.classList.toggle('actions-visibles', !deja);
        });

        bloc.querySelector('[data-modifier]')?.addEventListener('click', e => {
            e.stopPropagation();
            modifierCommentaire(ticketId, ligne);
        });

        bloc.querySelector('[data-supprimer]')?.addEventListener('click', e => {
            e.stopPropagation();
            supprimerCommentaire(ticketId, ligne);
        });
    });
}

async function modifierCommentaire(ticketId, ligne) {
    const texte = prompt('Modifier le commentaire :', ligne.contenu);
    if (texte === null) return;
    if (!texte.trim()) {
        showNotification('Le commentaire ne peut pas être vide', 'error');
        return;
    }

    try {
        const params = new URLSearchParams({ auteurId: currentUser.id, commentaire: texte.trim() });
        await lireReponse(await fetch(`${API_BASE}/interventions/${ligne.id}?${params}`,
                { method: 'PUT' }));
        showNotification('Commentaire modifié', 'success');
        chargerHistorique(ticketId);
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

async function supprimerCommentaire(ticketId, ligne) {
    if (!confirm('Supprimer définitivement ce commentaire ?')) return;

    try {
        await lireReponse(await fetch(
            `${API_BASE}/interventions/${ligne.id}?auteurId=${currentUser.id}`,
            { method: 'DELETE' }));
        showNotification('Commentaire supprimé', 'success');
        chargerHistorique(ticketId);
    } catch (error) {
        showNotification(error.message, 'error');
    }
}