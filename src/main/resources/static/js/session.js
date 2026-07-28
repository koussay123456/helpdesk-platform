/* ==========================================================================
   Connexion, déconnexion et orientation vers l'espace du rôle.
   ========================================================================== */

/* ============================================================
   CONNEXION
   L'accès repose sur une adresse présente en base et le mot de passe
   associé. Aucun contrôle de domaine : c'est la table utilisateur qui
   fait autorité.
   ============================================================ */
function messageConnexion(texte) {
    const zone = document.getElementById('messageConnexion');
    zone.textContent = texte || '';
    zone.classList.toggle('visible', !!texte);
}

async function handleLogin(e) {
    e.preventDefault();

    const email = document.getElementById('loginEmail').value.trim();
    const motDePasse = document.getElementById('loginPassword').value;
    const bouton = document.getElementById('btnConnexion');

    if (!email || !motDePasse) {
        messageConnexion('Renseignez votre adresse et votre mot de passe.');
        return;
    }

    messageConnexion('');
    bouton.disabled = true;

    try {
        const params = new URLSearchParams({ email, motDePasse });
        const response = await fetch(`${API_BASE}/auth/login?${params}`, { method: 'POST' });
        const data = await response.json();

        if (!data.success) {
            messageConnexion(data.message || 'Adresse ou mot de passe incorrect.');
            return;
        }

        currentUser = data.utilisateur;
        localStorage.setItem('currentUser', JSON.stringify(currentUser));
        document.getElementById('loginPassword').value = '';
        showDashboard(currentUser.role);
    } catch (error) {
        console.error(error);
        messageConnexion('Le serveur ne répond pas. Vérifiez que l\'application est démarrée.');
    } finally {
        bouton.disabled = false;
    }
}

function logout() {
    if (!confirm('Se déconnecter de Kipropha Helpdesk ?')) return;

    reinitialiserFiltres();
    currentUser = null;
    localStorage.removeItem('currentUser');
    document.querySelectorAll('.screen, .dashboard-screen').forEach(s => s.classList.remove('active'));
    document.getElementById('loginScreen').classList.add('active');
}

function showDashboard(role) {
    document.getElementById('loginScreen').classList.remove('active');
    document.querySelectorAll('.dashboard-screen').forEach(d => d.classList.remove('active'));

    // Chaque ouverture de session repart d'une vue neuve.
    reinitialiserFiltres();

    if (role === 'UTILISATEUR') {
        document.getElementById('userDashboard').classList.add('active');
        majAffichageIdentite();
        basculerOnglet('userDashboard', 'dashboard');
        chargerMesTickets();
    } else if (role === 'SUPPORT_IT') {
        document.getElementById('supportDashboard').classList.add('active');
        majAffichageIdentite();
        basculerOnglet('supportDashboard', 'ouverts');
        chargerTicketsOuverts();
    } else if (role === 'ADMINISTRATEUR') {
        document.getElementById('adminDashboard').classList.add('active');
        majAffichageIdentite();
        switchAdminTab('utilisateurs');
        // switchAdminTab lance déjà le chargement ; l'appel explicite garantit
        // que la table est peuplée même si l'onglet était déjà celui affiché.
        loadAllUsers();
    }
}

/** Reprend la session enregistrée, si elle existe. Appelée au démarrage. */
function reprendreSession() {
    const saved = localStorage.getItem('currentUser');
    if (saved) {
        try {
            currentUser = JSON.parse(saved);
            showDashboard(currentUser.role);
        } catch (e) {
            localStorage.removeItem('currentUser');
        }
    }
}

// Logo de la sidebar : bascule sur une icône si le PNG est absent.
function brancherReplisLogo() {
    const logoImg = document.querySelector('.logo-image');
    if (logoImg) {
        logoImg.addEventListener('error', () => {
            const container = document.getElementById('logoContainer');
            if (container) container.innerHTML = '<i class="fas fa-shield-halved"></i>';
        });
    }
}

/** Répercute l'identité du titulaire partout où elle est affichée. */
function majAffichageIdentite() {
    if (!currentUser) return;

    const nomComplet = currentUser.prenom + ' ' + currentUser.nom;
    const initialesTitulaire = initiales(nomComplet);

    ['userNameDisplay', 'userIdentite', 'supportIdentite', 'supportNomHeader',
     'adminIdentite', 'adminNomHeader']
        .forEach(id => {
            const cible = document.getElementById(id);
            if (cible) cible.textContent = nomComplet;
        });

    ['userAvatar', 'supportAvatar', 'adminAvatar'].forEach(id => {
        const cible = document.getElementById(id);
        if (cible) cible.textContent = initialesTitulaire;
    });
}

/* ============================================================
   HISTORIQUE DES TICKETS DU DEMANDEUR
   ============================================================ */