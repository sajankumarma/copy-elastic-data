package html;

import java.util.Map;

import objects.ProcessResult;

public class HtmlHelper {

    private static final String HEAD_COMMON = """
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <script>
                (function () {
                    const stored = localStorage.getItem('rdp-theme');
                    if (stored === 'dark' || (!stored && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
                        document.documentElement.classList.add('dark');
                    }
                })();
              </script>
              <link rel="preconnect" href="https://fonts.googleapis.com">
              <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
              <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
              <script src="https://cdn.tailwindcss.com"></script>
              <script>tailwind.config = { darkMode: 'class' };</script>
              <link rel="stylesheet" href="/styles.css">
            """;

    private static final String TOPBAR = """
              <header class="topbar">
                <div class="max-w-6xl mx-auto px-4 sm:px-6 py-3 flex items-center justify-between">
                  <a href="/" class="flex items-center gap-3">
                    <span class="brand-mark">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="icon">
                        <path d="M3 7l9-4 9 4-9 4-9-4z"/><path d="M3 17l9 4 9-4"/><path d="M3 12l9 4 9-4"/>
                      </svg>
                    </span>
                    <span class="font-semibold tracking-tight text-lg">RDP <span class="opacity-50">→</span> ES</span>
                  </a>
                  <nav class="flex items-center gap-2">
                    <a href="/" class="btn btn-ghost px-3 py-2 text-sm">Dashboard</a>
                    <a href="/config" class="btn btn-ghost px-3 py-2 text-sm">Config</a>
                    <a href="/index" class="btn btn-ghost px-3 py-2 text-sm">Copy</a>
                    <a href="/create" class="btn btn-ghost px-3 py-2 text-sm">Create</a>
                    <button id="themeToggle" class="btn btn-ghost px-3 py-2" onclick="RDP.toggleTheme()" aria-label="Toggle theme">
                      <svg id="icon-sun" class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="display:none"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>
                      <svg id="icon-moon" class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
                    </button>
                  </nav>
                </div>
              </header>
            """;

    public static StringBuilder generateResultsHtml(Map<String, ProcessResult> results, String fatalError) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                .append("<title>RDP → ES · Results</title>\n")
                .append(HEAD_COMMON)
                .append("</head>\n<body>\n")
                .append(TOPBAR);

