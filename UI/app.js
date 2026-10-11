// Bookkeeper front end.
// For now, data is kept in the browser's localStorage. Only the four data
// functions (getEntries, addEntry, updateEntry, deleteEntry) touch storage.
// so they are the tonly code that chagnes when we connect to backend.

const STORAGE_KEY = 'bookkeeper-entries';

const SAMPLE_ENTRIES = [
    { id: 1, title: 'Basket Case', mediaType: 'MOVIE', customType: null, rating: null,
      review: 'Quintessential b-movie horror', dateCreated: '2026-09-20T18:00:00', dateUpdated: '2026-09-20T18:00:00' },
    { id: 2, title: 'The Hobbit', mediaType: 'BOOK', customType: null, rating: 8,
      review: 'All time classic high fantasy novel', dateCreated: '2026-09-20T18:00:00', dateUpdated: '2026-09-20T18:00:00' },
    { id: 3, title: 'Monopoly', mediaType: 'CUSTOM', customType: 'Board game', rating: 5,
      review: 'Fun depending on who you play with', dateCreated: '2026-09-24T18:00:00', dateUpdated: '2026-09-24T18:00:00' },
    { id: 4, title: 'Halo', mediaType: 'GAME', customType: null, rating: 10,
      review: 'This is my favorite game to play online', dateCreated: '2026-09-25T18:00:00', dateUpdated: '2026-09-25T18:00:00' },
    { id: 5, title: 'Shrek', mediaType: 'MOVIE', customType: null, rating: 2,
      review: 'I hate this movie tbh', dateCreated: '2026-09-27T18:00:00', dateUpdated: '2026-09-27T18:00:00' },
    { id: 6, title: 'Dune', mediaType: 'BOOK', customType: null, rating: 10,
      review: 'Frank Herbert is a genius', dateCreated: '2026-09-28T18:00:00', dateUpdated: '2026-09-28T18:00:00' }
];

// Storage helpers

function loadEntries() {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === null) {
        return [...SAMPLE_ENTRIES];     // first visit: start with a copy of the samples
    }
    return JSON.parse(saved);
}

function saveEntries(entries) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
}

// Data functions

function getEntries() {
    return loadEntries();
}

function addEntry(fields) {
    const entries = loadEntries();

    // Next id is one more than the highest id
    let nextId = 1;
    for (const e of entries) {
        if (e.id >= nextId) {
            nextId = e.id + 1;
        }
    }

    const now = new Date().toISOString();
    const entry = { id: nextId, ...fields, dateCreated: now, dateUpdated: now };

    entries.push(entry);
    saveEntries(entries);
    return entry;
}

// Dislay helpers

// "BOOK" -> "Book"; for CUSTOM, show the user's own label
function typeLabel(entry) {
    if (entry.mediaType === 'CUSTOM') {
        return entry.customType;
    }
    // charAt(0) is the first letter. slice(1) is the rest of the string.
    return entry.mediaType.charAt(0) + entry.mediaType.slice(1).toLowerCase();
}

// "2026-09-28T18:00:00" -> "Sep 28, 2026"
function formatDate(isoText) {
    const date = new Date(isoText);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric'});
}

// List page

function makeCell(text, className) {
    const cell = document.createElement('td');
    const span = document.createElement('span');
    span.textContent = text;
    if (className) {
        span.className = className;
    }
    cell.append(span);
    return cell;
}

function makeActionsCell(entry) {
    const cell = document.createElement('td');
    for (const label of ['Edit', 'Delete']) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'link-button';
        button.textContent = label;
        button.dataset.id = entry.id;
        button.dataset.action = label.toLowerCase();
        cell.append(button);
    }
    return cell;
}

function renderList(entries) {
    const body = document.getElementById('entries-body');
    const table = body.closest('table');
    const emptyState = document.getElementById('empty-state');
    const count = document.getElementById('entry-count');

    body.replaceChildren();     // remove any old names

    for (const entry of entries) {
        const row = document.createElement('tr');
        row.append(
            makeCell(entry.title),
            makeCell(typeLabel(entry), 'badge'),
            makeCell(entry.rating ? entry.rating + '/10' : '-'),
            makeCell(entry.review || 'No review yet', 'review'),
            makeCell(formatDate(entry.dateCreated)),
            makeActionsCell(entry)
        );
        body.append(row);
    }

    count.textContent = entries.length + (entries.length === 1 ? ' entry' : ' entries');
    table.hidden = entries.length === 0;
    emptyState.hidden = entries.length !== 0;
}

// Form page

function readForm() {
    const mediaType = document.getElementById('media-type').value;
    const ratingText = document.getElementById('rating').value;

    return {
        title: document.getElementById('title').value.trim(),
        mediaType: mediaType,
        customType: mediaType === 'CUSTOM' ? document.getElementById('custom-type').value.trim() : null,
        rating: ratingText === '' ? null : Number(ratingText),
        review: document.getElementById('review').value.trim()
    };
}

function showError(inputId, errorId, hasError) {
    document.getElementById(inputId).classList.toggle('invalid', hasError);
    document.getElementById(errorId).hidden = !hasError;
}

function validate(fields) {
    const titleMissing = fields.title === '';
    const typeMissing = fields.mediaType === '';
    const customMissing = fields.mediaType === 'CUSTOM' && fields.customType === '';

    showError('title', 'title-error', titleMissing);
    showError('media-type', 'media-type-error', typeMissing);
    showError('custom-type', 'custom-type-error', customMissing);

    return !titleMissing && !typeMissing && !customMissing;
}

function updateCustomField() {
    const isCustom = document.getElementById('media-type').value === 'CUSTOM';
    document.getElementById('custom-field').hidden = !isCustom;
}

function setUpForm() {
    const form = document.getElementById('entry-form');

    document.getElementById('media-type').addEventListener('change', updateCustomField);

    form.addEventListener('submit', (event) => {
        event.preventDefault();     // stop the page from reloading
        const fields = readForm();
        if (!validate(fields)) {
            return;                 // errors are showing
        }

        addEntry(fields);
        window.location.href = 'Index.html';    // back to the list
    });
}

// Start
// app.js is shared by Index.html and Form.html, so the list code
// runs only on the page that has the table.
if (document.getElementById('entries-body')) {
    renderList(getEntries());
}

if (document.getElementById('entry-form')) {
    setUpForm();
}