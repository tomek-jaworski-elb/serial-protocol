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

        // Declared before both buttons: each of them names the tile in its aria-label.
        const label = total > 1 ? `photo ${index + 1} of ${total}` : 'photo';

        // Only where there is something to choose between. Participant and course counter hold
        // exactly one image and their DTOs carry no pointer at all, so the button there would be
        // a visible no-op that announces success while Spring silently drops the parameter.
        if (describe.multiple) {
        // Marks this photo as the one shown in tables. Disabled while the tile is marked for
        // removal: the server would refuse a pointer to a photo that is about to go, so an
        // enabled button would promise something it cannot deliver.
        const primary = document.createElement('button');
        primary.type = 'button';
        primary.className = 'image-primary btn btn-sm';
        primary.dataset.uuid = uuid;
        primary.textContent = 'Główne';
        primary.setAttribute('aria-pressed', 'false');
        primary.setAttribute('aria-label', `Set ${label} as the main photo`);
        primary.addEventListener('click', () => choosePrimary(describe, uuid));
        tile.appendChild(primary);
        }

        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'image-remove btn btn-sm';
        button.setAttribute('aria-pressed', 'false');
        button.setAttribute('aria-label', `Remove ${label}`);
        // A plain multiplication sign, not a wastebasket emoji: the page ships no icon
        // font, and U+1F5D1 rendered as tofu in Chrome on Windows.
        button.innerHTML = '&times;';
        button.addEventListener('click', () => toggle(tile, button, input, label, describe));
        tile.appendChild(button);

        return tile;
    }

    /** Same order the server falls back to, so the preview matches the outcome. */
    function minByString(uuids) {
        return uuids.slice().sort()[0];
    }

    function tilesIn(describe) {
        return Array.from(describe.grid.querySelectorAll('.image-tile:not(.is-new)'));
    }

    /** Records an explicit choice: this is the only thing that enables the hidden field. */
    function choosePrimary(describe, uuid) {
        describe.chosen = uuid;
        if (describe.primaryField) {
            describe.primaryField.value = uuid;
            describe.primaryField.disabled = false;
        }
        paintPrimary(describe);
        announce(describe, 'main photo changed');
    }

    /**
     * Moves the marker when the current one is marked for removal, using the server's rule so
     * the preview cannot disagree with what is saved. Does NOT enable the hidden field — the
     * user has not chosen anything, so the server stays free to decide.
     */
    function paintPrimary(describe) {
        const kept = tilesIn(describe).filter((t) => !t.classList.contains('is-removed'));
        const keptUuids = kept.map((t) => t.querySelector('.image-primary').dataset.uuid);
        let current = describe.chosen;
        if (!current || keptUuids.indexOf(current) === -1) {
            current = minByString(keptUuids);
        }
        tilesIn(describe).forEach((tile) => {
            const btn = tile.querySelector('.image-primary');
            const isPrimary = btn.dataset.uuid === current;
            const removed = tile.classList.contains('is-removed');
            tile.classList.toggle('is-primary', isPrimary && !removed);
            btn.setAttribute('aria-pressed', isPrimary && !removed ? 'true' : 'false');
            btn.disabled = removed;
        });
    }

    function toggle(tile, button, input, label, describe) {
        const marked = tile.classList.toggle('is-removed');
        input.disabled = !marked;
        button.setAttribute('aria-pressed', marked ? 'true' : 'false');
        button.setAttribute('aria-label', marked ? `Undo removing ${label}` : `Remove ${label}`);
        button.innerHTML = marked ? 'Undo' : '&times;';
        announce(describe, marked ? `${label} marked for removal` : `${label} kept`);
        // Undo restores the previous marker rather than leaving it moved: cancelling the only
        // action that changed anything must leave the record as it was found.
        paintPrimary(describe);
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
    function render(describe, uuids, currentPrimary) {
        // The update form is shared by every row, so a file the user picked for one
        // record would otherwise still be attached when another record is saved —
        // silently, because the preview tiles are rebuilt below. Clearing the input
        // is what makes "open a different row" mean a clean slate.
        const fileInput = describe.root.querySelector('input[type=file]');
        if (fileInput) fileInput.value = '';
        // Same reason as the file input above, with a nastier failure: a pointer left over from
        // another row would be submitted for this one, the server would find it outside the set
        // and fall back — wiping a choice the user never touched on a record they only meant to
        // edit the phone number of.
        // Shows what the server currently considers primary, so the marker agrees with the
        // avatar in the table. Displaying it does not make it an explicit choice — the field
        // below stays disabled until the user actually clicks.
        describe.chosen = currentPrimary || null;
        if (describe.primaryField) {
            describe.primaryField.value = '';
            describe.primaryField.disabled = true;
        }
        describe.grid.querySelectorAll('.image-tile.is-new img').forEach((img) => {
            URL.revokeObjectURL(img.src);
        });
        describe.grid.replaceChildren();
        uuids.forEach((uuid, i) => {
            describe.grid.appendChild(buildTile(uuid, i, uuids.length, describe));
        });
        describe.empty.classList.toggle('d-none', uuids.length > 0);
        paintPrimary(describe);
        refreshCount(describe);
    }

    function describeEditor(root) {
        return {
            root,
            grid: root.querySelector('.image-grid'),
            empty: root.querySelector('.image-empty'),
            counter: root.querySelector('.image-count'),
            live: root.querySelector('.image-live'),
            // Named primaryField, not field: describeEditor already uses `field` for the NAME
            // of the removal input. Reusing the key silently overwrote this element with that
            // string, and the pointer was never submitted.
            primaryField: root.querySelector('.image-primary-field'),
            chosen: null,
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
                render(describe, uuids, btn.dataset.primaryImage);
            });
        });
    });
})();