        int total = results == null ? 0 : results.size();
        int created = 0, updated = 0, noop = 0, notFound = 0, errored = 0;
        if (results != null) {
            for (ProcessResult r : results.values()) {
                if (r == null) continue;
                switch (r.getStatus()) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case NOOP -> noop++;
                    case NOT_FOUND -> notFound++;
                    case ERRORED -> errored++;
                }
            }
        }

        html.append("<main class=\"max-w-5xl mx-auto px-4 sm:px-6 py-10 fade-in\">\n")
                .append("<nav class=\"text-xs mb-4\" style=\"color:var(--text-soft)\"><a href=\"/\" class=\"hover:underline\">Dashboard</a> <span class=\"px-1\">›</span> <span>Results</span></nav>\n");

        if (fatalError != null && !fatalError.isBlank()) {
            html.append("<section class=\"glass rounded-2xl p-5 mb-6\" style=\"border-color: rgba(244,63,94,0.45)\">\n")
                    .append("<div class=\"flex items-start gap-3\">\n")
                    .append("<svg class=\"icon\" style=\"color:#e11d48\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/></svg>\n")
                    .append("<div><p class=\"font-semibold\" style=\"color:#be123c\">Processing failed</p>")
                    .append("<p class=\"text-sm mt-1 break-words\">").append(escape(fatalError)).append("</p></div>\n")
                    .append("</div></section>\n");
        }

        html.append("<section class=\"glass rounded-2xl p-6 sm:p-8 mb-6\">\n")
                .append("<header class=\"flex items-start justify-between gap-4 flex-wrap mb-5\">\n")
                .append("<div><h1 class=\"text-2xl font-bold tracking-tight\">Copy results</h1>")
                .append("<p class=\"text-sm mt-1\" style=\"color:var(--text-soft)\">Per-document outcome from the last copy job.</p></div>\n")
                .append("<div class=\"flex gap-2\">")
                .append("<button class=\"btn btn-ghost\" onclick=\"downloadCsv()\">")
                .append("<svg class=\"icon\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"/><polyline points=\"7 10 12 15 17 10\"/><line x1=\"12\" y1=\"15\" x2=\"12\" y2=\"3\"/></svg>")
                .append("CSV</button>")
                .append("<a href=\"/index\" class=\"btn btn-primary\">Copy more</a>")
                .append("</div></header>\n");

        html.append("<div class=\"grid grid-cols-2 sm:grid-cols-5 gap-3 mb-5\">")
                .append(statTile("Total", total, "indigo"))
                .append(statTile("Created", created, "emerald"))
                .append(statTile("Updated", updated, "amber"))
                .append(statTile("Not found", notFound, "slate"))
                .append(statTile("Errored", errored, "rose"))
                .append("</div>\n");

        html.append("<div class=\"flex gap-2 flex-wrap mb-4\">\n")
                .append("<input id=\"resultFilter\" class=\"field\" type=\"search\" placeholder=\"Filter by id, status, or message...\" oninput=\"applyFilter()\">\n")
                .append("<select id=\"statusFilter\" class=\"field sm:w-48\" onchange=\"applyFilter()\">\n")
                .append("<option value=\"\">All statuses</option>")
                .append("<option value=\"created\">Created</option>")
                .append("<option value=\"updated\">Updated</option>")
                .append("<option value=\"noop\">Noop</option>")
                .append("<option value=\"not_found\">Not found</option>")
                .append("<option value=\"errored\">Errored</option>")
                .append("</select>\n")
                .append("</div>\n");

        html.append("<div id=\"resultList\" class=\"space-y-2\">\n");
        if (results == null || results.isEmpty()) {
            html.append("<div class=\"text-center py-12\" style=\"color:var(--text-soft)\">")
                    .append("<p class=\"text-sm\">No results to display. Start a copy job to see outcomes here.</p>")
                    .append("</div>\n");
        } else {
            for (Map.Entry<String, ProcessResult> entry : results.entrySet()) {
                ProcessResult r = entry.getValue();
                String statusLabel = r == null ? "unknown" : r.statusLabel();
                String message = r == null ? "" : r.getMessage();
                String key = entry.getKey();
                String rowClass = "result-row fade-in is-" + statusLabel.replace("_", "");
                html.append("<div class=\"").append(rowClass).append("\" data-status=\"").append(escape(statusLabel)).append("\" data-search=\"")
                        .append(escape((key + " " + statusLabel + " " + message).toLowerCase())).append("\">\n")
                        .append("<div class=\"flex justify-between items-start gap-3\">\n")
                        .append("<span class=\"code break-all\">").append(escape(key)).append("</span>\n")
                        .append("<span class=\"chip chip-").append(chipKey(statusLabel)).append("\">").append(escape(statusLabel.replace("_", " "))).append("</span>\n")
                        .append("</div>\n");
                if (message != null && !message.isBlank()) {
                    html.append("<p class=\"text-xs mt-2 break-words\" style=\"color:var(--text-soft)\">").append(escape(message)).append("</p>\n");
                }
                html.append("</div>\n");
            }
        }
        html.append("</div>\n</section>\n");

        html.append("<div class=\"flex justify-between gap-3 flex-wrap\">\n")
                .append("<a href=\"/\" class=\"btn btn-ghost\">← Dashboard</a>\n")
                .append("<a href=\"/index\" class=\"btn btn-success\">Run another copy</a>\n")
                .append("</div>\n</main>\n");

        html.append("<script src=\"/app.js\"></script>\n<script>\n")
                .append("function applyFilter(){\n")
                .append("  const q=(document.getElementById('resultFilter').value||'').trim().toLowerCase();\n")
                .append("  const s=(document.getElementById('statusFilter').value||'').trim().toLowerCase();\n")
                .append("  document.querySelectorAll('#resultList > [data-status]').forEach(el=>{\n")
                .append("    const matchesQ=!q || el.dataset.search.includes(q);\n")
                .append("    const matchesS=!s || el.dataset.status===s;\n")
                .append("    el.style.display=(matchesQ&&matchesS)?'':'none';\n")
                .append("  });\n")
                .append("}\n")
                .append("function downloadCsv(){\n")
                .append("  const rows=[['id','status','message']];\n")
                .append("  document.querySelectorAll('#resultList > [data-status]').forEach(el=>{\n")
                .append("    const id=el.querySelector('.code').textContent;\n")
                .append("    const status=el.dataset.status;\n")
                .append("    const msgEl=el.querySelector('p');\n")
                .append("    rows.push([id,status,msgEl?msgEl.textContent:'']);\n")
                .append("  });\n")
                .append("  const csv=rows.map(r=>r.map(c=>'\"'+String(c).replaceAll('\"','\"\"')+'\"').join(',')).join('\\n');\n")
                .append("  const blob=new Blob([csv],{type:'text/csv'});\n")
                .append("  const url=URL.createObjectURL(blob);\n")
                .append("  const a=document.createElement('a');\n")
                .append("  a.href=url; a.download='rdp-to-es-results.csv'; a.click();\n")
                .append("  setTimeout(()=>URL.revokeObjectURL(url),500);\n")
                .append("}\n")
                .append("</script>\n</body>\n</html>");
        return html;
    }

    public static StringBuilder generateShowConfigHtml(String manageUrl, String managePort, String tenantId, String authorization) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                .append("<title>RDP → ES · Saved config</title>\n")
                .append(HEAD_COMMON)
                .append("</head>\n<body>\n")
                .append(TOPBAR)
                .append("<main class=\"max-w-3xl mx-auto px-4 sm:px-6 py-10 fade-in\">\n")
                .append("<nav class=\"text-xs mb-4\" style=\"color:var(--text-soft)\"><a href=\"/\" class=\"hover:underline\">Dashboard</a> <span class=\"px-1\">›</span> <span>Server configuration</span></nav>\n")
                .append("<section class=\"glass rounded-2xl p-8\">\n")
                .append("<header class=\"mb-6 flex items-start justify-between gap-4\">\n")
                .append("<div><h1 class=\"text-2xl font-bold tracking-tight\">Saved server configuration</h1>")
                .append("<p class=\"text-sm mt-1\" style=\"color:var(--text-soft)\">Current values written to <span class=\"code\">source-config.json</span>.</p></div>\n")
                .append("<a href=\"/config\" class=\"btn btn-primary\">Edit</a>\n")
                .append("</header>\n")
                .append("<dl class=\"grid grid-cols-1 sm:grid-cols-2 gap-4\">\n")
                .append(configField("Manage URL", manageUrl, false))
                .append(configField("Manage port", managePort, false))
                .append(configField("Tenant ID", tenantId, false))
                .append(configField("Authorization", authorization, true))
                .append("</dl>\n")
                .append("<div class=\"divider\"></div>\n")
                .append("<div class=\"flex flex-wrap gap-2 justify-between\">\n")
                .append("<a href=\"/\" class=\"btn btn-ghost\">← Back</a>\n")
                .append("<div class=\"flex gap-2\"><a href=\"/config\" class=\"btn btn-info\">Update config</a><a href=\"/index\" class=\"btn btn-success\">Start a copy</a></div>\n")
                .append("</div>\n</section>\n</main>\n")
                .append("<script src=\"/app.js\"></script>\n</body>\n</html>");
        return html;
    }

    public static StringBuilder generateErrorHtml(String title, String detail) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                .append("<title>RDP → ES · Error</title>\n")
                .append(HEAD_COMMON)
                .append("</head>\n<body>\n")
                .append(TOPBAR)
                .append("<main class=\"max-w-2xl mx-auto px-4 sm:px-6 py-12 fade-in\">\n")
                .append("<section class=\"glass rounded-2xl p-8\" style=\"border-color: rgba(244,63,94,0.45)\">\n")
                .append("<div class=\"flex items-start gap-3 mb-3\">\n")
                .append("<svg class=\"icon\" style=\"color:#e11d48\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/></svg>\n")
                .append("<h1 class=\"text-2xl font-bold tracking-tight\" style=\"color:#be123c\">")
                .append(escape(title == null ? "Error" : title))
                .append("</h1>\n</div>\n")
                .append("<pre class=\"code p-4 rounded-lg whitespace-pre-wrap break-words\" style=\"background:rgba(244,63,94,0.08); border:1px solid rgba(244,63,94,0.25); color:var(--text-strong)\">")
                .append(escape(detail == null ? "(no detail)" : detail))
                .append("</pre>\n")
                .append("<div class=\"divider\"></div>\n")
                .append("<div class=\"flex gap-2 justify-between flex-wrap\">\n")
                .append("<button onclick=\"history.back()\" class=\"btn btn-ghost\">← Go back</button>\n")
                .append("<a href=\"/\" class=\"btn btn-primary\">Back to dashboard</a>\n")
                .append("</div>\n</section>\n</main>\n")
                .append("<script src=\"/app.js\"></script>\n</body>\n</html>");
        return html;
    }

    private static String statTile(String label, int value, String accent) {
        String color = switch (accent) {
            case "emerald" -> "color:#059669";
            case "amber" -> "color:#a16207";
            case "rose" -> "color:#be123c";
            case "slate" -> "color:#475569";
            default -> "color:#4f46e5";
        };
        return "<div class=\"glass rounded-xl p-4 text-center\">"
                + "<p class=\"text-xs uppercase tracking-wider\" style=\"color:var(--text-soft)\">" + escape(label) + "</p>"
                + "<p class=\"text-2xl font-bold mt-1\" style=\"" + color + "\">" + value + "</p>"
                + "</div>";
    }

    private static String configField(String label, String value, boolean secret) {
        String safe = escape(value == null ? "" : value);
        if (secret) {
            String revealId = "secret-" + Math.abs(label.hashCode());
            return "<div class=\"sm:col-span-2\"><dt class=\"label\">" + escape(label) + "</dt>"
                    + "<dd class=\"field flex justify-between items-center gap-3\" style=\"cursor:default\">"
                    + "<span id=\"" + revealId + "\" class=\"code break-all\" data-secret=\"" + safe + "\">"
                    + maskSecret(value) + "</span>"
                    + "<button class=\"btn btn-ghost text-xs whitespace-nowrap\" onclick=\"(function(b){const s=document.getElementById('" + revealId + "');"
                    + "const showing=s.textContent===s.dataset.secret;s.textContent=showing?'" + escape(maskSecret(value)) + "':s.dataset.secret;b.textContent=showing?'Show':'Hide';})(this)\">Show</button>"
                    + "</dd></div>";
        }
        return "<div><dt class=\"label\">" + escape(label) + "</dt>"
                + "<dd class=\"field code break-all\" style=\"cursor:default\">" + safe + "</dd></div>";
    }

    private static String maskSecret(String value) {
        if (value == null || value.isBlank() || "Not set".equals(value)) return value == null ? "" : value;
        int len = value.length();
        if (len <= 8) return "••••••••";
        return value.substring(0, 4) + " •••• " + value.substring(len - 4);
    }

    private static String chipKey(String statusLabel) {
        return switch (statusLabel) {
            case "created" -> "created";
            case "updated" -> "updated";
            case "noop" -> "noop";
            case "not_found" -> "notfound";
            case "errored" -> "errored";
            default -> "notfound";
        };
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}