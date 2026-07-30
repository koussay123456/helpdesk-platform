/* ==========================================================================
   Espace administrateur : table des comptes, création, modification, suppression.
   ========================================================================== */

/* ============================================================
   ROBUSTESSE DU MOT DE PASSE
   Utilisée là où un mot de passe est choisi : « Mon compte » et la
   création d'un compte par l'administrateur. Elle a été retirée de
   l'écran de connexion, où l'on ressaisit un mot de passe existant.
   ============================================================ */

/**
 * Estimation en bits d'entropie : longueur × log2(taille du jeu de
 * caractères), diminuée pour les motifs prévisibles. La longueur pèse donc
 * davantage que la variété, ce qui correspond à la réalité.
 */
function evaluerRobustesse(motDePasse) {
    if (!motDePasse) return { score: 0, niveau: '—', classe: '' };

    let jeu = 0;
    if (/[a-z]/.test(motDePasse)) jeu += 26;
    if (/[A-Z]/.test(motDePasse)) jeu += 26;
    if (/\d/.test(motDePasse))    jeu += 10;
    if (/[^a-zA-Z0-9]/.test(motDePasse)) jeu += 33;

    let bits = motDePasse.length * Math.log2(jeu || 1);

    if (/(.)\1{2,}/.test(motDePasse)) bits -= 12;
    if (/(0123|1234|2345|3456|abcd|azer|qwer|admin)/i.test(motDePasse)) bits -= 15;
    if (/(kipropha|motdepasse|password|helpdesk|support|bonjour)/i.test(motDePasse)) bits -= 25;
    bits = Math.max(0, bits);

    const score = Math.max(8, Math.min(100, Math.round(bits / 90 * 100)));

    if (bits < 40) return { score, niveau: 'Faible', classe: 'jauge-faible' };
    if (bits < 70) return { score, niveau: 'Moyen',  classe: 'jauge-moyen' };
    return { score, niveau: 'Fort', classe: 'jauge-fort' };
}

function majJauge(motDePasse, jaugeId, barreId, texteId) {
    const jauge = document.getElementById(jaugeId);
    const barre = document.getElementById(barreId);
    const texte = document.getElementById(texteId);
    if (!jauge) return;

    const { score, niveau, classe } = evaluerRobustesse(motDePasse);
    jauge.classList.toggle('visible', !!motDePasse);
    barre.style.width = score + '%';
    barre.className = 'jauge-barre ' + classe;
    texte.textContent = niveau;
}

/* ============================================================
   MON COMPTE
   Même formulaire pour les trois espaces : le titulaire modifie son
   état civil et son mot de passe. L'adresse e-mail sert d'identifiant
   de connexion et le rôle relève de l'administration : les deux sont
   affichés mais verrouillés.
   ============================================================ */

/* ============================================================
   ADMIN — UTILISATEURS
   ============================================================ */
let tousLesUtilisateurs = [];

function filtresUtilisateurs() {
    return {
        recherche: (document.getElementById('filtreRecherche')?.value || '').trim().toLowerCase(),
        role: document.getElementById('filtreRole')?.value || '',
        departement: document.getElementById('filtreDepartement')?.value || '',
        statut: document.getElementById('filtreStatut')?.value || ''
    };
}

