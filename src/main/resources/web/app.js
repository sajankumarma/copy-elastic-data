(function () {
    const stored = localStorage.getItem('rdp-theme');
    if (stored === 'dark' || (!stored && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
        document.documentElement.classList.add('dark');
    }
})();

window.RDP = {
    toggleTheme() {
        const root = document.documentElement;
        root.classList.toggle('dark');
        const isDark = root.classList.contains('dark');
        localStorage.setItem('rdp-theme', isDark ? 'dark' : 'light');
        const btn = document.getElementById('themeToggle');
        if (btn) btn.setAttribute('aria-pressed', String(isDark));
        this.refreshThemeIcon();
    },

    refreshThemeIcon() {
        const sun = document.getElementById('icon-sun');
        const moon = document.getElementById('icon-moon');
        if (!sun || !moon) return;
        const isDark = document.documentElement.classList.contains('dark');
        sun.style.display = isDark ? 'inline-block' : 'none';
        moon.style.display = isDark ? 'none' : 'inline-block';
    },

    toast(message, kind) {
        kind = kind || 'info';
        let stack = document.getElementById('toastStack');
        if (!stack) {
            stack = document.createElement('div');
            stack.id = 'toastStack';
            stack.className = 'toast-stack';
            document.body.appendChild(stack);
        }
        const node = document.createElement('div');
        node.className = 'toast slide-in-right toast-' + kind;
        const icon = ({
            error: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>',
            success: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
            info: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>'
        })[kind] || '';
        node.innerHTML = icon + '<div class="text-sm leading-snug">' + this.escape(String(message)) + '</div>';
        stack.appendChild(node);
        setTimeout(() => {
            node.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
            node.style.opacity = '0';
            node.style.transform = 'translateX(20px)';
            setTimeout(() => node.remove(), 320);
        }, kind === 'error' ? 6000 : 3500);
    },

    setLoading(btn, loading, loadingText) {
        if (!btn) return;
        if (loading) {
            btn.dataset.originalHtml = btn.innerHTML;
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner"></span>' + this.escape(loadingText || 'Working...');
        } else if (btn.dataset.originalHtml) {
            btn.innerHTML = btn.dataset.originalHtml;
            btn.disabled = false;
        }
    },

    escape(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }
};

document.addEventListener('DOMContentLoaded', function () {
    if (window.RDP) window.RDP.refreshThemeIcon();
});