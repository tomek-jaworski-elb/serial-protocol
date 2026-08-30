/*
 * Photo editor for the custom/* update forms.
 *
 * Each page has ONE update form, filled in by JavaScript from the clicked row's
 * data-* attributes, so the thumbnails cannot be rendered server-side per row —
 * they are built here when the form opens.
 *
 * The form submits the photos to REMOVE, never the ones to keep. That direction is
 * deliberate: if this script failed to draw the tiles, a "keep these" list would
 * arrive empty and the server would wipe every photo. With removals, a broken
 * editor removes nothing.
 */
(function () {
    const MAX_IMAGES = 6;

    /**
     * data-images is a plain comma-separated list: the DTOs expose imagesUuidString
     * rather than the Set itself, whose toString form "[a, b]" every consumer used to
     * have to unwrap with its own copy of a bracket-stripping helper.
     */
    function uuidList(value) {
        return (value || '')
            .split(',')
            .map((s) => s.trim())
            .filter((s) => s && s !== 'null');
    }

    function singleUuid(value) {
        return value && value !== 'null' && value.trim() !== '' ? [value.trim()] : [];
    }

    function buildTile(uuid, index, total, describe) {
        const tile = document.createElement('div');
        tile.className = 'image-tile';

        const img = document.createElement('img');
        img.src = `/custom/image/${uuid}?size=thumb`;
        img.width = 96;
        img.height = 96;
        img.loading = 'lazy';
        // The name sits next to the tile in the form, so repeating it here would make
        // a screen reader read it twice; the button below carries the real label.
        img.alt = '';
        tile.appendChild(img);

        // Disabled means "not submitted". Marking a tile enables it, which is what puts
        // the uuid into removeImageUuids.
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = describe.field;
        input.value = describe.field === 'removeImage' ? 'true' : uuid;
        input.disabled = true;
        tile.appendChild(input);

        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'image-remove btn btn-sm';
        button.setAttribute('aria-pressed', 'false');
        const label = total > 1 ? `photo ${index + 1} of ${total}` : 'photo';
        button.setAttribute('aria-label', `Remove ${label}`);
        // A plain multiplication sign, not a wastebasket emoji: the page ships no icon
        // font, and U+1F5D1 rendered as tofu in Chrome on Windows.
        button.innerHTML = '&times;';
        button.addEventListener('click', () => toggle(tile, button, input, label, describe));
        tile.appendChild(button);

        return tile;
    }

    function toggle(tile, button, input, label, describe) {
        const marked = tile.classList.toggle('is-removed');
        input.disabled = !marked;
        button.setAttribute('aria-pressed', marked ? 'true' : 'false');
        button.setAttribute('aria-label', marked ? `Undo removing ${label}` : `Remove ${label}`);
        button.innerHTML = marked ? 'Undo' : '&times;';
        announce(describe, marked ? `${label} marked for removal` : `${label} kept`);
        refreshCount(describe);
    }

    function announce(describe, message) {
        if (describe.live) describe.live.textContent = message;
    }

    /** Marked-for-removal tiles free a slot; freshly picked files take one. */
    function keptCount(describe) {
        return describe.grid.querySelectorAll('.image-tile:not(.is-removed)').length;
    }

    function refreshCount(describe) {
        if (!describe.counter) return;
        const kept = keptCount(describe);
        if (describe.multiple) {
            const free = Math.max(0, MAX_IMAGES - kept);
            describe.counter.textContent = free === 0
                ? 'No slots left.'
                : `${free} slot${free === 1 ? '' : 's'} left.`;
        } else {
            describe.counter.textContent = kept === 0 ? 'No photo.' : '';
        }
    }

    /**
     * Rebuilds the tiles for one record. Called every time the update form opens, so
     * it must clear first: the form is shared between rows and stale tiles from a
     * previously opened row would otherwise linger.
     */
    function render(describe, uuids) {
        // The update form is shared by every row, so a file the user picked for one
        // record would otherwise still be attached when another record is saved —
        // silently, because the preview tiles are rebuilt below. Clearing the input
        // is what makes "open a different row" mean a clean slate.
        const fileInput = describe.root.querySelector('input[type=file]');
        if (fileInput) fileInput.value = '';
        describe.grid.querySelectorAll('.image-tile.is-new img').forEach((img) => {
            URL.revokeObjectURL(img.src);
        });
        describe.grid.replaceChildren();
        uuids.forEach((uuid, i) => {
            describe.grid.appendChild(buildTile(uuid, i, uuids.length, describe));
        });
        describe.empty.classList.toggle('d-none', uuids.length > 0);
        refreshCount(describe);
    }

    function describeEditor(root) {
        return {
            root,
            grid: root.querySelector('.image-grid'),
            empty: root.querySelector('.image-empty'),
            counter: root.querySelector('.image-count'),
            live: root.querySelector('.image-live'),
            multiple: root.dataset.multiple === 'true',
            field: root.dataset.multiple === 'true' ? 'removeImageUuids' : 'removeImage'
        };
    }

    /**
     * Shows what the user just picked, before anything is uploaded.
     *
     * These previews carry no remove button on purpose: a file cannot be taken out of
     * an <input type="file"> without rebuilding its FileList, and a button that looked
     * like the one on stored photos but behaved differently would be worse than none.
     * Re-picking replaces the whole selection, which is what the control already does.
     */
    function previewSelection(describe, files) {
        describe.grid.querySelectorAll('.image-tile.is-new').forEach((t) => {
            URL.revokeObjectURL(t.querySelector('img').src);
            t.remove();
        });

        Array.from(files).forEach((file) => {
            const tile = document.createElement('div');
            tile.className = 'image-tile is-new';
            const img = document.createElement('img');
            img.src = URL.createObjectURL(file);
            img.alt = '';
            tile.appendChild(img);
            const badge = document.createElement('span');
            badge.className = 'image-new-badge';
            badge.textContent = 'New';
            tile.appendChild(badge);
            describe.grid.appendChild(tile);
        });

        describe.empty.classList.toggle('d-none', describe.grid.children.length > 0);
        refreshCount(describe);
    }

    document.addEventListener('DOMContentLoaded', () => {
        // Add and update forms each carry their own editor.
        document.querySelectorAll('.image-editor').forEach((root) => {
            if (!root.querySelector('.image-grid')) return;
            const describe = describeEditor(root);
            const fileInput = root.querySelector('input[type=file]');
            if (fileInput) {
                fileInput.addEventListener('change', () => previewSelection(describe, fileInput.files));
            }
            root.__editor = describe;
        });

        // Every update button on the page feeds the editor inside the update form.
        const updateEditor = document.querySelector('form[action$="/update"] .image-editor')
            || document.querySelector('.image-editor');
        if (!updateEditor || !updateEditor.__editor) return;
        const describe = updateEditor.__editor;

        document.querySelectorAll('[data-image-source]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const raw = btn.dataset.images !== undefined ? btn.dataset.images : btn.dataset.image;
                const uuids = describe.multiple ? uuidList(raw) : singleUuid(raw);
                render(describe, uuids);
            });
        });
    });
})();