async function loadAllUsers() {
    const tbody = document.getElementById('usersTableBody');
    try {
        tousLesUtilisateurs = await lireReponse(await fetch(`${API_BASE}/utilisateurs`));

        // Le filtre par département se construit sur les valeurs réellement présentes.
        const liste = document.getElementById('filtreDepartement');
        const choisi = liste.value;
        const departements = [...new Set(tousLesUtilisateurs
                .map(u => u.departement).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'fr'));
        liste.innerHTML = '<option value="">Tous les départements</option>'
            + departements.map(d => `<option value="${escapeHtml(d)}">${escapeHtml(d)}</option>`).join('');
        liste.value = choisi;

        rendreUtilisateurs();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="5" class="etat-vide">Liste indisponible : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

function rendreUtilisateurs() {
    const tbody = document.getElementById('usersTableBody');
    const f = filtresUtilisateurs();

    const retenus = tousLesUtilisateurs.filter(u => {
        const texte = `${u.nom} ${u.prenom} ${u.email}`.toLowerCase();
        if (f.recherche && !texte.includes(f.recherche)) return false;
        if (f.role && u.role !== f.role) return false;
        if (f.departement && (u.departement || '') !== f.departement) return false;
        if (f.statut === 'actif' && !u.actif) return false;
        if (f.statut === 'inactif' && u.actif) return false;
        return true;
    });

    document.getElementById('compteUtilisateurs').textContent =
        `${retenus.length} compte${retenus.length > 1 ? 's' : ''} sur ${tousLesUtilisateurs.length}`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="etat-vide">Aucun compte ne correspond à ces critères.</td></tr>';
        return;
    }

    const libelles = { 'ADMINISTRATEUR': 'Administrateur', 'SUPPORT_IT': 'supportIT', 'UTILISATEUR': 'Utilisateur' };
    const classes = { 'ADMINISTRATEUR': 'role-badge-admin', 'SUPPORT_IT': 'role-badge-support', 'UTILISATEUR': 'role-badge-utilisateur' };

    retenus.forEach(user => {
        // Seul le compte protégé échappe à toute modification. Les autres
        // administrateurs sont gérables comme n'importe quel compte : c'est
        // ce compte-là qui garantit qu'un accès d'administration subsiste.
        const protege = user.superAdmin === true;
        const raison = 'Compte protégé : ni modifiable ni supprimable';

        const ligne = document.createElement('tr');
        ligne.innerHTML = `
            <td>${escapeHtml(user.nom)}</td>
            <td>${escapeHtml(user.prenom)}</td>
            <td>${escapeHtml(user.email)}</td>
            <td>
                <span class="role-badge-table ${classes[user.role]}">${libelles[user.role] || user.role}</span>
                ${user.superAdmin ? '<span class="badge-protege" title="Compte protégé"><i class="fas fa-shield-halved"></i></span>' : ''}
            </td>
            <td style="text-align:center;">
                <div class="action-buttons">
                    <button class="btn-small-action btn-edit" data-action="modifier"
                            ${protege ? `disabled title="${raison}"` : 'title="Modifier"'}>
                        <i class="fas fa-pen"></i>
                    </button>
                    <button class="btn-small-action btn-delete" data-action="supprimer"
                            ${protege ? `disabled title="${raison}"` : 'title="Supprimer"'}>
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </td>`;

        if (!protege) {
            ligne.querySelector('[data-action="modifier"]')
                 .addEventListener('click', () => ouvrirModalUtilisateur(user));
            ligne.querySelector('[data-action="supprimer"]')
                 .addEventListener('click', () => confirmerSuppression(user));
        }
        tbody.appendChild(ligne);
    });
}

/* ---------- Fenêtre de création et de modification ---------- */

/** Socle proposé même sur une base vierge. */
const DEPARTEMENTS_CONNUS = [
    "Direction des systèmes d'information", 'Direction générale', 'Ressources humaines',
    'Finance', 'Marketing', 'Ventes', 'Logistique', 'Production', 'Qualité'
];

const AUTRE_DEPARTEMENT = '__autre__';

/**
 * Remplit la liste des départements.
 *
 * Le socle est complété par les valeurs déjà présentes en base : un
 * département saisi via « Autres » réapparaît donc dans la liste dès le
 * compte suivant, sans qu'on ait à toucher au code.
 */
function garnirDepartements(valeurCourante) {
    const liste = document.getElementById('editDepartement');
    const existants = tousLesUtilisateurs.map(u => u.departement).filter(Boolean);

    const options = [...new Set([...DEPARTEMENTS_CONNUS, ...existants])]
        .sort((a, b) => a.localeCompare(b, 'fr'));

    liste.innerHTML = '<option value="">Non renseigné</option>'
        + options.map(d => `<option value="${escapeHtml(d)}">${escapeHtml(d)}</option>`).join('')
        + `<option value="${AUTRE_DEPARTEMENT}">Autres…</option>`;

    liste.value = valeurCourante && options.includes(valeurCourante) ? valeurCourante : '';
    basculerChampDepartement();
}

/** Le champ libre n'apparaît que sur le choix « Autres ». */
function basculerChampDepartement() {
    const liste = document.getElementById('editDepartement');
    const libre = document.getElementById('editDepartementAutre');
    const autre = liste.value === AUTRE_DEPARTEMENT;

    libre.hidden = !autre;
    if (!autre) libre.value = '';
    else libre.focus();
}

/** Département retenu : la saisie libre l'emporte quand « Autres » est choisi. */
function departementSaisi() {
    const liste = document.getElementById('editDepartement');
    if (liste.value !== AUTRE_DEPARTEMENT) return liste.value;
    return document.getElementById('editDepartementAutre').value.trim();
}
let compteEnEdition = null;

function ouvrirModalUtilisateur(user) {
    compteEnEdition = user || null;
    const creation = !user;

    document.getElementById('modalUtilisateurTitre').textContent =
        creation ? 'Créer un utilisateur' : `Modifier ${user.prenom} ${user.nom}`;

    document.getElementById('editNom').value = user ? user.nom : '';
    document.getElementById('editPrenom').value = user ? user.prenom : '';
    document.getElementById('editEmail').value = user ? user.email : '';
    garnirDepartements(user ? user.departement : '');
    document.getElementById('editRole').value = user ? user.role : 'UTILISATEUR';
    document.getElementById('editActif').value = user
        ? String(user.actif !== false)
        : 'true';

    // Le mot de passe n'est demandé qu'à la création.
    document.getElementById('champMotDePasse').style.display = creation ? 'flex' : 'none';
    document.getElementById('editMotDePasse').required = creation;
    document.getElementById('editMotDePasse').value = '';
    majJauge('', 'jaugeCreation', 'jaugeCreationBarre', 'jaugeCreationTexte');

    document.getElementById('modalUtilisateur').classList.add('open');
    document.getElementById('editNom').focus();
}

function fermerModalUtilisateur() {
    document.getElementById('modalUtilisateur').classList.remove('open');
    compteEnEdition = null;
}

async function enregistrerUtilisateur(e) {
    e.preventDefault();

    const saisies = {
        nom: document.getElementById('editNom').value.trim(),
        prenom: document.getElementById('editPrenom').value.trim(),
        email: document.getElementById('editEmail').value.trim(),
        departement: departementSaisi(),
        role: document.getElementById('editRole').value,
        actif: document.getElementById('editActif').value
    };

    try {
        if (!compteEnEdition) {
            const motDePasse = document.getElementById('editMotDePasse').value;
            if (!saisies.nom || !saisies.prenom || !saisies.email || !motDePasse) {
                showNotification('Nom, prénom, adresse et mot de passe sont obligatoires', 'error');
                return;
            }
            await lireReponse(await fetch(`${API_BASE}/utilisateurs/creer`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: new URLSearchParams({ ...saisies, motDePasse, adminId: currentUser.id })
            }));
            showNotification('Compte créé', 'success');
        } else {
            // Seuls les champs réellement modifiés partent au serveur.
            const modifies = {};
            Object.entries(saisies).forEach(([champ, valeur]) => {
                // L'état du compte est un booléen : la comparaison passe par sa
                // forme textuelle, sinon « false » serait confondu avec un vide.
                const actuel = champ === 'actif'
                    ? String(compteEnEdition.actif !== false)
                    : (compteEnEdition[champ] || '');
                if (valeur && valeur !== actuel) modifies[champ] = valeur;
            });
            if (!Object.keys(modifies).length) {
                showNotification('Aucune modification à enregistrer', 'info');
                return;
            }
            const params = new URLSearchParams({ ...modifies, adminId: currentUser.id });
            await lireReponse(await fetch(`${API_BASE}/utilisateurs/${compteEnEdition.id}?${params}`,
                    { method: 'PUT' }));
            showNotification('Compte modifié', 'success');
        }

        fermerModalUtilisateur();
        loadAllUsers();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

/* ---------- Fenêtre de confirmation de suppression ---------- */
let compteASupprimer = null;

function confirmerSuppression(user) {
    compteASupprimer = user;
    document.getElementById('texteSuppression').innerHTML =
        `Le compte de <strong>${escapeHtml(user.prenom)} ${escapeHtml(user.nom)}</strong>
         (${escapeHtml(user.email)}) sera définitivement supprimé.`;
    document.getElementById('modalSuppression').classList.add('open');
}

function fermerModalSuppression() {
    document.getElementById('modalSuppression').classList.remove('open');
    compteASupprimer = null;
}

async function validerSuppression() {
    if (!compteASupprimer) return;
    try {
        await lireReponse(await fetch(
            `${API_BASE}/utilisateurs/${compteASupprimer.id}?adminId=${currentUser.id}`,
            { method: 'DELETE' }));
        showNotification('Compte supprimé', 'success');
        fermerModalSuppression();
        loadAllUsers();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}