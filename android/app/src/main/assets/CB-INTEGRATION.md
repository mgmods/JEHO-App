# JEHO CB shared HTML renderer

The Android asset `cb-renderer.js` is the shared presentation layer for CB/CP relationship identity. It contains no hard-coded level artwork: all colors, titles, badges, frames and card backgrounds come from the current relationship payload returned by the backend.

## Include it in an Android HTML asset

```html
<script src="cb-renderer.js"></script>
```

## Load relationship data

Pass the API base including `/api/v1` (for example `https://api-host.example/api/v1`) and use the public profile endpoint for the viewed account:

```http
GET /api/v1/cp/users/{userId}
```

The response includes the active relationship, `level`, `currentLevelConfig`, `presentation`, `benefits`, `giftPresentation`, and `giftBonusPercent`. Authenticated relationship workspace responses from `GET /api/v1/cp/me` include the same presentation fields.

## Render reusable pieces

```js
const relationship = rows[0]; // response from /api/v1/cp/users/{userId}
document.querySelector('#cb-level').innerHTML =
  window.JehoCB.renderLevelBadge(relationship);
document.querySelector('#cb-card').innerHTML =
  window.JehoCB.renderCard(relationship, '<h3>الثنائي</h3>');
document.querySelector('#profile-name').innerHTML =
  window.JehoCB.renderName(displayName, relationship);
document.querySelector('#cb-gift').innerHTML = window.JehoCB.renderDuoGift(relationship, { a: partnerAvatarUrl, b: userAvatarUrl });
window.JehoCB.apply(document, relationship);

// Or load and render in one call:
window.JehoCB.load(userId, apiBase, document).catch(console.error);
```

For declarative integration, mark elements with `data-cb-level`, `data-cb-card`, `data-cb-name`, or `data-cb-gift`, then call `window.JehoCB.apply(root, relationship)`.

All URLs are supplied by the API; do not hard-code image URLs in HTML pages. Keep user names escaped when constructing custom markup.


## Profile identity and relationship duration

Add a profile mount point where the CB identity should appear:

```html
<div data-cb-profile></div>
```

Then load the viewed account's active relationship:

```js
window.JehoCB.load(viewedUserId, apiBaseIncludingApiV1, document)
  .catch(console.error);
```

The profile summary renders the partner avatar and display name, configured level badge/title, and the relationship duration returned by the API. Keep this mount point in the profile identity area; other pages can use the smaller `data-cb-level`, `data-cb-name`, `data-cb-card`, and `data-cb-gift` mount points independently.
