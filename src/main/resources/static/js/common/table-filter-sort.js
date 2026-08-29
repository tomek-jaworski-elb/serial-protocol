/*
 * Lightweight client-side filter + sort, shared by the custom/* CRUD pages
 * (trainer/technician/lecturer/participant/courses/course-type/course-counter).
 *
 * Attaches to any <table class="filterable-table">:
 *   - #tableFilterInput (if present on the page) hides <tbody> rows whose text
 *     doesn't match, case-insensitively, against the whole row.
 *   - <th data-sort="text|number|date"> becomes a real <button> so it can be
 *     operated by keyboard, and the <th> carries aria-sort. Each activation
 *     toggles ascending/descending. Sorting uses th.cellIndex so it lines up
 *     with the cell at the same position even when other columns in the row
 *     (checkbox, row number, Images, Actions) carry no data-sort of their own.
 *
 * Scope note: both operations act only on the rows of the current page, since
 * paging is done server-side. The templates label the filter accordingly.
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
            h.setAttribute('aria-sort', 'none');
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
        th.setAttribute('aria-sort', direction === 'asc' ? 'ascending' : 'descending');

        const indicator = th.querySelector('.sort-indicator');
        if (indicator) indicator.textContent = direction === 'asc' ? '▲' : '▼';

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

    /*
     * Turns a sortable <th> into <th aria-sort><button class="sort-btn">.
     * It used to listen for clicks on the bare <th>, which is not focusable —
     * sorting was unreachable by keyboard (WCAG 2.1.1, Level A). CSS makes the
     * button fill the cell so the click target does not shrink to just the text.
     */
    function makeHeaderSortable(table, th) {
        th.setAttribute('aria-sort', 'none');

        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'sort-btn';
        // Move the header's existing nodes into the button instead of reading its
        // text and clearing it: every header is plain text today, but clearing
        // would silently destroy any markup a header later gains (an abbr, an
        // i18n span, a tooltip trigger) with no error to notice.
        while (th.firstChild) button.appendChild(th.firstChild);

        const indicator = document.createElement('span');
        indicator.className = 'sort-indicator ms-1';
        indicator.setAttribute('aria-hidden', 'true');
        button.appendChild(indicator);

        button.addEventListener('click', () => sortByColumn(table, th));
        th.appendChild(button);
    }

    function applyFilter(table, query) {
        const needle = query.trim().toLowerCase();
        const tbody = table.tBodies[0];
        if (!tbody) return;

        const rows = Array.from(tbody.rows);
        let shown = 0;
        rows.forEach((row) => {
            const matches = needle === '' || row.textContent.toLowerCase().includes(needle);
            row.classList.toggle('d-none', !matches);
            if (matches) shown++;
        });

        // Say out loud that the filter only reaches the current page. Without
        // this a filtered table looks exactly like an unfiltered one, which is
        // how someone concludes a record is missing from the database.
        const scope = document.getElementById('crudFilterScope');
        if (scope) {
            scope.textContent = needle === '' ? '' : `${shown} of ${rows.length} on this page`;
        }

        // crud-table-actions.js re-derives selection state from what is visible
        document.dispatchEvent(new CustomEvent('crud:filtered'));
    }

    document.querySelectorAll('table.filterable-table').forEach((table) => {
        table.querySelectorAll('th[data-sort]').forEach((th) => makeHeaderSortable(table, th));
    });

    const filterInput = document.getElementById('tableFilterInput');
    const filterableTable = document.querySelector('table.filterable-table');
    if (filterInput && filterableTable) {
        filterInput.addEventListener('input', () => applyFilter(filterableTable, filterInput.value));
    }
})();
