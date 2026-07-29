/* ==========================================================================
   Chargeur : assemble les vues puis les modules, dans l'ordre.

   Le document ne contient plus qu'une coquille. Les vues sont des fragments
   HTML séparés, injectés au démarrage, puis les modules JavaScript sont
   ajoutés un par un — l'ordre compte, chacun s'appuyant sur les précédents.
   ========================================================================== */

const VUES = ['connexion', 'utilisateur', 'support', 'admin', 'modales'];

const MODULES = [
    'noyau',               // configuration, appels serveur, fonctions communes
    'session',             // connexion, déconnexion, orientation par rôle
    'navigation',          // passage d'un onglet à l'autre
    'graphiques',          // anneaux et histogrammes en SVG
    'tableau-bord',        // composant partagé par les trois espaces
    'admin-utilisateurs',  // table des comptes
    'admin-bord',          // tableau de bord de l'administrateur
    'admin-connexions',    // historique des tentatives de connexion
    'support',             // tickets ouverts et bilan personnel
    'utilisateur',         // suivi de ses tickets
    'tickets',             // fiche d'un ticket
    'demarrage'            // branchement des écouteurs
];

function chargerScript(nom) {
    return new Promise((resoudre, rejeter) => {
        const balise = document.createElement('script');
        balise.src = `js/${nom}.js`;
        balise.onload = resoudre;
        balise.onerror = () => rejeter(new Error(`Module introuvable : js/${nom}.js`));
        document.body.appendChild(balise);
    });
}

async function amorcer() {
    const conteneur = document.getElementById('application');

    try {
        const fragments = await Promise.all(
            VUES.map(async vue => {
                const reponse = await fetch(`vues/${vue}.html`);
                if (!reponse.ok) throw new Error(`Vue introuvable : vues/${vue}.html`);
                return reponse.text();
            }));

        conteneur.innerHTML = fragments.join('\n');

        // Séquentiel et non parallèle : un module suppose les précédents chargés.
        for (const module of MODULES) {
            await chargerScript(module);
        }

        demarrerApplication();
    } catch (erreur) {
        conteneur.innerHTML = `
            <div style="max-width:560px;margin:80px auto;padding:28px;
                        border:1px solid #E3E9ED;border-radius:14px;
                        font-family:system-ui,sans-serif;color:#1F2A33;">
                <h1 style="color:#00713F;font-size:1.1rem;margin-bottom:12px;">
                    L'application n'a pas pu démarrer
                </h1>
                <p style="line-height:1.5;">${erreur.message}</p>
                <p style="margin-top:12px;color:#6B7A87;font-size:.9rem;">
                    Vérifiez que le dossier <code>static</code> contient bien
                    <code>vues/</code> et <code>js/</code>.
                </p>
            </div>`;
        console.error(erreur);
    }
}

document.addEventListener('DOMContentLoaded', amorcer);