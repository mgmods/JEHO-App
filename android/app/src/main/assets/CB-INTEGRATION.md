# JEHO CB shared HTML renderer

The Android asset `cb-renderer.js` is the shared presentation layer for CB/CP relationship identity. It contains no hard-coded level artwork: all colors, titles, badges, frames and card backgrounds come from the current relationship payload returned by the backend.

## Include it in an Android HTML asset

```html
<script src="cb-renderer.js"></script>
```

## Load relationship data

Use the public profile endpoint for the viewed account:

```http
GET /api/cp/users/{userId}
```

The response includes the active relationship, `level`, `currentLevelConfig`, `presentation`, `benefits`, `giftPresentation`, and `giftBonusPercent`. Authenticated relationship workspace responses from `GET /api/cp/me` include the same presentation fields.

## Render reusable pieces

```js
const relationship = rows[0]; // response from /api/cp/users/{userId}
document.querySelector('#cb-level').innerHTML =
  window.JehoCB.renderLevelBadge(relationship);
document.querySelector('#cb-card').innerHTML =
  window.JehoCB.renderCard(relationship, '<h3>الثنائي</h3>');
document.querySelector('#profile-name').innerHTML =
  window.JehoCB.renderName(displayName, relationship);
window.JehoCB.apply(document, relationship);
```

For declarative integration, mark elements with `data-cb-level`, `data-cb-card`, or `data-cb-name`, then call `window.JehoCB.apply(root, relationship)`.

All URLs are supplied by the API; do not hard-code image URLs in HTML pages. Keep user names escaped when constructing custom markup.
