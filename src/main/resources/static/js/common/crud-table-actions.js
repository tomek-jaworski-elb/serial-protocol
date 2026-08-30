/*
 * Row selection and PDF report wiring, shared by the custom/* CRUD pages.
 *
 * Replaces the near-identical block that used to be copy-pasted into each of
 * the 7 templates. Binds to ids those templates already carry:
 *   #selectAllCheckbox, .row-select-checkbox, #printReportBtn,
 *   #pdfReportForm, #pdfReportIds, and optionally #crudSelectionCount.
 *
 * Rows hidden by the table filter are treated as out of scope throughout.
 * Previously they were not: filtering to "Kowalski" and hitting select-all
 * still submitted every row on the page, so the generated report could contain
 * records the user had filtered away and never saw.
 */
/*
 * A sticky cell always establishes its own stacking context, so a dropdown menu
 * rendered inside one is confined to it. Every Actions cell carries the same
 * z-index, and cells further down the table come later in DOM order, so they
 * paint over an open menu and swallow its clicks: opening the kebab on row 1 of
 * a 6-row table left Update and Delete unreachable. Raising just the cell whose
 * menu is open lifts it above the rows below for as long as the menu is shown.
 */
(function () {
    // .table-responsive is overflow-x:auto, and CSS then computes overflow-y as auto
    // too, so the container clips in BOTH directions. A menu that has no room to flip
    // upwards — a table with one or two rows — is cut off at the container's bottom
    // edge. Positioning it with strategy:'fixed' takes it out of that clip; it does
    // NOT escape the sticky cell's stacking context, which is what the z-index bump
    // below is for. Both are needed.
    function useFixedPositioning() {
        if (!window.bootstrap || !window.bootstrap.Dropdown) return;
        document.querySelectorAll('.crud-table [data-bs-toggle="dropdown"]').forEach((toggle) => {
            window.bootstrap.Dropdown.getOrCreateInstance(toggle, {
                popperConfig: (defaults) => ({ ...defaults, strategy: 'fixed' })
            });
        });
    }
    document.addEventListener('DOMContentLoaded', useFixedPositioning);

    const cellOf = (el) => el.closest('.crud-table td:last-child, .crud-table th:last-child');
    document.addEventListener('show.bs.dropdown', (e) => {
        const cell = cellOf(e.target);
        if (cell) cell.style.zIndex = '3';
    });
    document.addEventListener('hide.bs.dropdown', (e) => {
        const cell = cellOf(e.target);
        if (cell) cell.style.zIndex = '';
    });
})();

(function () {
    const selectAll = document.getElementById('selectAllCheckbox');
    const printBtn = document.getElementById('printReportBtn');
    const reportForm = document.getElementById('pdfReportForm');
    const reportIds = document.getElementById('pdfReportIds');
    const countEl = document.getElementById('crudSelectionCount');
    if (!selectAll && !printBtn) return;

    // Optional. The templates deliberately do not set data-max-records today:
    // pdf.report.max-records is enforced as size() > max and the largest page
    // size offered is 100, so the limit cannot be reached from the UI. Left in
    // place so that raising the page-size options, or lowering the limit, only
    // needs the attribute added to the template. Absent attribute => 0 => off.
    const maxRecords = Number(countEl && countEl.dataset.maxRecords) || 0;

    const allBoxes = () => Array.from(document.querySelectorAll('.row-select-checkbox'));
    const isVisible = (cb) => {
        const row = cb.closest('tr');
        return !!row && !row.classList.contains('d-none');
    };
    const visibleBoxes = () => allBoxes().filter(isVisible);
    const checkedVisible = () => visibleBoxes().filter((cb) => cb.checked);

    function refresh() {
        // Filtering a row away also drops its selection, so what is counted,
        // what select-all covers and what the PDF receives can never disagree.
        // Deliberate trade-off, raised in review and kept: typing into the filter
        // does silently clear an existing selection, but the alternative -- keeping
        // hidden rows selected -- would show "5 selected" while producing a
        // one-record PDF. Do not "fix" this without changing the counter too.
        allBoxes().forEach((cb) => {
            if (!isVisible(cb)) cb.checked = false;
        });

        const visible = visibleBoxes();
        const checked = checkedVisible();

        if (selectAll) {
            selectAll.checked = visible.length > 0 && checked.length === visible.length;
            selectAll.indeterminate = checked.length > 0 && checked.length < visible.length;
        }

        const overLimit = maxRecords > 0 && checked.length > maxRecords;
        if (printBtn) printBtn.disabled = checked.length === 0 || overLimit;

        if (countEl) {
            if (checked.length === 0) {
                countEl.textContent = '';
            } else if (overLimit) {
                countEl.textContent = `${checked.length} selected — limit is ${maxRecords}`;
            } else {
                countEl.textContent = `${checked.length} selected`;
            }
            countEl.classList.toggle('over-limit', overLimit);
        }
    }

    if (selectAll) {
        selectAll.addEventListener('change', () => {
            visibleBoxes().forEach((cb) => { cb.checked = selectAll.checked; });
            refresh();
        });
    }

    document.addEventListener('change', (e) => {
        const t = e.target;
        if (t && t.classList && t.classList.contains('row-select-checkbox')) refresh();
    });

    // table-filter-sort.js fires this after showing/hiding rows
    document.addEventListener('crud:filtered', refresh);

    if (printBtn && reportForm && reportIds) {
        printBtn.addEventListener('click', () => {
            reportIds.value = checkedVisible().map((cb) => cb.value).join(',');
            reportForm.submit();
        });
    }

    refresh();
})();
