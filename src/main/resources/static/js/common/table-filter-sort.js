/*
 * Lightweight client-side filter + sort, shared by the custom/* CRUD pages
 * (trainer/technician/lecturer/participant/courses/course-type/course-counter-service).
 *
 * Attaches to any <table class="filterable-table">:
 *   - #tableFilterInput (if present on the page) hides <tbody> rows whose text
 *     doesn't match, case-insensitively, against the whole row.
 *   - <th data-sort="text|number|date"> becomes clickable; each click toggles
 *     ascending/descending. Uses th.cellIndex so the sort lines up with the
 *     <td> at the same position even when other columns in the row (checkboxes,
 *     row number, Images, Actions) have no data-sort of their own.
 *
 * Dates are parsed as dd/MM/yyyy (this project's date convention, see
 * #temporals.format(..., 'dd/MM/yyyy') in the templates); empty or
 * unparseable date/number cells sort to the start.
 */
(function () {
    function parseCellValue(cell, type) {
        const raw = cell ? cell.textContent.trim() : '';
        if (type === 'number') {
            const n = parseFloat(raw.replace(',', '.'));
            return Number.isNaN(n) ? Number.NEGATIVE_INFINITY : n;
        }
        if (type === 'date') {
            const m = raw.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
            return m ? Number(m[3]) * 10000 + Number(m[2]) * 100 + Number(m[1]) : Number.NEGATIVE_INFINITY;
        }
        return raw;
    }

    function compareValues(a, b, type) {
        return type === 'text' ? a.localeCompare(b, undefined, {sensitivity: 'base', numeric: true}) : a - b;
    }

    function clearOtherSortIndicators(table, exceptTh) {
        table.querySelectorAll('th[data-sort]').forEach((h) => {
            if (h === exceptTh) return;
            delete h.dataset.sortDir;
            const indicator = h.querySelector('.sort-indicator');
            if (indicator) indicator.textContent = '';
        });
    }

    function sortByColumn(table, th) {
        const type = th.dataset.sort;
        const tbody = table.tBodies[0];
        if (!type || !tbody) return;

        const direction = th.dataset.sortDir === 'asc' ? 'desc' : 'asc';
        clearOtherSortIndicators(table, th);
        th.dataset.sortDir = direction;

        let indicator = th.querySelector('.sort-indicator');
        if (!indicator) {
            indicator = document.createElement('span');
            indicator.className = 'sort-indicator ms-1';
            th.appendChild(indicator);
        }
        indicator.textContent = direction === 'asc' ? '▲' : '▼';

        const cellIndex = th.cellIndex;
        const rows = Array.from(tbody.rows);
        rows.sort((r1, r2) => {
            const v1 = parseCellValue(r1.cells[cellIndex], type);
            const v2 = parseCellValue(r2.cells[cellIndex], type);
            const cmp = compareValues(v1, v2, type);
            return direction === 'asc' ? cmp : -cmp;
        });
        rows.forEach((row) => tbody.appendChild(row));
    }

    function applyFilter(table, query) {
        const needle = query.trim().toLowerCase();
        const tbody = table.tBodies[0];
        if (!tbody) return;
        Array.from(tbody.rows).forEach((row) => {
            const matches = needle === '' || row.textContent.toLowerCase().includes(needle);
            row.classList.toggle('d-none', !matches);
        });
    }

    document.querySelectorAll('table.filterable-table').forEach((table) => {
        table.querySelectorAll('th[data-sort]').forEach((th) => {
            th.style.cursor = 'pointer';
            th.addEventListener('click', () => sortByColumn(table, th));
        });
    });

    const filterInput = document.getElementById('tableFilterInput');
    const filterableTable = document.querySelector('table.filterable-table');
    if (filterInput && filterableTable) {
        filterInput.addEventListener('input', () => applyFilter(filterableTable, filterInput.value));
    }
})();
