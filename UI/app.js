// Bookkeeper front end.
// Entries are stored by the Java backend (open the app at http://localhost:8080).
// Only the data functions talk to the backend, using the routes under /entries.

// Data functions

// Sends one request to the backend and returns its JSON answer.
// Errors come back as {"error": "message"} and are thrown as an Error with that message.
async function request(method, path, fields) {
    const options = { method: method };
    if (fields !== undefined) {
        options.headers = { 'Content-Type': 'application/json' };
        options.body = JSON.stringify(fields);
    }
    const response = await fetch(path, options);
    const result = await response.json();
    if (!response.ok) {
        throw new Error(result.error);
    }
    return result;
}

// All entries.
function getEntries() {
    return request('GET', '/entries');
}

// Saves a new entry. The server gives it an id and dates and returns it.
function addEntry(fields) {
    return request('POST', '/entries', fields);
}

// Replaces entry `id` with these fields and returns the updated entry.
function updateEntry(id, fields) {
    return request('PUT', '/entries/' + id, fields);
}

// Removes entry `id` and returns the removed entry.
function deleteEntry(id) {
    return request('DELETE', '/entries/' + id);
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
        button.dataset.title = entry.title;     // for the delete question
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

// Gets the entries from the backend and shows them.
function loadList() {
    getEntries()
        .then(renderList)
        .catch(error => alert('Could not load entries: ' + error.message));
}

// One click handler for the whole table: Edit opens the form, Delete asks first.
function setUpListActions() {
    document.getElementById('entries-body').addEventListener('click', async (event) => {
        const button = event.target.closest('button[data-action]');
        if (!button) {
            return;                 // the click wasn't on a button
        }
        const id = button.dataset.id;

        if (button.dataset.action === 'edit') {
            window.location.href = 'Form.html?id=' + id;
            return;
        }

        if (!confirm('Delete "' + button.dataset.title + '"? This can\'t be undone.')) {
            return;
        }
        try {
            await deleteEntry(id);
            loadList();             // show the list without it
        } catch (error) {
            alert('Could not delete the entry: ' + error.message);
        }
    });
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

// Edit mode: finds entry `id` and puts its details into the form.
async function fillForm(id) {
    try {
        const entry = (await getEntries()).find(e => String(e.id) === id);
        if (!entry) {
            alert('No entry with id ' + id + '. It may have been deleted.');
            window.location.href = 'Index.html';
            return;
        }

        document.title = 'Edit entry - Bookkeeper';
        document.getElementById('form-heading').textContent = 'Edit entry';
        document.getElementById('save-button').textContent = 'Save changes';

        document.getElementById('title').value = entry.title;
        document.getElementById('media-type').value = entry.mediaType;
        document.getElementById('custom-type').value = entry.customType || '';
        document.getElementById('rating').value = entry.rating === null ? '' : String(entry.rating);
        document.getElementById('review').value = entry.review || '';
        updateCustomField();

        const dates = document.getElementById('dates');
        dates.textContent = 'Added ' + formatDate(entry.dateCreated) + ' · Last updated ' + formatDate(entry.dateUpdated);
        dates.hidden = false;
    } catch (error) {
        alert('Could not load the entry: ' + error.message);
    }
}

function setUpForm() {
    const form = document.getElementById('entry-form');
    const id = new URLSearchParams(window.location.search).get('id');  // null when adding

    document.getElementById('media-type').addEventListener('change', updateCustomField);

    if (id !== null) {
        fillForm(id);
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();     // stop the page from reloading
        const fields = readForm();
        if (!validate(fields)) {
            return;                 // errors are showing
        }

        try {
            // wait until the server has saved it
            if (id === null) {
                await addEntry(fields);
            } else {
                await updateEntry(id, fields);
            }
            window.location.href = 'Index.html';    // back to the list
        } catch (error) {
            alert('Could not save the entry: ' + error.message);
        }
    });
}

// Start
// app.js is shared by Index.html and Form.html, so the list code
// runs only on the page that has the table.
if (document.getElementById('entries-body')) {
    setUpListActions();
    loadList();
}

if (document.getElementById('entry-form')) {
    setUpForm();
}