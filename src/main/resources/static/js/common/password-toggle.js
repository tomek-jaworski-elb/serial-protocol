// Show/hide toggle for password inputs. A button declares its input with
// data-toggle-password="#inputId" and carries aria-controls.
(function () {
    function setState(button, input, visible) {
        input.type = visible ? 'text' : 'password';
        const label = button.dataset.label || 'password';
        button.textContent = visible ? 'Hide' : 'Show';
        button.setAttribute('aria-label', (visible ? 'Hide ' : 'Show ') + label);
    }

    document.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-toggle-password]');
        if (!button) {
            return;
        }
        const input = document.querySelector(button.dataset.togglePassword);
        if (input) {
            setState(button, input, input.type === 'password');
        }
    });

    // Puts every toggled input under root back to type=password with the toggle in its Show state.
    function hidePasswords(root) {
        root.querySelectorAll('button[data-toggle-password]').forEach((button) => {
            const input = document.querySelector(button.dataset.togglePassword);
            if (input) {
                setState(button, input, false);
            }
        });
    }
    window.hidePasswords = hidePasswords;

    // Never leave a password readable in a page restored from the back/forward cache.
    window.addEventListener('pagehide', () => hidePasswords(document));
})();
